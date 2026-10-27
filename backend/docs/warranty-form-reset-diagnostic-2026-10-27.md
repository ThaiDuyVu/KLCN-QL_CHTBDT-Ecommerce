# Chẩn đoán lỗi gửi yêu cầu bảo hành: currentTarget.reset

## Kết luận

Lỗi `Cannot read properties of null (reading 'reset')` trong ảnh bắt nguồn từ frontend, ở `Fontend/src/features/warranty/pages/WarrantyDetailPage.jsx:14`. Hàm submit giữ event React qua hai lần await rồi gọi `e.currentTarget.reset()`. Sau khi listener trả Promise và kết thúc lượt dispatch, React gán `event.currentTarget=null`; lúc async function tiếp tục thì đối tượng form không còn lấy được qua event này.

Đây không phải kết luận do API trả null. Lỗi xảy ra sau createTicket, sau đọc lại bảo hành và sau setNotice thành công, nên UI vừa có thông báo thành công vừa có lỗi do catch bắt cả lỗi reset. Nội dung mô tả không được xóa, dễ khiến người dùng gửi thêm yêu cầu trùng.

## Evidence

1. Source submit dòng 13–15: preventDefault → đọc FormData → await createTicket → await myWarranty → setData → setNotice → `e.currentTarget.reset()` → catch hiển thị nextError.message.
2. React DOM bản cài tại `Fontend/node_modules/react-dom/cjs/react-dom-client.development.js:20801–20808`: executeDispatch gán currentTarget trước listener và gán null sau khi listener trả về. Nguyên nhân là vòng đời currentTarget, không phải giải thích pooling toàn bộ event của React cũ.
3. Đăng nhập CUSTOMER development theo repo, chỉ GET bảo hành trong ảnh qua `/api/warranties/mine/{warrantyId}`: HTTP 200. Dữ liệu trả về có MacBook Pro 14 M3, eligible=true, hai tickets; ticket mới mã `WT-51A01FD3-27D3-4183-ABE0-A4FCB47E80D8` trạng thái RECEIVED, createdAt=2026-10-27T12:17:46.753863Z. Không gửi POST tạo thêm ticket; không công khai customer DTO/credentials.
4. API adapter `Fontend/src/features/warranty/api/warrantyApi.js:21` gọi POST tạo ticket; Spring `WarrantyController.java:45–51` trả HTTP 201 sau service.createTicket. Không lấy HTTP 201 này làm status được ghi nhận của request gốc, vì không có capture HTTP request gốc trong chẩn đoán.
5. Component diagnostic test dùng chính WarrantyDetailPage hiện tại và mock hai API, tạo sự kiện bằng Testing Library/userEvent. Sau POST mock thành công và refresh thành công, test xác nhận đồng thời: alert chính xác lỗi reset; notice thành công; ticket mới trong lịch sử; textarea vẫn còn nội dung; createTicket được gọi đúng một lần.

## Kết quả test

Chạy trong Fontend:

```text
node node_modules/vitest/vitest.mjs run src/features/warranty/pages/WarrantyDetailPage.diagnostic.test.jsx
```

Exit 0, 1 test passed, thời lượng 710 ms. Đây là **test xác nhận hành vi lỗi hiện tại**, không phải PASS cho chức năng sau sửa. Test file tạm được xóa sau chạy. Source test, stdout và exit code giữ tại `.local/warranty-reset-diagnostic/{test-source.jsx,result.txt,result.json}`; không có business mutation.

Codebase-memory index/coverage đã được kiểm tra và source được đọc trực tiếp. Chưa thay production code hoặc chạy full suite trong task chẩn đoán.

## Bản sửa tối thiểu đề xuất, chưa triển khai

Lưu DOM form vào biến trước await, dùng biến đó để đọc FormData và reset sau thành công:

```jsx
e.preventDefault();
const form = e.currentTarget;
const issue = String(new FormData(form).get('issueDescription') || '').trim();
// ... await createTicket; await refresh; cập nhật state ...
form.reset();
```

Không dùng `e.currentTarget?.reset()` làm bản sửa chính: cách đó chỉ che exception nhưng form vẫn không được reset. Không cần thay backend/auth/ownership/schema. Sau sửa cần regression khẳng định tạo ticket một lần, lịch sử cập nhật, textarea rỗng, có success notice và không có alert; thêm tình huống API thật sự thất bại phải giữ nội dung để người dùng thử lại.

Do ticket đã xuất hiện trong dữ liệu backend, người dùng nên mở ticket hiện có thay vì gửi lại chỉ vì thông báo reset trên giao diện. Không tự xóa ticket hoặc thay trạng thái bảo hành.

## Đã sửa theo yêu cầu tiếp theo

Đã lưu `const form = e.currentTarget` trước await, đọc FormData từ biến này và gọi `form.reset()` sau khi tạo ticket/tải lại dữ liệu thành công. Success notice được đặt sau reset. Không đổi API hoặc nghiệp vụ backend.

Thêm `Fontend/src/features/warranty/pages/WarrantyDetailPage.test.jsx`: hai regression tests PASS. Case thành công xác minh POST một lần, lịch sử ticket cập nhật, textarea rỗng, success notice và không có alert. Case API tạo ticket thất bại xác minh giữ nội dung, hiển thị lỗi, không có notice thành công, nút gửi hoạt động lại.

Vitest focused: 2 passed, exit 0, 724 ms. ESLint hai file frontend: exit 0. `git diff --check`: exit 0. Không gửi thêm request tạo ticket thật trong quá trình kiểm tra; không commit/push. Vite đang chạy sẽ nạp thay đổi frontend.
