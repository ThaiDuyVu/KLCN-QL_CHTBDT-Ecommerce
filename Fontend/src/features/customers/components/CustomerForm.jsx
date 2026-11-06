import { useState } from 'react';
import { useAuth } from '../../../hooks/useAuth';
import { customerApi } from '../api/customerApi';

/**
 * Create (no `customer`) or edit a customer profile. A customer registered at the counter signs in with
 * the phone number unless staff picks another username; staff hands over the initial password.
 */
export default function CustomerForm({ customer, onSaved, onClose }) {
  const { invalidateSession } = useAuth();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const editing = Boolean(customer);
  async function submit(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const value = (name) => String(form.get(name) || '').trim();
    const body = { fullName: value('fullName'), phone: value('phone'), email: value('email') || null, address: value('address') || null };
    setBusy(true); setError('');
    try {
      const saved = editing ? await customerApi.update(customer.customerId, body)
        : await customerApi.create({ ...body, username: value('username') || null, password: String(form.get('password') || '') });
      onSaved(saved);
    } catch (e) {
      if (e.status === 401) invalidateSession();
      setError(e.message);
    } finally { setBusy(false); }
  }
  const realEmail = customer?.email && !customer.email.endsWith('@customer.invalid') ? customer.email : '';
  return <section className="panel customer-form" aria-label={editing ? 'Sửa thông tin khách hàng' : 'Thêm khách hàng'}>
    <header className="data-section-heading"><div><h2>{editing ? 'Sửa thông tin khách hàng' : 'Thêm khách hàng tại quầy'}</h2>
      {!editing && <p>Khách đăng nhập bằng số điện thoại (hoặc tên đăng nhập bạn đặt) và mật khẩu ban đầu.</p>}</div>
      <button type="button" className="button button-quiet" onClick={onClose} disabled={busy}>Đóng</button></header>
    {error && <p role="alert" className="auth-alert">{error}</p>}
    <form onSubmit={submit}>
      <fieldset disabled={busy}>
        <label>Họ và tên<input name="fullName" required maxLength={255} defaultValue={customer?.fullName || ''} autoFocus /></label>
        <label>Số điện thoại<input name="phone" type="tel" required pattern="(0[0-9]{9}|\+84[0-9]{9})" title="0xxxxxxxxx hoặc +84xxxxxxxxx" defaultValue={customer?.phone || ''} /></label>
        <label>Email (không bắt buộc)<input name="email" type="email" maxLength={255} defaultValue={realEmail} /></label>
        <label className="customer-form-wide">Địa chỉ (không bắt buộc)<input name="address" maxLength={500} defaultValue={customer?.address || ''} /></label>
        {!editing && <>
          <label>Tên đăng nhập (không bắt buộc)<input name="username" pattern="[A-Za-z0-9._\-]{4,50}" placeholder="Mặc định là số điện thoại" autoComplete="off" /></label>
          <label>Mật khẩu ban đầu<input name="password" type="password" required minLength={8} autoComplete="new-password" title="Ít nhất 8 ký tự, gồm cả chữ và số" /></label>
        </>}
      </fieldset>
      <button className="button" disabled={busy}>{busy ? 'Đang lưu…' : editing ? 'Lưu thay đổi' : 'Tạo khách hàng'}</button>
    </form>
  </section>;
}
