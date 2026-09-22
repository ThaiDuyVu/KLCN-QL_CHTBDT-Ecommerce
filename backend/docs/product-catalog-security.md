# Authorization của Product Catalog

Catalog sử dụng convention hiện tại: JWT/cookie xác thực user, principal chứa authority là tên role, và `@RequireAnyAuthority` kiểm tra role ở controller qua method security.

| API | Đọc (GET, bao gồm list, filter, detail) | Tạo/cập nhật/xóa (POST/PUT/DELETE) |
| --- | --- | --- |
| `/api/v1/categories` | User đã đăng nhập | ADMIN, MANAGER |
| `/api/v1/brands` | User đã đăng nhập | ADMIN, MANAGER |
| `/api/v1/products` | User đã đăng nhập | ADMIN, MANAGER |
| `/api/v1/products/{id}/detail` | User đã đăng nhập | Không có API ghi |
| `/api/v1/product-variants` | User đã đăng nhập | ADMIN, MANAGER |
| `/api/v1/products/{productId}/images` | User đã đăng nhập | ADMIN, MANAGER |
| `/api/v1/products/{productId}/specifications` | User đã đăng nhập | ADMIN, MANAGER |

Các quy tắc áp dụng cho cả endpoint collection và endpoint theo ID. STAFF và CUSTOMER được đọc nhưng không được quản lý Catalog. API ghi vẫn yêu cầu CSRF theo cấu hình cookie hiện có. Với request hợp lệ về CSRF, chưa đăng nhập trả 401, đăng nhập nhưng thiếu authority trả 403.

API `PUT /api/users/{userId}/role` cũng được giới hạn cho ADMIN để ngăn user tự nâng quyền rồi đăng nhập lại nhằm vượt authorization của Catalog. Migration hiện tại chỉ cấp USER_ROLE_ASSIGN cho ADMIN.

## Permission còn thiếu — đề xuất, chưa triển khai

V1 hiện chỉ khởi tạo permission cho user, role, employee và customer; V2 tạo refresh token. Chưa có permission cho Catalog. Principal hiện cũng chưa nạp permission từ role_permissions, nên chỉ thêm bản ghi permission sẽ chưa có tác dụng authorization.

Nếu cần chuyển Catalog sang phân quyền bằng permission, đề xuất bổ sung các tên sau theo convention VIEW/CREATE/UPDATE của migration hiện tại, thêm DELETE cho API xóa đang có:

| Tài nguyên | Permission đề xuất |
| --- | --- |
| Category | CATEGORY_VIEW, CATEGORY_CREATE, CATEGORY_UPDATE, CATEGORY_DELETE |
| Brand | BRAND_VIEW, BRAND_CREATE, BRAND_UPDATE, BRAND_DELETE |
| Product | PRODUCT_VIEW, PRODUCT_CREATE, PRODUCT_UPDATE, PRODUCT_DELETE |
| ProductVariant | PRODUCT_VARIANT_VIEW, PRODUCT_VARIANT_CREATE, PRODUCT_VARIANT_UPDATE, PRODUCT_VARIANT_DELETE |
| ProductImage | PRODUCT_IMAGE_VIEW, PRODUCT_IMAGE_CREATE, PRODUCT_IMAGE_UPDATE, PRODUCT_IMAGE_DELETE |
| Specification | SPECIFICATION_VIEW, SPECIFICATION_CREATE, SPECIFICATION_UPDATE, SPECIFICATION_DELETE |

Cần thống nhất cách cấp quyền đọc cho CUSTOMER trước khi áp dụng permission cho GET và cách kiểm tra quyền đọc Product Detail chứa nhiều loại dữ liệu. Sau đó mới bổ sung migration dữ liệu permission/role_permissions, nạp permission vào principal và cập nhật authorization. Thay đổi hiện tại không sửa schema/migration hay tạo seed security.
