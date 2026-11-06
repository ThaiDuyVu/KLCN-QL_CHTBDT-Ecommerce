import { useCallback, useState } from 'react';
import { userManagementApi as api } from '../api/userManagementApi';
import useManagementRequest from '../hooks/useManagementRequest';

/** Employee code and position of a staff account (customers have no employee profile). */
export default function EmployeeProfileForm({ userId, disabled }) {
  const load = useCallback((signal) => api.employeeByUser(userId, signal), [userId]);
  const { data, error, loading, reload } = useManagementRequest(`employee:${userId}`, load);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [saveError, setSaveError] = useState('');
  if (loading) return <p className="muted" role="status">Đang tải hồ sơ nhân viên…</p>;
  if (error) return <p className="muted">{error.status === 404 ? 'Tài khoản này chưa có hồ sơ nhân viên.' : error.message}</p>;
  async function submit(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setBusy(true); setMessage(''); setSaveError('');
    try {
      await api.updateEmployee(data.employeeId, { fullName: String(form.get('fullName')).trim(), position: String(form.get('position')).trim() || null });
      setMessage('Đã lưu hồ sơ nhân viên.'); reload();
    } catch (e) { setSaveError(e.message); } finally { setBusy(false); }
  }
  return <form onSubmit={submit} className="management-form" aria-label="Hồ sơ nhân viên">
    <h3>Hồ sơ nhân viên · {data.employeeCode}</h3>
    {saveError && <p role="alert" className="auth-alert">{saveError}</p>}
    {message && <p role="status">{message}</p>}
    <fieldset disabled={disabled || busy}>
      <label>Họ và tên<input name="fullName" defaultValue={data.fullName} required maxLength={255} /></label>
      <label>Vị trí<input name="position" defaultValue={data.position || ''} maxLength={100} /></label>
    </fieldset>
    <button className="button button-quiet" disabled={disabled || busy}>{busy ? 'Đang lưu…' : 'Lưu hồ sơ nhân viên'}</button>
  </form>;
}
