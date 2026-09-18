import { apiClient } from '../../../api/apiClient';
import { authApi } from '../../../auth/api/authApi';

async function request(path, options = {}) {
  const { signal, ...rest } = options;
  const config = { ...rest, signal: signal ? AbortSignal.any([signal, AbortSignal.timeout(15000)]) : undefined };
  try { return await apiClient(path, config); }
  catch (error) {
    if (error.status !== 401 || signal?.aborted) throw error;
    await authApi.refresh();
    return apiClient(path, config);
  }
}
export const userManagementApi = {
  users(filters, signal) {
    const query = new URLSearchParams({ page: String(filters.page), size: String(filters.size) });
    for (const key of ['keyword', 'status', 'roleId']) if (filters[key]) query.set(key, filters[key]);
    return request(`/users?${query}`, { signal });
  },
  user(id) { return request(`/users/${encodeURIComponent(id)}`); },
  update(id, body) { return request(`/users/${encodeURIComponent(id)}`, { method: 'PUT', body }); },
  status(id, status) { return request(`/users/${encodeURIComponent(id)}/status`, { method: 'PATCH', body: { status } }); },
  role(id, roleId) { return request(`/users/${encodeURIComponent(id)}/role`, { method: 'PUT', body: { roleId } }); },
  roles(signal) { return request('/roles', { signal }); },
  permissions(signal) { return request('/permissions', { signal }); },
  rolePermissions(id, signal) { return request(`/roles/${encodeURIComponent(id)}/permissions`, { signal }); },
  updatePermissions(id, permissionIds) { return request(`/roles/${encodeURIComponent(id)}/permissions`, { method: 'PUT', body: { permissionIds } }); },
};
