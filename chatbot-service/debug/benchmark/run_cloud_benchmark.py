"""Conservative, resumable Gemini benchmark; reuses local cases and graders.

Bounded 503 case retries, no parallel POSTs, no Ollama fallback. Runtime budgets are local
bench limits, NOT a guarantee of a Google project quota or monetary limit.
"""
import argparse
import fcntl
import hashlib
import json
import copy
import random
from pathlib import Path
import sys
import time
import uuid
from datetime import datetime, timezone

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import run_benchmark as baseline

ROOT = baseline.SERVICE.parent
WORST_CALLS_PER_TURN = 3  # current QU: 1 + 1 repair, response generation: <= 1


class SafetyStop(Exception):
    pass


def write_json(path, data):
    tmp = path.with_suffix(path.suffix + '.tmp')
    tmp.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')
    tmp.replace(path)


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, default=str).encode()).hexdigest()


def source_digest():
    h = hashlib.sha256()
    for p in sorted((baseline.SERVICE / 'app').rglob('*.py')):
        h.update(str(p.relative_to(baseline.SERVICE)).encode())
        h.update(p.read_bytes())
    return h.hexdigest()


class Guard:
    def __init__(self, state, save, interval, max_calls, max_tokens, *, clock=time.time, sleep=time.sleep):
        self.state, self.save = state, save
        self.interval, self.max_calls, self.max_tokens = interval, max_calls, max_tokens
        self.clock, self.sleep = clock, sleep

    def before(self):
        if self.state.get('inflight'):
            raise SafetyStop('AMBIGUOUS_INFLIGHT: không gửi lại lượt chưa rõ kết quả; cần review trace trước.')
        if self.state['charged_calls'] + WORST_CALLS_PER_TURN > self.max_calls:
            raise SafetyStop('MODEL_CALL_BUDGET: không đủ ngân sách dự phòng 3 lần gọi cho lượt kế tiếp.')
        if self.state['tokens'] >= self.max_tokens:
            raise SafetyStop('TOKEN_BUDGET: đã chạm ngưỡng token quan sát; không gửi lượt kế tiếp.')
        while True:
            wait = max(self.state.get('last_finished_at', 0) + self.interval,
                       (self.state.get('pending_retry') or {}).get('not_before', 0)) - self.clock()
            if wait <= 0:
                return
            self.sleep(min(wait, 30))

    def reserve(self, trace_id, session_id):
        self.state['inflight'] = {'trace_id': trace_id, 'session_id': session_id}
        self.state['charged_calls'] += WORST_CALLS_PER_TURN
        self.save()

    def settle(self, events):
        attempts = sum(e['name'] == 'gemini.chat' for e in events)
        usage = [e.get('output') or {} for e in events if e['name'] == 'gemini.usage']
        self.state['observed_calls'] += attempts
        self.state['tokens'] += sum(u.get('totalTokenCount', 0) for u in usage)
        self.state['last_finished_at'] = self.clock()
        # Do not refund a reservation when trace evidence is missing.
        if attempts:
            self.state['charged_calls'] += max(0, attempts - WORST_CALLS_PER_TURN) - max(0, WORST_CALLS_PER_TURN - attempts)
        self.save()  # inflight cleared ONLY after turn result is checkpointed
        return attempts


def cloud_evidence(trace):
    names = {'llm.provider_selected', 'gemini.chat', 'gemini.http_status', 'gemini.usage',
             'query.plan.initial', 'query.plan.effective', 'query.validation',
             'query.domain_validation', 'conversation.reference_resolution', 'response.strategy'}
    return [{k: e.get(k) for k in ('name', 'status', 'offset_ms', 'duration_ms', 'output')}
            for e in trace.get('events', []) if e['name'] in names]


def stop_reason(http_status, trace):
    events = trace.get('events', [])
    if http_status != 200:
        return f'HTTP_{http_status}: cần kiểm tra mã upstream trước khi quyết định retry.'
    if not events:
        return 'MISSING_TRACE: không đủ bằng chứng để tiếp tục gọi cloud.'
    selected = [e.get('output') or {} for e in events if e['name'] == 'llm.provider_selected']
    if not selected or any(e.get('provider') != 'gemini' for e in selected):
        return 'PROVIDER_MISMATCH: không xác nhận được Gemini.'
    if any(e['name'] == 'ollama.chat' for e in events):
        return 'LOCAL_CHAT_DETECTED: dừng để tránh trộn kết quả.'
    if any(e['name'] == 'gemini.chat' and e.get('status') != 'ok' for e in events):
        return 'CLOUD_ATTEMPT_FAILED: không lặp lại.'
    if any(e['name'] == 'gemini.http_status' and (e.get('output') or {}).get('status_code') != 200 for e in events):
        return 'CLOUD_HTTP_ERROR: dừng ngay, gồm 429/5xx.'
    return None


def retryable_503(evidence):
    """Local API 503 alone is insufficient: require an actual Gemini HTTP 503."""
    events = evidence.get('events', [])
    providers = [e.get('output') or {} for e in events if e['name'] == 'llm.provider_selected']
    codes = [(e.get('output') or {}).get('status_code') for e in events if e['name'] == 'gemini.http_status']
    return (evidence.get('http_status') == 503 and bool(providers)
            and all(p.get('provider') == 'gemini' for p in providers)
            and 503 in codes and all(code in (200, 503) for code in codes)
            and not evidence.get('local_chat_detected', False))


def schedule_case_retry(state, record, evidence, run_dir, max_retries, *, now=time.time, jitter=None):
    """Archive the ENTIRE attempt; never replay a failed POST in its old session."""
    count = state.setdefault('retry_counts', {}).get(record['id'], 0)
    if not retryable_503(evidence) or count >= max_retries:
        return False
    count += 1
    delay = min(30 * 2 ** (count - 1), 300) + (random.uniform(0, 5) if jitter is None else jitter)
    attempt_path = run_dir/'attempts'/f'{record["id"]}-failed-{count:02}.json'
    attempt_path.parent.mkdir(exist_ok=True)
    write_json(attempt_path, copy.deepcopy(record))
    state['retry_counts'][record['id']] = count
    state.setdefault('retry_events', []).append({'case_id': record['id'], 'retry': count,
        'failed_trace_id': evidence['trace_id'], 'delay_seconds': round(delay, 2),
        'archive': str(attempt_path.relative_to(run_dir)), 'reason': 'GEMINI_HTTP_503'})
    state['pending_retry'] = {'case_id': record['id'], 'retry': count, 'not_before': now() + delay}
    state['results'].remove(record)
    return True


class CloudClient:
    """Small wrapper to reuse baseline.send's response parsing and grader inputs."""
    def __init__(self, client, guard):
        self.client, self.guard = client, guard
        self.last = None

    def get(self, *args, **kwargs):
        return self.client.get(*args, **kwargs)

    def post(self, path, *, json, headers):
        self.guard.before()
        trace_id = headers['X-Chat-Trace-Id']
        session_id = path.split('/sessions/')[1].split('/')[0]
        self.guard.reserve(trace_id, session_id)
        try:
            response = self.client.post(path, json={**json, 'provider': 'gemini'}, headers=headers)
        except Exception:
            self.guard.state['last_finished_at'] = time.time()
            self.guard.save()
            raise SafetyStop('POST_TRANSPORT_ERROR: kết quả không rõ; không tự gửi lại.') from None
        try:
            tr = self.client.get(f'/api/v1/chat/sessions/{session_id}/traces/{trace_id}')
            trace = tr.json() if tr.status_code == 200 else {}
        except Exception:
            trace = {}
        self.guard.settle(trace.get('events', []))
        self.last = {'trace_id': trace_id, 'http_status': response.status_code,
                     'trace_status': trace.get('status'), 'events': cloud_evidence(trace),
                     'local_chat_detected': any(e['name'] == 'ollama.chat' for e in trace.get('events', [])),
                     'stop_reason': stop_reason(response.status_code, trace)}
        return response


def report(state, path):
    rows = [(c, t) for c in state['results'] for t in c['turns']]
    counts = {v: sum(t['verdict'] == v for _, t in rows) for v in ('PASS', 'FAIL', 'INFO', 'BLOCKED_ENVIRONMENT')}
    complete = len(rows) == state['planned_turns'] and not state.get('stop_reason') and not counts['BLOCKED_ENVIRONMENT']
    lines = ['# Gemini cloud benchmark', '', f"Model: `{state['model']}`; bắt đầu: {state['started_at']}; commit `{state['commit']}`.",
             '', f"**{'COMPLETE' if complete else 'INCOMPLETE'}** — {len(rows)}/{state['planned_turns']} lượt, {len(state['results'])}/{state['planned_cases']} case đã bắt đầu.",
             f"Verdicts: {counts}. Gemini calls quan sát: {state['observed_calls']}; quota dự phòng đã tính: {state['charged_calls']}; token: {state['tokens']}.",
             f"Dừng: {state.get('stop_reason') or 'không'}.", '',
             f"Chạy tuần tự; retry 503 tối đa {state.get('policy', {}).get('max_503_retries', 0)} lần/case; không đổi provider; lưu sau từng lượt.",
             f"Số case retry đã lên lịch: {len(state.get('retry_events', []))}. Lần lỗi lưu riêng trong attempts/, không tính là kết quả chất lượng cuối.",
             f"Nghỉ {state.get('policy', {}).get('interval', 20)} giây sau mỗi lượt; trong một lượt pipeline vẫn có thể gọi model tối đa 3 lần (QU repair + generation).",
             'User báo quota 15 request/phút, 250.000 token/phút. Mặc định 20 giây giữa các lượt: dự phòng tối đa khoảng 9 call/phút với contract hiện tại, chừa khoảng trống cho lượt gọi khác.',
             'Trần token là ngưỡng quan sát sau response, không phải hard billing cap. Hạn mức Google thực theo project/model chưa được xác minh.',
             'Không chấm điểm toàn bộ khi chưa chạy đủ; lượt chưa chạy là NOT_RUN, không phải PASS.', '',
             '| Case | Lượt | Verdict | Trace | Thời gian |', '|---|---|---|---|---|']
    for c, t in rows:
        lines.append(f"| {c['id']} | {t['turn']} | {t['verdict']} | `{t.get('trace_id', '')}` | {t['seconds']}s |")
    lines += ['', 'Kết quả local tham chiếu: `../20261027T110447Z/results.json`. Khác ngày/code/data nên không quy mọi chênh lệch cho model.',
              'Evidence chi tiết trong `results.json` và `evidence/`. Không chứa API key, cookies hoặc Authorization headers.',
              'Giỏ/đơn/bảo hành so sánh fingerprint trước/sau; read-only snapshot có giới hạn phân trang của tool cũ, không phải audit toàn DB.',
              f"Business fingerprint thay đổi: {state.get('writes_detected', 'UNKNOWN')}."]
    path.write_text('\n'.join(lines) + '\n')


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--interval', type=float, default=20)
    p.add_argument('--max-model-calls', type=int, default=50)
    p.add_argument('--max-tokens', type=int, default=100000)
    p.add_argument('--max-503-retries', type=int, default=5)
    p.add_argument('--resume', type=Path)
    p.add_argument('--plan-only', action='store_true')
    args = p.parse_args()
    if args.interval < 20 or args.max_model_calls < 3 or args.max_tokens < 1 or not 0 <= args.max_503_retries <= 10:
        p.error('interval >=20, model budget >=3, token budget >0, 503 retries 0..10')
    groups = [json.loads(f.read_text()) for f in sorted((HERE/'cases').glob('*.json'))]
    cases = [(g, c) for g in groups for c in g['cases']]
    print(f'PLAN {len(cases)} cases / {sum(len(c["turns"]) for _, c in cases)} turns; serial; 503 retries <= {args.max_503_retries}; interval {args.interval}s', flush=True)
    if args.plan_only:
        return
    lockpath = ROOT/'.local/cloud-benchmark.lock'; lockpath.parent.mkdir(exist_ok=True)
    with lockpath.open('a+') as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise SystemExit('Một cloud benchmark đang chạy; không tạo run thứ hai.')
        return execute(args, cases)


def execute(args, cases):
    run_dir = args.resume or HERE/'runs'/(datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-gemini')
    run_dir.mkdir(parents=True, exist_ok=True); (run_dir/'evidence').mkdir(exist_ok=True)
    state_path = run_dir/'results.json'
    if args.resume and not state_path.exists():
        raise SystemExit('Không có results.json để resume.')
    client = baseline.login()
    try:
        status = client.get('/api/v1/chat/status'); status.raise_for_status(); status = status.json()
        cfg = status.get('providers', {}).get('gemini', {})
        if not cfg.get('enabled'):
            raise SafetyStop('Gemini chưa bật; không chạy.')
        if not status.get('ai_enabled'):
            raise SafetyStop('AI chưa bật; không chạy.')
        if not status.get('trace_enabled') or status.get('product_mode') != 'real':
            raise SafetyStop('Cần trace bật và Product Advisor real; không chạy benchmark thiếu evidence.')
        catalog = baseline.load_catalog()
        personal = baseline.personal_snapshot(client)
        fingerprint = digest(baseline.write_fingerprint(personal))
        plan_hash = digest(cases)
        current_source = source_digest()
        if args.resume:
            state = json.loads(state_path.read_text())
            if state['model'] != cfg['model'] or state['plan_hash'] != plan_hash or state['source_hash'] != current_source or state['business_before'] != fingerprint:
                raise SafetyStop('RESUME_MISMATCH: model/cases/source/business data khác run cũ.')
            if state.get('inflight'):
                raise SafetyStop('AMBIGUOUS_INFLIGHT: cần review trace trước khi resume.')
        else:
            state = {'started_at': datetime.now(timezone.utc).isoformat(), 'model': cfg['model'],
                     'commit': baseline.subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),
                     'source_hash': current_source, 'plan_hash': plan_hash, 'business_before': fingerprint,
                     'planned_cases': len(cases), 'planned_turns': sum(len(c['turns']) for _,c in cases),
                     'charged_calls': 0, 'observed_calls': 0, 'tokens': 0, 'results': [], 'inflight': None}
        state['stop_reason'] = None
        state['policy'] = {'interval': args.interval, 'max_model_calls': args.max_model_calls, 'max_tokens': args.max_tokens, 'max_503_retries': args.max_503_retries}
        def save():
            write_json(state_path, state)
            report(state, run_dir/'REPORT.md')
        guard = Guard(state, save, args.interval, args.max_model_calls, args.max_tokens)
        cloud = CloudClient(client, guard); grader = baseline.Grader(catalog, personal)
        save(); print('RUN_DIR', run_dir, flush=True)
        try:
            for group, case in cases:
                while True:
                    record = next((r for r in state['results'] if r['id'] == case['id']), None)
                    if record and record['turns'] and record['turns'][-1]['verdict'] == 'BLOCKED_ENVIRONMENT':
                        last = record['turns'][-1]
                        ep = run_dir/'evidence'/f'{case["id"]}-{last["turn"]:02}-{last["trace_id"]}.json'
                        if not ep.exists():  # compatible with first, no-retry cloud run
                            ep = run_dir/'evidence'/f'{case["id"]}-{last["turn"]:02}.json'
                        previous = json.loads(ep.read_text()) if ep.exists() else {}
                        if schedule_case_retry(state, record, previous, run_dir, args.max_503_retries):
                            save()
                            retry = state['pending_retry']
                            print(f'WAIT {case["id"]}: 503 retry {retry["retry"]}/{args.max_503_retries}; new session, replay case from turn 1', flush=True)
                            continue
                        raise SafetyStop(f'{case["id"]}: retry exhausted or non-retryable cloud failure; review evidence.')
                    if record is None:
                        record = {k: case[k] for k in ('id','title','difficulty','probe')}
                        record.update(group=group['group'], group_title=group['title'], session_id=str(uuid.uuid4()), turns=[], history=[])
                    for n, turn in enumerate(case['turns'],1):
                        if n <= len(record['turns']):
                            continue
                        if turn.get('new_session'):
                            record['session_id'], record['history'] = str(uuid.uuid4()), []
                        result = baseline.send(cloud, record['session_id'], turn['say'])
                        evidence = cloud.last
                        if evidence['stop_reason']:
                            verdict, fails, notes = 'BLOCKED_ENVIRONMENT', [], [evidence['stop_reason']]
                        else:
                            try:
                                verdict, fails, notes = grader.grade(turn['expect'], result, record['history'])
                            except Exception as exc:
                                verdict, fails, notes = 'FAIL', ['GRADER_ERROR'], [type(exc).__name__]
                        row = {'turn':n, 'say':turn['say'], 'verdict':verdict,'failures':fails,'notes':notes,
                               'reply':result['message'],'cards':result['cards'],'filters':result['filters'],
                               'strategy':result['strategy'],'seconds':result['seconds'],'comparison':bool(result['comparison']),
                               'sources':len(result['sources']),'trace_id':evidence['trace_id'],'http_status':result['status']}
                        record['history'].append(result); record['turns'].append(row)
                        record['task_success'] = len(record['turns']) == len(case['turns']) and all(t['verdict'] in ('PASS','INFO') for t in record['turns'])
                        if record not in state['results']:
                            state['results'].append(record)
                        write_json(run_dir/'evidence'/f'{case["id"]}-{n:02}-{evidence["trace_id"]}.json', evidence)
                        state['inflight'] = None
                        state['pending_retry'] = None
                        save(); print(f'{case["id"]}.{n}: {verdict}; calls={state["observed_calls"]} tokens={state["tokens"]}',flush=True)
                        if evidence['stop_reason']:
                            break
                    if record['turns'] and record['turns'][-1]['verdict'] == 'BLOCKED_ENVIRONMENT':
                        continue
                    break
        except (SafetyStop, KeyboardInterrupt) as exc:
            state['stop_reason'] = str(exc) or 'INTERRUPTED'
            print('STOP',state['stop_reason'],flush=True)
        except Exception as exc:
            state['stop_reason'] = f'UNEXPECTED_{type(exc).__name__}: cần review, không tự gửi lại.'
            print('STOP', state['stop_reason'], flush=True)
        finally:
            try:
                after = baseline.personal_snapshot(client)
                state['writes_detected'] = fingerprint != digest(baseline.write_fingerprint(after))
            except Exception:
                state['writes_detected'] = 'UNKNOWN: snapshot failed'
            state['updated_at'] = datetime.now(timezone.utc).isoformat()
            state['case_statuses'] = {c['id']: ('BLOCKED_ENVIRONMENT' if any(r['id']==c['id'] and any(t['verdict']=='BLOCKED_ENVIRONMENT' for t in r['turns']) for r in state['results'])
                                             else 'COMPLETED' if any(r['id']==c['id'] and len(r['turns'])==len(c['turns']) for r in state['results'])
                                             else 'PARTIAL' if any(r['id']==c['id'] for r in state['results']) else 'NOT_RUN') for _,c in cases}
            observed = {(r['id'], t['turn']): t['verdict'] for r in state['results'] for t in r['turns']}
            state['turn_statuses'] = {f'{c["id"]}.{n}': observed.get((c['id'], n), 'NOT_RUN')
                                      for _, c in cases for n in range(1, len(c['turns']) + 1)}
            save()
        return 2 if state.get('stop_reason') else 0
    finally:
        client.close()


if __name__ == '__main__':
    sys.exit(main())
