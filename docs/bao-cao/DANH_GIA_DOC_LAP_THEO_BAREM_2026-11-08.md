# Đối chiếu độc lập với barem đề cương — 08/11/2026

## 1. Kết luận và phạm vi

**Điểm tạm đánh giá: 6,75 / 9,00 điểm có thể xem xét từ project; còn 1,00 điểm chưa chấm (thái độ 0,50 và thuyết trình/slide 0,50). Nếu nộp đúng bộ source và tài liệu đang có, khoảng điểm tổng tương ứng là 6,75–7,75 / 10, tùy hai mục chưa đánh giá.** Không coi phần chưa đánh giá là thiếu hoặc mặc định được trọn điểm.

Riêng nhóm sản phẩm thực thi — chức năng cơ bản, chức năng bài toán, kiểm thử/triển khai — đạt **4,00 / 4,75**, tương đương khoảng **8,42/10 trong phạm vi riêng đó**, không phải điểm khóa luận. Khoảng cách giữa phần mềm và hồ sơ trình bày đang kéo điểm toàn bài xuống.

Điểm là nhận định bảo thủ để chuẩn bị review, không phải kết quả chấm chính thức. Các điểm dưới 1/4 điểm chỉ giúp giải thích phần bị trừ, không khẳng định hội đồng sẽ chấm tới hai chữ số thập phân. Nội dung và hình thức báo cáo là nhận định sơ bộ trên DOCX/text trong repository; nếu có bản nộp mới hơn ở nơi khác thì phải chấm lại hai mục này.

**Nguồn:** `/Users/vu/Downloads/KLCN057_XayDungHeThongCuaHangMuaBanThietBiDienTu.pdf`, 6 trang. Tên file là KLCN057 nhưng mã in trong tài liệu là **CNTT-KLCN054**. Đã trích text và xem ảnh trang 3–4 để xác định đúng trọng số. Không dùng trọng số tự đặt của `DANH_GIA_THEO_DE_CUONG.md` cũ.

**Mốc code:** branch `dev/vu`, HEAD `f298294` + working tree chưa commit ngày kiểm tra. Bao gồm các thay đổi local, migrations V15–V17, mobile chatbot và tài liệu tri thức; không chỉ chấm commit HEAD. Không sửa logic ứng dụng, không sửa/xóa báo cáo cũ, không commit/push. Không gọi Gemini hay chạy lại benchmark tốn quota. Các kiểm thử tích hợp dùng PostgreSQL riêng qua Testcontainers, không tạo đơn/đổi trả/restore trên database demo.

Đồ thị codebase-memory có chỉ mục ngày 20/10, nhiều file mới chưa được index; kết luận dựa trên đọc source hiện tại và test mới, không dùng kết quả đồ thị thiếu file để kết luận chức năng không tồn tại.

## 2. Barem gốc và cách tính

| Mục trong PDF trang 3–4 | Tối đa | Tạm chấm | Căn cứ chính |
|---|---:|---:|---|
| 1. Lý do, mục tiêu; khảo sát nghiệp vụ và biểu mẫu | 0,75 | 0,40 | Có nội dung mục tiêu/quy trình; báo cáo còn yêu cầu bổ sung số liệu, chứng từ khảo sát thực tế |
| 2. Kế hoạch, phân công | 0,25 | 0,10 | Có tài liệu kế hoạch/chỉnh sửa; chưa đủ bằng chứng kế hoạch 12 tuần và phân công thành viên đã hoàn thành để cho trọn điểm |
| 3. Use case nghiệp vụ, hệ thống và lớp phân tích | 0,75 | 0,50 | Có sơ đồ, đặc tả; phạm vi đổi trả trong báo cáo bị loại dù đề cương yêu cầu và code đã có |
| 4a. Lớp thiết kế, mô hình dữ liệu | 0,50 | 0,40 | Có entity/schema/migrations và sơ đồ; các bản phân tích/Word còn khác source mới |
| 4b. Thiết kế giao diện web và mobile | 0,50 | 0,35 | Có hai client; phần tài liệu còn chỗ cần bổ sung ảnh, chưa kiểm chứng toàn bộ UI mới bằng mắt trong lần này |
| 5. API và phân tích phân bổ chức năng trên nền tảng | 0,50 | 0,45 | Có API thực, hai client dùng chung; ma trận mô tả trong Word chưa cập nhật hoàn toàn |
| 6a. Đăng nhập/đăng xuất, phân quyền, backup/restore | 0,50 | 0,45 | Có thực thi và integration tests; STAFF tổng quát còn quyền rộng hơn các chức danh chuyên biệt |
| 6b. Chức năng theo bài toán | 3,50 | 2,95 | Chi tiết ở mục 3; lỗi lặp đổi trả sau EXCHANGED được tái hiện |
| 6c. Kiểm thử và triển khai | 0,75 | 0,60 | Có kiểm thử mới nhiều tầng; chưa phải nghiệm thu browser/mobile/payment gateway toàn luồng; có test tích hợp bị skip và benchmark cloud chưa hoàn tất |
| 7. Nội dung kiến thức trong báo cáo | 0,50 | 0,25 | Có kiến thức và giải thích; còn mô tả trái với chức năng/source hiện tại, ghi chú biên tập và thiếu kết quả nghiệm thu cập nhật |
| 8. Hình thức/định dạng báo cáo | 0,50 | 0,30 | Có DOCX, hình và cấu trúc chương; còn placeholder và nhiều bản chưa chốt. Chưa kiểm duyệt từng trang theo mẫu nộp chính thức |
| **Tổng phần xem xét được** | **9,00** | **6,75** | |
| 9. Thái độ/tác phong | 0,50 | Chưa chấm | Không suy ra từ số commit hay việc dùng AI |
| 10. Phong cách báo cáo, slide | 0,50 | Chưa chấm | Không có buổi trình bày/slide được cung cấp để đánh giá |
| **Barem cơ bản** | **10,00** | **6,75 + hai mục chưa chấm** | |

Mục NCKH khuyến khích không cộng: không có bằng chứng để xét và người dùng yêu cầu không thưởng phần ngoài phạm vi. Không tạo riêng một mục CSDL 2 điểm như báo cáo cũ: dữ liệu được xét ở mục thiết kế, chức năng và test theo barem gốc.

## 3. Chấm chức năng 3,50 điểm

| Hạng mục đúng tên barem | Tối đa | Tạm chấm | Thực trạng và lý do |
|---|---:|---:|---|
| Loại sản phẩm, sản phẩm | 0,25 | 0,25 | Có danh mục, sản phẩm, biến thể, ảnh, tìm kiếm; backend persistence tests mới chạy đạt |
| Khách hàng | 0,25 | 0,25 | Có quản lý hồ sơ, khách vãng lai, địa chỉ và dữ liệu khách trên hai client |
| Khuyến mãi | 0,25 | 0,25 | Có chương trình, thời hạn, gắn sản phẩm và áp giá. Không đòi voucher hay điểm tích lũy vì đề cương không bắt buộc |
| Đặt hàng, hủy đặt | 0,25 | 0,25 | Có checkout, ownership, hủy và quy tắc trạng thái/giữ tồn; không chỉ có nút giao diện |
| Mua hàng, thanh toán, hóa đơn | 0,50 | 0,40 | Có POS, COD/VNPAY, payment, hóa đơn và in. Chưa nghiệm thu lại gateway VNPAY thật trong lần chấm; xác nhận REFUNDED còn là ghi nhận thủ công, không chứng minh chuyển tiền thực tế |
| Trạng thái đơn, giao hàng, đổi trả | 0,50 | **0,20** | Có luồng và dữ liệu thật, nhưng tái hiện API nhận lại yêu cầu trả trên lượng hàng đã EXCHANGED. Trừ 0,30 vì sai invariant nghiệp vụ, không coi là chỉ thiếu giao diện |
| Lịch sử mua hàng | 0,25 | 0,25 | Có danh sách/chi tiết đơn của khách, lọc ownership và trạng thái |
| Bảo hành | 0,25 | 0,20 | Có kích hoạt, tra cứu, lập/nhận/xử lý phiếu. `createForDeliveredOrder` bỏ qua dòng không có serial; nếu cửa hàng bán hàng có bảo hành nhưng không tracking serial thì chưa bao phủ |
| Chatbot tư vấn sản phẩm | 0,50 | 0,40 | Có pipeline thật, tư vấn theo backend, mobile chat và unit tests. Có evidence runtime cũ, nhưng chưa chứng minh mọi lỗi tư vấn đã hết trên source hiện tại; benchmark Gemini còn incomplete |
| Kết xuất biểu mẫu | 0,25 | 0,25 | Có in hóa đơn, phiếu nhập/giao/đổi trả/bảo hành; không chỉ export thống kê |
| Thống kê báo cáo | 0,25 | 0,25 | Có doanh thu, sản phẩm và loại sản phẩm; ReportCategoryIntegrationTest kiểm tra roll-up danh mục và phạm vi kho |
| **Tổng** | **3,50** | **2,95** | |

Các phần chưa nghiệm thu live bị giới hạn mức tin cậy/điểm nhận định; không gán thành lỗi runtime đã xác nhận. Không trừ thêm vì thiếu VAT, thanh toán thật thay Sandbox, mô hình khuyến mãi nâng cao, phí ship động hoặc VPS nếu đề cương không ghi điều kiện đó.

## 4. Lỗi đã tái hiện: yêu cầu đổi/trả lặp sau khi đổi thành công

**Mức độ: HIGH — thuộc đúng yêu cầu đổi trả trong đề cương.**

- Vị trí: `backend/src/main/java/com/example/backend/returns/service/ReturnServiceImpl.java`, danh sách `ACTIVE` và lời gọi `quantities(order.getOrderId(), ACTIVE)` trong `create`.
- `ACTIVE` gồm REQUESTED, APPROVED, RECEIVED, REFUNDED nhưng **thiếu EXCHANGED**.
- Sau khi một yêu cầu đổi hàng hoàn tất, lượng hàng gốc đã đổi không được trừ khỏi số lượng còn có thể yêu cầu lại.
- Giao diện web tính mọi yêu cầu không REJECTED nên che sản phẩm đã dùng hết; **gọi API trực tiếp vẫn vượt qua**. Vì vậy kiểm tra UI không đủ để phát hiện lỗi.

### Tái hiện bằng PostgreSQL cô lập

Dùng lại fixture và chuỗi thao tác của `ReturnIntegrationTest.customerExchangesTwoCasesForAHeadsetAndPaysTheDifferenceOnDelivery()`:

1. Đơn đã giao có 2 phụ kiện gốc.
2. Yêu cầu đổi cả 2 sang sản phẩm khác.
3. Bán hàng duyệt, kho nhận, hoàn tất EXCHANGED, giao đơn thay thế.
4. Gửi lại yêu cầu trả chính 2 phụ kiện gốc của đơn ban đầu.
5. Expected: **409**, đã dùng hết quyền đổi/trả số lượng gốc.
6. Actual: **201**, tạo được yêu cầu mới.

Diagnostic regression: **1 test FAIL, 0 errors**, assertion `Status expected:<409> but was:<201>`. Đây là lỗi được phát hiện ngoài 345 tests sẵn có, không mâu thuẫn với việc suite cũ vẫn xanh.

Artifact: `.local/outline-audit/ReturnOutlineAuditTest.java`, `return-reclaim-reproduction.log`, `return-reclaim-command.json`. Test source tạm được gỡ khỏi `backend/src/test` sau khi thực thi; bản tái hiện giữ riêng trong `.local`. Không sửa production code hoặc dữ liệu demo. Chưa thực thi lần nhận/hoàn tiền thứ hai: thiệt hại tiền thực là **rủi ro**, không báo đã có chuyển tiền hai lần.

**Khuyến nghị, chưa thực hiện:** đưa EXCHANGED vào nhóm trạng thái đã chiếm/tiêu thụ quyền đổi trả; thống nhất backend/mobile/web; thêm regression cho hàng không serial lẫn hàng serial, trả/đổi từng phần và yêu cầu lặp sau trạng thái cuối.

## 5. Hồ sơ chưa đồng bộ — phần bị trừ đáng kể

Đọc trực tiếp hai DOCX trong repo, không chỉ dựa vào các Markdown nhận xét:

- `BaoCaoKhoaLuan_ChinhSua.docx`: trích được 2.872 đoạn, 17 media items.
- `BaoCaoKhoaLuan_ChinhSua_TuBanGoc.docx`: 3.019 đoạn, 138 media items.
- Cả hai bản còn **14 vị trí `[CẦN BỔ SUNG`**; lần lượt **9 và 10 `[GHI CHÚ`**. Một số gợi ý là tùy chọn, không coi toàn bộ 14 chỗ là thiếu bắt buộc; các phần khảo sát, ảnh chức năng và kết quả kiểm thử vẫn cần chốt.
- Còn câu nêu đổi trả không thuộc phạm vi, có chỗ nói ReturnRequest/ReturnItem chỉ là thiết kế sẵn; trái PDF trang 2 và source module returns hiện tại.
- Còn câu nói mobile chưa gọi chatbot; trái `flutter/lib/features/chatbot/data/chat_repository.dart` và các kiểm thử mobile chat hiện tại.
- Phần khảo sát còn yêu cầu bổ sung số liệu/biểu mẫu thực tế; chưa đủ chứng cứ để trao trọn 0,50 cho khảo sát + thu thập biểu mẫu. Không kết luận nhóm không khảo sát nếu chứng từ thực tế nằm ngoài repository.
- Có lớp phân tích vẫn đặt thời hạn bảo hành ở sản phẩm, trong khi code ở biến thể; mô hình tổng quát chưa bao phủ nhóm return/exchange mới. Có nhiều bộ sơ đồ và DOCX chưa xác định bản nộp cuối.

Các sơ đồ PlantUML và SQL tổng hợp V1–V17 mới có trong working tree là bằng chứng tích cực; chúng chưa tự động làm Word cũ trở thành đồng bộ. Việc chấm hồ sơ ở đây là giả định **dùng tài liệu hiện có để nộp**, không phải khẳng định đây là bản nộp được nhóm chọn.

## 6. Ma trận mobile — không dùng chức năng web để thay điểm mobile

PDF trang 2 yêu cầu app khách hàng có tìm/xem chi tiết, đặt/thanh toán/trạng thái, khuyến mãi, đổi trả, phản hồi/đánh giá, thông tin khách và lịch sử.

| Yêu cầu | Bằng chứng source | Mức xác nhận |
|---|---|---|
| Tìm/xem sản phẩm, khuyến mãi | `flutter/lib/features/products/`, `product_catalog_test.dart` | Có implementation và test; chưa review toàn bộ màn hình live hôm nay |
| Đặt/thanh toán/trạng thái | `features/checkout/`, `features/orders/`, `order_mutations_test.dart`, `payment_methods_test.dart` | Test widget/repository đạt; live backend checkout test chưa bật |
| Đổi/trả | `features/returns/data/return_repository.dart`, `presentation/return_request_screen.dart` | Có RETURN/EXCHANGE, chọn sản phẩm thay thế, gọi API thật; lỗi backend EXCHANGED ảnh hưởng app |
| Phản hồi/đánh giá | `features/reviews/data/review_repository.dart`, `presentation/review_widgets.dart` | Có gửi sao/nội dung và xem đánh giá; ReviewIntegrationTest xác nhận chỉ người mua đơn đã giao được đánh giá |
| Hồ sơ/lịch sử | `features/profile/`, `features/orders/` | Có source và test |
| Chatbot | `features/chatbot/`, `chatbot_flow_test.dart` | Có gửi API, lịch sử, cards và tests; đề cương không dành thêm điểm riêng cho số provider |

Không yêu cầu mobile có đầy đủ màn hình quản trị nhân viên vì PDF mô tả các chức năng mobile cho khách hàng.

## 7. Kiểm thử thực thi mới trong lần chấm này

| Lệnh | Kết quả | Giới hạn |
|---|---|---|
| Backend: `./mvnw -B -Dapi.version=1.44 test` (JAVA_HOME từ scripts/env.sh, Docker env cho Testcontainers) | **345 tests, 0 failures, 0 errors, 0 skipped; exit 0** | Các PostgreSQL integration tests dùng DB/container cô lập. Không phải VNPAY ngân hàng live |
| Web: `npm test` | **209 tests / 25 files PASS** | Component/API mock tùy test; không coi là browser E2E |
| Web: `npm run build` | **PASS** | Có warning chunk lớn; không xem warning này là chức năng thiếu |
| Python: `.venv/bin/python -m pytest tests -q` | **661 PASS, 13 skipped; exit 0** | Các tests cần DSN riêng chưa cấu hình; không dùng mocked LLM như bằng chứng chất lượng model thật |
| Flutter: `flutter test --no-pub` lần đầu | **BLOCKED_ENVIRONMENT, exit 1** | Native assets cache báo Invalid SDK hash, chưa phải assertion của app |
| Flutter: chuyển cache generated hooks ra `.local`, `flutter pub get`, chạy lại test | **52 PASS, 1 skipped; exit 0** | Test backend checkout bị tắt khi chưa bật RUN_BACKEND_CHECKOUT_TEST; không xóa cache/source người dùng |
| Diagnostic `ReturnOutlineAuditTest#alreadyExchangedOriginalUnitsMustNotBeClaimedAgain` | **1 FAIL, 0 errors** | Regression cố ý kỳ vọng đúng nghiệp vụ; actual 201 thay vì 409, không sửa để ép PASS |

Logs mới: `.local/outline-audit/backend-tests.log`, `backend-full-suite-summary.json`, `frontend-tests.log`, `frontend-build.log`, `python-tests.log`, `flutter-tests.log`, `flutter-tests-refreshed.log`, `return-reclaim-reproduction.log`. Raw logs giữ local, không đưa credentials/dump cá nhân vào báo cáo.

### Bằng chứng cũ, không tính thành chạy mới

- Local benchmark `debug/benchmark/runs/20261027T110447Z/REPORT.md`: 255/284 lượt chấm đạt, điểm benchmark 91,9/100. Đây là thang riêng, code/ngày khác; **không cộng 9,19 vào barem** và không chứng minh source hôm nay đã hết lỗi.
- Gemini benchmark `debug/benchmark/runs/20261103T121502Z-gemini/REPORT.md`: 38/290 lượt, 37 PASS và 1 BLOCKED, INCOMPLETE. Không báo toàn bộ benchmark cloud PASS.
- Việc khởi động web/mobile ở các lượt trước và cấu hình Cloudflare là bằng chứng từng vận hành; hôm nay không kiểm tra lại toàn bộ mạng demo hoặc mua/hoàn tiền ở gateway.

## 8. Những phần không được cộng và không được tự đặt điều kiện để trừ

Không thưởng điểm riêng cho Gemini song song Ollama, combo nâng cao, trace viewer, Cloudflare Tunnel, VPS, nhiều chi nhánh, tối ưu ảnh hay số lượng bảng. Chỉ dùng chúng làm bằng chứng hỗ trợ một yêu cầu gốc nếu phù hợp.

Không trừ vì thiếu chương trình điểm thành viên, voucher phức tạp, hóa đơn VAT điện tử, GPU, chatbot tự giao dịch hoặc production SLA: PDF không bắt buộc các điều kiện này. Phân quyền, tính nhất quán đổi trả và khả năng khôi phục dữ liệu vẫn thuộc chất lượng nghiệp vụ đã cam kết nên phải xem xét.

PDF mục 7 nêu C#/Flutter/PHP/ASP.NET và SQL Server/MySQL,…; source dùng Java Spring Boot, React, Python, PostgreSQL cùng Flutter. Cần xác nhận với GVHD việc đổi stack đã được chấp thuận. Không có rubric riêng cho độ lệch này nên không tự trừ một mức tùy tiện hoặc tự kết luận vi phạm/được miễn.

## 9. Ưu tiên trước khi nghiệm thu

1. **P0 — Chặn yêu cầu đổi/trả lặp sau EXCHANGED.** Dùng regression thất bại ở mục 4 làm điều kiện đóng lỗi, kiểm tra cả API trực tiếp.
2. **P1 — Chốt báo cáo theo PDF thật.** Đưa đổi trả và mobile chat vào đúng phạm vi/use case/design; cập nhật các số liệu test; không nộp placeholder hoặc chỉ ghi chú cần sửa.
3. **P1 — Bổ sung bằng chứng khảo sát và phân công.** Biểu mẫu/biên bản/ngày khảo sát, tiến độ và trách nhiệm thành viên; không tự tạo số liệu khảo sát.
4. **P1 — Nghiệm thu theo chuỗi hai nền tảng.** Mua → thanh toán Sandbox/COD → giao → hóa đơn → đánh giá → đổi/trả → kiểm tra tồn/báo cáo; backup/restore trên DB thử riêng. Ghi rõ bước nào manual và bước nào có gateway confirmation.
5. **P2 — Làm rõ giới hạn bảo hành và quyền STAFF.** Không hiển thị quyền lợi bảo hành mà hệ thống không cấp được cho hàng không serial; xác định quyền chức danh theo nghiệp vụ đã duyệt. Không tự mở rộng scope nếu cửa hàng chỉ quản lý bảo hành hàng serialized.

Đây là đánh giá và đề xuất, **chưa triển khai bất kỳ fix nghiệp vụ nào**. Nếu sửa xong P0 và cập nhật đúng hồ sơ, phần điểm mất có thể được xem xét lại; không cam kết mức tăng trước khi kiểm chứng.
