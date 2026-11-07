import { commerceRequest } from '../../orders/api/orderApi';

export const returnApi = {
  /** Any signed-in user may search variants (used to pick exchange products). */
  searchVariants(keyword, signal) { return commerceRequest(`/v1/product-variants?page=0&size=10&keyword=${encodeURIComponent(keyword)}`, { signal }); },
  create(body) { return commerceRequest('/returns/mine', { method: 'POST', body }); },
  mine(page, signal) { return commerceRequest(`/returns/mine?page=${page}&size=20`, { signal }); },
  myReturn(id, signal) { return commerceRequest(`/returns/mine/${encodeURIComponent(id)}`, { signal }); },
  forOrder(orderId, signal) { return commerceRequest(`/returns/mine/by-order/${encodeURIComponent(orderId)}`, { signal }); },
  list(page, status, signal) { return commerceRequest(`/returns?page=${page}&size=20${status ? `&status=${status}` : ''}`, { signal }); },
  detail(id, signal) { return commerceRequest(`/returns/${encodeURIComponent(id)}`, { signal }); },
  status(id, body) { return commerceRequest(`/returns/${encodeURIComponent(id)}/status`, { method: 'PATCH', body }); },
};
