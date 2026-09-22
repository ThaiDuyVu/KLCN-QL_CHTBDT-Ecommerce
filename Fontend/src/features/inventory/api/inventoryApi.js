import { apiClient } from '../../../api/apiClient';
import { authApi } from '../../../auth/api/authApi';

async function request(path, options = {}) {
  try { return await apiClient(path, options); }
  catch (error) {
    if (error.status !== 401 || options.signal?.aborted) throw error;
    await authApi.refresh();
    return apiClient(path, options);
  }
}

function queryString(values) {
  const params = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') params.set(key, value);
  });
  return params.toString();
}

export const inventoryApi = {
  inventory(filters = {}, signal) {
    return request(`/inventory?${queryString({ ...filters, size: filters.size || 20 })}`, { signal });
  },
  inventoryDetail(id, signal) { return request(`/inventory/${encodeURIComponent(id)}`, { signal }); },
  serials(filters = {}, signal) {
    return request(`/serials?${queryString({ ...filters, size: filters.size || 20 })}`, { signal });
  },
  serialDetail(id, signal) { return request(`/serials/${encodeURIComponent(id)}`, { signal }); },
  lookupSerial(code, signal) { return request(`/serials/lookup?${queryString({ code })}`, { signal }); },
  receipts(page = 0, signal) { return request(`/goods-receipts?page=${page}&size=20`, { signal }); },
  receipt(id, signal) { return request(`/goods-receipts/${encodeURIComponent(id)}`, { signal }); },
  createReceipt(body) { return request('/goods-receipts', { method: 'POST', body }); },
  updateReceipt(id, body) { return request(`/goods-receipts/${encodeURIComponent(id)}`, { method: 'PUT', body }); },
  receiptStatus(id, status) { return request(`/goods-receipts/${encodeURIComponent(id)}/status`, { method: 'PATCH', body: { status } }); },
  warehouses(signal) { return request('/warehouses?page=0&size=100', { signal }); },
  suppliers(signal) { return request('/suppliers?page=0&size=100', { signal }); },
  variants(signal) { return request('/v1/product-variants?page=0&size=100', { signal }); },
};
