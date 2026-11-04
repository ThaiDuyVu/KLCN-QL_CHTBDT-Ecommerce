# Những file cần mang khi demo trên máy khác

Đối chiếu cấu hình repository ngày 04/11/2026. Có hai cách demo khác nhau.

## 1. Giữ server ở nhà, chỉ dùng laptop khác để trình diễn — cấu hình hiện tại

Website: `https://shop.devthaiduyvu.bid`. Backend, PostgreSQL, Ollama và chatbot vẫn chạy trên máy chủ hiện tại qua Caddy + Cloudflare Tunnel.

| Mang sang laptop demo | Mục đích |
|---|---|
| `.local/demo/dien-viet-demo.apk` | Cài lên emulator/Android để demo mobile. File không có trên GitHub; chuyển riêng bằng USB hoặc kênh riêng |
| `deploy/HUONG_DAN_LAPTOP_DEMO.md` | Hướng dẫn cài Android Emulator, APK, kiểm tra kết nối |
| Tài liệu trình bày/sơ đồ cần dùng | Dùng khi báo cáo; có thể lấy từ GitHub |

**Không cần mang** `.env`, database, Gemini key, token Cloudflare, tunnel credentials hoặc toàn bộ source lên laptop chỉ để trình diễn. APK đã build chứa thông tin kết nối và service token; cần bảo quản riêng, không đăng công khai. Nếu chỉ demo web thì không cần APK hoặc emulator, chỉ cần trình duyệt và tài khoản email đã được Cloudflare Access cho phép.

Trước khi đi: bật server bằng `bash scripts/server-start.sh`, bảo đảm máy chủ không ngủ, có Internet; laptop cũng phải có Internet. Không cần chạy script server trên laptop demo. Máy demo đăng nhập hệ thống bằng tài khoản của project sau khi qua Access.

## 2. Chuyển toàn bộ hệ thống sang máy khác để chạy độc lập

Clone repository trước. Các file sau đang bị Git bỏ qua, cần chuyển riêng hoặc tạo lại theo `.env.example`:

| File | Nội dung cần giữ/điều chỉnh |
|---|---|
| `.env` ở root | Biến Docker/PostgreSQL và cấu hình local liên quan |
| `backend/.env` | PostgreSQL, JWT, VNPAY Sandbox, cookie/CORS và các biến backend đang dùng |
| `chatbot-service/.env` | Gemini key/provider, Ollama, URL backend/DB, warehouse UUID, knowledge/product index, persistence |
| `Fontend/.env` | API/proxy và cấu hình frontend công khai; không đặt secret vào biến VITE_* |

Phải đồng nhất thông tin DB giữa root, backend và chatbot; sửa URL/port/path theo máy mới. Nếu giữ cấu hình seed hoặc mã kho cũ thì cần khôi phục dữ liệu tương ứng; chỉ copy `.env` không tạo ra dữ liệu đó.

**Dữ liệu/tài nguyên phải chuẩn bị thêm:**

- Backup PostgreSQL bằng công cụ phù hợp (ví dụ pg_dump), rồi restore vào database mới có pgvector. Bao gồm catalog, tồn kho, tài khoản demo, tri thức và các index dữ liệu cần thiết. `docs/co-so-du-lieu/CSDL_TONG_QUAT_V1_V9.sql` chủ yếu là cấu trúc + dữ liệu quyền/role, không phải bản sao đầy đủ dữ liệu demo đang dùng.
- Ảnh đã upload: mặc định `backend/uploads/products/`, hoặc đúng thư mục được cấu hình bằng `PRODUCT_IMAGES_DIRECTORY`. Ảnh tĩnh `Fontend/public/images/` có trong repository.
- Model Ollama: tải lại đúng model, hoặc chuyển kho `.local/ollama-models/` nếu dùng cấu hình script hiện tại. Chat và embedding được cấu hình trong chatbot `.env`; embedding vẫn cần khi chọn Gemini.
- Cài đúng Java/Node/Python/Flutter/Docker phù hợp hệ điều hành. Không copy nguyên `.venv`, `node_modules` hoặc bộ công cụ macOS sang Windows. Build lại backend JAR vì `scripts/start.sh` dùng `backend/target/backend-0.0.1-SNAPSHOT.jar`; cài dependencies frontend/Python tương ứng.

## 3. Chỉ khi máy mới cần chạy cùng public tunnel hoặc build lại APK

| Tài nguyên riêng ngoài repo | Khi nào cần |
|---|---|
| `~/.config/klcn/cf-access-token.env` | Máy cần chạy `scripts/flutter-build-demo.sh` để build lại APK qua Cloudflare Access. Hoặc đặt biến CF_TOKEN_FILE trỏ đến file tương ứng |
| `~/.cloudflared/6b927b04-7441-4c27-a801-dfc5d56f0af5.json` | Máy mới thực sự chạy connector của tunnel hiện tại |

`deploy/cloudflared.yml` có trong Git nhưng `credentials-file` đang là đường dẫn tuyệt đối `/Users/vu/...`; cần đổi đúng đường dẫn trên máy mới. Caddyfile dùng PROJECT_ROOT; scripts hiện tại cần công cụ và path tương ứng. Không cần chuyển Cloudflare account certificate chỉ để chạy tunnel bằng credentials JSON này. Nếu tạo tunnel riêng thì dùng credentials riêng và cấu hình host tương ứng.

Nếu chỉ chạy localhost, không cần tunnel credentials hoặc Cloudflare token; build mobile với API_BASE_URL phù hợp emulator/thiết bị. Nếu chuyển public server, cũng phải kiểm tra lại URL callback VNPAY, CORS, cookie và kết nối chatbot. Không đưa bất kỳ file secret, database backup hoặc APK chứa token nào lên GitHub.
