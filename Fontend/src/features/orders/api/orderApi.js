import { apiClient } from '../../../api/apiClient';
import { authApi } from '../../../auth/api/authApi';
// timeoutMs: requests carrying large bodies (image uploads) need longer than the 15s default.
export async function commerceRequest(path, { timeoutMs = 15000, ...options } = {}) {
  const timeout = AbortSignal.timeout(timeoutMs);
  const config = { ...options, signal: options.signal ? AbortSignal.any([options.signal, timeout]) : timeout };
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
