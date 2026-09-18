# Review Product Catalog trước frontend

Ngày review: 17/09/2026. Đối chiếu source hiện tại với V1/V2; graph có generation cũ nên đã đọc trực tiếp code/migration liên quan. Không thay schema/migration, không thêm seed module hay file test.

## Catalog và contract hiện tại

| Thành phần | Kết quả đối chiếu |
| --- | --- |
| Category | UUID, parent nullable, tên tối đa 255, description TEXT; kiểm tra parent tồn tại và chu trình. Xóa bị chặn khi có con/Product. Đã flush khi ghi để dịch lỗi parent bị xóa đồng thời thành 404. |
| Brand | Tên tối đa 255; UNIQUE brand_name phân biệt hoa/thường đúng migration. Duplicate trả 409; xóa khi Product tham chiếu trả 409. |
| Product | Category/Brand bắt buộc và phải tồn tại; tên tối đa 255, TEXT và timestamps đúng bảng. List phân trang 0/20, tối đa 100, sort ổn định; keyword/categoryId/brandId/status filter. FK RESTRICT trả 409 khi xóa. |
| ProductVariant | product_id bắt buộc; SKU UNIQUE phân biệt hoa/thường, tối đa 100. price/costPrice NUMERIC(15,2), không âm. Xóa variant đã được dependency tham chiếu trả 409 từ lỗi FK. |
| ProductImage | URL tối đa 1000, HTTP/HTTPS hoặc đường dẫn frontend bắt đầu bằng /. Ghi có khóa Product; bỏ ảnh chính cũ trước khi đặt ảnh chính mới, đúng partial unique index. Cho phép không có ảnh chính. |
| Specification | spec_key/spec_value bắt buộc, tối đa 100/1000. CRUD giữ nguyên nội dung; không tự thêm uniqueness hoặc danh sách key cho phép. |

Category/Brand/Product/Variant dùng enum ACTIVE/INACTIVE lưu dạng VARCHAR qua EnumType.STRING. DTO không trả JPA entity. Product Detail gồm product/category/brand/variants/images/specifications; root dùng EntityGraph và ba query collection riêng để tránh join nhân bản các collection. Chưa đo query count trên database thực.

GET yêu cầu đăng nhập; ghi Catalog chỉ ADMIN/MANAGER qua RequireAnyAuthority. Đổi role user chỉ ADMIN để tránh tự nâng quyền. Chi tiết và permission còn thiếu: [product-catalog-security.md](product-catalog-security.md).

Product không tự cascade delete: ảnh, specification, variant và các tham chiếu khác đều RESTRICT theo migration. Frontend cần xử lý 409; không tự xóa lịch sử để né lỗi. List toàn bộ Product/Brand/Variant phân trang; Category và collection nhỏ theo Product trả list. Error hiện có chủ yếu là chuỗi thông báo, không phải một JSON error DTO thống nhất.

## Dependency

| Dependency | Quan hệ trong migration | Code và giới hạn hiện tại |
| --- | --- | --- |
| Inventory | warehouse_id + variant_id UNIQUE; quantity >= 0, 0 <= reserved_quantity <= quantity; variant FK RESTRICT | Có entity/repository, chưa có controller/service đọc tồn kho cho frontend. |
| Goods Receipt | items.variant_id FK RESTRICT; quantity > 0, unit_cost >= 0 | Có CRUD đọc/tạo và chuyển DRAFT sang CONFIRMED/CANCELLED. Xác nhận khóa phiếu và cộng Inventory trong cùng transaction; không lấy giá vốn lịch sử từ giá Catalog hiện tại. Cần Supplier/Warehouse/Employee tồn tại. |
| Serial | serial_numbers.variant_id và warehouse_id, serial_number UNIQUE | Chưa có backend entity/repository/service/controller cho Serial. Xác nhận Goods Receipt hiện không tạo serial. |
| Cart | cart_items.variant_id; UNIQUE cart + variant; quantity/unit_price | Chưa có backend Cart. Frontend mua hàng phải dùng variantId, không dùng productId thay thế. |
| Order | order_items.variant_id và serial_id nullable; snapshot cost_price/unit_price/discount/final_unit_price | Chưa có backend Order/checkout/reservation. Catalog CRUD không thay snapshot giá lịch sử trong các bảng này. |

Không bổ sung rule tự cascade trạng thái Category/Brand/Product sang Variant, không tự cấm nhập hàng theo status: migration chưa định nghĩa các rule này. Storefront cần thống nhất cách xử lý các trạng thái liên quan; filter status=ACTIVE hiện chỉ lọc Product.

## Seed dev và dependency

Các seed đang hoạt động đều yêu cầu cả profile dev và cờ app.seed.*.enabled=true; mặc định cờ không bật. Runner product_seed_data cũ chỉ chứa code comment, không ghi dữ liệu. Flyway tạo role trước khi các runner chạy. Mỗi seed có transaction riêng; chạy lại bổ sung phần còn thiếu, không ghi đè giá/trạng thái/hồ sơ hiện có.

| Thứ tự | Seed | Trên database mới, đủ dependency |
| --- | --- | --- |
| 5 | Auth | 5 user/role; 3 Employee, 2 Customer |
| 10 | Category | 3 cha, 7 con |
| 20 | Brand | Apple, Samsung, Xiaomi, Dell, ASUS |
| 30 | Product | 6 Product liên kết đúng Category/Brand |
| 40 | ProductVariant | 8 SKU DEV-* liên kết 6 Product |
| 50 | ProductImage | 12 đường dẫn giả, tối đa 1 ảnh chính/Product |
| 60 | Specification | 24 thông số mẫu, 4/Product |

Sửa trong review:

- Category seed không còn bỏ qua toàn bộ khi bảng có dữ liệu; kiểm tra tên + parent cho từng record.
- Product và seed con chọn đúng nhánh category mẫu, tránh nhánh khác trùng tên Điện thoại/Laptop. Thiếu dependency thì cảnh báo và bỏ qua, không tạo FK mồ côi.
- SKU đã tồn tại giữ nguyên; cảnh báo nếu đang thuộc Product khác.
- Specification seed giữ thông số đã được tùy chỉnh theo key; đây chỉ là cách seed, CRUD vẫn cho phép key trùng theo schema.
- Auth seed bổ sung profile còn thiếu chỉ khi email/role khớp account mẫu; không đổi role/mật khẩu/hồ sơ cũ. Xung đột employee_code ngoài user_id sẽ làm rollback seed thay vì ghi đè dữ liệu khác.

Chạy từ thư mục backend, sau khi cấu hình database PostgreSQL có pgvector và JWT như môi trường dev hiện tại:

```sh
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run \
  -Dspring-boot.run.arguments="--app.seed.auth.enabled=true --app.seed.category.enabled=true --app.seed.brand.enabled=true --app.seed.product.enabled=true --app.seed.product-variant.enabled=true --app.seed.product-image.enabled=true --app.seed.specification.enabled=true"
```

Không có seed Supplier/Warehouse/Goods Receipt/Inventory/Serial/Cart/Order. Catalog seed không tạo số lượng tồn giả. Để demo nhập hàng cần tạo Supplier/Warehouse, chọn employeeId từ profile Employee (khác userId), tạo phiếu với variantId rồi xác nhận. Hiện chưa có API chọn Employee/Customer profile cho các luồng này. Đường dẫn ảnh nằm tại frontend `public/images/products/<slug>/main.jpg` và `detail.jpg`; cần bổ sung file ảnh thật.

Seed được thiết kế cho một instance dev chạy tuần tự. Không có UNIQUE tên Category/Product hay tuple Specification trong schema, nên không cam kết chống trùng khi nhiều instance seed đồng thời. Không sửa schema để giải quyết việc đó.

## Blocker và phạm vi frontend có thể bắt đầu

Frontend quản lý Catalog có thể bắt đầu tích hợp các API hiện có sau khi xác nhận startup/seed trên database dev thực. Storefront mua hàng hoàn chỉnh còn bị chặn bởi:

1. Chưa có API tồn kho, Serial, Cart, Order/checkout/reservation; các bảng hiện có không thay thế được nghiệp vụ backend.
2. Chưa có dữ liệu Supplier/Warehouse/tồn kho và API chọn profile Employee/Customer cho luồng liên quan.
3. ProductVariantResponse và variants trong Product Detail vẫn chứa costPrice; GET hiện cho cả CUSTOMER. Cần thống nhất DTO/quyền xem giá vốn trước khi dùng các response này cho khách hàng; review này giữ contract đã xây dựng.
4. Chưa thực thi startup + chạy seed hai lần trên PostgreSQL/pgvector. Docker chưa có, PostgreSQL 18 cài trên máy không có vector.control; chưa có database kiểm chứng độc lập phù hợp toàn bộ baseline V1. Không thay migration hoặc dùng database dev hiện tại để thử ghi dữ liệu.

Permission Catalog chưa có trong migration/principal, nhưng authorization bằng role hiện hoạt động theo convention project; đây là phần cần làm nếu chuyển sang permission, không chặn UI quản lý dùng role hiện tại. GET chưa mở anonymous nên trang Catalog công khai cần một contract riêng được thống nhất.

Validation đã chạy: compile offline thành công; 22 ProductServiceImplTest và 16 GoodsReceiptServiceImplTest hiện có pass; 15 derived query cho repository được kiểm tra bằng Spring Data PartTree, không lỗi property path. Không viết test mới. Build/service tests và kiểm tra metadata không chứng minh FK/lock/partial unique index/seed startup trên database; các điểm đó còn cần kiểm chứng thực tế nêu trên.
