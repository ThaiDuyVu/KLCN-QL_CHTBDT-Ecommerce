# Chạy từng project

Mở Terminal tại thư mục chứa file này. Trước mỗi Terminal mới:

```sh
source scripts/env.sh
```

## Backend + PostgreSQL

Mở Docker Desktop, sau đó:

```sh
docker compose -p klcn-ecommerce up -d --wait
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.arguments=--server.address=127.0.0.1
```

Backend: `http://localhost:8080`. Test đầy đủ từ root: `bash scripts/test-backend.sh`.

## Frontend React

Chạy backend trước. Trong Terminal khác:

```sh
cd Fontend
npm run dev -- --host 127.0.0.1
```

Mở `http://localhost:5173`. Tài khoản mẫu: `seed.admin` / `123`.

## Chatbot FastAPI

```sh
cd chatbot-service
python -m uvicorn app.main:app --host 127.0.0.1 --port 8090
```

API docs: `http://localhost:8090/docs`. Hiện chatbot chạy mock.

## Flutter Android

Chạy backend trước. Mở Android Studio → Open thư mục `flutter`; Flutter SDK nằm ở `.local/tools/flutter`, Android SDK ở `~/Library/Android/sdk`.

Nếu Terminal đang ở thư mục `flutter`, chạy đầy đủ:

```sh
source ../scripts/env.sh
flutter emulators --launch ecommerce_android
flutter devices
flutter run -d emulator-5554 --dart-define=API_BASE_URL=http://10.0.2.2:8080
```

Nếu đang ở root dự án: `source scripts/env.sh`, rồi `cd flutter` và chạy các lệnh trên từ dòng `flutter emulators`. Cần nạp `env.sh` trong mỗi Terminal mới; nếu thiếu bước này sẽ báo `zsh: command not found: flutter`.

Nếu ID thiết bị khác, dùng ID từ `flutter devices`. Tài khoản khách mẫu: `seed.customer1` / `123`.

Điện thoại Android qua USB: bật USB debugging, dùng `adb reverse tcp:8080 tcp:8080`, rồi chạy `flutter run -d <device-id> --dart-define=API_BASE_URL=http://127.0.0.1:8080`.

## Dừng

Nhấn `Ctrl+C` ở mỗi Terminal; với Flutter nhấn `q`. Tắt emulator trong Android Studio Device Manager hoặc `adb -s emulator-5554 emu kill`.

Từ root, dừng các project đã chạy bằng script và database, giữ nguyên dữ liệu:

```sh
bash scripts/stop.sh
```

Chạy nhanh cả backend/frontend/chatbot: `bash scripts/start.sh`.

### Giao diện sản phẩm Flutter

- CUSTOMER: Sản phẩm, Giỏ hàng, Đơn hàng, Tài khoản. ADMIN/MANAGER/STAFF: tra cứu Sản phẩm và Tài khoản; quản trị sử dụng web hiện có. Đây là phân luồng giao diện; quyền API vẫn do backend kiểm soát.
- Trang sản phẩm: tìm tên/thương hiệu, lọc danh mục, sắp xếp giá, chọn chi nhánh xem tồn kho, kéo xuống để tải lại. Có thể xem chi tiết cả sản phẩm hết hàng.
- Danh mục, giá, tồn kho, mô tả và phiên bản dùng GET API backend hiện có. Ảnh `/images/products/...` được đóng gói từ `Fontend/public/images/products` vào `flutter/assets/products` để hiển thị trên mobile.
- Đặt hàng COD: chọn chi nhánh ở trang sản phẩm → mở chi tiết → chạm phiên bản → thêm giỏ → chỉnh số lượng/xóa dòng → tiếp tục đặt hàng → nhập người nhận, điện thoại, địa chỉ. Khi đổi chi nhánh có giỏ khác, app yêu cầu xác nhận xóa các dòng cũ. Phiên hết hạn cần đăng nhập lại; chưa tự refresh.
