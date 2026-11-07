import { useState } from 'react';
import { useAuth } from '../../../hooks/useAuth';
import { userManagementApi as api } from '../api/userManagementApi';
import BranchSelect from './BranchSelect';

/** Only ADMIN may create ADMIN/MANAGER accounts; customers are created on the Customers page. */
const ADMIN_ONLY = ['ADMIN', 'MANAGER'];

export default function EmployeeCreateForm({ roles, onCreated, onClose }) {
  const { user, invalidateSession } = useAuth();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const choices = roles.filter((role) => role.roleName !== 'CUSTOMER' && (user?.roleName === 'ADMIN' || !ADMIN_ONLY.includes(role.roleName)));
  async function submit(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const value = (name) => String(form.get(name) || '').trim();
    setBusy(true); setError('');
    try {
      const created = await api.createEmployee({
        username: value('username'), password: String(form.get('password') || ''), fullName: value('fullName'),
        email: value('email'), phone: value('phone') || null, roleId: value('roleId'),
        employeeCode: value('employeeCode') || null, position: value('position') || null, warehouseId: value('warehouseId') || null,
      });
      onCreated(created);
    } catch (e) {
      if (e.status === 401) invalidateSession();
      setError(e.message);
    } finally { setBusy(false); }
  }
  return <section className="panel management-editor" aria-label="Thêm nhân viên">
    <div className="management-section-header"><h2>Thêm nhân viên</h2>
      <button type="button" className="button button-quiet" onClick={onClose} disabled={busy}>Đóng</button></div>
    {error && <p role="alert" className="auth-alert">{error}</p>}
    <form onSubmit={submit} className="management-form">
      <fieldset disabled={busy}>
        <label>Họ và tên<input name="fullName" required maxLength={255} autoFocus /></label>
        <label>Chức danh<select name="roleId" required defaultValue=""><option value="" disabled>Chọn chức danh</option>
          {choices.map((role) => <option key={role.roleId} value={role.roleId}>{role.description || role.roleName}</option>)}</select></label>
        <label>Vị trí (không bắt buộc)<input name="position" maxLength={100} placeholder="Mặc định theo chức danh" /></label>
        <BranchSelect />
        <label>Tên đăng nhập<input name="username" required pattern="[A-Za-z0-9._\-]{4,50}" title="4–50 ký tự: chữ không dấu, số, . _ -" autoComplete="off" /></label>
        <label>Mật khẩu ban đầu<input name="password" type="password" required minLength={8} autoComplete="new-password" title="Ít nhất 8 ký tự, gồm cả chữ và số" /></label>
        <label>Mã nhân viên (không bắt buộc)<input name="employeeCode" maxLength={50} pattern="[A-Za-z0-9\-]{2,50}" placeholder="Tự sinh NVxxxx" /></label>
        <label>Email<input name="email" type="email" required maxLength={255} /></label>
        <label>Số điện thoại<input name="phone" type="tel" pattern="(0[0-9]{9}|\+84[0-9]{9})" title="0xxxxxxxxx hoặc +84xxxxxxxxx" /></label>
      </fieldset>
      <button className="button" disabled={busy}>{busy ? 'Đang tạo…' : 'Tạo nhân viên'}</button>
    </form>
  </section>;
}
