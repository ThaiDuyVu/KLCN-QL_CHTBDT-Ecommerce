import { commerceRequest } from '../../orders/api/orderApi';

const PAGE_SIZE = 100;
const MAX_PAGES = 20;

function incomplete(message) {
  const error = new Error(message);
  error.name = 'IncompleteWarehouseList';
  return error;
}

export const warehouseApi = {
  list(signal) {
    return commerceRequest(`/warehouses?page=0&size=${PAGE_SIZE}`, { signal });
  },
  // /api/warehouses is paginated and unsorted, so pages can shift between requests.
  // Load every page and refuse a list that does not add up instead of treating it as complete.
  async listAll(signal) {
    const first = await commerceRequest(`/warehouses?page=0&size=${PAGE_SIZE}`, { signal });
    const totalPages = Math.max(1, Number(first?.totalPages) || 1);
    if (totalPages > MAX_PAGES) throw incomplete('Danh sách chi nhánh vượt quá giới hạn tải.');
    const rest = await Promise.all(Array.from({ length: totalPages - 1 }, (_, index) =>
      commerceRequest(`/warehouses?page=${index + 1}&size=${PAGE_SIZE}`, { signal })));
    const pages = [first, ...rest];
    const byId = new Map(pages.flatMap((page) => page?.content || []).map((warehouse) => [warehouse.warehouseId, warehouse]));
    const total = first?.totalElements;
    if (typeof total === 'number' && (byId.size !== total || rest.some((page) => page?.totalElements !== total))) {
      throw incomplete('Danh sách chi nhánh thay đổi trong lúc tải.');
    }
    return [...byId.values()];
  },
};
