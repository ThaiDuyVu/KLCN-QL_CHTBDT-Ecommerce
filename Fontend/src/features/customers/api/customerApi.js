import { commerceRequest } from '../../orders/api/orderApi';

function query(values) {
  const params = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => { if (value !== '' && value != null) params.set(key, value); });
  return params.toString();
}
export const customerApi = {
  list(filters, signal) { return commerceRequest(`/customers?${query(filters)}`, { signal }); },
  detail(id, signal) { return commerceRequest(`/customers/${encodeURIComponent(id)}`, { signal }); },
  history(id, resource, page, signal) {
    return commerceRequest(`/customers/${encodeURIComponent(id)}/${resource}?${query({ page, size: 10 })}`, { signal });
  },
};
