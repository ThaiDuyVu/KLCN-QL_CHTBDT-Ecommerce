import { commerceRequest } from '../../orders/api/orderApi';
export const promotionApi = {
  list({ page = 0, keyword = '', status = '' }, signal) {
    const query = new URLSearchParams({ page, size: 20 });
    if (keyword) query.set('keyword', keyword);
    if (status) query.set('status', status);
    return commerceRequest(`/promotions?${query}`, { signal });
  },
  detail(id, signal) { return commerceRequest(`/promotions/${encodeURIComponent(id)}`, { signal }); },
  save(id, body) { return commerceRequest(`/promotions${id ? `/${encodeURIComponent(id)}` : ''}`, { method: id ? 'PUT' : 'POST', body }); },
  status(id, status) { return commerceRequest(`/promotions/${encodeURIComponent(id)}/status`, { method: 'PATCH', body: { status } }); },
  remove(id) { return commerceRequest(`/promotions/${encodeURIComponent(id)}`, { method: 'DELETE' }); },
};
