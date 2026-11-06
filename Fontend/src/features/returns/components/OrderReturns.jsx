import { useCallback, useState } from 'react';
import { Link } from 'react-router';
import StatusBadge from '../../../components/ui/StatusBadge';
import useProductRequest from '../../products/hooks/useProductRequest';
import { date, money } from '../../orders/components/orderFormat';
import { returnApi } from '../api/returnApi';
import { RETURN_WINDOW_DAYS, returnStatusLabels } from '../returnFormat';
import ReturnLines from './ReturnLines';
import '../returns.css';

/**
 * Customer side of returns on a delivered order: existing requests and a form for a new one.
 * The server enforces the window and the remaining quantities; the form only pre-fills them.
 */
export default function OrderReturns({ order }) {
  const load = useCallback((signal) => returnApi.forOrder(order.orderId, signal), [order.orderId]);
  const existing = useProductRequest(`returns:${order.orderId}`, load);
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const claimed = {};
  (existing.data || []).filter((row) => row.status !== 'REJECTED')
    .forEach((row) => row.items.forEach((line) => { claimed[line.orderItemId] = (claimed[line.orderItemId] || 0) + line.quantity; }));
  const returnable = order.items.map((item) => ({ ...item, remaining: item.quantity - (claimed[item.orderItemId] || 0) })).filter((item) => item.remaining > 0);
  async function submit(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const items = returnable.filter((item) => form.get(`pick-${item.orderItemId}`))
      .map((item) => ({ orderItemId: item.orderItemId, quantity: Number(form.get(`qty-${item.orderItemId}`) || 1) }));
    if (!items.length) { setError('Chọn ít nhất một sản phẩm cần trả.'); return; }
    setBusy(true); setError('');
    try {
      const created = await returnApi.create({ orderId: order.orderId, reason: String(form.get('reason') || '').trim(), items });
      setOpen(false); setNotice(`Đã gửi yêu cầu đổi trả ${created.returnCode}. Cửa hàng sẽ liên hệ để nhận lại hàng.`); existing.retry();
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  return <section className="panel commerce-section" aria-label="Đổi trả hàng"><h3>Đổi trả hàng</h3>
    <p className="muted">Được đổi trả trong {RETURN_WINDOW_DAYS} ngày kể từ ngày nhận hàng. Tiền hoàn sau khi cửa hàng nhận và kiểm tra hàng.</p>
    {notice && <p role="status" className="commerce-notice">{notice}</p>}
    {(existing.data || []).map((row) => <article className="return-card" key={row.returnId}>
      <div className="return-card-head"><strong>{row.returnCode}</strong><StatusBadge status={row.status}>{returnStatusLabels[row.status]}</StatusBadge></div>
      <p className="muted">Gửi ngày {date(row.requestDate)} · Hoàn {money(row.refundAmount)}</p>
      <ReturnLines items={row.items} />
      <Link to={`/my-returns/${row.returnId}`}>Xem chi tiết</Link>
    </article>)}
    {!open && returnable.length > 0 && <button className="button button-quiet" onClick={() => { setOpen(true); setNotice(''); }}>Yêu cầu đổi trả</button>}
    {open && <form className="return-form" onSubmit={submit}>
      {error && <p role="alert" className="auth-alert">{error}</p>}
      <fieldset disabled={busy}><legend>Sản phẩm cần trả</legend>
        {returnable.map((item) => <label key={item.orderItemId} className="return-pick">
          <input type="checkbox" name={`pick-${item.orderItemId}`} aria-label={`Trả ${item.productName}${item.serialNumber ? ` (${item.serialNumber})` : ''}`} />
          <span>{item.productName}{item.serialNumber ? ` · ${item.serialNumber}` : ''}</span>
          {item.remaining > 1 ? <input type="number" name={`qty-${item.orderItemId}`} min="1" max={item.remaining} defaultValue={item.remaining} aria-label={`Số lượng trả ${item.productName}`} /> : <input type="hidden" name={`qty-${item.orderItemId}`} value="1" />}
        </label>)}
      </fieldset>
      <label>Lý do đổi trả<textarea name="reason" required maxLength={1000} disabled={busy} /></label>
      <div className="order-detail-actions"><button className="button" disabled={busy}>{busy ? 'Đang gửi…' : 'Gửi yêu cầu'}</button><button type="button" className="button button-quiet" disabled={busy} onClick={() => setOpen(false)}>Đóng</button></div>
    </form>}
  </section>;
}
