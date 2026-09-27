import { apiClient } from '../../../api/apiClient';
import { commerceRequest } from '../../orders/api/orderApi';
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
  createWithImages(product, images, primaryImageIndex, specifications = []) {
    const body = new FormData();
    body.append('product', new Blob([JSON.stringify(product)], { type: 'application/json' }));
    images.forEach((file) => body.append('images', file));
    body.append('primaryImageIndex', String(primaryImageIndex));
    if (specifications.length) body.append('specifications', new Blob([JSON.stringify({ specifications })], { type: 'application/json' }));
    return commerceRequest('/v1/products/with-images', { method: 'POST', body, signal: AbortSignal.timeout(60000) });
  },
  brands(page, signal) {
    return commerceRequest(`/v1/brands?page=${page}&size=20`, { signal });
  },
  list({ page = 0, size = 12, keyword = '', categoryId = '', status = '', warehouseId = '', onSale = false }, signal) {
    const query = new URLSearchParams({ page: String(page), size: String(size) });
    if (keyword) query.set('keyword', keyword);
    if (categoryId) query.set('categoryId', categoryId);
    if (status) query.set('status', status);
    if (warehouseId) query.set('warehouseId', warehouseId);
    if (onSale) query.set('onSale', 'true');
    return request(`/v1/products?${query}`, signal);
  },
  detail(productId, warehouseId, signal) {
    const query = warehouseId ? `?warehouseId=${encodeURIComponent(warehouseId)}` : '';
    return request(`/v1/products/${encodeURIComponent(productId)}/detail${query}`, signal);
  },
};
