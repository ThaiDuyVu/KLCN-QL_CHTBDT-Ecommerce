# Final release candidate — independent review pending

[C2C] STATE: EXECUTED · TASK_ID: c2c_chatbot_final120_ui_readiness · ITERATION:1

**READY_FOR_MANUAL_UAT — LAB SCOPE ONLY.** Đây là candidate để user thử và reviewer đánh giá, chưa phải acceptance hoặc PRODUCTION_READY. **UI_LAB_READY=YES; UI_STOREFRONT_READY=NO.** OfficialRAG relevance và multi-intent không được nghiệm thu như supported features.

## Source và coverage

Git HEAD `74f4e198466217b26409a3995a6bd7991d243082` trên `dev/thai-duy-vu` + proposed worktree hotfix candidate `final120-help-and-first-post-retry`; [source hashes](source_hashes.json), [exact diff](evidence/hotfix.diff). Không commit/push; working tree có2appfiles,1frontendtestfile thay đổi,1Pythonregressionfile mới và dossier này. Reviewer phải đánh giá cả diff, không chỉ HEAD. Connector stale `f99f713` được ghi rõ; actual Git là source of truth.

**120/120unique IDs accounted:111PASS /3WARN /2FAIL /4DEFERRED /0BLOCKED.** Quan sát riêng114PASS/3WARN/3FAIL (N44observedFAIL thuộc deferred scope). Không cộng19UIchecks,8supplementalcontrols hoặc15repeats vào120. [Results](results.json), [matrix](01_FROZEN_120_MATRIX.md), [execution comparison](02_EXECUTION_RESULTS.md), [defects](03_DEFECT_REGISTER.md).

| Distribution | PASS | WARN | FAIL | DEFERRED | BLOCKED |
|---|---:|---:|---:|---:|---:|
| Original120 dossier |99|8|9|4|0|
| Current pre-hotfix full120 |110|3|3|4|0|
| Final same-source full120 |111|3|2|4|0|

Original N33WARN→currentFAIL không phải chứng minh thuật toán xấu đi: old evaluator forcedWARN dù citation checkfalse. Lần này giữ actualFAIL+OPEN_DEFERRED_RAG. N42safe clarification nằm trong frozenexpected, không đổi expectation để PASS. N03/N04/N12/N27/N28/N38/N40/N46/N47 và B07/B10 đã được kiểm chứng mới, không dùng acceptance cũ thay execution.

## Mandatory gates

| Gate | Current cases | Verdict |
|---|---|---|
| Fix01 | N03,N04,N12,N46,N47 | ALL PASS |
| Fix02 | N27,N28 | ALL PASS |
| Fix04 | B07,B08,B09,B10,B11,N38,N40,N41 | ALL PASS |


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
- Sequential API times min54.6ms / median1311.1ms / max6893.4ms; includesclient+trace fetch, không phải loadbenchmark hoặc puremodel latency. Relevantstage timings ở từngcaseJSON.

## Manual UAT / readiness limits

Làm theo [Vietnamese manual guide](05_MANUAL_UAT_GUIDE.md). URL sau khi khởi động lại disposable UATstack: `http://localhost:5174/chatbot-lab`. Sau diagnosticcleanup hiện không giữ teststack chạy. Không công bố password/token/privateDSN; guide trỏ tới documented synthetic account credentials trong repo. UAT phải ghi case, PASS/FAIL/BLOCKED, trace và screenshots.

Technicalgates và agreedLabUIgate đã qua cho read-only supportedscope; reviewer/user vẫn phải xem2RAGfailures,4deferrals và knownlimitations trước acceptance. Không gắn READY_WITH_KNOWN_LIMITATIONS như thể user đã chấp nhận mọi finding mới. Không deploy hoặc tuyên bố PRODUCTION_READY; officialRAG/storefront/load/durablemetadata/idempotency còn scope chưa được duyệt/kiểm chứng đầy đủ.

## Integrity, cleanup và Git

[cleanup.json](cleanup.json): sourcebusinessbefore=after; cloned12business tables identical; protected original/accepted reports unchanged; source/app hashes và frozenplan đúng; disposablecontainer removed, ownedprocesses stopped, sourceDBrunningstate restored. Syntheticfixtures chỉ trong clone đã xóa. Existingnormalapplications/Ollama preexisting không bị dừng. Private dump removed; private state/logs ngoàiGit và chmod600.

[Exact commands](test_commands.json), sanitizedoutput và unsuccessful preliminary commands được giữ. Finalresults chỉ từ source sauhotfix; prehotfixarchive tách riêng. Không Flyway/schema/API/auth/businessmutation/RAGchanges. Gitdiff cuối chỉ2productionfiles+tests+dossier, chờreview; không commit/push. Dừng tại C2C này, chờ ChatGPTindependentreview và usermanualacceptance.
