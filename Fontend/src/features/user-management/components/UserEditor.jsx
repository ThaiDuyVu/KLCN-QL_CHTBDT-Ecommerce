import { useState } from 'react';
import { useAuth } from '../../../hooks/useAuth';
import { userManagementApi as api } from '../api/userManagementApi';

export default function UserEditor({ target, roles, onSaved, onClose, disabled = false, onSubmitting }) {
  const { user, refreshSession, invalidateSession } = useAuth();
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');
  const isBusy = disabled || Boolean(busy);
  const [roleId, setRoleId] = useState(target.roleId || '');
  async function execute(kind, action) {
    setBusy(kind); onSubmitting?.(true); setError('');
    try {
      const updated = await action();
      onSaved(updated);
      if (kind === 'profile' && target.userId === user?.userId) await refreshSession();
    } catch (e) {
      if (e.status === 401) invalidateSession();
      setError(e.message);
    } finally { setBusy(''); onSubmitting?.(false); }
  }
  function saveProfile(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    execute('profile', () => api.update(target.userId, {
      displayName: form.get('displayName').trim() || null,
      email: form.get('email').trim(), phone: form.get('phone').trim() || null,
    }));
  }
  function saveRole(event) {
    event.preventDefault();
    const role = roles.find((r) => r.roleId === roleId);
    if (!role || !window.confirm(`Đổi role của ${target.username} thành ${role.roleName}?`)) return;
    execute('role', async () => {
      await api.role(target.userId, roleId);
      if (target.userId === user?.userId) {
        await refreshSession();
        return { ...target, roleId: role.roleId, roleName: role.roleName };
      }
      return api.user(target.userId);
    });
  }
  return <section className="panel management-editor" aria-label={`Sửa ${target.username}`}>
    <div className="management-section-header"><h2>Sửa tài khoản: {target.username}</h2>
      <button className="button button-quiet" onClick={onClose} disabled={isBusy}>Đóng</button></div>
    {error && <p role="alert" className="auth-alert">{error}</p>}
    <form onSubmit={saveProfile} className="management-form">
      <fieldset disabled={isBusy}>
        <label>Tên hiển thị<input name="displayName" defaultValue={target.displayName || ''} maxLength={255} autoFocus /></label>
        <label>Email<input name="email" type="email" defaultValue={target.email || ''} required maxLength={255} /></label>
        <label>Số điện thoại<input name="phone" type="tel" defaultValue={target.phone || ''} maxLength={30} /></label>
      </fieldset>
      <button className="button" disabled={isBusy}>{busy === 'profile' ? 'Đang lưu…' : 'Lưu thông tin'}</button>
    </form>
    {user?.roleName === 'ADMIN' && <form onSubmit={saveRole} className="management-role-form">
      <label>Role<select value={roleId} onChange={(event) => setRoleId(event.target.value)} required disabled={isBusy}>
        <option value="" disabled>Chọn role</option>{roles.map((role) => <option key={role.roleId} value={role.roleId}>{role.roleName}</option>)}
      </select></label>
      <button className="button button-quiet" disabled={isBusy || !roleId || roleId === target.roleId}>{busy === 'role' ? 'Đang đổi…' : 'Đổi role'}</button>
    </form>}
  </section>;
}
