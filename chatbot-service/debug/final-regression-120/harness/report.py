"""Generate review dossier from current executed evidence; never rewrite case expectations."""
import hashlib,json,statistics,subprocess,sys,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[4];sys.path.insert(0,str(ROOT/'chatbot-service/debug/post-upgrade-validation/harness'))
from common import *
rows=load(OUT/'results.json');cases=load(OUT/'cases.json');old=load(ROOT/'chatbot-service/debug/post-upgrade-validation/results.json');baseline={r['id']:r for r in old};by={r['id']:r for r in rows}
assert len(rows)==120 and {r['id'] for r in rows}=={c['id'] for c in cases}
from collections import Counter
actual=Counter(r['verdict'] for r in rows)
summary=load(OUT/'verdict_summary.json');summary['counts']={k:actual[label] for k,label in [('PASS','PASS'),('WARN','PASS_WITH_WARNINGS'),('FAIL','FAIL'),('DEFERRED','DEFERRED_APPROVED_SCOPE'),('BLOCKED','BLOCKED_ENVIRONMENT')]};assert sum(summary['counts'].values())==120
# Narrative diagnoses below are reviewed for this exact execution; stop rather than
# publish stale prose if a future replay produces a different distribution.
assert summary['counts']=={'PASS':111,'WARN':3,'FAIL':2,'DEFERRED':4,'BLOCKED':0},'New results need independent narrative/defect review'
summary['source_version']=load(OUT/'source_hashes.json')['source_version'];summary['readiness']='READY_FOR_MANUAL_UAT_LAB_SCOPE_ONLY';summary['production_ready']=False
dump(OUT/'verdict_summary.json',summary)
commands=load(OUT/'evidence/commands.json');dump(OUT/'test_commands.json',commands)
ui=load(OUT/'ui_inventory.json')
features=['Login / unauthorized','Lab route / readiness','ProductCards / facts','Product detail navigation','DEMO sources / missing official guide','New / switch / close sessions','Cursor history / reload','Session pagination','Send failure / safe retry','Switch during pending requests','Double-submit / loading','Feedback','Trace panel / filter','JSON export','Malformed response sample','Responsive layout','Keyboard / basic a11y','Console / page errors','Storefront integration inventory']
for row,feature in zip(ui,features):
 row.update(feature=feature,existing_ui=row['id']!='UI19',real_browser_tested=True,feature_status='NOT_IMPLEMENTED' if row['id']=='UI19' else 'WORKING_WITH_LIMITATIONS' if row['id'] in ['UI07','UI12','UI13'] else row['status'],gap='New feature needs approval; direct Lab is not storefront chat.' if row['id']=='UI19' else 'Text-only restored history.' if row['id']=='UI07' else 'RAM-backed; reset after restart.' if row['id'] in ['UI12','UI13'] else None)
dump(OUT/'ui_inventory.json',ui)
def esc(x):return str(x).replace('|','/').replace('\n','<br>')
def table(headers,lines):return '| '+' | '.join(headers)+' |\n|'+'|'.join(['---']*len(headers))+'|\n'+'\n'.join('| '+' | '.join(esc(x) for x in line)+' |' for line in lines)+'\n'
def write(name,text):(OUT/name).write_text(text.rstrip()+'\n')
def link(row):return row.get('trace_evidence') or f"evidence/boundaries/{row['id']}.json" if row['id'].startswith('B') else row.get('trace_evidence') or 'evidence/browser.json'
# Root causes for every actual failure/warning; deferred subset failure remains explicit.
findings=[
 {'id':'RAG01','cases':['N33','N34'],'stage':'KNOWLEDGE_RETRIEVAL','status':'VERIFIED','severity':'MEDIUM','priority':'P1_AFTER_RAG_APPROVAL','disposition':'OPEN_DEFERRED_RAG','owner':'PERSON_3 / Knowledge','symptom':'N33 cites unrelated payment chunk; N34 returns return-policy chunk for promotion question.','cause':'relevant_chunks aliases omit doi hang / giam gia; with no matched topic, shared lexical tokens from DEMO boilerplate satisfy fallback overlap. Positive cosine alone does not establish support.','files':['chatbot-service/app/features/knowledge/service.py:relevant_chunks','chatbot-service/app/features/response_generation/ollama_service.py:generate'],'fix':'After separate RAG approval, add evidence-based topic/relevance tests and require source support; do not fabricate official corpus or change algorithms in this checkpoint.','regression':'N33/N34 must retain FAIL until unrelated chunks are excluded and grounded abstention is verified. Test boilerplate-only overlap, zero relevant evidence and DEMO/official separation.','risk':'RAG quality design and official corpus/workflow require separate approval; no schema change proposed.'},
 {'id':'L01','cases':['T51'],'stage':'UNSUPPORTED_CAPABILITY','status':'VERIFIED_LIMITATION','severity':'LOW','priority':'P2','disposition':'KNOWN_LIMITATION','owner':'PERSON_3 / Identity','symptom':'Typo Xiaomii15 is clarified rather than silently corrected.','cause':'Exact identity validation deliberately rejects uncertain model identity.','files':['chatbot-service/app/features/query_understanding/normalization.py'],'fix':'Keep safe clarification; fuzzy suggestions require approved identity confidence policy.','regression':'Typo must never bind unrelated product or invent stock.','risk':'Broader fuzzy matching can weaken exact identity; not authorized here.'},
 {'id':'L02','cases':['T59'],'stage':'CONVERSATION','status':'VERIFIED_CONTRACT_LIMITATION','severity':'LOW','priority':'P2','disposition':'KNOWN_LIMITATION','owner':'PERSON_1 / Conversation + frontend','symptom':'Text/IDs survive process restart; historical cards absent, feedback1→0 and trace200→404.','cause':'History API exposes text rows; feedback and dev trace index remain RAM-backed.','files':['chatbot-service/app/features/conversation/postgres_repository.py','chatbot-service/app/shared/tracing/__init__.py','Fontend/src/features/chatbot/ChatbotLabPage.jsx'],'fix':'Do not promise durable metadata. Obtain approval for feedback1–5 vs API-1/0/1 mapping, trace retention and rich-history DTO if needed.','regression':'Current T59 guarantees text/IDs; future approved richer contract must restore real evidence, not derive it from text.','risk':'Requires explicit contracts/schema decisions, not an arbitrary mapping.'},
 {'id':'L03','cases':['N18'],'stage':'UNSUPPORTED_CAPABILITY','status':'VERIFIED_LIMITATION','severity':'LOW','priority':'P2','disposition':'KNOWN_LIMITATION','owner':'SHARED / Warehouse contract','symptom':'Only configured warehouse stock is returned; no all-warehouse aggregation.','cause':'Current chatbot warehouse is server-configured; response names selected-warehouse scope.','files':['chatbot-service/app/features/product_advisor/real_service.py','chatbot-service/app/shared/config.py'],'fix':'Keep selected-warehouse wording; warehouse switching/aggregation needs approved design.','regression':'Do not claim all warehouses sold out from one warehouse zero stock.','risk':'New selection contract not authorized.'},
 {'id':'D01','cases':['T56','N43','N44','N48'],'stage':'UNSUPPORTED_CAPABILITY','status':'VERIFIED_APPROVED_DEFERRAL','severity':'MEDIUM','priority':'P2_AFTER_MULTI_INTENT_APPROVAL','disposition':'DEFERRED_MULTI_INTENT','owner':'PERSON_1 / Orchestration + SHARED contract','symptom':'No bounded independent task execution; N44 returns cart subset without explicitly acknowledging omitted comparison.','cause':'Single-plan routing selects cart/order or asks a clarifying question; multi-task contract deferred.','files':['chatbot-service/app/features/chat/policy.py','chatbot-service/app/features/chat/service.py'],'fix':'Design bounded tasks and explicit partial-completion wording after separate approval; do not advertise full multi-intent support.','regression':'N44 must disclose omitted comparison before its observed FAIL can become PASS; all4 remain scope-deferred even safe clarification is observed.','risk':'Public task/evidence orchestration contract not finalized.'},
]
for finding in findings:
 finding['evidence']=[{'case_id':id,'trace_id':by[id].get('trace_id'),'observed_verdict':by[id]['observed_verdict'],'path':link(by[id]),'actual':by[id].get('response',by[id].get('observations'))} for id in finding['cases']]
 finding['dependencies']='Independent reviewer approval of changes outside this checkpoint.'
for r in rows:
 if r['observed_verdict'] in ['FAIL','PASS_WITH_WARNINGS']:
  matched=[f for f in findings if r['id'] in f['cases']];assert matched,r['id'];r['diagnosis_ids']=[f['id'] for f in matched]
  r['first_fault']=matched[0]['stage']
dump(OUT/'results.json',rows);dump(OUT/'evidence/defect_register.json',findings)
base=load(OUT/'evidence/baseline.json');env=load(OUT/'environment_sanitized.json');cleanup=load(OUT/'cleanup.json')
write('00_BASELINE_AND_SCOPE.md',f'''# Baseline và phạm vi final120

Workspace **QL_CHTBDT_Ecommerce**, branch **dev/thai-duy-vu**. Actual Git HEAD **{base['actual_head']}**, initial tracked tree clean. Connector đúng workspace nhưng báo `f99f713`; không dùng metadata đó thay actual Git. Chưa sync/rebase/merge hoặc tự suy đoán HEAD planning còn đúng. Accepted Fix01 `8b1753f`, Fix02 `6c7e408`, Fix04 `74f4e19` được đối chiếu actual history và source.

Đã đọc4reports post-upgrade-validation,3accepted fix reports, frozen cases và runtime/boundary/evaluation/fixture/frontend code. Original reports/fixtures/expectations không bị sửa. [Hash protected dossier](evidence/protected_reports_sha256.json), [cleanup](cleanup.json).

Tested final source = HEAD trên **+ worktree hotfix** `final120-help-and-first-post-retry`, định danh từng file bằng [source_hashes.json](source_hashes.json). Checkout HEAD đơn thuần không chứa hotfix đang chờ review. [Exact diff](evidence/hotfix.diff), Python regression mới `tests/test_final_release_regressions.py`; không commit/push trong checkpoint này.

## Cách thực thi

-104real chatbot API cases (Qwen inference khi cần, deterministic guards khi có);3real security cases;1real Chrome restart case;12boundary cases theo frozen execution class. =120unique IDs.
-Boundary9controlled adapter/PG fault tests, B08real concurrency, B11real security, B12controlled knowledge timeout. Không gọi tất cả120 là Qwen E2E.
-19UI checks riêng,8Fix04controls riêng,15repeat requests riêng; không cộng vào120.
-Real Spring/FastAPI/Qwen + isolated PostgreSQL/pgvector. AI=true, advisor=real, conversation=postgres; selected warehouse, empty warehouse và no-warehouse profiles giữ nguyên. Synthetic CUSTOMER only;5orders/4payments/2warranties/1ticket trong clone. Không seed official policy.

Python3.12.15, pytest theo command output, Node22.23.3, Temurin21.0.12.1. Chat `qwen3:4b-instruct`, embedding `qwen3-embedding:0.6b`; digests và Flyway/schema/corpus tại [environment](environment_sanitized.json).16products/38variants/2warehouses;2DEMOdocuments/2chunks. Production defaults AI=false/advisor=mock/history=memory khác test overrides.

## Hai hotfix hẹp sau lần chạy đầu

1. T03 HELP bị mất từ “những” vì folded `nhung` cũng là separator “nhưng”. Model rơi về GREETING. Chỉ dùng nguyên câu folded cho HELP guard; affirmative refusal vẫn chạy trước.
2. UI09 failed first POST503 rồi history404 giữ needsSync vĩnh viễn dù session chưa tạo. Chỉ clear optimistic row và mở retry sau HTTPerror đã trả về + history404 + không abort; network/abort/history503 vẫn phải sync. Không auto POST, không đổi API/authentication.

Before tests chứng minh1HELPfailure và1frontendfailure. Sau hotfix chạy lại toàn120 và full suites; không trộn source versions. `runs/pre-hotfix/` lưu đủ120kết quả trước sửa (110PASS/3WARN/3FAIL/4DEFERRED) và partial UI run. Hai application files đổi; không đổi schema/Java/public DTO/security rules/RAG. Hai focused regression tests Python mới và1frontendtest mới. N33/N34 vẫn FAIL.

## Harness compatibility corrections

Original evaluator có forced known-FAIL assertion và forced warning overrides; bản mới chấm lại actual semantic checks. N42 frozen expected cho phép safe clarification, nên evaluator phải chấp nhận đúng điều đó; không sửa prompt/expected. N27 nhận approved safe unavailable wording thay exact old literal; không suy payment. B07 catch existing fail-closed coverage; B10 kiểm completed context thay raw audit. Initial supplemental ticket URL sai404 được sửa về existing `/api/warranties/mine/tickets`, chỉ enrich lại observations, không chạy giả requests. Initial focused command trỏ module không tồn tại exit4; đã sửa và rerun264focusedPASS. UI10 không được ép select khi real UI disable trong POST: test xác minh prevention rồi switch sau completion; initial timeout/cascade được giữ. Partial UI run dừng có kiểm soát exit-15, không ghi thành PASS. Command logs giữ nguyên outcomes; Vitest counts lấy Tests line thay repeated regex headings.
''')
write('01_FROZEN_120_MATRIX.md','# Frozen120 matrix\n\nExact byte snapshot của original `post-upgrade-validation/cases.json`. SHA256: `'+load(OUT/'evidence/plan_sha256.json')['cases_sha256']+'`. Hash được đóng trước execution và kiểm lại lúc cleanup. Original baseline verdict trong case metadata chỉ để đối chiếu.\n\n'+table(['ID','Exact prompt/action','Frozen expected','Mode','Profile/session/setup'],[(c['id'],c['prompt'],c['expected'],c['mode'],str({k:c[k] for k in ['profile','session_class','setup','fixture'] if k in c})) for c in cases]))
comparison=table(['Case','Original120 verdict','Final release verdict','Observed verdict','Scope disposition','Before → After','Evidence'],[(r['id'],baseline[r['id']]['verdict'],r['verdict'],r['observed_verdict'],r['release_scope_disposition'],str((baseline[r['id']].get('response') or {}).get('message',baseline[r['id']].get('observations','')))[:180]+' → '+str((r.get('response') or {}).get('message',r.get('observations','')))[:240],f"[artifact]({link(r)})") for r in rows])
commandtable=table(['Command','UTC start','Exit','Passed/Failed/Skipped','Source/output'],[(r['command'],r['started_at'],r['exit_code'],str(r['counts']),f"[output]({r['sanitized_output']})") for r in commands])
write('02_EXECUTION_RESULTS.md',f'''# Execution results

**120/120 accounted:111PASS /3WARN /2FAIL /4DEFERRED /0BLOCKED.** T01–T60,N01–N48,B01–B12 duy nhất. [All exact responses/plans/cards/grounding](results.json), [trace index](trace_index.json), [commands](test_commands.json).

Observed axis riêng:114PASS/3WARN/3FAIL. Ba FAIL quan sát =N33/N34/N44; N44 thuộc approved multi-intent DEFERRED, không biến thành supported PASS. T56/N43/N48 clarify/partial checks có thể PASS trong observed axis nhưng không tính capability đã triển khai. N33/N34 actualFAIL + OPEN_DEFERRED_RAG, không chuyển PASS vì thiếu official documents.

API200 chỉ là một acceptance check; còn đối chiếu eligible variants/card/text, hard filters, backend live price/quantity/warehouse, independent comparison target, chunk document mapping và authenticated personal DB/API projection. Boundary observations ghi HTTP-equivalent khi injection tại service boundary, không giả HTTP response E2E. Null trace IDs cho controlled/security/browser observations được ghi rõ, không bịa trace.

## Before/after từng case

{comparison}
## Automated commands

{commandtable}

Final full Python435PASS/0FAIL/0SKIP; focused264PASS; new HELP controls2PASS là subset, không cộng lại. Full frontend59PASS/0FAIL/0SKIP; ESLint/build exit0. Selected Java89tests,0failures/0errors/0skips từ Surefire XML, không gọi là full Java suite. Python còn1Starlette/httpx deprecation warning. Runtime/diagnostic driver exit0 nghĩa collection completed, không có nghĩa mọi functional verdict PASS. Reused helper metadata commit được hiệu chỉnh bằng actual Git/connector, không tin literal cũ.

UI19checks PASS sau hotfix. Trước đó UI09 thậtFAIL, UI10invalid interaction và UI11cascade timeout giữ trong pre-hotfix archive. Full initial suites433Python/58frontend là pre-hotfix, không thay final435/59.
''')
register='# Defect register và proposed fixes\n\nKhông sửa RAG, deferred tasks hoặc public contracts. Mỗi actualFAIL/WARN có diagnosis; approved deferral không đồng nghĩa PASS.\n\n'
register+=table(['ID','Cases','First demonstrated stage','Severity/Priority','Disposition'],[(f['id'],','.join(f['cases']),f['stage'],f['severity']+'/'+f['priority'],f['disposition']) for f in findings])
for f in findings:
 register+='\n## '+f['id']+'\n\n'+ '\n\n'.join(f'**{label}:** '+str(f[key]) for label,key in [('Symptom','symptom'),('Root cause','cause'),('Root status','status'),('Owner','owner'),('Files','files'),('Minimal recommendation','fix'),('Regression proving fix','regression'),('Compatibility/dependency risk','risk')])+'\n\n'+table(['Case','Expected','Actual','Trace','Evidence'],[(id,by[id]['expected'],str((by[id].get('response') or {}).get('message',by[id].get('observations'))),by[id].get('trace_id') or 'N/A controlled/browser',link(by[id])) for id in f['cases']])
register+='''\n## Regression hotfixes completed, not open defects

T03→ORCHESTRATION: pre-hotfix trace initialGREETING; full HELP text corrupted in routing_text by accent-folded separator. Exact helper test failed before, then2testsPASS including affirmative mutation control; finalT03HELP. Files `chat/policy.py`, `tests/test_final_release_regressions.py`.

UI09→frontend session recovery: real503 injected before first persist + actual history404 caused permanent needsSync; component reproduction failed; minimal status-scoped catch now allows user-triggered retry. Final real browser shows one USER and one ASSISTANT; no automatic replay. Files `ChatbotLabPage.jsx`, `ChatbotLab.test.jsx`.

## Harness defects corrected, not application regressions

Original N42 assertion rejected safe clarification explicitly allowed in frozen expectation. Correcting this is evaluation repair, not model bug/fix. Original N27 expected unavailable meaning is preserved while allowing accepted Fix02 wording. Extra backend ticket URL404 and focused missing-file command were harness mistakes, logs retained. Initial UI10 attempted to interact with intentionally disabled selector, leaving delayed network route pending and causing later UI timeout; final test checks the actual UI prevention semantics. No expectation changed.

## Unverified risks

Process death at COMMIT and exactly-once delivery are not proven; no idempotency API. Arbitrary multilingual prompt/quote nesting, arbitrary malformed card arrays and cross-tab/account-change races were not exhaustively verified. UI15 covers one incomplete200 DTO sample only. Earlier generic console404s lack URL and cannot all be conclusively attributed; supplemental real network run identifies normal anonymous401 and nonexistent-session404 with no pageerror. Full WCAG audit, production load/model statistical stability, official corpus quality and storefront chat are outside this acceptance evidence.
'''
write('03_DEFECT_REGISTER.md',register)
ui_table=table(['Feature','Existing UI','Real browser tested','Expected','Actual','Status','Gap'],[(u['feature'],u['existing_ui'],u['real_browser_tested'],u['expected'],json.dumps(u.get('observations',{}),ensure_ascii=False)[:650],u['feature_status'],u['gap'] or '—') for u in ui])
write('04_UI_READINESS_AUDIT.md',f'''# UI readiness audit

**UI_LAB_READY: YES — development/flag-enabled Lab scope.** **UI_STOREFRONT_READY: NO / NOT_IMPLEMENTED.**19browser audit checks PASS; UI19PASS là xác minh thiếu storefront integration, không phải storefront featurePASS. Không tạo route/widget/menu mới.

Đã đọc `App.jsx`, `AppLayout.jsx`, `ChatbotLabPage.jsx`, `TracePanel.jsx`, `chatbotApi.js`, CSS và component tests. `/chatbot-lab` nằm dưới RequireAuth, ngoài storefront AppLayout, bật bằng DEV hoặc VITE_CHATBOT_LAB_ENABLED. Direct authenticated URL đã dùng; không tìm thấy customer-facing chat widget/menu ở storefront `/products`.

{ui_table}
## Visual observations thật

[Mobile390](evidence/ui/top-390.png), [tablet768](evidence/ui/top-768.png), [desktop1440](evidence/ui/top-1440.png); [full page mobile](evidence/ui/viewport-390.png), [tablet](evidence/ui/viewport-768.png), [desktop](evidence/ui/viewport-1440.png). Browser measurements scrollWidth=viewport390/768/1440, không tràn ngang. Đã xem ảnh thực: mobile scenarios chuyển2cột và chat xuống dưới, cần scroll; tablet2cột rồi inspector bên dưới; desktop3cột với trace bên dưới. Trace42events làm trang mobile dài, vẫn đọc/scroll được. Đây là usability limitation của Lab, không phải bằng chứng storefront-ready. Keyboard Tab tới nút gửi/Enter hoạt động; log aria-live=polite; chưa audit WCAG đầy đủ.

[Browser errors/console](evidence/ui/browser_errors.json):0uncaught pageerrors, có console network401/404/controlled503; không tuyên bố zero console noise. [Supplemental attribution](evidence/ui/network-attribution.json) xác minh anonymous auth401 và fresh nonexistent session404; earlier generic404 chưa đủ URL để quy mọi cái về một cause.

## Known contract gaps / new features

- Historical text only: contract limitation; không dựng cards/sources từ text.
- Feedback/trace non-durable: contract limitation; mapping feedback -1/0/1 vs DB1–5 chưa duyệt.
- Missing storefront integration: new feature requiring approval, không thuộc implement scope.
- Malformed response test chỉ một sample incomplete200; không cam kết chịu mọi payload hỏng.
- Current Lab footer nói lịch sử postgres, RAM-feedback/trace đúng mode. Mutations và DEMO được gắn giới hạn rõ ràng. Production build flag/production infrastructure chưa phải runtime đã kiểm thử.

UI09hotfix được chứng minh bằng real browser retry+focused test. Session switch trong pendingPOST bị disable; stale close response được regression component test bảo vệ. Không đổi authentication/API routing.
''')
# Repeat semantic checks, separately counted.
repeat_path=OUT/'evidence/repeats/results.json';repeat=load(repeat_path);repeat_summary={}
for id in ['T07','T15','T30','T31','T51']:
 group=[r for r in repeat if r['repeated_case']==id];checks=[]
 for r in group:
  search=(r.get('effective_query_plan') or {}).get('product_search') or {};cards=(r.get('response') or {}).get('products',[]);text=(r.get('response') or {}).get('message','');plan=(r.get('grounding') or {}).get('query_plan') or r.get('query_plan') or {}
  ok=(search.get('min_ram_gb')==16 and float(search.get('max_price',0))==20000000 and bool(cards)) if id=='T07' else (len(cards)==1 and cards[0]['product_name']=='Xiaomi 15') if id=='T15' else (not cards and bool(plan.get('clarification'))) if id in ['T30','T51'] else ('DEMO' in text and '7 ngày' in text and len(r['response']['sources'])==1)
  checks.append(r['http_status']==200 and ok and all(all(v for k,v in c.items() if k not in ['kind','product_id','variant_id','chunk_id']) for c in r['source_of_truth_checks']))
 repeat_summary[id]={'runs':len(group),'semantic_checks':checks,'trace_ids':[r['trace_id'] for r in group]}
dump(OUT/'evidence/repeats/summary.json',repeat_summary)
assert all(len(r['semantic_checks'])==3 and all(r['semantic_checks']) for r in repeat_summary.values())
durations=[r['duration_ms'] for r in rows if r.get('duration_ms') is not None]
gates={'Fix01':['N03','N04','N12','N46','N47'],'Fix02':['N27','N28'],'Fix04':['B07','B08','B09','B10','B11','N38','N40','N41']}
for ids in gates.values():assert all(by[id]['verdict']=='PASS' for id in ids)
gatetable=table(['Gate','Current cases','Verdict'],[(k,','.join(ids),'ALL PASS') for k,ids in gates.items()])
write('06_FINAL_RELEASE_CANDIDATE_REPORT.md',f'''# Final release candidate — independent review pending

[C2C] STATE: EXECUTED · TASK_ID: c2c_chatbot_final120_ui_readiness · ITERATION:1

**READY_FOR_MANUAL_UAT — LAB SCOPE ONLY.** Đây là candidate để user thử và reviewer đánh giá, chưa phải acceptance hoặc PRODUCTION_READY. **UI_LAB_READY=YES; UI_STOREFRONT_READY=NO.** OfficialRAG relevance và multi-intent không được nghiệm thu như supported features.

## Source và coverage

Git HEAD `{base['actual_head']}` trên `dev/thai-duy-vu` + proposed worktree hotfix candidate `final120-help-and-first-post-retry`; [source hashes](source_hashes.json), [exact diff](evidence/hotfix.diff). Không commit/push; working tree có2appfiles,1frontendtestfile thay đổi,1Pythonregressionfile mới và dossier này. Reviewer phải đánh giá cả diff, không chỉ HEAD. Connector stale `f99f713` được ghi rõ; actual Git là source of truth.

**120/120unique IDs accounted:111PASS /3WARN /2FAIL /4DEFERRED /0BLOCKED.** Quan sát riêng114PASS/3WARN/3FAIL (N44observedFAIL thuộc deferred scope). Không cộng19UIchecks,8supplementalcontrols hoặc15repeats vào120. [Results](results.json), [matrix](01_FROZEN_120_MATRIX.md), [execution comparison](02_EXECUTION_RESULTS.md), [defects](03_DEFECT_REGISTER.md).

| Distribution | PASS | WARN | FAIL | DEFERRED | BLOCKED |
|---|---:|---:|---:|---:|---:|
| Original120 dossier |99|8|9|4|0|
| Current pre-hotfix full120 |110|3|3|4|0|
| Final same-source full120 |111|3|2|4|0|

Original N33WARN→currentFAIL không phải chứng minh thuật toán xấu đi: old evaluator forcedWARN dù citation checkfalse. Lần này giữ actualFAIL+OPEN_DEFERRED_RAG. N42safe clarification nằm trong frozenexpected, không đổi expectation để PASS. N03/N04/N12/N27/N28/N38/N40/N46/N47 và B07/B10 đã được kiểm chứng mới, không dùng acceptance cũ thay execution.

## Mandatory gates

{gatetable}

T23–T30continuity/reference, T37–T47personal/refusal/security và T57/T58/T60auth/ownershipPASS; T59WARN chỉ vì known text-only/feedback/traceRAM limits, durable text/IDs thật vẫnPASS. Real B08 200/409và release; B09write503; B10orphan retained audit nhưng excludedcontext sau restart/retry. [Extra Fix04controls](evidence/fix04-controls/results.json)8PASS gồm cancellation, independent sessions và real process restart, không gọi controlled fault là QwenE2E.

## Open findings / approved deferrals

- **N33/N34 FAIL, MEDIUM, OPEN_DEFERRED_RAG:** wrong-topic evidence/citation dù document mapping đúng và có DEMO disclaimer. Firstfault KNOWLEDGE_RETRIEVAL lexical fallback; không phải payment/order/model-fabrication fault. Không sửa RAG hoặc thêm official docs ở đây; cần checkpoint approval riêng.
- **T51/T59/N18 WARN:** safe typo clarification, RAMmetadata/text-onlyUI, selected-warehouse scope. Không quảng cáo fuzzy correction/durable metadata/all-warehouse aggregation.
- **T56/N43/N44/N48 DEFERRED:** không có bounded multi-task implementation. N44trảcart nhưng chưa nói rõ bỏ qua comparison, observedFAIL vẫn giữ. Không coi là supportedmulti-intent.
- **Storefrontchat NOT_IMPLEMENTED:** cần user approval cho feature mới; Labdirectroute không phải customer integration.

## Automated + UI verification

- Pythonfull **435PASS/0FAIL/0SKIP**, focused264PASS;2newHELPcontrols là subset.
- Selected Javaauth/product/order/payment/inventory/warehouse **89PASS/0FAIL/0SKIP**, Testcontainersisolated; không tuyên bố fullJava.
- FrontendfullVitest **59PASS/0FAIL/0SKIP**, fullESLint và productionbuildexit0.
- RealChrome **19checksPASS**, screenshot390/768/1440 và actual actions. No uncaughtpageerror; có expected/transient network console noise, details trong UIaudit.
- Separate15repeats semantic checks đềuPASS;3samples/prompt không phải thống kê độ tin cậy. T51repeat vẫn là safeclarification, không thành fuzzy-resolutionPASS.
- Sequential API times min{min(durations):.1f}ms / median{statistics.median(durations):.1f}ms / max{max(durations):.1f}ms; includesclient+trace fetch, không phải loadbenchmark hoặc puremodel latency. Relevantstage timings ở từngcaseJSON.

## Manual UAT / readiness limits

Làm theo [Vietnamese manual guide](05_MANUAL_UAT_GUIDE.md). URL sau khi khởi động lại disposable UATstack: `http://localhost:5174/chatbot-lab`. Sau diagnosticcleanup hiện không giữ teststack chạy. Không công bố password/token/privateDSN; guide trỏ tới documented synthetic account credentials trong repo. UAT phải ghi case, PASS/FAIL/BLOCKED, trace và screenshots.

Technicalgates và agreedLabUIgate đã qua cho read-only supportedscope; reviewer/user vẫn phải xem2RAGfailures,4deferrals và knownlimitations trước acceptance. Không gắn READY_WITH_KNOWN_LIMITATIONS như thể user đã chấp nhận mọi finding mới. Không deploy hoặc tuyên bố PRODUCTION_READY; officialRAG/storefront/load/durablemetadata/idempotency còn scope chưa được duyệt/kiểm chứng đầy đủ.

## Integrity, cleanup và Git

[cleanup.json](cleanup.json): sourcebusinessbefore=after; cloned12business tables identical; protected original/accepted reports unchanged; source/app hashes và frozenplan đúng; disposablecontainer removed, ownedprocesses stopped, sourceDBrunningstate restored. Syntheticfixtures chỉ trong clone đã xóa. Existingnormalapplications/Ollama preexisting không bị dừng. Private dump removed; private state/logs ngoàiGit và chmod600.

[Exact commands](test_commands.json), sanitizedoutput và unsuccessful preliminary commands được giữ. Finalresults chỉ từ source sauhotfix; prehotfixarchive tách riêng. Không Flyway/schema/API/auth/businessmutation/RAGchanges. Gitdiff cuối chỉ2productionfiles+tests+dossier, chờreview; không commit/push. Dừng tại C2C này, chờ ChatGPTindependentreview và usermanualacceptance.
''')
# Capture exact reviewable git status/diff without mutating the repository.
dump(OUT/'evidence/final_git_status.json',{'head':subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),'branch':subprocess.check_output(['git','branch','--show-current'],text=True).strip(),'status':subprocess.check_output(['git','status','--short'],text=True).splitlines(),'committed_or_pushed':False})
print('Seven Markdown reports + complete machine-readable dossier generated.')
