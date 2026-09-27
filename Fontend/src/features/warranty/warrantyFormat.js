export const warrantyStatus = { ACTIVE: 'Còn hạn', EXPIRED: 'Hết hạn' };
export const ticketStatus = {
  RECEIVED: 'Đã tiếp nhận', IN_PROGRESS: 'Đang xử lý', COMPLETED: 'Hoàn tất', REJECTED: 'Từ chối',
};
export const ticketAction = {
  IN_PROGRESS: 'Tiếp nhận xử lý', COMPLETED: 'Hoàn tất', REJECTED: 'Từ chối',
};
export function formatDate(value) {
  if (!value) return '—';
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium' }).format(new Date(value));
}
