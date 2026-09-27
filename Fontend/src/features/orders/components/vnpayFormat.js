export const paymentStatusLabels = { PENDING: 'Chờ thanh toán', PAID: 'Đã thanh toán', FAILED: 'Thanh toán thất bại' };

export const vnpayResponseMessages = {
  '07': 'VNPAY báo giao dịch cần kiểm tra. Vui lòng liên hệ cửa hàng để đối soát.',
  '09': 'Tài khoản chưa đăng ký Internet Banking.',
  '10': 'Thông tin thẻ hoặc tài khoản xác thực không đúng.',
  '11': 'Đã hết thời gian thanh toán.',
  '12': 'Thẻ hoặc tài khoản bị khóa.',
  '13': 'Mã xác thực OTP không đúng.',
  '24': 'Giao dịch đã được hủy tại cổng thanh toán.',
  '51': 'Tài khoản không đủ số dư.',
  '65': 'Giao dịch vượt hạn mức trong ngày.',
  '75': 'Ngân hàng đang bảo trì.',
  '79': 'Đã nhập sai thông tin xác thực quá số lần cho phép.',
  '99': 'Cổng thanh toán chưa hoàn tất giao dịch.',
};

export function redirectToVnpay(value) {
  const url = new URL(value);
  if (url.origin !== 'https://sandbox.vnpayment.vn' || url.pathname !== '/paymentv2/vpcpay.html' || url.username || url.password) {
    throw new Error('Đường dẫn VNPAY Sandbox không hợp lệ. Đơn đã được tạo, vui lòng kiểm tra trong Đơn hàng của tôi.');
  }
  window.location.assign(url.href);
}
