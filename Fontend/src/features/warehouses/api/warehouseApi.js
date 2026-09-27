import { commerceRequest } from '../../orders/api/orderApi';

export const warehouseApi = {
  list(signal) {
    return commerceRequest('/warehouses?page=0&size=100', { signal });
  },
};
