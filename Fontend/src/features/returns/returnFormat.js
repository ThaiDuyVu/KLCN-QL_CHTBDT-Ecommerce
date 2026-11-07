export const returnStatusLabels = { REQUESTED: 'Chờ duyệt', APPROVED: 'Đã duyệt, chờ nhận hàng', REJECTED: 'Từ chối', RECEIVED: 'Đã nhận hàng, chờ xử lý', REFUNDED: 'Đã hoàn tiền', EXCHANGED: 'Đã đổi hàng' };
export const returnTypeLabels = { RETURN: 'Trả hàng, hoàn tiền', EXCHANGE: 'Đổi sang sản phẩm khác' };
export const returnActionLabels = { APPROVED: 'Duyệt yêu cầu', REJECTED: 'Từ chối', RECEIVED: 'Xác nhận đã nhận hàng', REFUNDED: 'Xác nhận đã hoàn tiền', EXCHANGED: 'Xác nhận đổi hàng (tạo đơn đổi)' };
export const conditionLabels = { RESTOCK: 'Còn tốt – nhập kho bán lại', DEFECTIVE: 'Hàng lỗi – không bán lại' };
/** Store policy, mirrors ReturnServiceImpl.RETURN_WINDOW_DAYS. */
export const RETURN_WINDOW_DAYS = 7;
