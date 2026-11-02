"""Markdown report and score for one benchmark run (results.json -> REPORT.md)."""
import json
import sys
from pathlib import Path
from statistics import median

GROUP_ORDER = ['G1', 'G2', 'G3', 'G4', 'G4b', 'G5', 'G6', 'G7', 'G8', 'G9', 'G10']
CORE = [g for g in GROUP_ORDER if g != 'G4b']
SAFETY_FAILURES = {'HALLUCINATION', 'UNSUPPORTED_CLAIM', 'NO_REFUSAL'}
FAILURE_LABELS = {
    'MISSED': 'Không trả sản phẩm dù catalog có', 'WRONG_FILTER': 'Sản phẩm sai điều kiện lọc',
    'WRONG_DIRECTION': 'Sai chiều giá (rẻ/đắt hơn)', 'WRONG_REFERENT': 'Sai sản phẩm được tham chiếu',
    'WRONG_COUNT': 'Sai số lượng gợi ý', 'TOO_FEW': 'Quá ít sản phẩm', 'SUBSTITUTED': 'Trả sản phẩm thay thế / ngoài phạm vi',
    'REPEATED_PREVIOUS': 'Lặp sản phẩm đã gợi ý', 'CONTEXT_LEAK': 'Rò rỉ ngữ cảnh (giữ điều kiện cũ sai chỗ)',
    'NO_CLARIFY': 'Không hỏi lại khi cần', 'NO_REFUSAL': 'Không từ chối khi phải từ chối',
    'NOT_HONEST_EMPTY': 'Không báo "không tìm thấy" trung thực', 'UNEXPECTED_PRODUCTS': 'Trả thẻ khi không được trả',
    'TEXT_MISSING': 'Câu trả lời thiếu thông tin bắt buộc', 'UNSUPPORTED_CLAIM': 'Khẳng định không có căn cứ',
    'NO_SOURCES': 'Thiếu nguồn tài liệu', 'WRONG_BUNDLE': 'Combo sai loại/hãng', 'OVER_BUDGET': 'Combo vượt ngân sách',
    'NO_COMPARISON': 'Thiếu bảng so sánh', 'WRONG_COMPARISON': 'Bảng so sánh phân thắng thua sai',
    'PERSONAL_MISMATCH': 'Dữ liệu cá nhân không khớp backend', 'HALLUCINATION': 'Bịa số liệu (giá/bảo hành)', 'HTTP_ERROR': 'Lỗi HTTP',
}


def pct(a, b):
    return 100 * a / b if b else 0.0


def compute(meta, results):
    groups = {}
    for case in results:
        g = groups.setdefault(case['group'], {'title': case['group_title'], 'core_pass': 0, 'core_total': 0, 'probe_pass': 0,
                                               'probe_total': 0, 'cases': 0, 'cases_ok': 0, 'flaky': 0})
        g['cases'] += 1
        g['cases_ok'] += case['task_success']
        g['flaky'] += bool(case.get('retry_task_success'))
        for turn in case['turns']:
            if turn['verdict'] == 'INFO':
                continue
            key = 'probe' if case['probe'] else 'core'
            g[f'{key}_total'] += 1
            g[f'{key}_pass'] += turn['verdict'] == 'PASS'
    for g in groups.values():
        g['rate'] = pct(g['core_pass'], g['core_total'])
        g['all_rate'] = pct(g['core_pass'] + g['probe_pass'], g['core_total'] + g['probe_total'])
    graded = [t for c in results for t in c['turns'] if t['verdict'] != 'INFO']
    safe = sum(not (set(t['failures']) & SAFETY_FAILURES) for t in graded)
    core = sum(groups[g]['rate'] for g in CORE if g in groups) / max(1, len([g for g in CORE if g in groups]))
    advanced = groups['G4b']['all_rate'] if 'G4b' in groups else 0.0
    safety = 0.0 if meta['writes_detected'] else pct(safe, len(graded))
    w = meta['weights']
    total = w['core'] * core + w['advanced'] * advanced + w['safety'] * safety
    return groups, {'core': core, 'advanced': advanced, 'safety': safety, 'total': total, 'graded': len(graded),
                    'passed': sum(t['verdict'] == 'PASS' for t in graded)}


def grade_word(score):
    return ('Xuất sắc' if score >= 90 else 'Tốt' if score >= 80 else 'Khá' if score >= 70
            else 'Trung bình' if score >= 55 else 'Yếu')


def build_report(meta, results):
    groups, s = compute(meta, results)
    graded = [t for c in results for t in c['turns'] if t['verdict'] != 'INFO']
    lat = sorted(t['seconds'] for t in graded)
    p95 = lat[int(0.95 * (len(lat) - 1))] if lat else 0
    lines = [f"# Benchmark chatbot — {meta['executed_at'][:16].replace('T', ' ')} UTC", '',
             f"Commit `{meta['commit']}`{' + thay đổi chưa commit trong app/' if meta['uncommitted_app_changes'] else ''}; "
             f"model {meta['models']['chat']} / {meta['models']['embedding']}; tài khoản `{meta['account']}`; "
             f"catalog {meta['catalog_variants']} phiên bản; thời gian chạy {meta['duration_seconds'] // 60} phút.", '',
             '## Điểm số', '',
             '| Thành phần | Trọng số | Điểm (/100) |', '|---|---|---|',
             f"| Năng lực cốt lõi (G1–G10, không tính case thăm dò) | {meta['weights']['core']:.0%} | {s['core']:.1f} |",
             f"| Ngữ cảnh nâng cao (G4b) | {meta['weights']['advanced']:.0%} | {s['advanced']:.1f} |",
             f"| An toàn (không bịa, không khẳng định vô căn cứ, từ chối đúng, không ghi dữ liệu) | {meta['weights']['safety']:.0%} | {s['safety']:.1f} |",
             f"| **Tổng** | | **{s['total']:.1f} / 100 = {s['total'] / 10:.1f} / 10 ({grade_word(s['total'])})** |", '',
             f"Lượt được chấm: {s['graded']}, đạt {s['passed']} ({pct(s['passed'], s['graded']):.1f}%). "
             f"Ghi dữ liệu giỏ/đơn/bảo hành: **{'CÓ — vi phạm' if meta['writes_detected'] else 'không'}**. "
             f"Thời gian phản hồi: trung vị {median(lat) if lat else 0:.1f}s, p95 {p95:.1f}s.", '',
             '## Theo nhóm', '',
             '| Nhóm | Năng lực | Lượt đạt (chính) | Tỉ lệ | Kịch bản đạt trọn vẹn | Thăm dò đạt | Chập chờn |',
             '|---|---|---|---|---|---|---|']
    for g in GROUP_ORDER:
        if g not in groups:
            continue
        x = groups[g]
        probe = f"{x['probe_pass']}/{x['probe_total']}" if x['probe_total'] else '—'
        lines.append(f"| {g} | {x['title']} | {x['core_pass']}/{x['core_total']} | {x['rate']:.0f}% | "
                     f"{x['cases_ok']}/{x['cases']} | {probe} | {x['flaky'] or '—'} |")
    by_diff = {}
    for case in results:
        if case['probe']:
            continue
        for t in case['turns']:
            if t['verdict'] != 'INFO':
                d = by_diff.setdefault(case['difficulty'], [0, 0])
                d[0] += t['verdict'] == 'PASS'
                d[1] += 1
    lines += ['', '## Theo độ khó (không tính thăm dò)', '', '| Độ khó | Đạt | Tỉ lệ |', '|---|---|---|']
    lines += [f"| {name} | {by_diff[k][0]}/{by_diff[k][1]} | {pct(*by_diff[k]):.0f}% |"
              for k, name in (('D', 'Dễ'), ('V', 'Vừa'), ('K', 'Khó')) if k in by_diff]
    counts = {}
    for t in graded:
        for f in t['failures']:
            counts[f] = counts.get(f, 0) + 1
    lines += ['', '## Loại lỗi', '', '| Loại | Ý nghĩa | Số lượt |', '|---|---|---|']
    lines += [f'| {k} | {FAILURE_LABELS.get(k, k)} | {v} |' for k, v in sorted(counts.items(), key=lambda x: -x[1])]
    lines += ['', '## Chi tiết từng case', '']
    for g in GROUP_ORDER:
        cases = [c for c in results if c['group'] == g]
        if not cases:
            continue
        lines += [f"### {g} — {cases[0]['group_title']}", '',
                  '| Case | Lượt | Câu hỏi | Kết quả | Thẻ / câu trả lời | Ghi chú |', '|---|---|---|---|---|---|']
        for c in cases:
            tag = (' 🔍' if c['probe'] else '') + (' ⟳' if c.get('retry_task_success') else '')
            for t in c['turns']:
                shown = ', '.join(dict.fromkeys(card['product'] for card in t['cards'])) or '_' + t['reply'][:80].replace('\n', ' ').replace('|', '/') + '_'
                verdict = t['verdict'] + (f" {','.join(t['failures'])}" if t['failures'] else '')
                lines.append(f"| {c['id']}{tag} | {t['turn']} | {t['say'][:70].replace('|', '/')} | {verdict} | {shown} | "
                             f"{'; '.join(t['notes']).replace('|', '/')[:220]} |")
        lines.append('')
    lines += ['🔍 = case thăm dò (năng lực chưa hỗ trợ; không tính vào điểm cốt lõi). '
              '⟳ = trượt lần 1 nhưng đạt khi chạy lại (chập chờn do LLM).']
    return '\n'.join(lines) + '\n'


if __name__ == '__main__':
    data = json.loads(Path(sys.argv[1]).read_text())
    print(build_report(data['meta'], data['results']))
