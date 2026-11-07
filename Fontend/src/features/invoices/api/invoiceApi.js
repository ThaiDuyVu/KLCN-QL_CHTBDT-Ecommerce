import { commerceRequest } from '../../orders/api/orderApi';

export const invoiceApi = {
  list(page, keyword, signal) { return commerceRequest(`/invoices?page=${page}&size=20${keyword ? `&keyword=${encodeURIComponent(keyword)}` : ''}`, { signal }); },
  detail(id, signal) { return commerceRequest(`/invoices/${encodeURIComponent(id)}`, { signal }); },
  mineForOrder(orderId, signal) { return commerceRequest(`/invoices/mine/by-order/${encodeURIComponent(orderId)}`, { signal }); },
};
