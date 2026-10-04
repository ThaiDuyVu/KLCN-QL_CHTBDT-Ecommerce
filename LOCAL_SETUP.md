# Chạy dự án trên macOS Apple Silicon

Mã nguồn được tải từ nhánh `main` và có đầy đủ `.git`, remote GitHub và lịch sử commit. Flutter Android cũng đã được cài dependencies và cấu hình; xem hướng dẫn ngắn theo từng project ở `RUN_PROJECTS.md`.

## Các dịch vụ

| Thành phần | Phiên bản / địa chỉ |
| --- | --- |
| React/Vite | http://localhost:5173 |
| Spring Boot | Java Temurin 21.0.12.1, http://localhost:8080 |
| PostgreSQL/pgvector | PostgreSQL 16, localhost:5432, database `ecommerce_db` |
| FastAPI | Python 3.12.15, http://localhost:8090/docs |
| Node.js/npm | Node 22.23.3 / npm 10.9.9 |
| Maven | Wrapper của dự án, 3.9.16 |
| Flutter / Dart | Flutter 3.47.6 / Dart 3.13.5 |
| Android Studio | Rabbit 1, 2026.2.1; Flutter và Dart plugins đã cài |
| Android SDK | API 36, emulator ARM64 `ecommerce_android` |

Runtime Java, Node và uv nằm trong `.local/tools`. Python được uv cài trong thư mục người dùng; chatbot có virtualenv riêng tại `chatbot-service/.venv`. Không cần Homebrew. Không thay Python/macOS Java mặc định.

## Khởi động và dừng

Mở Terminal trong thư mục chứa tài liệu này:

```sh
bash scripts/start.sh
```

Script dùng profile backend `dev`, chạy ba ứng dụng local và chờ các endpoint sẵn sàng. Docker Desktop cần đang chạy. Nếu vừa thay đổi mã nguồn, build trước:

```sh
bash scripts/build.sh
```

Dừng các ứng dụng và PostgreSQL, giữ nguyên dữ liệu:

```sh
bash scripts/stop.sh
```

Log tại `.local/logs/`. Database lưu trong volume `klcn-ecommerce_postgres_data`; script dừng không xoá volume. PostgreSQL và ba ứng dụng chỉ mở cổng trên localhost.

## Tài khoản và cấu hình local

Đã bật các seed có sẵn cho database local mới, gồm 16 sản phẩm và tài khoản `seed.admin`, `seed.manager`, `seed.staff`, `seed.customer1`, `seed.customer2`. Mật khẩu mẫu trong mã nguồn là `123`, chỉ dùng cho môi trường phát triển local.

Các file `.env` được tạo riêng ở root, backend, Fontend và chatbot-service. Mật khẩu database và JWT secret được sinh ngẫu nhiên, không nằm trong tài liệu hoặc Git. Không ghi đè `.env` khi chạy lại. VNPay vẫn tắt vì chưa có thông tin merchant.

Frontend gọi `/api` qua Vite proxy tới backend, bao gồm cookie đăng nhập và CSRF. Chatbot hiện là mock theo mã nguồn; chưa tích hợp Ollama/backend thật, nên không cần tải model Ollama để chạy phiên bản hiện tại.

## Dùng runtime trong Terminal và IDE

```sh
source scripts/env.sh
java -version
node --version
python --version
```

Trong IntelliJ, chọn Project SDK Java 21 tại `.local/tools/jdk-21.0.12.1+1/Contents/Home`, Maven Wrapper, profile `dev`, và working directory `backend` để đọc đúng `.env`.

Xcode đã được cài và Git hệ thống hoạt động (`git version 2.50.1`, developer directory `/Applications/Xcode.app/Contents/Developer`). Có thể dùng `git status`, `git pull --ff-only` trực tiếp. Script Git qua Docker vẫn được giữ làm phương án dự phòng:

```sh
bash scripts/git.sh status
bash scripts/git.sh pull --ff-only
```

## Kiểm tra

```sh
bash scripts/test-backend.sh
source scripts/env.sh
npm test --prefix Fontend
(cd chatbot-service && .venv/bin/python -m pytest -q)
```

Script test backend chỉ định Docker API 1.44 để Testcontainers 1.20.6 kết nối được Docker Engine 29; không hạ phiên bản Docker hoặc thay đổi daemon.

Frontend build thành công, 42 test đạt; chatbot 58 test đạt. Toàn bộ backend đã chạy `clean test` thành công: **242 test, 0 failures, 0 errors, 0 skipped**, bao gồm integration test PostgreSQL thực qua Testcontainers. Log: `.local/logs/backend-test.log`; báo cáo chi tiết: `backend/target/surefire-reports/`.

Đã đồng bộ các test cũ với mã hiện tại: chọn chi nhánh trước khi thêm giỏ hàng/checkout; mô phỏng tồn kho giảm sau khi thêm giỏ để kiểm tra rollback; kiểm tra VNPay sandbox bị tắt; kỳ vọng đúng 6 migration V1–V6. Các test sản phẩm dùng đúng API và dependencies hiện tại. Không thay đổi logic nghiệp vụ, schema hoặc migration để làm test pass.

Đăng nhập qua frontend proxy, `/api/auth/me`, danh sách 16 sản phẩm và chatbot health/chat được kiểm tra bằng HTTP thực tế. Các migration V1–V6 có sẵn áp dụng thành công.

## Thay đổi Docker có thể hoàn tác

Docker ban đầu không chạy vì lỗi cài Rosetta. Đã tắt `UseVirtualizationFrameworkRosetta` và khởi động lại Docker; các image dùng ARM64. Cấu hình trước thay đổi được sao lưu tại `~/Library/Group Containers/group.com.docker/settings-store.json.klcn-backup`. Có thể bật lại Rosetta trong Docker Desktop Settings khi cần chạy image x86 và Rosetta đã được cài thành công. Không factory reset, xoá Docker data, hoặc nâng cấp macOS.

## Kiểm tra Flutter Android

Đã cài Android Studio Rabbit 1 (2026.2.1) và xác nhận IDE tải plugin Flutter 97.0.0, Dart 510.0.0. Android SDK API 36, build-tools, platform-tools, NDK 28.2.13676358, CMake 3.22.1 và emulator ARM64 `ecommerce_android` đã cài; Android toolchain và giấy phép SDK đạt kiểm tra `flutter doctor`.

`flutter test` đạt; `flutter build apk --debug` thành công. Đã cài APK trên emulator và đăng nhập tài khoản khách mẫu thành công tới màn hình chính qua backend `http://10.0.2.2:8080`. Log ở `.local/logs/flutter-build.log`, `.local/logs/flutter-test.log`. Analyzer không có error/warning, còn 56 lint mức info trong mã hiện có; Gradle/AGP/Kotlin hiện tại build được, Flutter mới có thông báo về yêu cầu nâng phiên bản trong tương lai. iOS/macOS chưa được cấu hình CocoaPods hoặc Simulator vì yêu cầu hiện tại là Android.

Sau kiểm tra đã dừng các project backend/frontend/chatbot, PostgreSQL, emulator và Gradle daemon; dữ liệu PostgreSQL vẫn giữ nguyên. Xem `RUN_PROJECTS.md` để khởi chạy lại từng phần.
