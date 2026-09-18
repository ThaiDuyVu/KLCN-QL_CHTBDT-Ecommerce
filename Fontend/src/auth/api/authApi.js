import { apiClient } from '../../api/apiClient';

let pendingSession = null;
let pendingRefresh = null;

export const authApi = {
  login({ username, password }) {
    return apiClient('/auth/login', { method: 'POST', body: { username, password } });
  },
  me() {
    return apiClient('/auth/me');
  },
  refresh() {
    // Refresh tokens rotate on the server. Never refresh concurrently.
    pendingRefresh ??= apiClient('/auth/refresh', { method: 'POST' })
      .finally(() => { pendingRefresh = null; });
    return pendingRefresh;
  },
  restoreSession() {
    // StrictMode can mount effects twice; share in-flight restoration.
    pendingSession ??= authApi.me().catch((error) => {
      if (error.status !== 401) throw error;
      return authApi.refresh().catch((refreshError) => {
        if (refreshError.status === 401) return null;
        throw refreshError;
      });
    }).finally(() => { pendingSession = null; });
    return pendingSession;
  },
  logout() {
    return apiClient('/auth/logout', { method: 'POST' });
  },
};
