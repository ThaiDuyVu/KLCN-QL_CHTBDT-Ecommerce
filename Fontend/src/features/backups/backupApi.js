import { apiClient } from '../../api/apiClient';
import { commerceRequest } from '../orders/api/orderApi';
import { projectConfig } from '../../config/projectConfig';

/** pg_dump/pg_restore may take minutes; commerceRequest caps requests at 15 s, so these call apiClient with the server's 10-minute limit. */
const LONG = 600000;

export const backupApi = {
  list(signal) { return commerceRequest('/admin/backups', { signal }); },
  create() { return apiClient('/admin/backups', { method: 'POST', signal: AbortSignal.timeout(LONG) }); },
  restore(name, confirmName) { return apiClient(`/admin/backups/${encodeURIComponent(name)}/restore`, { method: 'POST', body: { confirmName }, signal: AbortSignal.timeout(LONG) }); },
  remove(name) { return commerceRequest(`/admin/backups/${encodeURIComponent(name)}`, { method: 'DELETE' }); },
  /** Plain link: the auth cookie is sent by the browser. */
  downloadUrl(name) { return `${projectConfig.apiBaseUrl}/admin/backups/${encodeURIComponent(name)}/download`; },
};
