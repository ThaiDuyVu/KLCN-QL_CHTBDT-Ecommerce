import { apiClient } from '../../../api/apiClient';
import { authApi } from '../../../auth/api/authApi';

async function request(path, signal) {
  const options = { signal: AbortSignal.any([signal, AbortSignal.timeout(15000)]) };
  try {
    return await apiClient(path, options);
  } catch (error) {
    if (error.status !== 401 || signal.aborted) throw error;
    // Share the existing refresh operation; retry an expired access cookie once.
    await authApi.refresh();
    return apiClient(path, options);
  }
}

export const productApi = {
  list({ page = 0, size = 12, keyword = '', status = '', warehouseId = '' }, signal) {
    const query = new URLSearchParams({ page: String(page), size: String(size) });
    if (keyword) query.set('keyword', keyword);
    if (status) query.set('status', status);
    if (warehouseId) query.set('warehouseId', warehouseId);
    return request(`/v1/products?${query}`, signal);
  },
  detail(productId, warehouseId, signal) {
    const query = warehouseId ? `?warehouseId=${encodeURIComponent(warehouseId)}` : '';
    return request(`/v1/products/${encodeURIComponent(productId)}/detail${query}`, signal);
  },
};
