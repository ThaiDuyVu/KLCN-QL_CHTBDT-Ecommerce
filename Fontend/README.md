# Frontend — QL_CHTBDT_Ecommerce

React + Vite, JavaScript/JSX, React Router (declarative), Vitest và ESLint.
Tên thư mục `Fontend` được giữ đúng theo template yêu cầu.

## Chạy local

Node.js 20.19+ (nhánh 20) hoặc 22.12+; khuyến nghị dùng Node.js LTS.

```bash
cd Fontend
npm ci
npm run dev
```

```bash
npm run lint
npm run build
npm run preview
```

## Phạm vi

- Layout responsive, sidebar/header, home và các trang hệ thống.
- Mỗi domain sở hữu `features/<domain>/{api,components,pages}`. Thư mục trống
  có `.gitkeep`; không khai báo endpoint hay giả lập API nghiệp vụ.
- Product có trang list và detail kết nối backend. Cart/Checkout/Order đã kết nối API thật. Category, Customer,
  Inventory và User Management hiện vẫn có trang placeholder.
- `App.jsx` khai báo routing; `main.jsx` bootstrap router và auth provider.
- Login đã kết nối backend; `auth/api/authApi.js` sở hữu các API auth.
  `AuthContext` chỉ lưu metadata phiên trong React; không lưu mật khẩu/JWT.
  Reload khôi phục phiên bằng cookie HttpOnly của backend.
- `components/ui/` chỉ chứa UI tái sử dụng; UI đặc thù domain đặt trong feature.
- Các test skeleton cũ vẫn được giữ nguyên, chưa cập nhật sang auth thật.
  Task triển khai login không thêm/sửa hoặc chạy test theo yêu cầu.

## Route và vai trò

| Route | Điều hướng frontend |
| --- | --- |
| `/`, `/categories`, `/login`, `/forbidden` | Công khai |
| `/account`, `/products`, `/products/:productId` | Đã đăng nhập |
| `/orders`, `/customers`, `/inventory` | STAFF, MANAGER hoặc ADMIN |
| `/user-management` | ADMIN |
| Các path khác | Trang 404 |

Role lấy từ `LoginSessionResponse.roleName`, không do người dùng chọn.
Cart/Checkout/Order đã kết nối API thật; Category, Customer, Inventory và User Management còn placeholder. Route protection chỉ bảo vệ điều hướng UI;
backend là nguồn quyết định xác thực và quyền truy cập API, kể cả khi permission
được cấu hình lại trong database.

## Product list và detail

- `/products`: card hiển thị tên, Category, Brand, mô tả và trạng thái; nút xem
  chi tiết. Tìm theo tên/mô tả và trạng thái, submit bằng nút Tìm kiếm hoặc Enter.
- Phân trang backend, 12 sản phẩm/trang. `page` trong URL bắt đầu từ 1, khi gọi
  API chuyển về 0. URL giữ keyword/status/page khi reload hoặc quay lại từ detail.
- `GET /api/v1/products?page=0&size=12&keyword=...&status=...` trả page DTO.
  Không gọi detail riêng cho từng card vì list API hiện chưa trả ảnh/giá.
- `/products/:productId`: `GET /api/v1/products/{productId}/detail`, hiển thị
  Product/Category/Brand, chọn ảnh, bảng variants/giá bán, specifications.
  Giá vốn chỉ hiển thị trong UI cho ADMIN/MANAGER. Backend hiện vẫn trả giá vốn
  cho CUSTOMER; ẩn UI không thay thế authorization backend (xem báo cáo Catalog).
- Có loading skeleton, empty state, nút thử lại khi lỗi kết nối, 403/404;
  chuyển trang sẽ hủy request cũ. Access cookie hết hạn thử refresh một lần;
  nếu vẫn 401, xóa metadata phiên và trở về login, giữ URL cần truy cập.
- Chưa có form CRUD/mua hàng/giỏ hàng hoặc dữ liệu tồn kho trong hai trang này.
  Các API tương ứng chưa nằm trong phạm vi list/detail.
- Ảnh mẫu dùng `/images/products/<slug>/main.jpg` và `detail.jpg`, file thuộc
  `Fontend/public`. Nếu chưa bổ sung file, UI hiện “Chưa có ảnh”.

## Login và kết nối backend

### Tài khoản local development

Seed nằm ở `backend/src/main/java/com/example/backend/auth/seed/AuthSeedData.java`.
Chỉ chạy với profile `dev` và cờ opt-in; mặc định không seed, không ghi đè tài khoản
đã tồn tại và không thay đổi schema/Flyway.

```bash
cd backend
./mvnw -DskipTests spring-boot:run -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.arguments=--app.seed.auth.enabled=true
```

Tài khoản mẫu: `seed.admin`, `seed.manager`, `seed.staff`, `seed.customer1`,
`seed.customer2`. Tất cả dùng mật khẩu development được yêu cầu trong task seed,
mã hóa BCrypt bằng PasswordEncoder hiện có. Không sử dụng các tài khoản mẫu này
trong production.

Mở `http://localhost:5173/login` để khớp origin CORS mặc định của backend;
`localhost` và `127.0.0.1` là hai origin khác nhau.

Mặc định frontend gọi `/api` cùng origin. Vite proxy chuyển `/api` đến
`http://localhost:8080`; backend phải được khởi động riêng. Có thể cấu hình
`BACKEND_PROXY_TARGET` trong `Fontend/.env.local` nếu backend dùng port khác.

Luồng theo backend hiện tại:

- `GET /api/auth/csrf`: tạo cookie `XSRF-TOKEN` khi chưa có; mọi mutation gửi
  header `X-XSRF-TOKEN` và `credentials: include`.
- `POST /api/auth/login`: JSON `{ username, password }`; không hỗ trợ login
  bằng email hoặc tham số `rememberMe`.
- `GET /api/auth/me`: lấy `{ userId, username, displayName, roleName }`.
- Khi `/me` trả 401, thử `POST /api/auth/refresh` một lần để khôi phục phiên.
  Refresh dùng single flight vì backend xoay vòng refresh token.
- `POST /api/auth/logout`: thu hồi refresh token và xóa cookie trên máy chủ.
  Nếu request thất bại, UI báo lỗi và không giả vờ đã đăng xuất thành công.

HTTP client xử lý JSON, lỗi text/JSON và timeout 15 giây. Không đọc access/refresh
token bằng JavaScript, không lưu token trong localStorage/sessionStorage.
Tùy chọn ghi nhớ **chỉ lưu username**; thời hạn cookie do backend quản lý.
Quên mật khẩu/đăng ký chỉ hiện thông báo chưa hỗ trợ, không gọi endpoint giả.
Không chỉnh sửa backend, schema hoặc tạo dữ liệu trong task này.

Production cần reverse proxy `/api` đến backend (proxy Vite chỉ dùng development).
Nếu đổi `VITE_API_BASE_URL` sang origin khác, phải kiểm tra CORS, cookie Domain,
Path/SameSite/Secure; frontend phải đọc được cookie CSRF trên origin của mình.
Không chứa secret trong biến `VITE_*` vì các biến này là public.

Deployment dùng BrowserRouter cần cấu hình server fallback về `index.html`
cho các đường dẫn frontend, không áp dụng fallback cho asset/API.

## Cart, Checkout và Order

CUSTOMER: thêm variant từ Product Detail, mở `/cart`, checkout COD tại
`/checkout`, xem danh sách/chi tiết/hủy PENDING tại `/my-orders`.
STAFF/MANAGER/ADMIN: `/orders` và `/orders/:orderId` có action theo state machine
được backend trả về; không cho nhập status tùy ý.

Client riêng trong features/cart/api và features/orders/api dùng apiClient chung.
Có loading/error/empty/submitting state và confirmation khi xóa item/hủy/chuyển
status. VNPAY và Trả góp disabled Coming soon. Giá trên cart là dự kiến; backend
đọc lại giá và reserve stock khi checkout. Cần inventory có stock khả dụng;
Catalog seed không tự thêm tồn kho. Khi kiểm thử toàn bộ seed ở profile `dev`, bật
thêm `--app.seed.inventory.enabled=true` để tạo kho và tồn mẫu cho các SKU `DEV-*`.

Chi tiết contract/permission còn thiếu và giới hạn allocation theo kho:
`backend/docs/cart-checkout-order.md`.
