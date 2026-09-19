export const statusLabels = { PENDING: 'Chờ xác nhận', CONFIRMED: 'Đã xác nhận', PROCESSING: 'Đang xử lý', SHIPPED: 'Đang giao', DELIVERED: 'Đã giao', CANCELLED: 'Đã hủy' };
export const money = (value) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 2 }).format(value || 0);
export const date = (value) => value ? new Date(value).toLocaleString('vi-VN') : '—';

export const actionLabels = { CONFIRMED: 'Xác nhận đơn', PROCESSING: 'Bắt đầu xử lý', SHIPPED: 'Gửi hàng', DELIVERED: 'Xác nhận đã giao', CANCELLED: 'Hủy đơn' };
