import { commerceRequest } from './orderApi';

export const installmentApi = {
  activeProviders(signal) { return commerceRequest('/installment-providers', { signal }); },
  providers(signal) { return commerceRequest('/installment-providers/manage', { signal }); },
  saveProvider(id, body) {
    return commerceRequest(id ? `/installment-providers/${encodeURIComponent(id)}` : '/installment-providers',
      { method: id ? 'PUT' : 'POST', body });
  },
  list({ page, status }, signal) {
    const params = new URLSearchParams({ page: String(page), size: '20' });
    if (status) params.set('status', status);
    return commerceRequest(`/installments?${params}`, { signal });
  },
  detail(id, signal) { return commerceRequest(`/installments/${encodeURIComponent(id)}`, { signal }); },
  status(id, status) {
    return commerceRequest(`/installments/${encodeURIComponent(id)}/status`, { method: 'PATCH', body: { status } });
  },
};
