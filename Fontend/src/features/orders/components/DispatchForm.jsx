import { useCallback, useState } from 'react';
import { orderApi } from '../api/orderApi';
import useProductRequest from '../../products/hooks/useProductRequest';

/**
 * PROCESSING → SHIPPED: pick the delivery employee and carrier. A delivery employee may leave the
 * employee empty to take the order themselves; the server then assigns the caller.
 */
export default function DispatchForm({ selfAssignable, busy, onSubmit, onClose }) {
  const load = useCallback((signal) => orderApi.deliveryStaff(signal), []);
  const staff = useProductRequest('delivery-staff', load);
  const [error, setError] = useState('');
  function submit(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const value = (name) => String(form.get(name) || '').trim();
    const deliveryEmployeeId = value('deliveryEmployeeId');
    if (!deliveryEmployeeId && !selfAssignable) { setError('Vui lòng chọn nhân viên giao hàng.'); return; }
    setError('');
    onSubmit({ ...(deliveryEmployeeId ? { deliveryEmployeeId } : {}), shippingProvider: value('shippingProvider') || null, trackingNumber: value('trackingNumber') || null });
  }
  return <form className="order-dispatch-form" onSubmit={submit} aria-label="Giao hàng">
    <h4>Bàn giao cho vận chuyển</h4>
    {error && <p role="alert" className="auth-alert">{error}</p>}
    {staff.error && <p role="alert" className="auth-alert">Chưa tải được nhân viên giao hàng: {staff.error.message}</p>}
    <label>Nhân viên giao hàng<select name="deliveryEmployeeId" defaultValue="" disabled={busy || staff.isLoading}>
      <option value="">{selfAssignable ? 'Tôi nhận giao đơn này' : 'Chọn nhân viên'}</option>
      {(staff.data || []).map((row) => <option key={row.employeeId} value={row.employeeId}>{row.fullName} ({row.employeeCode})</option>)}
    </select></label>
    <label>Đơn vị vận chuyển<input name="shippingProvider" maxLength={255} placeholder="Giao nội bộ" disabled={busy} /></label>
    <label>Mã vận đơn<input name="trackingNumber" maxLength={100} pattern="[A-Za-z0-9\-]*" placeholder="Tự sinh nếu để trống" disabled={busy} /></label>
    <div className="order-detail-actions"><button className="button" disabled={busy}>{busy ? 'Đang cập nhật…' : 'Xác nhận gửi hàng'}</button>
      <button type="button" className="button button-quiet" disabled={busy} onClick={onClose}>Đóng</button></div>
  </form>;
}
