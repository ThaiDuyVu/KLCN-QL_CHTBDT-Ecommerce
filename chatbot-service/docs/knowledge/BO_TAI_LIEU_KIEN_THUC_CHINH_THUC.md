# Bộ tài liệu kiến thức chính thức — Trợ lý AI Điện Việt

> **Mục đích:** nguồn kiến thức (RAG) để Trợ lý AI trả lời khách hàng về chính sách và quy trình của cửa hàng.
> **Nguyên tắc soạn:** mọi quy định dưới đây được **đối chiếu với nghiệp vụ đã cài đặt trong hệ thống**
> (backend, web, app) tại thời điểm 2026-11-04 — chatbot không được nói khác với những gì hệ thống thực sự làm.
> Phiên bản đã duyệt 2026-11-04: dùng 1 chi nhánh mẫu, đổi trả ghi nhận là chưa triển khai, giao hàng dự kiến
> 3–7 ngày, thời hạn bảo hành theo logic nghiệp vụ trong code. Script nạp từ chối tài liệu còn chỗ trống chờ điền.

## Cách nạp vào hệ thống

| Thuộc tính | Giá trị |
|---|---|
| Bảng | `knowledge_documents` (mỗi mục `KT-xx` dưới đây = 1 tài liệu) |
| `title` | Tiêu đề của mục |
| `document_type` | `POLICY` (chính sách), `GUIDE` (hướng dẫn), `FAQ` |
| `source` | `official://dien-viet/<mã>` — ví dụ `official://dien-viet/kt-07-bao-hanh` |
| `status` | `PUBLISHED` khi đã duyệt (chatbot chỉ đọc tài liệu `PUBLISHED`) |
| `content` | Phần **Nội dung** của mục (bỏ tiêu đề markdown) |

Sau khi chèn: chạy `python -m app.features.knowledge.ingest` trong `chatbot-service` để cắt chunk (~1000 ký tự) và tạo vector.
Hai tài liệu `DEMO` hiện có ("Chính sách đổi trả kiểm thử", "Hướng dẫn kiểm tra thanh toán") được **thay thế** bởi KT-03 và KT-10.

**Lưu ý kỹ thuật khi sửa nội dung:** bộ lọc `relevant_chunks` chỉ dùng chunk **có chứa đúng từ khoá chủ đề**
(bảo hành, thanh toán, đổi trả/hoàn trả, khuyến mãi, giao hàng/vận chuyển). Vì vậy mỗi đoạn trong tài liệu đều
nhắc lại từ khoá chủ đề của nó — khi chỉnh sửa, giữ các từ khoá này trong từng đoạn.

## Danh mục

| Mã | Tiêu đề | Loại | Trạng thái nội dung |
|---|---|---|---|
| KT-01 | Giới thiệu cửa hàng Điện Việt và chi nhánh | GUIDE | Đã duyệt (1 chi nhánh mẫu) |
| KT-02 | Hướng dẫn mua hàng trên website Điện Việt | GUIDE | Khớp hệ thống |
| KT-03 | Chính sách thanh toán | POLICY | Khớp hệ thống |
| KT-04 | Chính sách mua trả góp | POLICY | Khớp hệ thống |
| KT-05 | Quy trình xử lý và hủy đơn hàng | POLICY | Khớp hệ thống |
| KT-06 | Chính sách giao hàng | POLICY | Khớp hệ thống, giao dự kiến 3–7 ngày |
| KT-07 | Chính sách bảo hành | POLICY | Theo logic nghiệp vụ trong code |
| KT-08 | Hướng dẫn gửi yêu cầu bảo hành | GUIDE | Khớp hệ thống |
| KT-09 | Chính sách khuyến mãi | POLICY | Khớp hệ thống |
| KT-10 | Chính sách đổi trả và hoàn tiền | POLICY | Ghi nhận hệ thống chưa triển khai |
| KT-11 | Tài khoản, bảo mật và phạm vi của Trợ lý AI | FAQ | Khớp hệ thống |

---

## KT-01 — Giới thiệu cửa hàng Điện Việt và chi nhánh

**Loại:** GUIDE · **Nguồn:** `official://dien-viet/kt-01-cua-hang`

**Nội dung**

Điện Việt là cửa hàng bán lẻ thiết bị điện tử và công nghệ, gồm các nhóm hàng: điện thoại, máy tính bảng,
laptop, máy tính để bàn và phụ kiện (tai nghe, sạc và cáp, pin dự phòng). Các thương hiệu đang kinh doanh:
Apple, Samsung, Xiaomi, Dell và ASUS.

Khách hàng mua hàng trực tuyến qua website và ứng dụng di động Điện Việt. Mỗi đơn hàng được xử lý tại một
chi nhánh do khách chọn; giá bán, tồn kho và giỏ hàng được tính theo chi nhánh đó.

Chi nhánh cửa hàng: chi nhánh đang phục vụ mua sắm là "Kho phát triển" tại Thành phố Hồ Chí Minh. Khách xem
danh sách chi nhánh đang hoạt động trong mục "Chi nhánh mua sắm" trên website và ứng dụng.

Liên hệ cửa hàng: hệ thống hiện chưa công bố số hotline, email hỗ trợ và giờ mở cửa; khách vui lòng theo dõi
thông tin trên website. Hệ thống hiện chưa hỗ trợ yêu cầu xuất hóa đơn VAT trực tuyến.

---

## KT-02 — Hướng dẫn mua hàng trên website Điện Việt

**Loại:** GUIDE · **Nguồn:** `official://dien-viet/kt-02-mua-hang`

**Nội dung**

Hướng dẫn mua hàng tại Điện Việt gồm các bước: đăng ký hoặc đăng nhập tài khoản khách hàng, chọn chi nhánh mua
sắm, chọn sản phẩm và phiên bản (màu sắc, dung lượng), thêm vào giỏ hàng, sau đó tiến hành thanh toán.

Chọn chi nhánh khi mua hàng: khi bắt đầu mua sắm, khách chọn một chi nhánh. Giỏ hàng chỉ chứa sản phẩm của một
chi nhánh. Muốn đổi sang chi nhánh khác khi giỏ đang có sản phẩm, khách phải đồng ý xóa giỏ hàng hiện tại. Số
lượng mua tối đa bằng tồn kho còn lại tại chi nhánh đã chọn; sản phẩm hoặc phiên bản ngừng bán không thể thêm
vào giỏ hàng.

Địa chỉ nhận hàng khi mua hàng: đơn hàng bắt buộc dùng một địa chỉ đã lưu trong sổ địa chỉ của tài khoản (người
nhận, số điện thoại, địa chỉ cụ thể; phường/xã, quận/huyện, tỉnh/thành không bắt buộc). Khách có thể thêm địa
chỉ mới ngay ở bước thanh toán và đặt một địa chỉ làm mặc định. Số điện thoại có dạng 0901234567 hoặc
+84901234567.

Giá khi mua hàng: giá và tồn kho tại chi nhánh được hệ thống xác nhận lại ngay lúc đặt hàng, nên tổng tiền cuối
cùng có thể khác nếu giá hoặc khuyến mãi vừa thay đổi. Sau khi đặt hàng thành công, sản phẩm được giữ cho đơn
và khách theo dõi đơn trong mục "Đơn hàng của tôi".

---

## KT-03 — Chính sách thanh toán

**Loại:** POLICY · **Nguồn:** `official://dien-viet/kt-03-thanh-toan` · *Thay thế tài liệu DEMO "Hướng dẫn kiểm tra thanh toán"*

**Nội dung**

Điện Việt hỗ trợ ba phương thức thanh toán: thanh toán khi nhận hàng (COD), thanh toán trực tuyến qua cổng
VNPAY, và mua trả góp qua đơn vị trả góp liên kết. Khách chọn phương thức thanh toán ở bước đặt hàng.

Thanh toán khi nhận hàng (COD): khách trả tiền trực tiếp khi nhận hàng. Đơn COD được nhân viên xác nhận và xử
lý ngay, không cần thanh toán trước.

Thanh toán qua VNPAY: sau khi đặt hàng, khách được chuyển sang cổng VNPAY để thanh toán. Phiên thanh toán VNPAY
có hiệu lực 15 phút kể từ lúc đặt đơn. Đơn VNPAY chỉ được cửa hàng xác nhận sau khi thanh toán thành công. Nếu
quá 15 phút chưa thanh toán, phiên hết hạn: khách hủy đơn chưa thanh toán rồi đặt lại. Nếu VNPAY báo giao dịch
đang chờ đối soát, đơn tạm thời không thể hủy hoặc thanh toán lại cho đến khi có kết quả. Môi trường VNPAY của
hệ thống hiện là môi trường thử nghiệm (sandbox), không dùng thẻ hoặc tài khoản thật.

Trạng thái thanh toán của đơn gồm: chờ thanh toán, đã thanh toán, thanh toán thất bại. Trạng thái thanh toán
chỉ được xác định từ dữ liệu của hệ thống; việc khách cho biết đã chuyển khoản không làm đơn chuyển sang đã
thanh toán.

An toàn khi thanh toán: Điện Việt và Trợ lý AI không bao giờ yêu cầu mật khẩu, mã OTP hay thông tin thẻ qua
chat. Trợ lý AI không thu tiền và không thực hiện thanh toán thay khách.

---

## KT-04 — Chính sách mua trả góp

**Loại:** POLICY · **Nguồn:** `official://dien-viet/kt-04-tra-gop`

**Nội dung**

Mua trả góp tại Điện Việt là hình thức thanh toán qua đơn vị trả góp liên kết với cửa hàng. Khi chọn thanh toán
trả góp, khách cần chọn đơn vị trả góp đang hoạt động, kỳ hạn trả góp (số tháng, tối thiểu 1 tháng) và số tiền
trả trước (từ 0 đồng đến tối đa bằng tổng tiền đơn hàng). Hệ thống hiển thị số tiền còn lại cần trả góp.

Quy trình duyệt hồ sơ trả góp: sau khi đặt đơn, hồ sơ trả góp được cửa hàng tiếp nhận và xét duyệt với các trạng
thái: chờ xử lý, đang xử lý, đã duyệt, từ chối. Đơn hàng trả góp chỉ được xác nhận sau khi hồ sơ trả góp được
duyệt. Nếu hồ sơ trả góp bị từ chối, khách có thể hủy đơn hoặc liên hệ cửa hàng để chọn phương thức thanh toán
khác.

Khi chưa có đơn vị trả góp nào đang hoạt động, lựa chọn thanh toán trả góp sẽ không gửi được hồ sơ; khách vui
lòng chọn COD hoặc VNPAY, hoặc liên hệ cửa hàng. Lãi suất và điều kiện cụ thể của từng đơn vị trả góp do đơn vị
đó quy định.

---

## KT-05 — Quy trình xử lý và hủy đơn hàng

**Loại:** POLICY · **Nguồn:** `official://dien-viet/kt-05-don-hang`

**Nội dung**

Quy trình xử lý đơn hàng tại Điện Việt đi qua các trạng thái: chờ xác nhận → đã xác nhận → đang chuẩn bị hàng →
đang giao hàng → đã giao hàng. Đơn hàng cũng có thể ở trạng thái đã hủy. Nhân viên bán hàng xác nhận đơn, thủ
kho chuẩn bị hàng, nhân viên giao hàng cập nhật đang giao và đã giao. Khách theo dõi trạng thái đơn hàng trong
mục "Đơn hàng của tôi" hoặc hỏi Trợ lý AI kèm mã đơn hàng (dạng ORD-…).

Hủy đơn hàng: khách tự hủy được đơn hàng khi đơn còn ở trạng thái chờ xác nhận và chưa được thanh toán. Đơn hàng
đã xác nhận, đang chuẩn bị, đang giao hoặc đã giao thì khách không tự hủy được; vui lòng liên hệ cửa hàng. Đơn
hàng đã thanh toán (ví dụ đã thanh toán VNPAY) không hủy trực tiếp trên hệ thống mà cần cửa hàng xử lý hoàn
tiền. Khi đơn hàng bị hủy, số lượng sản phẩm được trả lại tồn kho của chi nhánh.

Giữ hàng cho đơn hàng: khi đặt hàng thành công, sản phẩm được giữ riêng cho đơn tại chi nhánh đã chọn. Với điện
thoại và laptop, mỗi máy giao cho khách gắn với một số serial hoặc IMEI riêng, dùng để tra cứu bảo hành sau này.

---

## KT-06 — Chính sách giao hàng

**Loại:** POLICY · **Nguồn:** `official://dien-viet/kt-06-giao-hang`

**Nội dung**

Giao hàng tại Điện Việt: đơn hàng được giao từ chi nhánh khách đã chọn khi mua đến địa chỉ nhận hàng của đơn.
Phí giao hàng hiện tại là 0 đồng cho mọi đơn hàng (miễn phí vận chuyển).

Trạng thái giao hàng: khi nhân viên giao hàng bắt đầu giao, đơn chuyển sang "đang giao hàng"; khi khách nhận
hàng, đơn chuyển sang "đã giao hàng". Với đơn thanh toán khi nhận hàng (COD), khách thanh toán cho nhân viên
giao hàng lúc nhận.

Ngày giao hàng thành công được dùng làm ngày bắt đầu bảo hành của sản phẩm trong đơn.

Thời gian giao hàng dự kiến từ 3 đến 7 ngày kể từ khi đơn hàng được xác nhận. Đây là thời gian giao hàng tham
khảo; Trợ lý AI báo được trạng thái giao hàng hiện tại của đơn nhưng không báo ngày giao cụ thể.

---

## KT-07 — Chính sách bảo hành

**Loại:** POLICY · **Nguồn:** `official://dien-viet/kt-07-bao-hanh`

**Nội dung**

Chính sách bảo hành Điện Việt áp dụng cho sản phẩm có số serial hoặc IMEI (điện thoại, laptop) được mua tại
cửa hàng. Bảo hành điện tử (gắn với số serial/IMEI) được hệ thống tự kích hoạt cho từng máy khi đơn hàng chuyển
sang trạng thái đã giao hàng; khách không cần đăng ký bảo hành thủ công. Khi máy gặp lỗi, khách gửi yêu cầu bảo
hành để tạo phiếu bảo hành (xem hướng dẫn gửi yêu cầu bảo hành).

Thời hạn bảo hành được quy định riêng cho từng phiên bản sản phẩm (tính theo tháng) và được ghi trên trang chi
tiết sản phẩm. Thời hạn bảo hành bắt đầu từ ngày đơn hàng giao thành công và kết thúc sau đúng số tháng bảo hành
của phiên bản đó. Phiên bản có thời hạn bảo hành 0 tháng là sản phẩm không bảo hành. Sản phẩm không có số serial
hoặc IMEI (ví dụ một số phụ kiện) không được kích hoạt bảo hành điện tử. Ngày bắt đầu và ngày hết hạn bảo hành của
từng máy được hiển thị trong mục "Bảo hành của tôi".

Điều kiện còn hiệu lực bảo hành: bảo hành đang ở trạng thái còn hiệu lực, ngày hiện tại nằm trong thời hạn bảo
hành, và thiết bị vẫn thuộc đơn hàng đã bán hợp lệ. Hết thời hạn, bảo hành chuyển sang hết hạn và không tiếp nhận
yêu cầu bảo hành mới.

Tra cứu bảo hành: khách xem danh sách bảo hành trong mục "Bảo hành của tôi" hoặc tra cứu bằng số serial/IMEI của
máy. Mỗi khách chỉ xem được bảo hành của chính mình.

Từ chối bảo hành: nhân viên kỹ thuật bảo hành kiểm tra thiết bị và có thể từ chối phiếu bảo hành; khi từ chối,
lý do được ghi rõ trong kết quả xử lý của phiếu để khách xem.

---

## KT-08 — Hướng dẫn gửi yêu cầu bảo hành

**Loại:** GUIDE · **Nguồn:** `official://dien-viet/kt-08-yeu-cau-bao-hanh`

**Nội dung**

Gửi yêu cầu bảo hành: khách vào "Bảo hành của tôi", chọn thiết bị còn hiệu lực bảo hành, mô tả lỗi và bấm gửi
yêu cầu bảo hành. Hệ thống tạo một phiếu bảo hành có mã dạng WT-… để theo dõi. Trợ lý AI không tạo phiếu bảo
hành thay khách.

Mỗi thiết bị chỉ có một phiếu bảo hành đang xử lý tại một thời điểm. Khi thiết bị đang có phiếu bảo hành ở trạng
thái đã tiếp nhận hoặc đang xử lý, khách chưa thể gửi yêu cầu bảo hành mới; sau khi phiếu đó hoàn thành hoặc bị
từ chối, khách có thể gửi yêu cầu bảo hành mới nếu thiết bị vẫn còn thời hạn bảo hành.

Trạng thái phiếu bảo hành: đã tiếp nhận → đang xử lý → hoàn thành; hoặc đã tiếp nhận → từ chối. Nhân viên kỹ thuật
bảo hành phụ trách phiếu và bắt buộc ghi kết quả xử lý khi hoàn thành hoặc từ chối. Khách xem kết quả bảo hành
trong mục "Phiếu bảo hành của tôi" hoặc hỏi Trợ lý AI về phiếu bảo hành của mình.

---

## KT-09 — Chính sách khuyến mãi

**Loại:** POLICY · **Nguồn:** `official://dien-viet/kt-09-khuyen-mai`

**Nội dung**

Chương trình khuyến mãi tại Điện Việt được áp dụng theo sản phẩm: một chương trình khuyến mãi gắn với một hoặc
nhiều sản phẩm, và áp dụng cho mọi phiên bản (màu sắc, dung lượng) của sản phẩm đó.

Hình thức khuyến mãi gồm giảm theo phần trăm (từ 0% đến 100%) hoặc giảm một số tiền cố định. Mức giảm không vượt
quá giá bán của sản phẩm. Mỗi sản phẩm chỉ có tối đa một chương trình khuyến mãi hiệu lực tại một thời điểm; các
khuyến mãi không cộng dồn.

Thời gian khuyến mãi: chương trình khuyến mãi chỉ áp dụng khi đang bật và trong khoảng thời gian bắt đầu – kết
thúc của chương trình. Giá khuyến mãi được hệ thống tính lại lúc đặt hàng; nếu chương trình khuyến mãi vừa kết
thúc, đơn hàng dùng giá thường. Sản phẩm đang khuyến mãi được hiển thị giá gốc, giá sau giảm và có thể lọc trong
mục "Ưu đãi" trên website.

---

## KT-10 — Chính sách đổi trả và hoàn tiền

**Loại:** POLICY · **Nguồn:** `official://dien-viet/kt-10-doi-tra` · *Thay thế tài liệu DEMO "Chính sách đổi trả kiểm thử"*

**Nội dung**

Chính sách đổi trả và hoàn tiền: hệ thống Điện Việt hiện chưa triển khai chức năng đổi trả và hoàn tiền. Website
và ứng dụng chưa hỗ trợ gửi yêu cầu đổi trả hoặc yêu cầu hoàn tiền trực tuyến, và cửa hàng chưa công bố điều kiện
đổi trả chính thức. Trợ lý AI không tạo yêu cầu đổi trả hay hoàn tiền thay khách.

Hoàn tiền đơn hàng đã thanh toán: đơn hàng đã thanh toán (ví dụ thanh toán VNPAY) không hủy trực tiếp được trên hệ
thống vì chưa có quy trình hoàn tiền. Khách cần liên hệ cửa hàng để được hỗ trợ đổi trả hoặc hoàn tiền.

Sản phẩm gặp lỗi kỹ thuật trong thời hạn bảo hành được xử lý theo chính sách bảo hành: khách gửi yêu cầu bảo hành
trong mục "Bảo hành của tôi" thay cho đổi trả.

---

## KT-11 — Tài khoản, bảo mật và phạm vi của Trợ lý AI

**Loại:** FAQ · **Nguồn:** `official://dien-viet/kt-11-tai-khoan-tro-ly`

**Nội dung**

Tài khoản khách hàng: khách tự đăng ký tài khoản trên website hoặc ứng dụng để mua hàng, lưu sổ địa chỉ, theo
dõi đơn hàng, bảo hành và phiếu bảo hành. Thông tin đơn hàng, thanh toán và bảo hành của mỗi tài khoản chỉ chủ
tài khoản xem được.

Bảo mật: Điện Việt không bao giờ hỏi mật khẩu, mã OTP, cookie hoặc thông tin thẻ qua chat, email hay điện thoại.
Trợ lý AI không cung cấp thông tin của khách hàng khác.

Trợ lý AI Điện Việt hỗ trợ: tư vấn và so sánh sản phẩm theo nhu cầu, ngân sách; tra cứu giỏ hàng, đơn hàng, trạng
thái thanh toán, bảo hành và phiếu bảo hành của chính khách đang đăng nhập; giải đáp chính sách thanh toán, trả
góp, giao hàng, bảo hành, khuyến mãi, đổi trả theo tài liệu chính thức của cửa hàng. Giá và tồn kho trong câu
trả lời lấy từ hệ thống tại thời điểm hỏi.

Trợ lý AI không thực hiện giao dịch: không đặt hàng, không thanh toán, không hủy hoặc sửa đơn, không thêm/xóa giỏ
hàng và không tạo phiếu bảo hành thay khách. Các thao tác này khách thực hiện trên trang chức năng của website
hoặc ứng dụng.

---

## Phụ lục A — Đối chiếu nguồn trong hệ thống

| Quy định | Nguồn trong mã nguồn / dữ liệu |
|---|---|
| 3 phương thức COD / VNPAY / INSTALLMENT | `order/entity/PaymentMethod.java` |
| VNPAY hết hạn 15 phút; chờ đối soát không hủy/không mở lại | `order/payment/service/VnpayServiceImpl.java`, `OrderServiceImpl` (allowedStatuses) |
| Đơn VNPAY/trả góp chỉ xác nhận khi đã thanh toán/đã duyệt | `OrderServiceImpl` — lọc `CONFIRMED` |
| Trả góp: kỳ hạn > 0, trả trước 0…tổng tiền, đơn vị đang hoạt động | `order/service/InstallmentService.java` |
| Khách hủy khi `PENDING` và chưa thanh toán; đã `PAID` cần hoàn tiền | `order/service/OrderTransitions.java` |
| Trạng thái đơn và vai trò cập nhật | `OrderStatus.java`, `OrderStaffPolicy.java` |
| Hủy đơn trả hàng về tồn kho | `OrderServiceImpl` — `stock.apply(... RELEASE)` |
| Phí giao hàng 0 đ | `OrderServiceImpl` — `shippingFee = 0` |
| Giao hàng dự kiến 3–7 ngày | Quy định cửa hàng (chưa lưu trong hệ thống) |
| Chưa có đổi trả / hoàn tiền | `OrderTransitions.require` — "Payment đã PAID; cần refund flow" |
| Bảo hành tạo khi đơn `DELIVERED`, bắt đầu từ ngày giao, theo `warranty_months` | `warranty/service/WarrantyProvisioningServiceImpl.java` |
| Thời hạn theo phiên bản; 0 tháng = không bảo hành; chỉ máy có serial/IMEI | `product_variants.warranty_months` (CHECK ≥ 0), `WarrantyProvisioningServiceImpl` |
| Một phiếu bảo hành đang mở / thiết bị | `WarrantyServiceImpl.createTicket` |
| Phiếu: RECEIVED → IN_PROGRESS → COMPLETED; RECEIVED → REJECTED; bắt buộc ghi chú | `WarrantyServiceImpl.requireTransition`, `updateTicket` |
| Khuyến mãi theo sản phẩm, % (0–100) hoặc số tiền, không vượt giá, 1 KM/sản phẩm | `promotion/service/PromotionServiceImpl.java` |
| Giỏ theo chi nhánh; đổi chi nhánh phải xóa giỏ; giới hạn tồn kho | `cart/service/CartServiceImpl.java` |
| Đặt hàng bắt buộc địa chỉ đã lưu; định dạng SĐT | `order/dto/CheckoutRequest.java`, `customer/address/dto/CustomerAddressRequest.java` |
| Trợ lý AI chỉ đọc, từ chối giao dịch, không lộ mật khẩu/token | `chatbot-service/app/features/chat/policy.py`, `actions.py` |

## Phụ lục B — Bộ câu hỏi kiểm thử sau khi nạp

| Câu hỏi | Tài liệu mong đợi |
|---|---|
| Thanh toán VNPAY trong bao lâu thì hết hạn? | KT-03 |
| Mua trả góp cần trả trước bao nhiêu? | KT-04 |
| Đơn đã thanh toán rồi có hủy được không? | KT-05 |
| Phí giao hàng bao nhiêu, giao mất mấy ngày? | KT-06 |
| Bảo hành tính từ khi nào, xem thời hạn ở đâu? | KT-07 |
| Tôi gửi thêm yêu cầu bảo hành khi phiếu cũ chưa xong được không? | KT-08 |
| Khuyến mãi có được cộng dồn không? | KT-09 |
| Chính sách đổi trả như thế nào? | KT-10 |
| Chatbot có đặt hàng giúp tôi được không? | KT-11 (và bộ chặn giao dịch) |
