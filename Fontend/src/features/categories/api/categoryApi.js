import { apiClient } from '../../../api/apiClient';
import { authApi } from '../../../auth/api/authApi';

async function request(path, options = {}) {
  try {
    return await apiClient(path, options);
  } catch (error) {
    if (error.status !== 401 || options.signal?.aborted) throw error;
    await authApi.refresh();
    return apiClient(path, options);
  }
}

const basePath = '/v1/categories';

export const categoryApi = {
  list(signal) {
    return request(basePath, { signal });
  },
  detail(categoryId, signal) {
    return request(`${basePath}/${encodeURIComponent(categoryId)}`, { signal });
  },
  roots(signal) {
    return request(`${basePath}/roots`, { signal });
  },
  children(categoryId, signal) {
    return request(`${basePath}/${encodeURIComponent(categoryId)}/children`, { signal });
  },
  create(body) {
    return request(basePath, { method: 'POST', body });
  },
  update(categoryId, body) {
    return request(`${basePath}/${encodeURIComponent(categoryId)}`, { method: 'PUT', body });
  },
  remove(categoryId) {
    return request(`${basePath}/${encodeURIComponent(categoryId)}`, { method: 'DELETE' });
  },
};
