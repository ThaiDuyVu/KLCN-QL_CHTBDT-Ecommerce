import { useCallback, useState } from 'react';
import { Link, useParams } from 'react-router';
import PageHeader from '../../../components/ui/PageHeader';
import StatusBadge from '../../../components/ui/StatusBadge';
import useProductRequest from '../../products/hooks/useProductRequest';
import CommerceState from '../../orders/components/CommerceState';
import { date, money } from '../../orders/components/orderFormat';
import { returnApi } from '../api/returnApi';
import { conditionLabels, returnActionLabels, returnStatusLabels, returnTypeLabels } from '../returnFormat';
import ReturnLines from '../components/ReturnLines';
import { PrintButton } from '../../../components/print/PrintForm';
import { ReturnForm } from '../../forms/BusinessForms';
import '../../orders/commerce.css';
import '../returns.css';

export default function ReturnDetailPage({ customer = false }) {
  const { returnId } = useParams();
  const load = useCallback((signal) => (customer ? returnApi.myReturn(returnId, signal) : returnApi.detail(returnId, signal)), [customer, returnId]);
  const { data, error, isLoading, retry } = useProductRequest(`return:${customer}:${returnId}`, load);
  const [action, setAction] = useState('');
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState('');
  const [notice, setNotice] = useState('');
  async function submit(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const body = { status: action, note: String(form.get('note') || '').trim() || null };
    if (action === 'RECEIVED') body.conditions = data.items.map((line) => ({ returnItemId: line.returnItemId, condition: form.get(`condition-${line.returnItemId}`) }));
    setBusy(true); setActionError('');
    try { await returnApi.status(returnId, body); setAction(''); setNotice('Đã cập nhật yêu cầu đổi trả.'); retry(); }
    catch (e) { setActionError(e.message); } finally { setBusy(false); }
  }
  return <><PageHeader title="Chi tiết đổi trả" description={data ? `${data.returnCode} · đơn ${data.orderCode}` : 'Thông tin yêu cầu đổi trả'} />
    <Link className="commerce-back-link" to={customer ? '/my-returns' : '/returns'}>← Danh sách đổi trả</Link>
    {data && <><PrintButton label="In phiếu đổi trả"><ReturnForm request={data} statusLabel={returnStatusLabels[data.status]} /></PrintButton></>}
    <CommerceState loading={isLoading} error={error} retry={retry} />
    {notice && <p role="status" className="commerce-notice">{notice}</p>}
    {data && <div className="order-detail-layout"><div className="order-detail-main"><section className="panel commerce-section">
      <div className="return-card-head"><h2>{data.returnCode}</h2><StatusBadge status={data.status}>{returnStatusLabels[data.status]}</StatusBadge></div>
      <dl className="order-detail-fields">
        <div><dt>Đơn hàng</dt><dd><Link to={customer ? `/my-orders/${data.orderId}` : `/orders/${data.orderId}`}>{data.orderCode}</Link></dd></div>
        {!customer && <div><dt>Khách hàng</dt><dd>{data.customerName}</dd></div>}
        <div><dt>Ngày gửi</dt><dd>{date(data.requestDate)}</dd></div>
        <div><dt>Ngày xử lý xong</dt><dd>{data.processedAt ? date(data.processedAt) : '—'}</dd></div>
        <div><dt>Hình thức</dt><dd>{returnTypeLabels[data.requestType] || returnTypeLabels.RETURN}</dd></div>
        <div><dt>{data.requestType === 'EXCHANGE' ? 'Giá trị hàng trả (được trừ)' : 'Số tiền hoàn'}</dt><dd>{money(data.refundAmount)}</dd></div>
        {data.exchangeOrderId && <div><dt>Đơn đổi hàng</dt><dd><Link to={customer ? `/my-orders/${data.exchangeOrderId}` : `/orders/${data.exchangeOrderId}`}>{data.exchangeOrderCode}</Link></dd></div>}
        {data.exchangeDifference != null && <div><dt>{Number(data.exchangeDifference) >= 0 ? 'Khách trả thêm' : 'Cửa hàng hoàn lại'}</dt><dd>{money(Math.abs(Number(data.exchangeDifference)))}</dd></div>}
        <div><dt>Nhân viên xử lý</dt><dd>{data.employeeName || '—'}</dd></div>
        <div className="order-detail-full"><dt>Lý do</dt><dd className="order-note">{data.reason}</dd></div>
        {data.rejectionReason && <div className="order-detail-full"><dt>Lý do từ chối</dt><dd>{data.rejectionReason}</dd></div>}
      </dl><h3>Sản phẩm trả</h3><ReturnLines items={data.items} />
      {data.exchangeItems?.length > 0 && <><h3>Sản phẩm đổi sang</h3><ul className="return-lines">{data.exchangeItems.map((line) => <li key={line.variantId}>
        <strong>{line.productName}</strong> <span className="muted">{line.sku}</span> · SL {line.quantity} · giá hiện tại {money(line.currentPrice)}</li>)}</ul></>}</section></div>
      {!customer && <aside className="panel commerce-section return-actions"><h3>Xử lý</h3>
        {actionError && <p role="alert" className="auth-alert">{actionError}</p>}
        {!action && (data.allowedStatuses?.length ? <div className="order-detail-actions">{data.allowedStatuses.map((status) => <button key={status} className={`button ${status === 'REJECTED' ? 'button-quiet' : ''}`} onClick={() => { setAction(status); setNotice(''); }}>{returnActionLabels[status]}</button>)}</div> : <p className="muted">Không có thao tác phù hợp ở bước này.</p>)}
        {action && <form onSubmit={submit} className="return-actions" aria-label={returnActionLabels[action]}><h4>{returnActionLabels[action]}</h4>
          {action === 'RECEIVED' && data.items.map((line) => <label key={line.returnItemId}>Tình trạng {line.productName}{line.serialNumber ? ` (${line.serialNumber})` : ''}
            <select name={`condition-${line.returnItemId}`} required defaultValue=""><option value="" disabled>Chọn tình trạng</option>{Object.entries(conditionLabels).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label>)}
          {action === 'EXCHANGED' && <p>Tạo đơn đổi hàng theo giá hiện hành tại chi nhánh của đơn gốc, trừ {money(data.refundAmount)} giá trị hàng trả. Hàng giao như đơn thường (đơn mua tại quầy thì giao ngay).</p>}
          {action === 'REFUNDED' && <p>Xác nhận đã hoàn {money(data.refundAmount)} cho khách (tiền mặt, chuyển khoản hoặc hoàn qua VNPAY).</p>}
          {(action === 'REJECTED' || action === 'APPROVED') && <label>{action === 'REJECTED' ? 'Lý do từ chối (bắt buộc)' : 'Ghi chú (không bắt buộc)'}<textarea name="note" maxLength={500} required={action === 'REJECTED'} /></label>}
          <div className="order-detail-actions"><button className="button" disabled={busy}>{busy ? 'Đang lưu…' : 'Xác nhận'}</button><button type="button" className="button button-quiet" disabled={busy} onClick={() => setAction('')}>Hủy</button></div>
        </form>}
      </aside>}
    </div>}
  </>;
}
