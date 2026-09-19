import { useCallback, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router';
import { orderApi } from '../api/orderApi';
import useProductRequest from '../../products/hooks/useProductRequest';
import CommerceState from '../components/CommerceState';
import { statusLabels, actionLabels, money, date } from '../components/orderFormat';
import PageHeader from '../../../components/ui/PageHeader';
import { useAuth } from '../../../hooks/useAuth';
import '../commerce.css';
export default function OrderDetailPage({ customer = false }) {
  const { orderId } = useParams(); return <Detail key={`${customer}:${orderId}`} customer={customer} orderId={orderId} />;
}
function Detail({ customer, orderId }) {
  const location = useLocation(); const { invalidateSession } = useAuth();
  const load = useCallback((signal) => orderApi.detail(customer, orderId, signal), [customer, orderId]);
  const { data, error, isLoading, retry } = useProductRequest(`${customer}:${orderId}`, load);
  const [busy, setBusy] = useState(false); const [actionError, setActionError] = useState(''); const [notice, setNotice] = useState('');
  async function update(status) {
    if (!window.confirm(`${actionLabels[status]} ${data.orderCode}?`)) return;
    setBusy(true); setActionError(''); setNotice('');
    try { await (customer ? orderApi.cancel(orderId) : orderApi.status(orderId, status)); retry(); setNotice('Đã cập nhật trạng thái đơn hàng.'); }
    catch (e) { if (e.status === 401) invalidateSession(); setActionError(e.message); retry(); }
    finally { setBusy(false); }
  }
  return <><PageHeader title="Chi tiết đơn hàng" /><Link to={customer ? '/my-orders' : '/orders'}>← Danh sách đơn hàng</Link>
    {location.state?.checkoutComplete && <p role="status" className="panel commerce-section">Đặt hàng thành công. Thanh toán COD khi nhận hàng.</p>}
    {notice && <p role="status">{notice}</p>}{actionError && <p role="alert" className="auth-alert">{actionError}</p>}
    <CommerceState loading={isLoading} error={error} retry={retry} />
    {!isLoading && !error && data && <section className="panel commerce-section">
      <h2>{data.orderCode}</h2><p>{date(data.orderDate)} · <strong>{statusLabels[data.status]}</strong></p>
      <p>Người nhận: {data.recipientName} · {data.recipientPhone}</p><p>Địa chỉ: {data.shippingAddress}</p>{data.note && <p>Ghi chú: {data.note}</p>}
      <h3>Sản phẩm</h3>{data.items?.map((item) => <p key={item.orderItemId}>{item.productName} · {item.sku} × {item.quantity} · {money(item.finalUnitPrice)} / sản phẩm · {money(item.finalUnitPrice * item.quantity)}</p>)}
      <p>Tạm tính: {money(data.subtotal)}</p><p>Giảm giá: {money(data.discountAmount)} · Phí giao hàng: {money(data.shippingFee)}</p><strong>Tổng tiền: {money(data.totalAmount)}</strong>
      <p>Thanh toán: {data.payment?.paymentMethod} · {data.payment?.status === 'PAID' ? 'Đã thanh toán' : 'Chờ thanh toán'}</p>
      <div className="commerce-inline">{data.allowedStatuses?.map((status) => <button key={status} className="button" disabled={busy} onClick={() => update(status)}>{busy ? 'Đang cập nhật…' : actionLabels[status]}</button>)}</div>
      {!data.allowedStatuses?.length && <p className="muted">Không có thao tác trạng thái phù hợp ở bước này.</p>}
    </section>}
  </>;
}
