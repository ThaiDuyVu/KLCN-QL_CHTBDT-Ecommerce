import { apiClient } from '../../../api/apiClient';
import { authApi } from '../../../auth/api/authApi';
export async function commerceRequest(path, options = {}) {
  const config = { ...options, signal: options.signal ? AbortSignal.any([options.signal, AbortSignal.timeout(15000)]) : undefined };
  try { return await apiClient(path, config); }
  catch (error) {
    if (error.status !== 401 || options.signal?.aborted) throw error;
    await authApi.refresh(); return apiClient(path, config);
  }
}
export const orderApi = {
  checkout(body) { return commerceRequest('/orders/checkout', { method: 'POST', body }); },
  list(customer, page, signal) { return commerceRequest(`/orders${customer ? '/mine' : ''}?page=${page}&size=20`, { signal }); },
  detail(customer, id, signal) { return commerceRequest(`/orders${customer ? '/mine' : ''}/${encodeURIComponent(id)}`, { signal }); },
  cancel(id) { return commerceRequest(`/orders/mine/${encodeURIComponent(id)}/cancel`, { method: 'POST' }); },
  status(id, status) { return commerceRequest(`/orders/${encodeURIComponent(id)}/status`, { method: 'PATCH', body: { status } }); },
};
