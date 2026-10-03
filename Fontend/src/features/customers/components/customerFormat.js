export const accountLabels = { ACTIVE: 'Hoạt động', INACTIVE: 'Ngừng hoạt động', LOCKED: 'Đã khóa' };
export const installmentLabels = { PENDING: 'Chờ tiếp nhận', PROCESSING: 'Đang xét duyệt', APPROVED: 'Đã duyệt', REJECTED: 'Từ chối' };
export function pageNumber(value, size = 20) {
  const page = Number(value || 0);
  return Number.isSafeInteger(page) && page >= 0 && page * size <= 2147483647 ? page : 0;
}
