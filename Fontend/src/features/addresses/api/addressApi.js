import { commerceRequest } from '../../orders/api/orderApi';

const base = '/customers/me/addresses';
export const addressApi = {
  list(signal) { return commerceRequest(base, { signal }); },
  create(body) { return commerceRequest(base, { method: 'POST', body }); },
  update(id, body) { return commerceRequest(`${base}/${encodeURIComponent(id)}`, { method: 'PUT', body }); },
  remove(id) { return commerceRequest(`${base}/${encodeURIComponent(id)}`, { method: 'DELETE' }); },
  makeDefault(id) { return commerceRequest(`${base}/${encodeURIComponent(id)}/default`, { method: 'PATCH' }); },
};
