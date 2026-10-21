# Baseline và phạm vi final120

Workspace **QL_CHTBDT_Ecommerce**, branch **dev/thai-duy-vu**. Actual Git HEAD **74f4e198466217b26409a3995a6bd7991d243082**, initial tracked tree clean. Connector đúng workspace nhưng báo `f99f713`; không dùng metadata đó thay actual Git. Chưa sync/rebase/merge hoặc tự suy đoán HEAD planning còn đúng. Accepted Fix01 `8b1753f`, Fix02 `6c7e408`, Fix04 `74f4e19` được đối chiếu actual history và source.

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
