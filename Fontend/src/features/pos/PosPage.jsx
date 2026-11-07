import { useCallback, useState } from 'react';
import { Link } from 'react-router';
import PageHeader from '../../components/ui/PageHeader';
import { money } from '../orders/components/orderFormat';
import { warehouseApi } from '../warehouses/api/warehouseApi';
import useProductRequest from '../products/hooks/useProductRequest';
import { posApi } from './posApi';
import '../orders/commerce.css';
import './pos.css';

const unitPrice = (v) => Number(v.effectivePrice ?? v.price ?? 0);

/**
 * Counter sale (V13): pick or register the customer by phone, add products (scan serials for tracked
 * devices), take cash. Stock, serials, warranty and invoice are handled by the server in one step.
 */
export default function PosPage() {
  const loadBranches = useCallback((signal) => warehouseApi.listAll(signal), []);
  const branches = useProductRequest('pos-branches', loadBranches);
  const [warehouseId, setWarehouseId] = useState('');
  const [customer, setCustomer] = useState(null);
  const [customerHits, setCustomerHits] = useState(null);
  const [variantHits, setVariantHits] = useState(null);
  const [lines, setLines] = useState([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [sold, setSold] = useState(null);

  async function guard(action) {
    setBusy(true); setError('');
    try { await action(); } catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  function findCustomer(event) {
    event.preventDefault();
    const keyword = String(new FormData(event.currentTarget).get('keyword') || '').trim();
    if (keyword) guard(async () => setCustomerHits((await posApi.findCustomers(keyword)).content));
  }
  function createWalkIn(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    guard(async () => { setCustomer(await posApi.createWalkIn(String(form.get('fullName')).trim(), String(form.get('phone')).trim())); setCustomerHits(null); });
  }
  function findVariants(event) {
    event.preventDefault();
    const keyword = String(new FormData(event.currentTarget).get('keyword') || '').trim();
    if (keyword) guard(async () => setVariantHits((await posApi.searchVariants(keyword)).content));
  }
  function add(variant) {
    setLines((current) => current.some((l) => l.variantId === variant.variantId)
      ? current.map((l) => (l.variantId === variant.variantId ? { ...l, quantity: l.quantity + 1 } : l))
      : [...current, { variantId: variant.variantId, sku: variant.sku, productName: variant.productName, price: unitPrice(variant), trackingType: variant.trackingType, quantity: 1, serials: '' }]);
  }
  const update = (variantId, patch) => setLines((current) => current.map((l) => (l.variantId === variantId ? { ...l, ...patch } : l)));
  const total = lines.reduce((sum, l) => sum + l.price * l.quantity, 0);
  function checkout() {
    if (!customer) { setError('Chọn hoặc tạo khách hàng trước.'); return; }
    if (!lines.length) { setError('Chưa có sản phẩm.'); return; }
    if (!window.confirm(`Thu tiền mặt khoảng ${money(total)} (giá cuối do hệ thống tính)?`)) return;
    guard(async () => {
      const order = await posApi.sell({
        customerId: customer.customerId, ...(warehouseId ? { warehouseId } : {}),
        items: lines.map((l) => {
          const serialNumbers = l.serials.split(/[\s,;]+/).map((s) => s.trim()).filter(Boolean);
          return { variantId: l.variantId, quantity: l.quantity, ...(serialNumbers.length ? { serialNumbers } : {}) };
        }),
      });
      setSold(order); setLines([]); setCustomer(null); setVariantHits(null);
    });
  }
  return <><PageHeader eyebrow="Vận hành / Bán hàng" title="Bán tại quầy" description="Bán trực tiếp cho khách tại cửa hàng: thu tiền mặt, xuất kho, lập bảo hành và hóa đơn ngay." />
    {error && <p role="alert" className="auth-alert">{error}</p>}
    {sold && <p role="status" className="commerce-notice">Đã bán đơn {sold.orderCode}, thu {money(sold.totalAmount)}.{' '}
      <Link to={`/orders/${sold.orderId}`}>Xem đơn</Link>{sold.invoice && <> · <Link to={`/invoices/${sold.invoice.invoiceId}`}>In hóa đơn {sold.invoice.invoiceNumber}</Link></>}</p>}
    <div className="pos-layout">
      <section className="panel commerce-section" aria-label="Khách hàng"><h3>1. Khách hàng</h3>
        <label>Chi nhánh bán<select value={warehouseId} onChange={(e) => setWarehouseId(e.target.value)}>
          <option value="">Chi nhánh của tôi</option>{(branches.data || []).map((w) => <option key={w.warehouseId} value={w.warehouseId}>{w.warehouseName}</option>)}</select></label>
        {customer ? <p>Khách: <strong>{customer.fullName}</strong> · {customer.phone} <button className="button button-quiet" onClick={() => setCustomer(null)}>Đổi khách</button></p> : <>
          <form className="pos-inline" onSubmit={findCustomer} role="search"><input name="keyword" placeholder="Số điện thoại hoặc tên" aria-label="Tìm khách hàng" /><button className="button button-quiet" disabled={busy}>Tìm</button></form>
          {customerHits && (customerHits.length ? <ul className="pos-hits">{customerHits.map((c) => <li key={c.customerId}><button className="button button-quiet" onClick={() => { setCustomer(c); setCustomerHits(null); }}>{c.fullName} · {c.phone || '—'}</button></li>)}</ul> : <p className="muted">Không tìm thấy, tạo khách vãng lai bên dưới.</p>)}
          <form className="pos-inline" onSubmit={createWalkIn} aria-label="Khách vãng lai"><input name="fullName" required placeholder="Họ tên khách" aria-label="Họ tên khách" /><input name="phone" required pattern="(0[0-9]{9}|\+84[0-9]{9})" placeholder="Số điện thoại" aria-label="Số điện thoại khách" /><button className="button button-quiet" disabled={busy}>Tạo khách vãng lai</button></form>
        </>}
      </section>
      <section className="panel commerce-section" aria-label="Sản phẩm"><h3>2. Sản phẩm</h3>
        <form className="pos-inline" onSubmit={findVariants} role="search"><input name="keyword" placeholder="SKU hoặc tên sản phẩm" aria-label="Tìm sản phẩm" /><button className="button button-quiet" disabled={busy}>Tìm</button></form>
        {variantHits && <ul className="pos-hits">{variantHits.map((v) => <li key={v.variantId}><button className="button button-quiet" onClick={() => add(v)}>+ {v.sku} · {v.productName} · {money(unitPrice(v))}</button></li>)}</ul>}
        {lines.length > 0 && <table className="product-table"><thead><tr><th>Sản phẩm</th><th>SL</th><th>Serial đã quét</th><th>Tạm tính</th><th /></tr></thead><tbody>
          {lines.map((l) => <tr key={l.variantId}><td>{l.sku} · {l.productName}</td>
            <td><input type="number" min="1" max="100" value={l.quantity} aria-label={`Số lượng ${l.sku}`} onChange={(e) => update(l.variantId, { quantity: Math.max(1, Number(e.target.value) || 1) })} /></td>
            <td>{l.trackingType && l.trackingType !== 'NONE' ? <input value={l.serials} aria-label={`Serial ${l.sku}`} placeholder="Bỏ trống để hệ thống tự chọn" onChange={(e) => update(l.variantId, { serials: e.target.value })} /> : '—'}</td>
            <td>{money(l.price * l.quantity)}</td><td><button className="button button-quiet" onClick={() => setLines(lines.filter((x) => x.variantId !== l.variantId))}>Xóa</button></td></tr>)}
        </tbody></table>}
        <div className="summary-total"><span>Tạm tính</span><strong>{money(total)}</strong></div>
        <button className="button" disabled={busy || !lines.length || !customer} onClick={checkout}>{busy ? 'Đang xử lý…' : 'Thu tiền mặt & hoàn tất'}</button>
      </section>
    </div>
  </>;
}
