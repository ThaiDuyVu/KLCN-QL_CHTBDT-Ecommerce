import { commerceRequest } from '../orders/api/orderApi';

export const posApi = {
  searchVariants(keyword, signal) { return commerceRequest(`/v1/product-variants?page=0&size=10&keyword=${encodeURIComponent(keyword)}`, { signal }); },
  findCustomers(keyword, signal) { return commerceRequest(`/customers?page=0&size=5&keyword=${encodeURIComponent(keyword)}`, { signal }); },
  createWalkIn(fullName, phone) { return commerceRequest('/customers', { method: 'POST', body: { fullName, phone } }); },
  sell(body) { return commerceRequest('/orders/in-store', { method: 'POST', body }); },
};
