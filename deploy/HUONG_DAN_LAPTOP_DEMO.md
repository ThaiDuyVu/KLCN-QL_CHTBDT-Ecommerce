# Hướng dẫn cấu hình laptop demo (macOS) — app Flutter trên Android Emulator

Tài liệu này dành cho **agent (hoặc người) trên laptop demo**. Mục tiêu: chạy app mobile Điện Việt
trên Android Emulator, gọi về server thật tại `https://shop.devthaiduyvu.bid` qua Cloudflare,
để trình chiếu song song với bản web trên cùng một màn hình.

Phiên bản cho Windows sẽ bổ sung sau.

## 0. Bối cảnh — đọc trước khi làm

- Server (backend, chatbot, DB, web) chạy trên **máy chủ ở nhà**, không chạy trên laptop này.
  Laptop **không cần mã nguồn, không cần Flutter SDK, không cần Docker**.
- Mọi truy cập vào `shop.devthaiduyvu.bid` đều đi qua **Cloudflare Access**:
  - Trình duyệt: đăng nhập bằng một trong các Gmail được phép (mã PIN gửi qua mail).
  - App mobile: đã được build sẵn kèm **Service Token**, tự đi qua Access, chỉ cần đăng nhập tài khoản hệ thống.
- Bạn nhận được 1 tệp **`dien-viet-demo.apk`** (khoảng 79 MB) chuyển tay từ máy chủ (USB / Drive cá nhân).
  - APK **chứa token truy cập** → không commit vào git, không đăng lên nơi công khai, không gửi vào nhóm chat.
  - SHA-256 (16 ký tự đầu) của bản build mới nhất (2026-11-04, đã sửa luồng thanh toán): `c20485f692d22770`.
    Kiểm tra: `shasum -a 256 dien-viet-demo.apk | cut -c1-16`.
  - APK gồm cả bản arm64 và x86_64 → dùng được trên Mac chip M lẫn Mac Intel.

### Ràng buộc cho agent
- Không cần và **không được** sửa gì trên Cloudflare dashboard hay máy chủ.
- Không cài thêm gì ngoài các mục ở bước 1.
- Nếu một bước lỗi, xem mục **6. Xử lý sự cố**; không tự nghĩ ra cách vòng (ví dụ tắt Access, đổi URL).

## 1. Cài công cụ

Yêu cầu: macOS, còn trống ≥ 15 GB, RAM ≥ 16 GB (khuyến nghị), có Homebrew.

```bash
uname -m                      # arm64 = Mac chip M, x86_64 = Mac Intel (dùng ở bước 2)
brew install --cask android-studio
```

Mở **Android Studio** một lần, chạy hết **Setup Wizard** (Standard) để nó tải Android SDK,
Emulator và Platform-Tools vào `~/Library/Android/sdk`. Sau đó đóng Android Studio cũng được.

Thêm công cụ vào PATH (cho phiên terminal hiện tại và lâu dài):

```bash
cat >> ~/.zshrc <<'EOF'
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
EOF
source ~/.zshrc
adb version && emulator -version | head -1
```

Nếu `cmdline-tools/latest/bin/sdkmanager` chưa có: Android Studio → **Settings → Languages & Frameworks →
Android SDK → SDK Tools** → tick **Android SDK Command-line Tools (latest)** → Apply.

## 2. Tạo máy ảo (AVD)

Cấu hình giống máy chủ đã thử nghiệm: **Pixel 7, Android 16 (API 36), Google APIs**.

```bash
ARCH=$(uname -m | sed 's/x86_64/x86_64/;s/arm64/arm64-v8a/')
IMG="system-images;android-36;google_apis;$ARCH"
yes | sdkmanager --licenses >/dev/null
sdkmanager "platform-tools" "emulator" "$IMG"
echo no | avdmanager create avd -n dien_viet_demo -k "$IMG" -d pixel_7
```

Tăng RAM máy ảo để chạy mượt khi trình chiếu:

```bash
CFG=~/.android/avd/dien_viet_demo.avd/config.ini
sed -i '' 's/^hw.ramSize=.*/hw.ramSize=4096/' "$CFG"; grep -q '^hw.ramSize' "$CFG" || echo 'hw.ramSize=4096' >> "$CFG"
sed -i '' 's/^hw.keyboard=.*/hw.keyboard=yes/' "$CFG"; grep -q '^hw.keyboard' "$CFG" || echo 'hw.keyboard=yes' >> "$CFG"
```

(`hw.keyboard=yes` cho phép gõ bằng bàn phím laptop trong emulator — tiện khi demo.)

## 3. Khởi động emulator và cài APK

```bash
emulator -avd dien_viet_demo >/tmp/emulator.log 2>&1 &
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 3; done
adb install -r /đường/dẫn/tới/dien-viet-demo.apk
adb shell monkey -p com.example.ecommerce_app -c android.intent.category.LAUNCHER 1
```

Hoặc kéo-thả tệp APK vào cửa sổ emulator. Tên app trên máy ảo: **ecommerce_app**.

## 4. Kiểm tra kết nối

**4.1. Từ terminal laptop** (không cần token):

```bash
curl -s -o /dev/null -w '%{http_code}\n' https://shop.devthaiduyvu.bid/
```

| Kết quả | Ý nghĩa |
|---|---|
| `302` | Đúng. Cloudflare Access đang bảo vệ server. Chuyển sang 4.2. |
| `000` / timeout | Laptop không có Internet hoặc mạng chặn. Đổi mạng (4G điện thoại). |

Lưu ý: bước này **không** cho biết máy chủ ở nhà có đang bật hay không (Access trả 302 kể cả khi server tắt).
Bước 4.2 mới kiểm tra được điều đó.

**4.2. Trong app:** đăng nhập tài khoản khách hàng mẫu:

- Tài khoản: `seed.customer1` — Mật khẩu: `123`

Kết quả đúng: vào trang **Home**, chọn được **Chi nhánh xem hàng**, thấy danh sách sản phẩm có ảnh, giá, tồn kho.
Nếu báo lỗi kết nối / phiên làm việc → xem mục 6.

**Báo cáo cho người dùng** sau bước này: kết quả 4.1 (mã HTTP) và 4.2 (đăng nhập được, thấy bao nhiêu sản phẩm),
kèm một ảnh chụp: `adb exec-out screencap -p > ~/Desktop/emulator-check.png`.

## 5. Chuẩn bị cho buổi demo

### Lưu snapshot (mở lên là dùng ngay)
Sau khi đăng nhập xong và đang ở trang Home, để emulator tự lưu trạng thái khi tắt:
đóng cửa sổ emulator bình thường (Quick Boot sẽ lưu snapshot). Lần sau chạy `emulator -avd dien_viet_demo`
sẽ mở đúng màn hình đó trong vài giây.

### Checklist trước giờ demo (khoảng 30 phút trước)
1. Xác nhận với chủ máy chủ: server ở nhà **đang bật** (`scripts/server-start.sh`) và máy không ngủ.
2. Laptop có Internet; chuẩn bị sẵn **4G phát từ điện thoại** làm dự phòng.
3. Mở trình duyệt vào `https://shop.devthaiduyvu.bid`, đăng nhập Gmail được phép (nhận PIN qua mail) — làm trước,
   đừng để lúc demo mới chờ mail.
4. Mở emulator, kiểm tra app vẫn đăng nhập và tải được sản phẩm.
5. Sắp xếp cửa sổ: trình duyệt (web) bên trái, emulator bên phải; tắt thông báo hệ thống (Focus / Không làm phiền).

### Tài khoản mẫu (mật khẩu đều là `123`)
| Tài khoản | Vai trò | Dùng ở |
|---|---|---|
| `seed.customer1` | Khách hàng | App mobile / web |
| `seed.sales` | Nhân viên bán hàng | Web quản trị |
| `seed.warehouse` | Thủ kho | Web quản trị |
| `seed.technician` | Kỹ thuật viên bảo hành | Web quản trị |
| `seed.delivery` | Nhân viên giao hàng | Web quản trị |

Kịch bản gợi ý thể hiện đồng bộ: đặt hàng trên app (seed.customer1) → trên web đăng nhập nhân viên
bán hàng → thấy đơn mới, cập nhật trạng thái → quay lại app thấy trạng thái đơn đổi.

### Phương án dự phòng
- Emulator chậm / lỗi: cài cùng tệp APK lên **điện thoại Android thật**, chiếu màn hình lên laptop bằng
  **scrcpy** (`brew install scrcpy`, bật *USB debugging* trên điện thoại, cắm cáp rồi chạy `scrcpy`).

## 6. Xử lý sự cố

| Hiện tượng | Nguyên nhân thường gặp | Cách xử lý |
|---|---|---|
| App báo lỗi kết nối, 4.1 trả `302` | Server ở nhà đang tắt / máy ngủ / tunnel rớt | Báo chủ máy chủ chạy `bash scripts/server-start.sh` |
| App báo lỗi kết nối, 4.1 trả `000` | Laptop mất mạng / mạng trường chặn | Đổi sang 4G |
| Đăng nhập báo sai | Gõ nhầm tài khoản | Dùng đúng `seed.customer1` / `123` |
| Mọi request bị từ chối (403) dù server bật | Service Token bị thu hồi/đổi trên Cloudflare | Báo chủ máy chủ build lại APK (`scripts/flutter-build-demo.sh`) rồi cài lại |
| Emulator không khởi động / rất chậm | Thiếu RAM, chọn sai kiến trúc image | Đóng app nặng; kiểm tra image đúng `arm64-v8a` (Mac M) hoặc `x86_64` (Intel) |
| `adb: device offline` | Emulator chưa boot xong | Chờ, hoặc `adb kill-server && adb start-server` |
| Ảnh sản phẩm không hiện | Mạng chậm | Kéo để tải lại; kiểm tra 4.1 |

## 7. Sau buổi demo
- Gỡ app khỏi máy ảo nếu laptop dùng chung: `adb uninstall com.example.ecommerce_app`.
- Xoá tệp APK khỏi laptop.
- Chủ máy chủ có thể thu hồi / đổi Service Token `flutter-demo` trên Cloudflare để vô hiệu hoá APK đã phát.
