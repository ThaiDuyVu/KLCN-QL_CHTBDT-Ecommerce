"""Build the review dossier from final execution evidence; never alter old reports."""
from collections import Counter
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[4]
OUT = ROOT / 'chatbot-service/debug/post-upgrade-fixes'
E = OUT / 'evidence/fix01'
OLD = ROOT / 'chatbot-service/debug/post-upgrade-validation'


def load(p):
    return json.loads(p.read_text())


def dump(p, value):
    p.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')


rows = load(E / 'runtime/results.json')
by_id = {r['id']: r for r in rows}
counts = dict(Counter(r['verdict'] for r in rows))
assert len(rows) == 35 and counts == {'PASS': 33, 'PASS_WITH_WARNINGS': 1, 'DEFERRED_APPROVED_SCOPE': 1}
expected_hashes = load(E / 'runtime/evidence/source_worktree_sha256.json')
assert all(hashlib.sha256((ROOT / p).read_bytes()).hexdigest() == sha for p, sha in expected_hashes.items())
assert all(load(E / r['trace_evidence'].replace('evidence/', 'runtime/evidence/', 1)).get('dropped_events') == 0 for r in rows)
old_hashes = load(E / 'original_reports_sha256.json')
assert all(hashlib.sha256((ROOT / p).read_bytes()).hexdigest() == sha for p, sha in old_hashes.items())
source_commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
mandatory = ['N03', 'N04', 'N12', 'N46', 'N47']
records = []
for id in mandatory:
    trace = load(OLD / f'evidence/traces/{id}.json')
    model = next((e['output'] for e in trace['events'] if e['name'] == 'query.validation'), None)
    normalization = next(e['output'] for e in trace['events'] if e['name'] == 'query.domain_validation')
    actual = by_id[id]
    records.append({'id': id, 'prompt': actual['prompt'], 'historical_trace_id': trace['trace_id'],
                    'before_model_plan': model, 'before_normalized_plan': normalization['normalized'],
                    'after_live_model_plan': actual.get('model_query_plan'), 'after_effective_plan': actual.get('effective_query_plan'),
                    'after_query_plan': actual['query_plan'], 'after_response': actual['response'],
                    'after_grounding': actual['grounding'], 'after_trace_id': actual['trace_id'],
                    'verdict': actual['verdict']})
dump(E / 'before_after.json', records)
dump(E / 'summary.json', {'source_commit': source_commit, 'generated_at': datetime.now(timezone.utc).isoformat(),
    'runtime_distinct_cases': 35, 'runtime_verdicts': counts, 'mandatory': {id: by_id[id]['verdict'] for id in mandatory},
    'python_full': {'passed': 340, 'failed': 0, 'skipped': 0}, 'focused': {'passed': 169, 'failed': 0, 'skipped': 0},
    'postgres_persistence_group': {'passed': 20, 'failed': 0, 'skipped': 0},
    'production_source_matches_final_runtime_hashes': True, 'historical_reports_preserved': True,
    'commit_or_push_performed_for_fix': False})


def brief(plan):
    if not plan:
        return 'schema-invalid → fallback UNKNOWN'
    p = plan.get('product_search') or {}
    if plan.get('clarification'):
        return 'UNKNOWN + clarification; product_search=null'
    targets = [('/'.join(str(x) for x in [t.get('product_name') or t.get('sku'), t.get('ram_gb'), t.get('storage_gb')] if x is not None)) for t in p.get('targets', [])]
    return '; '.join(str(x) for x in [plan.get('intent'), p.get('category'), ','.join(p.get('brands', [])) or None,
        f'max={p["max_price"]}' if p.get('max_price') else None, f'RAM>={p["min_ram_gb"]}' if p.get('min_ram_gb') else None,
        ','.join(targets) or None, f'top_k={p["top_k"]}' if p.get('top_k') else None] if x)


faults = {'N03': 'F01: Lenovo bị bỏ, SKU do model bịa', 'N04': 'F02: mất Laptop',
          'N12': 'F03: mất RAM/storage từng target', 'N46': 'F08: maxRAM bị bỏ', 'N47': 'F09: count2 thành5/3cards'}
expected = {'N03': 'Clarify; không card hãng khác', 'N04': 'Laptop + Dell + max20M',
            'N12': 'Đúng 12/256 và 6/128; 2 cards', 'N46': 'Clarify trước product retrieval', 'N47': 'top_k2; tối đa2 sản phẩm Xiaomi'}
md = f'''# 01 — QueryPlan Integrity Fix

[C2C] STATE: EXECUTED · TASK_ID: c2c_chatbot_fix01_query_contract · ITERATION: 1

## 1. Source commit và Git baseline

Workspace connector xác nhận **QL_CHTBDT_Ecommerce**, branch **dev/thai-duy-vu**. Đã fetch GitHub và fast-forward tới **{source_commit}** (`55fe4ed`, PR44). Working tree sạch trước triển khai. `origin/dev/thai-duy-vu` vẫn ở `f6a04f7`; ahead1 là merge commit đã có trên GitHub/main, không phải commit Fix01. Không commit/push trong checkpoint này.

Git xác minh source `chatbot-service/app` và tests ở HEAD đồng nhất với commit kiểm thử **1c62129** trước khi sửa, không dựa vào kết luận Markdown. Current source được đọc qua connector và filesystem. [Lệnh Git và test](evidence/commands.json); [hash source thực thi](evidence/fix01/runtime/evidence/source_worktree_sha256.json). Source sửa còn trong working tree; HEAD không đại diện đầy đủ cho code được test.

Đã đọc matrix/results/register/final report và trace N03/N04/N12/N46/N47. Toàn bộ dossier gốc `eval60`, `chatbot-upgrade`, `post-upgrade-validation` được giữ nguyên; [hash kiểm tra](evidence/fix01/original_reports_sha256.json), [cleanup](evidence/fix01/cleanup.json).

## 2. Root causes đã tái hiện

Trước sửa, replay chính các QueryPlan đã ghi lại từ trace cũ: **5 failed**, đều là assertion chức năng, không phải lỗi môi trường. [Output](evidence/fix01/before-tests.txt). N47 replay UNKNOWN vì cả hai model attempts cũ đều sai schema; các test riêng thực sự chạy malformed JSON/repair rồi fallback, không coi model JSON giả là E2E.

- F01: model bỏ Lenovo/bịa SKU; normalizer loại SKU nhưng làm rộng sang mọi hãng. Cần đối chiếu thương hiệu được nêu với taxonomy và giữ trạng thái chưa resolve.
- F02: literal category matcher không hiểu “máy tính xách tay”, ghi đè Laptop thành null.
- F03: rebuild selectors từ tên và cấu hình toàn câu làm mất điều kiện riêng 12/256, 6/128.
- F08: parser không phân biệt đủ toán tử/filler; “RAM phải dưới” rơi mất thay vì bị từ chối.
- F09: top_k hardcode5 sau fallback, không đọc số sản phẩm user yêu cầu.

## 3. Files changed và lý do

| File | Thay đổi |
|---|---|
| `app/features/query_understanding/constraints.py` (mới) | Internal typed CapacityConstraint/Relation/ProductEvidence; grammar slot/toán tử, kiểm tra coverage, taxonomy/category/brand, cardinality và integrity gate. |
| `app/features/query_understanding/normalization.py` | Dùng utterance evidence thay model filters không có căn cứ; chia target theo vị trí tên trong câu; chuẩn hóa category; loại điều kiện chưa hỗ trợ bằng clarification. |
| `app/features/product_advisor/backend.py` | Báo taxonomy không đủ coverage khi brand pagination còn trang, tránh xem hãng vắng trong trang đầu là không tồn tại. Không thêm endpoint. |
| `app/features/conversation/references.py` | Dùng chung cú pháp storage_reference với normalizer; giữ nguyên ownership/snapshot/backend refresh. |
| `app/features/response_generation/ollama_service.py` | Với explicit requested count, nếu kết quả ít hơn thì nói rõ X/N sản phẩm; không đếm variants thành nhiều sản phẩm. |
| `tests/test_query_integrity.py` (mới) | **89** test instances: replay5 findings, paraphrases, model hallucination/repair/fallback, per-target configs/colors, eligibility/cards/text, category contamination, taxonomy failure, T25/T29. |
| `debug/post-upgrade-fixes/harness/` | Reproducible focused runtime, cleanup, report generation. Chỉ harness; không sửa harness/reports cũ. |

Không đổi `QueryPlan`, `ProductSelector`, `ProductSearchPlan`, public enum/API, prompt/schema Ollama, ChatService orchestration contract, RealProductAdvisor eligibility/stock/ranking, Java, authentication, schema hoặc migration. Luồng Ollama hiện tại đã gọi normalizer cho cả valid JSON và repair/fallback; không cần kéo dài system prompt. Internal Enum/dataclasses không mở rộng wire contract.

## 4. Before/after QueryPlans và mandatory evidence

Bảng “Before” là plan sau normalization cũ; “After” là runtime mới. Model plan ban đầu, grounding, response đầy đủ nằm trong [before_after.json](evidence/fix01/before_after.json). Không đánh đồng historical trace với execution mới.

| Case | Original Fault | Before QueryPlan | After QueryPlan | Expected | Actual | Verdict | Trace/Evidence |
|---|---|---|---|---|---|---|---|
'''
for record in records:
    id = record['id']
    row = by_id[id]
    actual = f"{row['http_status']}; {len(row['response']['products'])} cards; {sum(len(p['eligible_variants']) for p in row['grounding'].get('products', []))} eligible variants"
    md += f"| {id} | {faults[id]} | {brief(record['before_normalized_plan'])} | {brief(record['after_query_plan'])} | {expected[id]} | {actual} | {row['verdict']} | `{row['trace_id']}` [trace](evidence/fix01/runtime/{row['trace_evidence']}) |\n"
md += '''
## 5. Per-target validation

Tên catalog được resolve theo vị trí xuất hiện, không theo thứ tự backend trả về. Mỗi span sau tên đến target kế tiếp được parse RAM/storage/color riêng. Với N12: Xiaomi15→RAM12/storage256, iPhone15→RAM6/storage128. Compact `12GB/256GB` và dạng có nhãn đều hỗ trợ. Không truyền cấu hình A sang B; target không được nêu cấu hình vẫn không bị gán cấu hình từ target khác.

So sánh có global configuration mơ hồ, capacity minimum riêng mỗi target, nhiều giá riêng hoặc không rõ lượng GB thuộc RAM/storage thì clarify, vì contract hiện tại chỉ có exact per-target và global minima. Không biến exact comparison thành discovery toàn bộ variants. RealProductAdvisor hiện có dùng `_target_variants` và `_eligible`; build_cards refresh cùng evidence cho text/cards. N12 final có đúng hai ProductMatches, mỗi match một EligibleVariant và hai cards.

Synthetic unit fixture thêm candidate Dell ngoài category Laptop, deliberately trả cả hai từ structured adapter: sau eligibility/enrichment chỉ còn laptop; message không chứa điện thoại. Nhờ vậy N04 không chỉ “pass do catalog hiện tại chưa có Dell phone”. Không ghi fixture này vào business DB.

## 6. Hard-filter preservation rules

1. Category synonym được map về tên thật của active taxonomy: Laptop/notebook/máy tính xách tay; Điện thoại/Smartphone/phone; tablet chỉ được dùng khi taxonomy hiện tại có danh mục tương ứng. Category model phải có căn cứ trong utterance; không xem string bất kỳ là canonical.
2. Brand lấy từ active taxonomy, không từ danh sách năm hãng hiện tại. Hỗ trợ fixture Framework để chứng minh. Unknown stated brand ở slot sau category, explicit hãng/thương hiệu/brand hoặc model brand có mặt trong utterance phải clarify. Use-case boundary như để lập trình/gaming/phục vụ học tập không phải identity. SKU/ID chỉ tạo từ literal user, không nhận SKU model bịa.
3. CapacityConstraint giữ quan hệ EXACT/MINIMUM/MAXIMUM/RANGE/STRICT_MINIMUM trong nội bộ; wire contract không đổi. Discovery “RAM16GB” tiếp tục nghĩa RAM>=16 theo tương thích hiện có; exact target RAM16GB nghĩa đúng16. “Đúng RAM16GB” mà thiếu identity thì hỏi rõ.
4. Price parser phân biệt GB/TB với tiền, đọc VND/tr/triệu và price range. Multiple bounds cùng loại được lấy giao (max lower/min upper); mâu thuẫn thì hỏi rõ. Không lấy số lượng2 hay model15 làm giá/RAM.
5. Requested cardinality được đọc độc lập từ utterance, giới hạn1–20; không tin top_k do model tự thêm. Default5 khi user không nêu count. Invalid JSON, repair thất bại hoặc Ollama unavailable vẫn đi qua cùng normalizer. ProductSearchPlan.top_k được real advisor áp lên số products, không số variants; thiếu kết quả được nói rõ.
6. ProductEvidence kiểm tra equality phạm vi category/brands/targets/price/minima và count bound trước trả normalized plan; tests cố tình broaden target hoặc top_k để chứng minh gate từ chối. Backend eligibility và live card refresh tiếp tục là lớp kiểm chứng cuối.
7. Backend/taxonomy lỗi/rỗng/incomplete không thành “không có sản phẩm”: propagate availability error để ChatService trả503. Unknown brand chỉ là chưa xác minh trong catalog, không khẳng định hãng đó không tồn tại toàn cầu.

## 7. Unsupported-condition behavior

RAM/SSD upper bound (“dưới”, “phải dưới”, “chỉ”, “không quá”, “tối đa”, <=, under/at most), capacity range hoặc strict greater-than được nhận diện rồi trả UNKNOWN+clarification, product_search=null. Không thêm maxRAM/minus/exclusion backend fields; không đổi nghĩa thành minimum. Các condition thiếu slot, màu không xác định, loại trừ brand/category cũng clarify thay vì bỏ lọc.

Runtime N03/N46 không có effective product search, ProductMatch hoặc card; chỉ có catalog reads cần cho validation. Những trường hợp cần resolver như “Bản512GB” được để lại cho owned snapshot resolver; không dùng số512 làm global filter của một loại sản phẩm chưa biết. Sau resolve, backend kiểm chứng identity/variant và tồn kho hiện tại.

## 8. Tests, integration và runtime

Môi trường thật: Python3.12.15; Qwen `qwen3:4b-instruct`; embedding `qwen3-embedding:0.6b`; real Product Advisor; tracing enabled; PostgreSQL durable history; Spring Boot thật trên disposable pgvector clone. Clone có16 products/38 variants, hai warehouses; dùng selected warehouse hiện có. Knowledge DEMO hiện có, không tạo chính sách mới. Synthetic CUSTOMER từ repository, không đưa credentials/IDs cá nhân vào dossier. [Environment](evidence/fix01/environment_sanitized.json).

| Execution | Kết quả cuối | Evidence |
|---|---|---|
| Focused QU/contracts/ProductAdvisor/ChatFlow/tracing | 169 passed,0 failed,0 skipped | [output](evidence/commands/fix01-focused-acceptance.txt) |
| PostgreSQL knowledge/persistence group | 20 passed,0 failed,0 skipped, gồm5 PG integration tests | [output](evidence/commands/fix01-postgres-acceptance.txt) |
| Full chatbot Python suite | 340 passed,0 failed,0 skipped | [output](evidence/commands/fix01-full-python-acceptance.txt) |
| Real Qwen + Spring + isolated PostgreSQL | 35 distinct cases:33 PASS,1 warning,1 deferred;0FAIL/blocked | [results](evidence/fix01/runtime/results.json), [trace index](evidence/fix01/runtime/trace_index.json) |
| Git protected reports / diff --check | exit0 | [commands](evidence/commands.json) |

Các nhóm pytest có overlap, không cộng169+20+340 thành tổng unique tests. Có một Starlette/httpx deprecation warning. Smoke run không truyền DB env từng có104 passed/4 skipped; các skip đó được chạy lại với isolated DSN và final suite không skip. Các focused run trung gian140/153/161/167 và full330/338 là snapshot trong lúc thêm test, không thay thế kết quả final340. Initial harness import-path error được sửa trước khi thu baseline5 failures; đó không phải lỗi app.

Java không đổi: environment package backend thành công (exit0), không tuyên bố đã rerun Java tests ở Fix01. Frontend không đổi và không rerun build/component/browser suite; frontend-visible ChatResponse/message/cards được xác minh qua API. Không coi mocked models/MockTransport là real Qwen evidence.

Historical regressions được chạy mới T07–T22,T23–T30,T51–T56. T51 tiếp tục yêu cầu xác nhận typo an toàn và ghi warning; T56 tiếp tục DEFERRED_APPROVED_SCOPE, không được đánh PASS cho multi-intent.

| Case | Intent actual | Cards | Verdict | Evidence |
|---|---|---:|---|---|
'''
for row in rows:
    md += f"| {row['id']} | {(row.get('query_plan') or {}).get('intent')} | {len(row['response'].get('products', []))} | {row['verdict']} | [trace](evidence/fix01/runtime/{row['trace_evidence']}) |\n"
md += '''
## 9. Evidence cho từng case bắt buộc

'''
for record in records:
    row = by_id[record['id']]
    md += f"### {row['id']}\n\nPrompt: {row['prompt']}\n\nExpected: {expected[row['id']]}.\n\nActual ({row['verdict']}):\n\n```text\n{row['response']['message']}\n```\n\nHTTP {row['http_status']}; {row['duration_ms']}ms; trace `{row['trace_id']}`. Grounded variants/cards đối chiếu read-only backend detail; từng semantic check nằm trong results.json.\n\n"
md += '''## 10. Regressions phát hiện và sửa trong triển khai

Không che các lần chạy chưa đạt. Có bốn development replay batches, mỗi batch cùng35 prompts; chỉ final batch là ma trận acceptance35 distinct. Các batch trước là rerun/debug, không tăng coverage thành140 cases và không dùng để suy luận statistical reliability.

- [Attempt01](evidence/fix01/attempt-01/results.json): T25 hỏi rõ quá mức do grammar thiếu filler “vẫn”. Initial evaluator đã thiếu assertion giữ RAM/cheaper baseline; được siết theo expected đã đóng băng và record được đánh FAIL với lý do, không thay đổi expected hoặc response quan sát. Unit test mới bắt filler và không trộn assistant variant facts vào user filters.
- [Attempt02](evidence/fix01/attempt-02/results.json): T25 và toàn bộ batch hoạt động; sau đó thêm capacity coverage/exclusion protection.
- [Attempt03](evidence/fix01/attempt-03/results.json): coverage gate chặn T29 “Có phiên bản512GB không?” trước resolver. Chia sẻ storage_reference grammar với resolver hiện có; scoped literal của một reference không bị coi là global search. Thêm test có/không có snapshot.
- [Final](evidence/fix01/runtime/results.json): T25 giữ RAM>=16 và max_price15989999, backend không có kết quả; T29 resolve đúng product trước và trả hai variants512GB; T26 không bịa sản phẩm thứ hai. Hash production source lúc final runtime được kiểm tra trùng source hiện tại.

Các sửa này thuộc query/reference integrity, không triển khai thêm persistent-memory hay multi-intent feature. Frozen prompts/expected trong cases.json không đổi giữa các batches.

## 11. Remaining limitations

- Đây là bounded deterministic slot grammar + model extraction + backend verification, không phải chứng minh hỗ trợ mọi cách diễn đạt tiếng Việt/Anh. Unknown identity hoặc cấu hình thiếu slot được xử lý bảo thủ, có thể hỏi lại dù người dùng thấy tên đó rõ.
- Chưa có maxRAM, per-target minimum/range/price, arbitrary Boolean filter, nhiều SKU trong một comparison hoặc summary/multi-intent execution. Không thêm contracts chưa duyệt. Một số ngữ pháp cấu hình/màu ngoài vocabulary được hỏi lại thay vì bỏ lọc.
- Taxonomy còn trang sau100 brands hiện trả availability error, chưa triển khai full brand pagination; catalog/identity cap500 vẫn giữ nguyên. Cần review contract completeness/latency trước mở rộng.
- Price bounds tiếp tục inclusive theo contract/backend hiện có; không bổ sung strict-VND API. ProductCard vẫn một representative eligible variant mỗi product; message có thể liệt kê nhiều eligible variants nhưng luôn ghi đúng SKU/config/price/stock.
- Không rerun toàn bộ120-case diagnostic vì checkpoint này yêu cầu focused query/product regression. Các lỗi ngoài phạm vi như missing-payment/null warehouse order mapping, lexical RAG, transaction guard false positive, orphan USER persistence, RAM feedback/trace, official corpus missing còn nguyên phạm vi review trước.
- Test fixtures synthetic/unit và isolated DB; không có live active-promotion matrix hoặc load/reliability claim. Không tuyên bố production-ready.

## 12. Compatibility risks và cleanup

Public schemas/enums/API không đổi, không SQL do Qwen sinh, không migrations/Java/business mutations. Behavior chủ ý chặt hơn: unknown identity, unsupported filter hoặc taxonomy incomplete dừng/clarify thay vì broad search. Unknown brand sẽ không trả nhầm Dell/ASUS/Apple. Downstream phải tiếp tục tôn trọng `clarification`; current resolver đã có guard không overwrite domain errors và tests xác minh.

Chỉ app stack/container của diagnostic được dừng/xóa; source business fingerprint trước/sau bằng nhau, source DB running state được khôi phục, reports gốc không đổi. [Cleanup proof](evidence/fix01/cleanup.json). Không để fixture trên business DB, không thay authentication/ownership hoặc normal application processes.

## 13. Final HANDOFF cho review

**C2C EXECUTED, chưa accepted.** N03/N04/N12/N46/N47 đều có executed deterministic regression và real-stack trace PASS. 89 test instances mới; full340 passed. 35 real cases gồm33PASS/1warning/1deferred. Không commit/push; không triển khai Fix02.

Reviewer nên xem target scoping/grammar conservatism, unknown-brand ambiguity policy, taxonomy coverage error và semantics count/variant trước khi chấp thuận. [Diff/status](evidence/fix01/final_git_status.txt), [before/after](evidence/fix01/before_after.json), [machine summary](evidence/fix01/summary.json).
'''
(OUT / '01_QUERYPLAN_INTEGRITY_FIX.md').write_text(md)
print('Review report generated; final source hashes and immutable original reports verified.')
