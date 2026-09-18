import { useCallback, useState } from 'react';
import { useAuth } from '../../../hooks/useAuth';
import { userManagementApi as api } from '../api/userManagementApi';
import useManagementRequest from '../hooks/useManagementRequest';
import RequestState from './RequestState';

export default function RolesPermissionsPanel({ roles }) {
  const [choice, setChoice] = useState('');
  const [notice, setNotice] = useState('');
  const role = roles.find((item) => item.roleId === choice) || roles[0];
  const load = useCallback((signal) => Promise.all([api.permissions(signal), api.rolePermissions(role.roleId, signal)]), [role]);
  const { data, error, loading, reload } = useManagementRequest(role.roleId, load);
  return <section className="panel management-editor">
    <h2>Roles & Permissions</h2>
    <label className="management-role-choice">Role<select value={role.roleId} onChange={(event) => { setChoice(event.target.value); setNotice(''); }}>
      {roles.map((item) => <option key={item.roleId} value={item.roleId}>{item.roleName}</option>)}
    </select></label>
    <p className="muted">{role.description || role.roleName}</p>
    {notice && <p role="status">{notice}</p>}
    <RequestState loading={loading} error={error} retry={reload} />
    {!loading && !error && data && <PermissionsEditor key={`${role.roleId}:${JSON.stringify(data[1])}`} role={role} permissions={data[0]} assigned={data[1]} reload={reload} setNotice={setNotice} />}
  </section>;
}
function PermissionsEditor({ role, permissions, assigned, reload, setNotice }) {
  const { invalidateSession } = useAuth();
  const [selected, setSelected] = useState(() => new Set(assigned.map((item) => item.permissionId)));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const readOnly = role.roleName === 'ADMIN';
  const initial = new Set(assigned.map((item) => item.permissionId));
  const changed = selected.size !== initial.size || [...selected].some((id) => !initial.has(id));
  function toggle(id) { setSelected((previous) => { const next = new Set(previous); if (next.has(id)) next.delete(id); else next.add(id); return next; }); setNotice(''); }
  async function save(event) {
    event.preventDefault();
    if (readOnly || !window.confirm(`Cập nhật permission cho ${role.roleName}?`)) return;
    setBusy(true); setError('');
    try { await api.updatePermissions(role.roleId, [...selected]); setNotice('Đã lưu permission.'); reload(); }
    catch (e) { if (e.status === 401) invalidateSession(); setError(e.message); }
    finally { setBusy(false); }
  }
  return <form onSubmit={save}>
    {readOnly && <p className="badge">ADMIN chỉ xem · Không được chỉnh permission</p>}
    {error && <p className="auth-alert" role="alert">{error}</p>}
    <fieldset className="management-permissions" disabled={readOnly || busy}>
      <legend>{selected.size}/{permissions.length} permission được gán</legend>
      {permissions.length ? permissions.map((permission) => <label key={permission.permissionId}>
        <input type="checkbox" checked={selected.has(permission.permissionId)} onChange={() => toggle(permission.permissionId)}
          disabled={permission.protectedPermission && !selected.has(permission.permissionId)} />
        <span><strong>{permission.permissionName}</strong><small>{permission.description}</small>{permission.protectedPermission && <small>Quyền phân quyền dành riêng cho ADMIN</small>}</span>
      </label>) : <p>Chưa có permission trong hệ thống.</p>}
    </fieldset>
    {!readOnly && <button className="button" disabled={busy || !changed}>{busy ? 'Đang lưu…' : 'Lưu permission'}</button>}
  </form>;
}
