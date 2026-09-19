import { commerceRequest } from '../../orders/api/orderApi';
export const cartApi = {
  get(signal) { return commerceRequest('/cart', { signal }); },
  add(variantId, quantity) { return commerceRequest('/cart/items', { method: 'POST', body: { variantId, quantity } }); },
  quantity(id, quantity) { return commerceRequest(`/cart/items/${encodeURIComponent(id)}`, { method: 'PATCH', body: { quantity } }); },
  remove(id) { return commerceRequest(`/cart/items/${encodeURIComponent(id)}`, { method: 'DELETE' }); },
};
