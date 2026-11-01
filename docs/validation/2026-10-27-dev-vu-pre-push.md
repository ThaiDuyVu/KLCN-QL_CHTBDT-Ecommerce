# Kiểm tra trước khi push dev/vu — 27/10/2026

## Phạm vi

Theo yêu cầu commit các thay đổi hiện có và push nhánh `dev/vu`. Sau fetch, chưa có local/remote branch đúng tên `dev/vu`, nên tạo từ HEAD `746d5fd` của `dev/thai-duy-vu`, giữ nguyên các thay đổi trong workspace. Không merge hoặc push vào main.

Đã đưa 167 file thay đổi có sẵn vào bảy commit theo nhóm; tài liệu này là commit bổ sung ghi kết quả kiểm tra:

| Commit | Nội dung |
|---|---|
| 414758d | Chatbot: context/reference, combo ngân sách, so sánh có evidence, feedback lưu DB, UI và regression tests |
| 07169a8 | Bộ benchmark/context evaluation, harness và kết quả đánh giá đã có sẵn |
| eb15b40 | Chức danh nhân viên, quyền endpoint/giao diện, transition đơn hàng, migration V9 bổ sung role và tests |
| 8abda1c | Xuất báo cáo Excel-compatible, nhãn tiếng Việt và lưu ngưỡng tồn kho |
| d840ad8 | Flutter checkout đọc trạng thái bật VNPAY từ backend và chọn phương thức phù hợp |
| a5758f0 | Sửa reset form bảo hành sau await, hai regression tests và tài liệu chẩn đoán VNPAY/bảo hành |
| 663bf92 | Tài liệu khóa luận, DOCX/TXT, công cụ biên tập và sơ đồ |

## Kết quả kiểm tra mới

Các lệnh Node/Java/Flutter dùng toolchain trong `scripts/env.sh`.

| Kiểm tra | Lệnh | Kết quả |
|---|---|---|
| Chatbot full suite | Trong chatbot-service: `.venv/bin/python -m pytest tests -q` | Exit 0; 649 passed, 13 skipped, 1 deprecation warning |
| PostgreSQL conversation | Cùng pytest, `tests/test_persistent_conversation.py -q`, có `CONVERSATION_TEST_DATABASE_URL` trỏ container riêng | Exit 0; 20 passed |
| Frontend full suite | Trong Fontend: `npm test` | Exit 0; 18 test files, 174 tests passed |
| Frontend lint | `npm run lint` | Exit 0 |
| Frontend build | `npm run build` | Exit 0; có cảnh báo chunk lớn hơn 500 kB |
| Backend full suite | Trong backend: `DOCKER_HOST=unix://$HOME/.docker/run/docker.sock ./mvnw -q -Dapi.version=1.44 test` | Exit 0; 289 tests, 0 failures, 0 errors, 0 skipped |
| Flutter full suite | Trong flutter: `flutter test` | Exit 0; 18 passed, 1 skipped (integration backend opt-in không bật) |
| Flutter analyze | `flutter analyze` | Exit 1; 0 errors, 1 warning unused_element, 59 info về style/deprecation |

Số PostgreSQL 20 passed là tập con được chạy riêng để bao phủ phần persistence bị skip khi không có database URL; không cộng thành số test distinct của full suite.

Lần Maven đầu `./mvnw -q test` lỗi môi trường: Docker Java client/Testcontainers không kết nối được Docker API; 261 tests, 5 errors, 41 skipped. Thử `BackendApplicationTests` với socket/API explicit thành công, sau đó chạy lại **full suite** bằng cùng cấu hình và đạt kết quả ở bảng. Chỉ điều chỉnh tham số lệnh kiểm thử, không sửa cấu hình production để làm test pass.

Lần gọi PostgreSQL ban đầu có kèm đường dẫn test không tồn tại (`tests/test_conversation_turn_safety.py`), exit 4, không chạy test. Đã sửa lệnh sang đúng file persistence hiện có và chạy thành công. PostgreSQL kiểm thử dùng container pgvector riêng, schema V1, dữ liệu synthetic; đã xóa container sau mỗi lần thử, không sửa database business đang chạy.

Log thực thi lưu local tại `.local/pre-push-20261027/`, không commit raw runtime/test logs. Kết quả benchmark/context trong commit 07169a8 là **evidence lịch sử có sẵn**, không trình bày như benchmark vừa chạy lại trong lần push này. Các REPORT.md lịch sử giữ nguyên nội dung, gồm các dòng trống cuối file mà `git diff --check` cảnh báo; không chỉnh evidence chỉ để bỏ cảnh báo định dạng.

## Bảo vệ cấu hình và giới hạn

- Kiểm tra 167 file thay đổi, parse được 31 JSON. Kiểm tra cả XML bên trong DOCX để tìm credentials thực tế đã biết; không có match.
- Không thấy private key, JWT token hoặc GitHub token theo các pattern được kiểm tra. Scan là kiểm tra có giới hạn, không phải cam kết bảo mật tuyệt đối.
- Không đưa `.env`, credentials VNPAY, `.local/`, file log runtime hoặc toolchain vào commit.
- Không tự sửa các 60 thông báo Flutter analyzer ngoài phạm vi review/push. Warning duy nhất ở `warehouse_selector_widget.dart` là `_confirmWarehouseChange` chưa dùng.
- Không chạy lại thanh toán VNPAY thật hoặc tạo warranty ticket mới; lỗi gateway 71 vẫn là vấn đề chưa xử lý được bằng thay đổi local đã chẩn đoán riêng.
- Không thực hiện benchmark LLM E2E mới hoặc kiểm tra thủ công mọi trang. Các bộ test PASS không đồng nghĩa xác nhận production-ready.

Push dùng nhánh đích `origin/dev/vu`, không force push. Kết quả push thực tế được thông báo trong phiên làm việc sau khi server xác nhận.
