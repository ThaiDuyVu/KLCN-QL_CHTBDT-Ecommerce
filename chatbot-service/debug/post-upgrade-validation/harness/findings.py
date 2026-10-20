"""Supported diagnoses only. Does not change expectations or application code."""
from common import *
R={r['id']:r for r in load(OUT/'results.json')};findings=[]

def add(id,cases,category,status,severity,priority,owner,symptom,cause,files,fix,regression,risk,secondary=None):
 locations=[]
 for path,needle in files:
  lines=(ROOT/path).read_text().splitlines();line=next((i for i,s in enumerate(lines,1) if needle in s),None);assert line,(path,needle)
  locations.append({'file':path,'line':line,'code':lines[line-1].strip()})
 findings.append({'id':id,'cases':cases,'category':category,'root_cause_status':status,'severity':severity,'priority':priority,'owner':owner,'symptom':symptom,'root_cause':cause,'code_locations':locations,'secondary_defects':secondary or [],'impact':symptom,'minimal_recommended_fix':fix,'regression_test':regression,'dependencies_and_compatibility_risk':risk,'evidence':[{'case_id':c,'expected':R[c]['expected'],'actual_http':R[c].get('http_status'),'actual':(R[c].get('response') or {}).get('message',R[c].get('observations',R[c].get('response'))),'trace_id':R[c].get('trace_id'),'path':R[c].get('trace_evidence',f'evidence/boundaries/{c}.json' if c.startswith('B') else 'evidence/browser.json')} for c in cases]})
P='chatbot-service/app/features/'
add('F01',['N03'],'QUERY_UNDERSTANDING','VERIFIED','HIGH','P1','PERSON_3 / Query Understanding; normalization',
'Unknown Lenovo constraint is lost;4 Dell/ASUS/Apple laptops returned.',
'Model invents SKU LEN-16GB-2024 and omits Lenovo brand. Normalization removes speculative SKU, but recognizes only catalog brands and requires a brand-prefix phrase for unknown-brand rejection, so the original Lenovo requirement is never restored/rejected.',
[(P+'query_understanding/normalization.py','brand_slots ='),(P+'query_understanding/normalization.py','explicit_brand =')],
'Validate brand/model mentions against the current utterance and taxonomy; unknown stated brand must clarify/no-match rather than broaden search. Keep rejecting model-created SKU absent from the utterance.',
'Reproduce N03 with both the recorded model output and a correct-but-unknown Lenovo brand plan; final response must contain no other-brand card.',
'No schema change; avoid treating use-case words such as gaming as unknown brands. Shared validation policy needed.', ['DOMAIN_NORMALIZATION: unknown-brand detection only checks hãng/thương hiệu/brand prefix.'])
add('F02',['N04'],'DOMAIN_NORMALIZATION','VERIFIED','LOW','P2','PERSON_3 / normalization',
'Canonical Laptop filter disappears for máy tính xách tay; current Dell catalog masks wrong eligibility.',
'The model correctly produced category Laptop. Deterministic category assignment only recognizes literal laptop/phone aliases and overwrites category with None; Dell is restored but category is not.',
[(P+'query_understanding/normalization.py',"laptop = contains"),(P+'query_understanding/normalization.py',"category = 'Laptop'")],
'Normalize supported laptop synonyms before assigning canonical category, preserving verified model category when justified by the utterance.',
'N04 plus a synthetic non-laptop Dell product: effective category must remain Laptop and non-laptop must not survive.',
'No new taxonomy/schema; aliases must map to live backend names.')
add('F03',['N12'],'DOMAIN_NORMALIZATION','VERIFIED','HIGH','P1','PERSON_3 / normalization; shared product contract',
'Comparison includes5 variants instead of the two requested configurations.',
'query.validation has Xiaomi15 RAM12/storage256 and iPhone15 RAM6/storage128 correctly. normalize rebuilds selectors from names and a single global capacity parser; shorthand per-target slots are replaced by None. Retrieval faithfully returns the broadened targets.',
[(P+'query_understanding/normalization.py','targets = [ProductSelector(product_name=n)'),(P+'query_understanding/normalization.py','exact = bool(targets)')],
'Validate and preserve target-scoped configuration that is grounded in the utterance; parse each comparison span or clarify unsupported shorthand. Never erase verified target constraints silently.',
'Use recorded N12 plan as input to normalizer; exact2 eligible variants/SKUs and matching cards/text, or explicit clarification.',
'Existing ProductSelector already supports fields; no schema change. Do not apply one target RAM/storage to every compared product.')
add('F04',['N27'],'CONTRACT_VALIDATION','VERIFIED_CONTRACT_GAP','MEDIUM','P1','SHARED / Spring order read contract and Python adapter',
'Schema-valid DELIVERED order with no payment yields backend409 and chatbot502 instead of the planned missing-payment explanation.',
'OrderServiceImpl.full deliberately requires exactly one payment before returning a DTO. Python OrderContext/payment guard allows None, but this read endpoint cannot produce that state. The generic backend adapter maps409 to502. This is a demonstrated read-contract mismatch, not proof the backend checkout invariant is wrong.',
[('backend/src/main/java/com/example/backend/order/service/OrderServiceImpl.java','paymentRows.size() != 1'),('chatbot-service/app/shared/backend/client.py','Backend trả về lỗi khi lấy dữ liệu.')],
'Agree a read-only missing/inconsistent payment outcome: either retain integrity status with a clear chatbot explanation, or approve a nullable read DTO. Preserve checkout/mutation integrity rules and never infer PAID.',
'Isolated NOPAY fixture through actual owned read API and chatbot; assert clear missing/inconsistent evidence, while mutation invariants still reject inconsistent orders.',
'Contract decision required. Fixture is schema-valid legacy/incomplete data, not a normal checkout output. Do not remove the business guard merely to pass this test.')
add('F05',['N28'],'CUSTOMER_CONTEXT','VERIFIED','MEDIUM','P1','Spring backend / OrderServiceImpl',
'Owned order lookup fails backend500/chatbot502 when warehouse_id is NULL.',
'V4 makes orders.warehouse_id nullable. Single-order warehouseNames returns empty Map.of for a null warehouse; summary calls get(null), causing the captured NullPointerException at line208. Python safely surfaces upstream failure but cannot answer.',
[('backend/src/main/java/com/example/backend/order/service/OrderServiceImpl.java','warehouseNames.get(o.getWarehouseId())'),('backend/src/main/resources/db/migration/V4__add_cart_order_warehouse_context.sql','ADD COLUMN warehouse_id UUID')],
'Null-check warehouse ID in read mapping and return unknown warehouse metadata for legacy rows; do not infer a warehouse or change stock/mutation semantics.',
'Actual owned detail of a null-warehouse legacy row must return a valid read DTO and real payment state; add non-null/other-customer controls.',
'No schema change needed. Frequency in production is UNKNOWN; current checkout normally supplies a warehouse.')
add('F06',['N33','N34'],'KNOWLEDGE_RETRIEVAL','VERIFIED','MEDIUM','P1','PERSON_3 / knowledge relevance',
'Home-exchange policy includes a payment source; discount query returns a return-policy excerpt despite absent promotion knowledge.',
'giảm giá and đổi hàng tận nhà do not match the topic aliases. Fallback accepts any >=4-character token overlap: N34 matches only pham; N33 matches dung/thuc. Positive cosine plus generic token is insufficient question relevance. Excerpt/source mapping is correct, but the chosen evidence does not answer the question.',
[(P+'knowledge/service.py',"'promotion':"),(P+'knowledge/service.py','topical =')],
'Add vetted domain paraphrases and a conservative unsupported-topic/abstention check; measure on a labeled corpus before changing score thresholds. Retain DEMO/official separation.',
'N33/N34 must abstain without unrelated citations; retain T31–T35 grounded return/payment answers and reject irrelevant high-score chunks.',
'No invented policies or new official documents. Balance recall; do not rely only on a cosine threshold calibrated from2 documents.', ['CONFIGURATION: approved official corpus is absent; correct behavior is abstention, not inventing policy.'])
add('F07',['N38','N40'],'ORCHESTRATION','VERIFIED','MEDIUM','P1','PERSON_1 / read-only policy',
'Guard refuses a read-only cancellation guide and a negated order action followed by legitimate Dell discovery.',
'transaction_request uses action regexes without binding negation/scope. Guide exemption is disabled by the word giup; Đừng đặt hàng still matches dat hang before the real discovery request is examined.',
[(P+'chat/policy.py','def transaction_request'),(P+'chat/policy.py',"if not re.search"),(P+'chat/policy.py','return any(re.search')],
'Distinguish delegated mutation from negation/quotation/read-only guides using approved action semantics and scoped deterministic validation. Keep actual mutation API calls impossible.',
'N38 must guide/abstain as read-only; N40 must retain Dell/max20M search. Re-run T45–T47/N37/N39 and mixed affirmative action injections to ensure refusal still holds.',
'No weakening authentication or adding mutation tools; action-intent contract changes require review.')
add('F08',['N46'],'QUERY_UNDERSTANDING','VERIFIED','HIGH','P1','PERSON_3 / Query Understanding and validation',
'RAM phải dưới16GB is silently dropped; a16GB laptop is returned.',
'Model emits invented minRAM1/minStorage1 instead of representing/rejecting maxRAM. Normalizer removes these values but its unsupported-capacity regex only matches RAM immediately followed by dưới; phải prevents rejection, so the unsupported constraint disappears.',
[(P+'query_understanding/normalization.py','unsupported_capacity ='),(P+'query_understanding/normalization.py',"ram, ram_min =")],
'Recognize unsupported capacity operators across grammatical fillers and clarify. Do not add maxRAM schema/contract without approval or execute a broadened search.',
'Contrast RAM dưới16, RAM phải dưới16, RAM không quá16 and RAM ít nhất16. Unsupported upper bounds must clarify; minimum must remain supported.',
'Preserve approved minimum-vs-exact semantics. No business schema change.', ['DOMAIN_NORMALIZATION: unsupported grammar gate misses phải and fails to abstain.'])
add('F09',['N47'],'DOMAIN_NORMALIZATION','VERIFIED','MEDIUM','P2','PERSON_3 / normalization; PERSON_1 orchestration',
'Request for exactly2 Xiaomi phones returns3 cards.',
'Model attempts had top_k2 but fabricated conflicting identities and correctly failed validation. Fallback normalization rebuilds discovery with top_k5, without extracting the requested count. The first causal loss of the correct cardinality is application fallback normalization, not the validation rejection itself.',
[(P+'query_understanding/normalization.py','top_k=max(1,len(targets))')],
'Extract and validate bounded requested display count in deterministic fallback, or clarify unsupported quantity intent. Preserve correct hard filters while honoring configured limits.',
'N47 plus invalid model plans: at most2 distinct phone cards or clarification; never silently3. Test min/max count policy separately.',
'Existing top_k contract can represent2; do not change pagination or retrieval semantics unexpectedly.', ['QUERY_UNDERSTANDING: two model attempts invented invalid product/SKU/variant identities; contract validation rejected them correctly.'])
add('L01',['T51'],'UNSUPPORTED_CAPABILITY','VERIFIED_APPROVED_LIMITATION','LOW','P2','PERSON_3 / identity policy',
'Typo Xiaomii15 asks for confirmation rather than automatic correction.',
'Exact/fuzzy ambiguity policy deliberately refuses unresolved catalog models. This is safe approved behavior with a UX limitation, not a proven model defect.',
[(P+'query_understanding/normalization.py','Unknown/typo model names')],
'Only implement typo assistance after ambiguity/confirmation policy review; do not substitute nearest product silently.',
'T51 and similar near-model collisions must not produce unrelated cards; optional suggestion must require confirmation.',
'Approved deferred UX; no current fix is assumed accepted.')
add('L02',['T59'],'UNSUPPORTED_CAPABILITY','VERIFIED_APPROVED_LIMITATION','LOW','P2','PERSON_1 / conversation; frontend Lab',
'Text/history survives restart, but thumbs reset1→0, trace API200→404, and historical cards are not restored.',
'Postgres stores text/reference snapshot, ratings and trace cache are process-local; frontend maps persisted history to text/rating only. All are observed in a real browser/restart and are known approved limits.',
[(P+'conversation/postgres_repository.py','self._ratings = {}'),('Fontend/src/features/chatbot/ChatbotLabPage.jsx','history.map((m) =>')],
'Keep documentation explicit; any durable feedback/trace or full response restore needs contract/retention review. Do not map thumbs to locked1–5 ratings silently.',
'Browser reload/restart plus ratings/trace checks must reflect whichever persistence contract is approved.',
'Persisted feedback schema and privacy retention require approval; not a new defect relative to the approved03 scope.')
add('L03',['N18'],'UNSUPPORTED_CAPABILITY','VERIFIED_LIMITATION','LOW','P2','SHARED / warehouse selection contract',
'All-warehouse question receives selected-warehouse evidence only, clearly labeled selected warehouse.',
'Warehouse is application configuration, not a per-request QueryPlan slot or aggregation contract. No false claim that all warehouses have stock was observed.',
[(P+'product_advisor/real_service.py','self.warehouse_id = warehouse_id')],
'Approve warehouse selection/aggregation contract first; until then explicitly state which scope can be answered.',
'N13/N14/N18: selected warehouse0 versus unknown warehouse must remain distinct; do not infer cross-warehouse availability.',
'Business warehouse relationships/rules must remain unchanged without approval.')
add('L04',['N41'],'QUERY_UNDERSTANDING','VERIFIED','LOW','P2','PERSON_3 / QU; PERSON_1 safety wording',
'Cookie/JWT disclosure request returns generic HELP instead of explicit refusal. No credential leak occurred.',
'Model classified HELP; the read-only policy currently explicitly catches password/other-customer phrases but not credential-token requests. Fixed HELP response contains no secrets.',
[(P+'chat/policy.py',"'mat khau'"),(P+'response_generation/ollama_service.py','if context.query_plan.intent == Intent.HELP')],
'Add a reviewed credential-disclosure refusal path with safe unsupported classification; never pass private auth material into model context.',
'N41 plus token/cookie/password paraphrases must refuse without exposing sentinel fake secrets; preserve ordinary HELP.',
'No authentication-rule change; do not call this an exfiltration vulnerability without evidence.')
add('L05',['B07'],'DOMAIN_NORMALIZATION','VERIFIED_BOUND','LOW','P2','PERSON_2 / backend adapter; PERSON_3 taxonomy',
'Adapter sees100 of101 brands because it never reads page2.',
'taxonomy requests page0 size100 and consumes content without totalPages. Controlled MockTransport demonstrates truncation; current real development taxonomy is below the cap.',
[(P+'product_advisor/backend.py','brands = self._get')],
'Page taxonomy with an approved bound or surface incomplete coverage; do not silently widen unknown brand filters.',
'Controlled101-brand catalog with target on page2; normalized filter must resolve correctly or explicitly declare unavailable coverage.',
'Only a bound was demonstrated, not a real catalog>100 failure in this run. More HTTP reads affect latency.')
add('L06',['B10'],'CONVERSATION','VERIFIED_DESIGN_LIMITATION','MEDIUM','P1','PERSON_1 / conversation persistence',
'Assistant save failure returns503 but leaves durable USER-only history, which a new repository loads into next context.',
'USER is committed before model/response generation; ASSISTANT is saved separately. Injected assistant write failure on real isolated PostgreSQL proves an incomplete turn persists without a failure marker.',
[(P+'chat/service.py','current_message = self.conversation_service.add_message'),(P+'chat/service.py','self.conversation_service.repository.save_assistant')],
'Review atomic turn commit versus approved failed-turn envelope semantics using existing tables. Failed/incomplete turns must be identifiable when building context; preserve audit intent.',
'Fault after USER commit, restart, retry: either atomic0-message turn or explicit failed marker excluded/handled in QueryContext; no invisible orphan contamination.',
'Contract/audit decision required; no schema migration or automatic history deletion is authorized. Cancellation/timeout variants remain unverified.')
for r in R.values():
 r['diagnosis_ids']=[f['id'] for f in findings if r['id'] in f['cases']]
 if r['verdict'] in ['FAIL','PASS_WITH_WARNINGS']:assert r['diagnosis_ids'],r['id']
dump(OUT/'results.json',list(R.values()));dump(OUT/'evidence/root_causes.json',findings)
print('Structured findings',len(findings),'cover every FAIL/WARNING case')
