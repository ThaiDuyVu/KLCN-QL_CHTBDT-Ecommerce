# User, Role và Permission Management

Không đổi migration, không thêm status/permission hoặc seed. Auth dùng cookie/JWT
và refresh rotation hiện có; principal được bổ sung permission từ role_permissions
ở mỗi lần tải từ database, giữ authority role hiện có cho Catalog.

| API | Quyền |
| --- | --- |
| GET /api/users, GET /api/users/{id} | USER_VIEW |
| PUT /api/users/{id} | USER_UPDATE |
| PATCH /api/users/{id}/status | USER_DISABLE (permission hiện có) |
| GET /api/users/{id}/role | USER_ROLE_VIEW |
| PUT /api/users/{id}/role | USER_ROLE_ASSIGN; thêm USER_ROLE_REMOVE nếu thay role hiện có |
| GET /api/roles | USER_VIEW hoặc USER_ROLE_VIEW hoặc ROLE_PERMISSION_VIEW |
| GET /api/permissions, GET /api/roles/{id}/permissions | ROLE_PERMISSION_VIEW |
| PUT /api/roles/{id}/permissions | ROLE_PERMISSION_ASSIGN nếu thêm, ROLE_PERMISSION_REMOVE nếu xóa |

ADMIN được phép truy cập các API trên. Permission của role ADMIN không được sửa,
kể cả ADMIN gọi API. Giữ convention service hiện có: sáu permission quản trị
phân quyền chỉ dành cho ADMIN; client nhận `protectedPermission` để vô hiệu hóa
việc chọn cho role khác. Người không phải ADMIN không được gán/đổi role ADMIN.

GET users nhận `keyword`, `status`, `roleId`, `page=0`, `size=20` (1–100). Keyword
trim và tìm không phân biệt hoa thường trên username/email/displayName, escape
LIKE wildcard. Sort createdAt DESC, userId DESC để phân trang ổn định. Response
giữ cấu trúc page/content cũ, thêm roleId/roleName; batch tải role cho page thay
vì truy vấn từng user. UserRole tiếp tục convention một role/user hiện có.

PUT user nhận email bắt buộc, hợp lệ và tối đa 255; displayName tối đa 255, phone
tối đa 30, trim cả ba. Email trùng trả HTTP 409 theo unique constraint hiện tại
(case-sensitive), bao gồm race khi save. UUID/status/JSON sai hoặc validation
trả 400; không tồn tại trả 404; thiếu quyền trả 403.

PATCH status nhận ví dụ `{ "status": "LOCKED" }`, chỉ chấp nhận ACTIVE, INACTIVE
và LOCKED. Cập nhật updated_at,
khóa hàng user để tránh mất cập nhật khi sửa profile/status/role đồng thời. Auth
đã kiểm tra ACTIVE khi login, refresh và JWT: LOCKED/INACTIVE bị chặn cả token
đang có trên request kế tiếp; unlock/activate về ACTIVE. Đổi role thay binding
thay vì mutate composite primary key. Access token role cũ bị từ chối, refresh
token tạo phiên theo role mới. Frontend đồng bộ metadata khi đổi tài khoản mình.

Checklist kiểm tra local với backend/database thật:
1. Login ADMIN và mở /user-management; thử search/filter/page.
2. Sửa email trùng user khác: 409 và lỗi hiển thị trong form.
3. Lock STAFF: session hiện có và login/refresh bị từ chối; unlock rồi login lại.
4. Đổi STAFF sang MANAGER: danh sách cập nhật roleName/roleId, refresh theo role mới.
5. Sửa permission STAFF/MANAGER; kiểm tra quyền có hiệu lực trên request kế tiếp.
6. Chọn ADMIN: UI read-only, PUT trực tiếp trả 403.

Kiểm tra service/controller dùng mocks không thay thế kiểm tra database thật
cho truy vấn Specification, pessimistic lock hoặc persistence của composite key.
