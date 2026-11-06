import { commerceRequest } from '../../orders/api/orderApi';

export const returnApi = {
  create(body) { return commerceRequest('/returns/mine', { method: 'POST', body }); },
  mine(page, signal) { return commerceRequest(`/returns/mine?page=${page}&size=20`, { signal }); },
  myReturn(id, signal) { return commerceRequest(`/returns/mine/${encodeURIComponent(id)}`, { signal }); },
  forOrder(orderId, signal) { return commerceRequest(`/returns/mine/by-order/${encodeURIComponent(orderId)}`, { signal }); },
  list(page, status, signal) { return commerceRequest(`/returns?page=${page}&size=20${status ? `&status=${status}` : ''}`, { signal }); },
  detail(id, signal) { return commerceRequest(`/returns/${encodeURIComponent(id)}`, { signal }); },
  status(id, body) { return commerceRequest(`/returns/${encodeURIComponent(id)}/status`, { method: 'PATCH', body }); },
};
