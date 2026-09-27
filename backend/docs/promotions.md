# Promotion theo Product

## Phạm vi và schema

Dùng nguyên `promotions` và `promotion_products` trong V1. Không có migration mới, voucher/coupon, loyalty, stacking hoặc rule theo customer/category/brand. Mọi Variant thuộc Product được chọn hưởng cùng chương trình hợp lệ, với mức giảm tính riêng theo giá gốc của Variant.

Các enum lưu bằng VARCHAR hiện có:

- DiscountType: PERCENTAGE, FIXED_AMOUNT.
- PromotionStatus: ACTIVE, INACTIVE.

## API quản lý

Tất cả API dưới đây chỉ cho ADMIN/MANAGER. Giữ cookie auth, CSRF và RequireAnyAuthority hiện có. CUSTOMER/STAFF không có quyền CRUD hoặc xem cấu hình Promotion; CUSTOMER đọc giá/summary đã áp dụng qua Product/Cart API.

| Method | Route | Nội dung |
| --- | --- | --- |
| GET | /api/promotions?page=0&size=20&keyword=&status= | Danh sách phân trang, tìm tên và lọc status |
| GET | /api/promotions/{id} | Chi tiết và danh sách Product |
| POST | /api/promotions | Tạo |
| PUT | /api/promotions/{id} | Sửa toàn bộ thông tin và Product áp dụng |
| PATCH | /api/promotions/{id}/status | Bật/tắt; body {"status":"ACTIVE"} hoặc INACTIVE |
| DELETE | /api/promotions/{id} | Xóa liên kết Product rồi xóa Promotion; không sửa Order cũ |

Body create/update:

```json
{
  "promotionName": "Giảm giá thiết bị",
  "description": "Chương trình theo sản phẩm",
  "discountType": "PERCENTAGE",
  "discountValue": 10,
  "startDate": "2026-09-26T00:00:00+07:00",
  "endDate": "2026-09-30T23:59:59+07:00",
  "status": "ACTIVE",
  "productIds": ["<UUID Product thực tế>"]
}
```

Tên được trim, không rỗng, tối đa 255 ký tự. discountValue >= 0, tối đa 13 chữ số nguyên và 2 chữ số thập phân theo NUMERIC(15,2). PERCENTAGE không vượt 100. Ngày kết thúc >= ngày bắt đầu; gửi ISO 8601 có timezone. Product phải tồn tại. productIds có thể rỗng (chưa áp dụng), không được null hoặc chứa null; ID lặp được gộp thành một liên kết.

Lỗi validation: 400. Không tìm thấy Promotion/Product: 404. Overlap hoặc nhiều chương trình hợp lệ do dữ liệu nhập ngoài API: 409. Frontend giữ dữ liệu form và hiển thị lỗi backend.

## Tính giá và overlap

Promotion hợp lệ khi ACTIVE, startDate <= thời điểm đọc/checkout <= endDate và có liên kết Product.

- PERCENTAGE: discountAmount = unitPrice * discountValue / 100, làm tròn HALF_UP đến 2 chữ số thập phân trên từng đơn vị.
- FIXED_AMOUNT: discountAmount = min(discountValue, unitPrice).
- finalUnitPrice = unitPrice - discountAmount, tối thiểu 0.
- Làm tròn theo từng đơn vị trước khi nhân quantity, để snapshot item và tổng đơn luôn khớp.
- Không cộng nhiều Promotion. Nếu dữ liệu hiện có chứa nhiều chương trình hợp lệ cho một Product, trả 409 thay vì tự chọn một chương trình.

Hai chương trình ACTIVE overlap nếu `existing.startDate <= new.endDate && existing.endDate >= new.startDate`. Hai khoảng chạm cùng mốc thời gian cũng bị chặn. Chương trình INACTIVE có thể trùng lịch nhưng phải kiểm tra lại khi bật. Sửa chương trình bỏ qua chính ID của nó khi kiểm tra.

Create/update/status/delete khóa các Product liên quan theo thứ tự UUID cố định, kiểm tra và lưu trong cùng transaction. Update khóa cả Product cũ và mới. Checkout dùng cùng khóa Product trước khi resolve để không đọc dở dang thay đổi Promotion. Không có constraint overlap mới trong DB; các thao tác ghi ngoài service phải tự tuân thủ rule này.

## Contract Product, Cart, Order

ProductResponse bổ sung originalPrice/effectivePrice/discountAmount/promotion. Giá Product là giá khởi điểm của Variant ACTIVE rẻ nhất sau giảm; nếu cùng giá sau giảm, ưu tiên giá gốc nhỏ hơn. Không có Variant ACTIVE thì giá null. ProductVariantResponse giữ price gốc và bổ sung cùng bộ field theo từng Variant. Product listing batch-load Variant/Promotion cho trang hiện tại, không gọi detail cho từng Product.

CartItemResponse giữ unitPrice gốc, bổ sung originalPrice/effectivePrice/discountAmount (mỗi đơn vị)/promotion. lineTotal = effectivePrice * quantity. CartResponse.subtotal là tổng sau giảm; originalSubtotal là tổng giá gốc, discountAmount là tổng giảm theo quantity. Cart không khóa giá; checkout xác nhận lại.

Checkout không nhận giá/discount từ frontend. PromotionService tính giá; OrderService chỉ dùng kết quả để snapshot unitPrice, discountAmount, finalUnitPrice và costPrice:

- Order.subtotal = sum(unitPrice * quantity).
- Order.discountAmount = sum(discountAmount * quantity).
- Order.totalAmount = subtotal - discountAmount + shippingFee.
- shippingFee hiện vẫn 0 theo phase hiện tại.
- Payment.amount lấy totalAmount sau giảm.
- INSTALLMENT validate downPayment và tính remainingAmount theo totalAmount sau giảm.
- VNPAY ký amount = totalAmount * 100.
- Với hàng tracked, từng OrderItem quantity=1 vẫn nhận đúng snapshot giảm giá.

Tắt, sửa, hết hạn hoặc xóa Promotion không làm thay đổi giá Order cũ. Không đổi Order state machine, inventory reserve/release/deliver, Serial hoặc Warranty. VNPAY vẫn yêu cầu số tiền > 0 theo contract gateway hiện có; đơn giảm về 0 có thể checkout COD.

## Frontend

ADMIN/MANAGER có menu Khuyến mãi và các route /promotions, /promotions/new, /promotions/{id}, /promotions/{id}/edit. Có danh sách phân trang/search/status, tạo/sửa, bật/tắt với confirmation, chi tiết và xóa. Chọn Product bằng API phân trang, giữ selection qua các trang. Date input dùng giờ địa phương rồi gửi ISO timezone.

Customer Product list/detail dùng PriceDisplay để hiển thị giá gốc gạch ngang, giá sau giảm và badge. Variant picker/add-to-cart hiển thị giá hiện tại. Cart/Checkout hiển thị tạm tính gốc, giảm giá và tổng sau giảm; Order detail hiển thị snapshot từng item và tổng đã lưu. Bỏ ô coupon placeholder khỏi Cart để tránh nhầm với Promotion theo Product.

## Xác minh

Không viết file test và không chạy test suite trong task này. Xác minh qua API thật trên backend port 8081 với PostgreSQL tạm riêng, dùng migration và dev seed hiện có; không thay dữ liệu demo chính:

- CRUD, search/list, bật/tắt; update giữ cùng Product thành công.
- CUSTOMER/STAFF bị chặn quản lý, ADMIN/MANAGER được phép.
- Create/bật overlap trả 409; hai create đồng thời cùng Product cho kết quả 201/409.
- Khoảng chạm nhau ở mốc đầu/cuối bị chặn; future/expired không áp dụng.
- PERCENTAGE 101 trả 400; mức 100 đưa giá về 0; FIXED_AMOUNT vượt giá được cap ở giá gốc.
- Product/Variant trả giá và summary đúng; Cart subtotal/discount theo quantity.
- COD checkout lưu snapshot và tổng đúng; dữ liệu giá giả gửi từ client không được dùng.
- Snapshot giữ nguyên sau tắt/xóa Promotion; COD đơn giá 0 hoạt động.
- INSTALLMENT và VNPAY checkout dùng tổng sau giảm; remainingAmount/amount minor units đúng; cancel giải phóng reserve.
- Chỉ kiểm tra tạo Payment/URL, không thực hiện giao dịch ngân hàng hoặc IPN thật trong task Promotion.
- Backend package bỏ qua tests, frontend lint/build được kiểm tra. Database/backend tạm được dọn sau xác minh.

Backend chính phải restart để nạp service/DTO/API mới. Frontend dev server sẽ reload source; production cần build lại.

## File thay đổi

- `Fontend/src/App.jsx`
- `Fontend/src/components/layout/AppLayout.jsx`
- `Fontend/src/components/ui/PriceDisplay.jsx`
- `Fontend/src/components/ui/price-display.css`
- `Fontend/src/features/cart/pages/CartPage.jsx`
- `Fontend/src/features/cart/pages/CheckoutPage.jsx`
- `Fontend/src/features/orders/pages/OrderDetailPage.jsx`
- `Fontend/src/features/products/components/ProductCardCartAction.jsx`
- `Fontend/src/features/products/components/StorefrontProductCard.jsx`
- `Fontend/src/features/products/pages/ProductDetailPage.jsx`
- `Fontend/src/features/promotions/api/promotionApi.js`
- `Fontend/src/features/promotions/components/ProductPicker.jsx`
- `Fontend/src/features/promotions/components/PromotionForm.jsx`
- `Fontend/src/features/promotions/pages/PromotionDetailPage.jsx`
- `Fontend/src/features/promotions/pages/PromotionFormPage.jsx`
- `Fontend/src/features/promotions/pages/PromotionsPage.jsx`
- `Fontend/src/features/promotions/promotions.css`
- `backend/docs/promotions.md`
- `backend/src/main/java/com/example/backend/cart/dto/CartItemResponse.java`
- `backend/src/main/java/com/example/backend/cart/dto/CartResponse.java`
- `backend/src/main/java/com/example/backend/cart/service/CartServiceImpl.java`
- `backend/src/main/java/com/example/backend/common/exception/GlobalExceptionHandler.java`
- `backend/src/main/java/com/example/backend/order/service/OrderServiceImpl.java`
- `backend/src/main/java/com/example/backend/product/dto/ProductResponse.java`
- `backend/src/main/java/com/example/backend/product/dto/ProductVariantResponse.java`
- `backend/src/main/java/com/example/backend/product/repository/ProductVariantRepository.java`
- `backend/src/main/java/com/example/backend/product/service/ProductDetailServiceImpl.java`
- `backend/src/main/java/com/example/backend/product/service/ProductServiceImpl.java`
- `backend/src/main/java/com/example/backend/product/service/ProductVariantServiceImpl.java`
- `backend/src/main/java/com/example/backend/promotion/controller/PromotionController.java`
- `backend/src/main/java/com/example/backend/promotion/dto/PromotionPageResponse.java`
- `backend/src/main/java/com/example/backend/promotion/dto/PromotionProductResponse.java`
- `backend/src/main/java/com/example/backend/promotion/dto/PromotionRequest.java`
- `backend/src/main/java/com/example/backend/promotion/dto/PromotionResponse.java`
- `backend/src/main/java/com/example/backend/promotion/dto/PromotionStatusRequest.java`
- `backend/src/main/java/com/example/backend/promotion/dto/PromotionSummaryResponse.java`
- `backend/src/main/java/com/example/backend/promotion/entity/DiscountType.java`
- `backend/src/main/java/com/example/backend/promotion/entity/Promotion.java`
- `backend/src/main/java/com/example/backend/promotion/entity/PromotionProduct.java`
- `backend/src/main/java/com/example/backend/promotion/entity/PromotionProductId.java`
- `backend/src/main/java/com/example/backend/promotion/entity/PromotionStatus.java`
- `backend/src/main/java/com/example/backend/promotion/exception/PromotionException.java`
- `backend/src/main/java/com/example/backend/promotion/repository/PromotionProductRepository.java`
- `backend/src/main/java/com/example/backend/promotion/repository/PromotionRepository.java`
- `backend/src/main/java/com/example/backend/promotion/service/PromotionPrice.java`
- `backend/src/main/java/com/example/backend/promotion/service/PromotionService.java`
- `backend/src/main/java/com/example/backend/promotion/service/PromotionServiceImpl.java`
