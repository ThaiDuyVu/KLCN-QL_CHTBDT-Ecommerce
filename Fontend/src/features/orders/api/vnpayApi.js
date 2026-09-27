import { commerceRequest } from './orderApi';

export const vnpayApi = {
  config(signal) { return commerceRequest('/payments/vnpay/config', { signal }); },
  paymentUrl(orderId) { return commerceRequest(`/payments/vnpay/orders/${encodeURIComponent(orderId)}/url`, { method: 'POST' }); },
  synchronize(orderId, signal) { return commerceRequest(`/payments/vnpay/orders/${encodeURIComponent(orderId)}/sync`, { method: 'POST', signal }); },
};
