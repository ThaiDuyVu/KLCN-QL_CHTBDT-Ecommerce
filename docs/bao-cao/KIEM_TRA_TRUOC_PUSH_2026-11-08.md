# Kiểm tra trước push dev/vu — 08/11/2026

Kiểm tra working tree trên nền `f298294`, trước commit tổng hợp thay đổi local. Remote `origin/dev/vu` được fetch; local chứa thêm 7 commit, không thiếu commit remote tại thời điểm kiểm tra. Không merge/push main.

Phạm vi thay đổi hiện có: migration V15–V17 và mapping tương ứng; truy vết serial từ phiếu nhập; giỏ hàng duy nhất/customer; lọc cây danh mục; chặn yêu cầu lại hàng đã EXCHANGED; chặn ticket bảo hành đang mở; UI bảo hành; xử lý lỗi tải trang/cache frontend; hội thoại/cards/comparison trên Flutter; ảnh sản phẩm; tài liệu Knowledge và báo cáo/SQL tổng hợp.

| Kiểm tra chạy mới | Lệnh | Kết quả |
|---|---|---|
| Backend | `source scripts/env.sh`, `cd backend`, `./mvnw -B -Dapi.version=1.44 test` | Exit 0, 345 test, 0 failure/error/skip |
| Frontend | `npm test` tại Fontend | Exit 0, 209 test / 25 file PASS |
| Frontend build | `npm run build` tại Fontend | Exit 0; cảnh báo chunk >500 kB |
| Flutter | `flutter test --no-pub` | Exit 0, 52 PASS, 1 skip |
| Flutter static analysis | `flutter analyze lib/features/chatbot lib/features/warranty lib/core/network/api_client.dart test/features/chatbot test/core/network/api_environment_test.dart` | Exit 0, no issues |
| Knowledge script | AST parse, import bằng `runpy` với tên không phải `__main__`, gọi hàm `parse` | 11 document có source riêng; không kết nối/ghi DB hoặc index |
| Whitespace | `git diff --check`, kiểm tra staged tương ứng trước commit | Không còn lỗi; đã bỏ một dòng whitespace cuối trong Flutter test |
| Bí mật | Rà soát regex key/private key và đối chiếu giá trị bí mật cấu hình local, không in giá trị | Chưa phát hiện trong các file được rà soát |

Backend test dùng Docker Desktop socket macOS và PostgreSQL Testcontainers cô lập. Flutter skip bài checkout backend vì chưa bật `RUN_BACKEND_CHECKOUT_TEST`; không diễn giải là đã chạy E2E checkout mobile. Không chạy benchmark/model cloud/cổng VNPAY. Không chạy lại full Python suite: Python application logic không đổi; script Knowledge mới chỉ kiểm syntax/import/parse.

Raw log giữ local tại `.local/pre-push-20261108/`, không commit. `.env`, API key, database dump/log, APK chứa cấu hình truy cập vẫn nằm ngoài Git. Các class Java chẩn đoán tạm đã được gỡ khỏi source test và output biên dịch trước full suite, để không làm sai số lượng/kỳ vọng test.

Các lỗi còn lại được mô tả trong [kiểm toán CSDL](KIEM_TOAN_CSDL_THEO_DE_CUONG_2026-11-08.md): bán lại serial đã trả bị UNIQUE chặn; danh tính hóa đơn cũ thay đổi theo catalog; hủy đơn thay thế chưa xử lý nghĩa vụ credit. Chưa triển khai sửa các lỗi đó. Tài liệu Knowledge KT-10 còn ghi đổi trả chưa triển khai; giữ nguyên nội dung chính sách chờ review, không chạy seed để thay đổi dữ liệu.

Push bản hiện tại là đồng bộ công việc và bằng chứng kiểm thử, không phải nghiệm thu production hoặc xác nhận toàn bộ nghiệp vụ đã hết lỗi.
