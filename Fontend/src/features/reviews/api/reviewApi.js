import { commerceRequest } from '../../orders/api/orderApi';

export const reviewApi = {
  forProduct(productId, page, signal) { return commerceRequest(`/v1/products/${encodeURIComponent(productId)}/reviews?page=${page}&size=10`, { signal }); },
  create(body) { return commerceRequest('/reviews', { method: 'POST', body }); },
  mineForOrder(orderId, signal) { return commerceRequest(`/reviews/mine/by-order/${encodeURIComponent(orderId)}`, { signal }); },
  list(page, status, signal) { return commerceRequest(`/reviews?page=${page}&size=20${status ? `&status=${status}` : ''}`, { signal }); },
  moderate(id, body) { return commerceRequest(`/reviews/${encodeURIComponent(id)}`, { method: 'PATCH', body }); },
};
