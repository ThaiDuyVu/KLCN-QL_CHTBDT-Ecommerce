import PriceDisplay from '../../../components/ui/PriceDisplay';
import { useCallback, useEffect, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router';
import { orderApi } from '../api/orderApi';
import useProductRequest from '../../products/hooks/useProductRequest';
import CommerceState from '../components/CommerceState';
import { statusLabels, actionLabels, money, date } from '../components/orderFormat';
import PageHeader from '../../../components/ui/PageHeader';
import StatusBadge from '../../../components/ui/StatusBadge';
import { useAuth } from '../../../hooks/useAuth';
import { ROLES } from '../../../config/projectConfig';
import ShopBreadcrumb from '../../../components/ui/ShopBreadcrumb';
import ComingSoonButton from '../../../components/ui/ComingSoonButton';
import { vnpayApi } from '../api/vnpayApi';
import { paymentStatusLabels, redirectToVnpay } from '../components/vnpayFormat';
import ShipmentInfo from '../components/ShipmentInfo';
import StatusTimeline from '../components/StatusTimeline';
import { PrintButton } from '../../../components/print/PrintForm';
import { DeliveryNoteForm } from '../../forms/BusinessForms';
import DispatchForm from '../components/DispatchForm';
import OrderReturns from '../../returns/components/OrderReturns';
import OrderReviews from '../../reviews/components/OrderReviews';
import '../commerce.css';
const installmentLabels = { PENDING: 'Chờ tiếp nhận', PROCESSING: 'Đang xét duyệt', APPROVED: 'Đã duyệt', REJECTED: 'Đã từ chối' };
export default function OrderDetailPage({ customer = false }) {
  const { orderId } = useParams(); return <Detail key={`${customer}:${orderId}`} customer={customer} orderId={orderId} />;
}
function Detail({ customer, orderId }) {
  const location = useLocation(); const { user, invalidateSession } = useAuth();
  const load = useCallback((signal) => orderApi.detail(customer, orderId, signal), [customer, orderId]);
  const { data, error, isLoading, retry } = useProductRequest(`${customer}:${orderId}`, load);
  const [busy, setBusy] = useState(false); const [actionError, setActionError] = useState(''); const [notice, setNotice] = useState('');
  const [now, setNow] = useState(() => Date.now());
  const [dispatching, setDispatching] = useState(false);
  useEffect(() => { const timer = window.setInterval(() => setNow(Date.now()), 30000); return () => window.clearInterval(timer); }, []);
  async function update(status, shipping) {
    let reason;
    if (status === 'SHIPPED' && !shipping) { setDispatching(true); setNotice(''); return; }
    if (!customer && status === 'CANCELLED' && data.status === 'SHIPPED') {
      const answer = window.prompt(`Ghi nhận giao không thành công cho đơn ${data.orderCode}: hàng sẽ được nhập lại chi nhánh và đơn bị hủy. Nhập lý do (bắt buộc):`, '');
      if (answer === null) return;
      reason = answer.trim();
      if (!reason) { setActionError('Cần nhập lý do giao không thành công.'); return; }
    } else if (!customer && status === 'CANCELLED') {
      const refund = data.payment?.status === 'PAID';
      const answer = window.prompt(refund
        ? `Đơn ${data.orderCode} đã thanh toán. Hủy đơn sẽ chuyển thanh toán sang "Đã hoàn tiền" — hãy hoàn tiền cho khách trước. Nhập lý do hủy (bắt buộc):`
        : `Nhập lý do hủy đơn ${data.orderCode} (không bắt buộc):`, '');
      if (answer === null) return;
      reason = answer.trim();
      if (refund && !reason) { setActionError('Cần nhập lý do khi hủy đơn đã thanh toán.'); return; }
    } else if (!shipping && !window.confirm(`${actionLabels[status]} ${data.orderCode}?`)) return;
    setBusy(true); setActionError(''); setNotice('');
    try {
      await (customer ? orderApi.cancel(orderId) : shipping ? orderApi.status(orderId, status, undefined, shipping) : orderApi.status(orderId, status, reason));
      setDispatching(false); retry(); setNotice('Đã cập nhật trạng thái đơn hàng.');
    }
    catch (e) { if (e.status === 401) invalidateSession(); setActionError(e.message); retry(); }
    finally { setBusy(false); }
  }
  async function payVnpay() {
    setBusy(true); setActionError('');
    try { const link = await vnpayApi.paymentUrl(orderId); redirectToVnpay(link.paymentUrl); }
    catch (requestError) { if (requestError.status === 401) invalidateSession(); setActionError(requestError.message); retry(); }
    finally { setBusy(false); }
  }
  async function synchronizeVnpay() {
    setBusy(true); setActionError(''); setNotice('');
    try { await vnpayApi.synchronize(orderId); retry(); setNotice('Đã kiểm tra kết quả thanh toán với VNPAY.'); }
    catch (requestError) { if (requestError.status === 401) invalidateSession(); setActionError(requestError.message); retry(); }
    finally { setBusy(false); }
  }
  const progress = ['PENDING', 'CONFIRMED', 'PROCESSING', 'SHIPPED', 'DELIVERED'];
  return <>{customer && <ShopBreadcrumb items={[{ label: 'Đơn hàng của tôi', to: '/my-orders' }, { label: data?.orderCode || 'Chi tiết đơn hàng' }]} />}<PageHeader title="Chi tiết đơn hàng" description="Thông tin giao hàng, sản phẩm và tiến độ xử lý." /><Link className="commerce-back-link" to={customer ? '/my-orders' : '/orders'}>← Danh sách đơn hàng</Link>
    {location.state?.checkoutComplete && <p role="status" className="commerce-notice">{location.state.paymentMethod === 'INSTALLMENT' ? 'Đã tạo đơn và gửi hồ sơ trả góp. Đơn sẽ được xác nhận sau khi hồ sơ được duyệt.' : location.state.paymentMethod === 'VNPAY' ? 'Đơn đã được tạo. Hoàn tất thanh toán tại VNPAY Sandbox.' : 'Đặt hàng thành công. Thanh toán COD khi nhận hàng.'}</p>}
    {location.state?.paymentRedirectError && <p role="alert" className="auth-alert">{location.state.paymentRedirectError}</p>}
    {notice && <p role="status" className="commerce-notice">{notice}</p>}{actionError && <p role="alert" className="auth-alert">{actionError}</p>}
    <CommerceState loading={isLoading} error={error} retry={retry} />
    {customer && !isLoading && !error && data && <section className="panel customer-order-progress" aria-label="Tiến độ đơn hàng">{data.status === 'CANCELLED' ? <p>Đơn hàng đã hủy.</p> : progress.map((status, index) => <div key={status} className={index <= progress.indexOf(data.status) ? 'is-complete' : ''} aria-current={data.status === status ? 'step' : undefined}><span>{index < progress.indexOf(data.status) ? '✓' : index + 1}</span><strong>{statusLabels[status]}</strong></div>)}</section>}
    {!isLoading && !error && data && <div className="order-detail-layout"><div className="order-detail-main">
      <section className="panel commerce-section order-detail-overview">
        <div><p className="eyebrow">Đơn hàng</p><h2>{data.orderCode}</h2><p className="muted">Đặt ngày {date(data.orderDate)}</p></div>
        <StatusBadge status={data.status}>{statusLabels[data.status]}</StatusBadge>
      </section>
      <section className="panel commerce-section"><h3>Thông tin nhận hàng</h3><dl className="order-detail-fields">
        <div><dt>Người nhận</dt><dd>{data.recipientName}</dd></div><div><dt>Điện thoại</dt><dd>{data.recipientPhone}</dd></div>
        <div><dt>Chi nhánh xử lý</dt><dd>{data.warehouseName || 'Chưa xác định'}</dd></div>
        <div className="order-detail-full"><dt>Địa chỉ giao hàng</dt><dd>{data.shippingAddress}</dd></div>
        {data.note && <div className="order-detail-full"><dt>Ghi chú</dt><dd className="order-note">{data.note}</dd></div>}
        {data.cancelReason && <div className="order-detail-full"><dt>Lý do hủy</dt><dd>{data.cancelReason}</dd></div>}
        {data.exchangeFromReturnCode && <div className="order-detail-full"><dt>Đơn đổi hàng</dt><dd>Từ yêu cầu {data.exchangeFromReturnCode}: đã trừ {money(data.exchangeCredit)} giá trị hàng trả, cần thu {money(data.payment?.amount)}</dd></div>}
      </dl></section>
      <ShipmentInfo shipment={data.shipment} />
      <StatusTimeline history={data.statusHistory} />
      {!customer && data.shipment && <div className="order-detail-actions"><PrintButton label="In phiếu giao hàng"><DeliveryNoteForm order={data} /></PrintButton></div>}
      {customer && data.status === 'DELIVERED' && <OrderReviews order={data} />}
      {customer && data.status === 'DELIVERED' && <OrderReturns order={data} />}
      <section className="panel commerce-section"><h3>Sản phẩm</h3>{data.items?.map((item) => <article className="commerce-order-item" key={item.orderItemId}>
        <div className="order-item-heading"><div><strong>{item.productName}</strong><span>{item.sku} · SL {item.quantity}</span></div><strong>{money(item.finalUnitPrice * item.quantity)}</strong></div>
        <p className="muted"><PriceDisplay originalPrice={item.unitPrice} effectivePrice={item.finalUnitPrice} discountAmount={item.discountAmount} /> / sản phẩm</p>
        {item.trackingType && item.trackingType !== 'NONE' && (item.serialNumber ? <p className="muted">
          Serial: <strong>{item.serialNumber}</strong>{item.imeiNumbers?.length ? ` · IMEI: ${item.imeiNumbers.join(', ')}` : ''}
        </p> : <p className="muted">Thiết bị cụ thể sẽ được cấp khi đơn chuyển sang Đang xử lý.</p>)}
      </article>)}</section></div>
      <aside className="panel commerce-section order-detail-summary"><h3>Tổng kết đơn hàng</h3>
        <dl className="summary-lines"><div><dt>Tạm tính</dt><dd>{money(data.subtotal)}</dd></div><div><dt>Giảm giá</dt><dd>{money(data.discountAmount)}</dd></div><div><dt>Phí giao hàng</dt><dd>{money(data.shippingFee)}</dd></div></dl>
        <div className="summary-total"><span>Tổng tiền</span><strong>{money(data.totalAmount)}</strong></div>
        <p className="order-payment">Thanh toán: {data.payment?.paymentMethod || '—'} · {paymentStatusLabels[data.payment?.status] || 'Chờ thanh toán'}</p>
        {data.payment?.transactionCode && <p className="muted">Mã giao dịch: {data.payment.transactionCode}</p>}
        {data.payment?.paymentMethod === 'VNPAY' && data.payment.status !== 'PAID' && <div className="order-vnpay-info">
          <p>Đơn chỉ được xác nhận sau khi VNPAY xác nhận thanh toán thành công.</p>
          {customer && <button className="button button-quiet" disabled={busy} onClick={synchronizeVnpay}>{busy ? 'Đang kiểm tra…' : 'Kiểm tra kết quả thanh toán'}</button>}
          {data.payment.status === 'PENDING' && data.payment.transactionCode && <p className="auth-alert" role="alert">VNPAY báo giao dịch cần đối soát. Không mở thanh toán lại hoặc hủy đơn cho đến khi cửa hàng kiểm tra kết quả.</p>}
          {customer && data.status === 'PENDING' && data.payment.status === 'PENDING' && !data.payment.transactionCode && (Date.parse(data.payment.paymentExpiresAt) > now ? <button className="button" disabled={busy} onClick={payVnpay}>{busy ? 'Đang mở…' : 'Mở thanh toán VNPAY'}</button> : <p>Phiên thanh toán đã hết hạn. Kiểm tra kết quả thanh toán trước khi hủy đơn và đặt lại.</p>)}
          {customer && data.payment.status === 'FAILED' && <p>Thanh toán chưa thành công. Bạn có thể hủy đơn chưa thanh toán rồi đặt lại.</p>}
        </div>}
        {data.status === 'CANCELLED' && data.payment?.status === 'REFUNDED' && <p className="commerce-notice" role="status">Đơn đã hủy và cửa hàng đã hoàn tiền {money(data.payment.amount)}.</p>}
        {data.status === 'CANCELLED' && data.payment?.status === 'PAID' && <p className="auth-alert" role="alert">Đơn đã hủy nhưng thanh toán đã được ghi nhận. Cần liên hệ cửa hàng để đối soát/hoàn tiền; hệ thống không tự khôi phục đơn.</p>}
        {data.installment && <section className="installment-status-card" aria-label="Hồ sơ trả góp">
          <h4>Hồ sơ trả góp</h4><StatusBadge status={data.installment.status}>{installmentLabels[data.installment.status] || data.installment.status}</StatusBadge>
          <p>Đơn vị: <strong>{data.installment.providerName}</strong></p>
          <p>Kỳ hạn: <strong>{data.installment.termMonths} tháng</strong></p>
          <p>Trả trước: <strong>{money(data.installment.downPayment)}</strong></p>
          <p>Còn lại: <strong>{money(data.installment.remainingAmount)}</strong></p>
          {!customer && [ROLES.ADMIN, ROLES.MANAGER].includes(user?.roleName) && <Link to={`/installments/${data.installment.installmentId}`}>Xem và xử lý hồ sơ</Link>}
        </section>}
        {dispatching ? <DispatchForm selfAssignable={user?.roleName === ROLES.DELIVERY_STAFF} busy={busy} onClose={() => setDispatching(false)} onSubmit={(shipping) => update('SHIPPED', shipping)} />
          : <div className="order-detail-actions">{data.allowedStatuses?.map((status) => <button key={status} className={`button ${status === 'CANCELLED' ? 'button-quiet' : ''}`} disabled={busy} onClick={() => update(status)}>{busy ? 'Đang cập nhật…' : status === 'CANCELLED' && data.status === 'SHIPPED' ? 'Giao không thành công' : actionLabels[status]}</button>)}</div>}
        {!data.allowedStatuses?.length && <p className="muted">Không có thao tác trạng thái phù hợp ở bước này.</p>}
        {data.invoice && <p className="order-payment">Hóa đơn: <Link to={customer ? `/my-orders/${data.orderId}/invoice` : `/invoices/${data.invoice.invoiceId}`}>{data.invoice.invoiceNumber}</Link>{data.invoice.status === 'CANCELLED' ? ' (đã hủy)' : ''}</p>}
        {customer && <div className="order-future-actions"><ComingSoonButton>Liên hệ hỗ trợ</ComingSoonButton></div>}
      </aside>
    </div>}
  </>;
}
