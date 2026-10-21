# Cửa sổ chọn chi nhánh bắt buộc cho storefront

> [C2C] TASK_ID: c2c_storefront_mandatory_branch_selection · ITERATION: 1. Báo cáo do coding agent viết, chưa được review độc lập. Không commit, không push.

## 1. Source baseline

Workspace `QL_CHTBDT_Ecommerce` (`KLCN-QL_CHTBDT-Ecommerce-main`), branch `dev/thai-duy-vu`, HEAD `df64381`.

Thay đổi chưa commit có sẵn trước nhiệm vụ này, không thuộc phạm vi và không bị chỉnh lại:
- Giao diện chatbot khách hàng: `src/App.jsx`, 2 dòng nav trong `AppLayout.jsx`, các file `src/features/chatbot/Customer*`, `Fontend/docs/CUSTOMER_*`.
- Dropdown danh mục nhập kho: `src/features/inventory/**`.
- Hotfix/evidence Phase 2 của chatbot-service.

## 2. File thay đổi

| File | Nội dung |
|---|---|
| `src/features/warehouses/api/warehouseApi.js` | `listAll()`: tải mọi trang `/warehouses`, từ chối danh sách lệch; giữ `list()` |
| `src/features/warehouses/WarehouseProvider.jsx` | Danh sách theo chủ sở hữu (user), thử lại, xác nhận phiên theo user, `selectWarehouse(id, { confirmClear })`, chống race bằng epoch, các field context mới |
| `src/features/warehouses/components/BranchSelectionModal.jsx` + `.css` | Cửa sổ bắt buộc |
| `src/components/layout/AppLayout.jsx` | Gate cho CUSTOMER trong storefront; header "Chi nhánh mua sắm" + địa chỉ; focus `#main-content` khi gate mở |
| `src/features/warehouses/BranchSelectionGate.test.jsx` | 32 test tích hợp (provider thật + layout thật, chỉ mock HTTP) |
| `src/features/warehouses/warehouseApi.test.js` | 4 test phân trang |
| `src/App.test.jsx`, `src/features/chatbot/CustomerChatbotRouting.test.jsx` | Chỉ cập nhật **fixture** context: thêm `branchConfirmed: true` và các field mới để biểu diễn phiên đã xác nhận; không đổi expectation |
| `docs/STORE_FRONT_BRANCH_SELECTION_GATE.md`, `docs/branch-selection-evidence/` | Báo cáo, output lệnh, script và ảnh trình duyệt |

Không đổi: backend Java (warehouse, cart, security), Product Search API, checkout, payment, Flyway, chatbot, management UI, `CartProvider`, `cartApi`.

## 3. Luồng UX

1. Khách (guest) xem storefront bình thường, không có cửa sổ và không gọi `/api/warehouses` (API này yêu cầu đăng nhập).
2. CUSTOMER đăng nhập → trang mặc định sau login (`/account`) hoặc trang được yêu cầu (`state.from`) đều hiện **"Chọn chi nhánh mua sắm"** thay cho storefront.
3. Danh sách chỉ gồm chi nhánh ACTIVE, có tên và địa chỉ (nếu backend có), đánh dấu "Chi nhánh của giỏ hàng hiện tại". Chi nhánh đã dùng trước đó được chọn sẵn **nếu** còn ACTIVE, nhưng vẫn phải bấm xác nhận.
4. Chọn radio chỉ đổi lựa chọn tạm. "Tiếp tục mua sắm" chỉ bật khi đã có chi nhánh hợp lệ.
5. Nếu giỏ có sản phẩm ở chi nhánh khác → cảnh báo ngay trong cửa sổ ("…sẽ xóa toàn bộ sản phẩm trong giỏ") với "Giữ giỏ hàng hiện tại" hoặc "Xóa giỏ và tiếp tục".
6. Thành công → cửa sổ đóng, storefront được mount tại **đúng URL đang yêu cầu** (kể cả query), focus chuyển vào nội dung chính.
7. Chuyển trang/refresh trong cùng phiên không hỏi lại. Đổi chi nhánh sau đó bằng selector "Chi nhánh mua sắm" trên header (cùng cơ chế, giữ hộp xác nhận xóa giỏ hiện có).

Trạng thái phụ: "Đang tải danh sách chi nhánh…"; "Không thể tải danh sách chi nhánh. Vui lòng thử lại." + "Thử lại"; "Không thể kiểm tra giỏ hàng hiện tại. Vui lòng thử lại." + "Thử lại"; "Hiện chưa có chi nhánh mua sắm khả dụng. Vui lòng liên hệ cửa hàng." Mọi trạng thái đều có nút "Đăng xuất". Không có "Bỏ qua"; Escape và click nền không đóng cửa sổ.

## 4. Kiến trúc

- **Một nguồn sự thật:** gate đọc `WarehouseProvider` và `CartProvider`; cửa sổ gọi đúng `selectWarehouse` mà header dùng. Không có state chi nhánh song song.
- `selectWarehouse(warehouseId, { confirmClear })`: tham số mới là tùy chọn. Header không truyền nên vẫn dùng `window.confirm` như cũ; cửa sổ truyền `() => true` **sau khi** khách đã đồng ý trong cửa sổ. `clearItems` gửi cho backend vẫn là `mustClear` tính như trước (giỏ có item và chi nhánh giỏ khác chi nhánh mới).
- **Gate trong `AppLayout`:** CUSTOMER chưa xác nhận → layout trả về riêng cửa sổ, **không render** header/nav/giỏ/Outlet. Các trang mua sắm không được mount nên không thể thao tác bằng chuột, bàn phím hay focus. URL không bị điều hướng. Chỉ `/forbidden` được miễn. Management layout (ADMIN/MANAGER/STAFF) và guest không đi qua gate.
- Field context mới: `listStatus` (`idle|loading|ready|error`), `reloadWarehouses`, `clearRequiredFor(id)`, `cartReady`, `branchConfirmed`. Field cũ giữ nguyên ý nghĩa (`warehouses`, `selectedWarehouse`, `selectedWarehouseId`, `selectWarehouse`, `isLoading`, `isChanging`, `error`).

## 5. Chính sách chi nhánh hợp lệ

`branchConfirmed` chỉ đúng khi **tất cả**:
1. Người dùng là CUSTOMER.
2. Danh sách chi nhánh của **chính user này** đã tải đủ (`listStatus='ready'`). Danh sách của tài khoản trước không được dùng.
3. Giỏ hàng của user đã tải xong và không lỗi (`cartReady`); `cart=null` lúc đang tải không bị coi là "chưa có giỏ".
4. Chi nhánh hiệu lực (ưu tiên `cart.warehouseId`, như trước) nằm trong danh sách ACTIVE.
5. `cart.warehouseId` **bằng** chi nhánh đó (giỏ đã đồng bộ theo contract).
6. Dấu xác nhận của phiên (`sessionStorage`) trỏ đúng chi nhánh đó.

Hệ quả: lựa chọn trong localStorage, UUID lạ, chi nhánh INACTIVE, giỏ trỏ chi nhánh INACTIVE, hoặc giỏ chưa gắn chi nhánh đều **không** mở storefront và không được chọn thay bằng chi nhánh khác. `selectWarehouse` từ chối chi nhánh không có trong danh sách ACTIVE đã tải. Nếu backend trả giỏ với chi nhánh khác chi nhánh vừa chọn thì không xác nhận.

**Phân trang** `/api/warehouses`: backend phân trang, không sắp xếp (trang có thể lệch giữa các lần gọi) và không lọc trạng thái. `listAll()` đọc `totalPages`, tải mọi trang (size 100, tối đa 20 trang), loại trùng theo `warehouseId`. Nếu số bản ghi duy nhất khác `totalElements` hoặc `totalElements` đổi giữa các trang thì báo lỗi và cửa sổ hiện "Thử lại", không coi danh sách thiếu là đầy đủ.

## 6. Ghi nhớ phiên

- `localStorage.shoppingWarehouseId`: chỉ để **gợi ý** (như trước), không bao giờ tự mở gate.
- `sessionStorage['storefrontBranchConfirmed.v1.<userId>'] = warehouseId`: dấu xác nhận theo tab/phiên trình duyệt và theo user, chỉ chứa ID chi nhánh. Không lưu giỏ hàng, tồn kho hay dữ liệu cá nhân. Được ghi sau mỗi lần chọn thành công (cửa sổ hoặc header).
- Đổi tài khoản, đăng xuất, phiên hết hạn (`invalidateSession` → `user=null`): xóa dấu xác nhận của tài khoản trước. Đăng nhập lại (cùng hoặc khác tài khoản) phải xác nhận lại.
- Phiên trình duyệt mới: `sessionStorage` trống nên hỏi lại, có gợi ý chi nhánh cũ.

## 7. Đồng bộ giỏ hàng

| Tình huống | Hành vi |
|---|---|
| Chưa có giỏ (`cartId=null`) / giỏ chưa có chi nhánh | Xác nhận → `PUT /cart/warehouse {warehouseId, clearItems:false}` (backend tạo/gắn giỏ) |
| Giỏ đã ở chi nhánh được chọn | Xác nhận **không gọi API**, không chạm items |
| Giỏ có item ở chi nhánh khác | Cảnh báo trong cửa sổ; "Giữ giỏ hàng hiện tại" → không gọi API, giỏ nguyên vẹn; "Xóa giỏ và tiếp tục" → `clearItems:true` |
| API lỗi (409/5xx/...) | Cửa sổ giữ nguyên, hiển thị thông báo backend, không ghi dấu xác nhận |
| Giỏ đang tải | Hiện trạng thái tải; nút xác nhận chưa xuất hiện |
| Tải giỏ lỗi | "Không thể kiểm tra giỏ hàng hiện tại" + Thử lại (`refreshCart`), không mở gate |

## 8. Chống race

- `epoch` tăng mỗi khi user đổi. Kết quả `selectWarehouse` đến muộn của tài khoản cũ bị bỏ: không `setSelectedWarehouseId`, không ghi localStorage hay dấu xác nhận, không đóng cửa sổ (có test).
- Danh sách chi nhánh gắn `owner`; request bị abort khi user đổi; kết quả cũ không hiển thị cho tài khoản mới.
- Double-submit: ref đồng bộ trong cửa sổ + ref `changing` trong provider. Double-click và submit thêm chỉ tạo 1 request (có test).
- Giỏ hàng: giữ cơ chế `cartOwner` sẵn có của `CartProvider`.

## 9. Auth

- Guest: không gọi API protected, không có cửa sổ, luồng đăng nhập không đổi.
- CUSTOMER: gate như trên; 401 từ danh sách → `invalidateSession` (về guest); 403/500 → lỗi + Thử lại + Đăng xuất.
- ADMIN/MANAGER/STAFF: management layout không có gate (có test và kiểm trình duyệt với STAFF).

## 10. Accessibility

- `role="dialog"`, `aria-modal="true"`, `aria-labelledby` (heading) và `aria-describedby` (mô tả).
- Mở ra thì focus vào radio đang chọn hoặc heading.
- Focus trap Tab/Shift+Tab tính nhóm radio là một điểm dừng như trình duyệt; bản đầu chưa làm vậy nên focus thoát ra `body`, và test đã phát hiện.
- Radio gốc trong `fieldset`/`legend` nên mũi tên chuyển lựa chọn và screen reader đọc được trạng thái đã chọn.
- Escape bị chặn nhưng không mắc kẹt: luôn có "Đăng xuất".
- Gate mở thì focus chuyển tới `#main-content`.
- Mobile ≤560px: dạng bottom sheet, nút chính rộng toàn màn hình.

## 11. Kết quả test

Node 22.23.3, trong `Fontend/`:

| Lệnh | Kết quả | Output |
|---|---|---|
| `npm test` | 14 file, **145 passed**, 0 failed, 0 skipped, exit 0 | [test.txt](branch-selection-evidence/test.txt) |
| `npx vitest run src/features/warehouses src/App.test.jsx` | 47 passed | [focused.txt](branch-selection-evidence/focused.txt) |
| `npm run lint` | exit 0 | [lint.txt](branch-selection-evidence/lint.txt) |
| `npm run build` | exit 0 | [build.txt](branch-selection-evidence/build.txt) |

Nhóm test mới (36):
- **A. Cửa sổ:** chặn storefront, chỉ ACTIVE, tên + địa chỉ, chưa chọn không tiếp tục được, Escape/backdrop, focus trap, mũi tên, tìm kiếm không dấu theo tên/địa chỉ.
- **B. Hợp lệ:** chọn hợp lệ và giữ URL detail + query; localStorage ACTIVE được gợi ý nhưng vẫn hỏi; UUID lạ/INACTIVE không gợi ý; giỏ trỏ INACTIVE; không có ACTIVE; API chậm, lỗi rồi Thử lại; 401/403; phân trang nhiều trang, trang lệch, total đổi.
- **C. Giỏ hàng:** giỏ đang tải; tải giỏ lỗi rồi Thử lại; giỏ cùng chi nhánh không gọi API; cảnh báo/giữ giỏ/xóa giỏ (`clearItems:true`); API lỗi; giỏ trả về sai chi nhánh; double submit.
- **D. Phiên:** refresh; dấu xác nhận không khớp giỏ; dấu xác nhận khi giỏ chưa đồng bộ; chuyển trang; logout, login lại, đổi tài khoản; kết quả muộn của tài khoản cũ; `/account` bị gate, `/forbidden` vẫn vào được.
- **E. Hồi quy:** STAFF/ADMIN/MANAGER; guest; selector header với `window.confirm` (từ chối rồi đồng ý) và không hiện lại onboarding.

Kiểm tra đột biến (sửa tạm rồi khôi phục): bỏ kiểm epoch → 1 test FAIL; bỏ cảnh báo trong cửa sổ → 1 FAIL; bỏ kiểm tính nhất quán phân trang → 2 FAIL. Lần đầu, bỏ điều kiện giỏ trùng chi nhánh **không** làm test nào FAIL; đã thêm test "dấu xác nhận không đủ khi giỏ chưa đồng bộ", và đột biến này giờ làm 1 test FAIL.

## 12. Bằng chứng trình duyệt

`docs/branch-selection-evidence/browser_check.py`: Chrome thật → Vite 5173 → Spring 8080 (DB dev chỉ có tài khoản seed, 2 chi nhánh ACTIVE). **13 PASS, 0 FAIL, 1 NOT_RUN**, 0 page error, **0 request ghi vào `/api/cart`** ([browser_check.json](branch-selection-evidence/browser_check.json)).

| Bước | Kết quả | Ảnh |
|---|---|---|
| Guest vào trang chủ: không có cửa sổ | PASS | `01-guest.png` |
| CUSTOMER đăng nhập → cửa sổ bắt buộc, 2 chi nhánh có địa chỉ, chi nhánh giỏ được gợi ý, storefront không được mount | PASS | `02-dialog.png` |
| Escape + click ngoài không đóng | PASS | — |
| Chọn chi nhánh khác khi giỏ có 1 sản phẩm → cảnh báo trong cửa sổ → "Giữ giỏ hàng hiện tại": không có request | PASS | `03-clear-warning.png` |
| Xác nhận chi nhánh của giỏ → storefront mở, header "Chi nhánh mua sắm" + địa chỉ, focus `#main-content`, không request | PASS | `04-shop-open.png` |
| Vào Cửa hàng: request sản phẩm mang `warehouseId` của chi nhánh đã chọn; không hỏi lại | PASS | `05-catalog.png` |
| Refresh: không hỏi lại | PASS | — |
| Header đổi chi nhánh → `window.confirm` hiện, từ chối → giữ nguyên | PASS | `06-header.png` |
| Đăng xuất → đăng nhập lại: hỏi lại | PASS | — |
| Phiên trình duyệt mới + mở thẳng `/products/<id>?from=link` → hỏi; xác nhận xong vẫn ở URL đó | PASS | `07-direct-detail.png` |
| Mobile 390px / tablet 768px: không tràn ngang, nút xác nhận hiển thị | PASS | `08-mobile.png`, `08-tablet.png` |
| STAFF vào `/inventory`: management layout, không có cửa sổ | PASS | `09-staff.png` |
| Thêm vào giỏ và đổi chi nhánh thật (xóa giỏ) | **NOT_RUN** | sẽ sửa dữ liệu giỏ trong DB dev; đã có test tích hợp nhóm C |

## 13. Giới hạn còn lại

- Chọn chi nhánh trước khi đăng nhập không làm được vì `/api/warehouses` yêu cầu xác thực. Nếu cần, phải có **hợp đồng backend riêng** (API công khai danh sách chi nhánh ACTIVE).
- `/api/warehouses` không sắp xếp. Danh sách nhiều trang có thể lệch giữa các lần gọi; khi đó UI báo lỗi và cho thử lại thay vì hiển thị thiếu. Backend nên sắp xếp ổn định (ví dụ theo tên/ID) và có thể lọc ACTIVE.
- ACTIVE chỉ là trạng thái hợp lệ theo backend, không phải giờ mở cửa.
- `sessionStorage` theo tab; Chrome sao chép nó khi mở tab bằng "Duplicate"/link `target=_blank` từ tab đã xác nhận, nên tab đó không hỏi lại (vẫn được kiểm lại giỏ và danh sách).
- Đổi chi nhánh ở tab khác: tab hiện tại sẽ hỏi lại khi giỏ được tải lại (CartProvider không tự đồng bộ giữa các tab).
- Chatbot khách hàng vẫn dùng kho cấu hình của chatbot, không theo chi nhánh này (giới hạn CB-12 đã biết).
- Chưa kiểm bằng screen reader thật, Safari hay Firefox.

## 14. Kiểm thử thủ công

1. `bash scripts/start.sh`, mở `http://localhost:5173` khi chưa đăng nhập: không có cửa sổ.
2. Đăng nhập CUSTOMER (tài khoản seed trong `chatbot-service/docs/CHATBOT_LAB_HUONG_DAN_KIEM_THU.md`): cửa sổ hiện; thử Escape, click nền và Tab (focus không thoát).
3. Chọn chi nhánh → "Tiếp tục mua sắm" → kiểm header, vào Cửa hàng và xem tồn kho theo chi nhánh.
4. Refresh và chuyển trang: không hỏi lại. Đăng xuất rồi đăng nhập: hỏi lại.
5. Với **dữ liệu thử** (không phải giỏ thật): thêm sản phẩm, đổi chi nhánh ở cửa sổ hoặc header, xác nhận xóa giỏ; thử "Giữ giỏ hàng hiện tại".
6. Đăng nhập STAFF/ADMIN: vào quản trị không có cửa sổ.
7. Thu nhỏ còn 390px: cửa sổ dạng bottom sheet, nút bấm được.
