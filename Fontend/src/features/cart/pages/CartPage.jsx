import { useCallback, useState } from 'react';
import { Link } from 'react-router';
import PageHeader from '../../../components/ui/PageHeader';
import { cartApi } from '../api/cartApi';
import useProductRequest from '../../products/hooks/useProductRequest';
import CommerceState from '../../orders/components/CommerceState';
import { money } from '../../orders/components/orderFormat';
import { useAuth } from '../../../hooks/useAuth';
import { useCart } from '../../../hooks/useCart';
import '../../orders/commerce.css';
export default function CartPage() {
  const { invalidateSession } = useAuth();
  const { applyCart } = useCart();
  const load = useCallback((signal) => cartApi.get(signal), []);
  const { data, error, isLoading, retry } = useProductRequest('cart', load);
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState('');
  const [notice, setNotice] = useState('');
  async function change(action) {
    setBusy(true); setActionError(''); setNotice('');
    try { const cart = await action(); applyCart(cart); retry(); setNotice('Đã cập nhật giỏ hàng.'); }
    catch (e) { if (e.status === 401) invalidateSession(); setActionError(e.message); }
    finally { setBusy(false); }
  }
  return <><PageHeader title="Giỏ hàng" description="Giá được cập nhật theo variant hiện tại và xác nhận lại khi checkout." />
    {actionError && <p role="alert" className="auth-alert">{actionError}</p>}{notice && <p role="status">{notice}</p>}
    <CommerceState loading={isLoading} error={error} retry={retry} />
    {!isLoading && !error && (data?.items?.length ? <section className="panel commerce-section">
      {data.items.map((item) => <article className="commerce-cart-item" key={item.cartItemId}>
        <div><strong>{item.productName}</strong><p>{item.sku}</p><p>{money(item.unitPrice)} / sản phẩm · {money(item.lineTotal)}</p></div>
        <QuantityEditor key={`${item.cartItemId}:${item.quantity}`} item={item} disabled={busy} save={(quantity) => change(() => cartApi.quantity(item.cartItemId, quantity))} />
        <button className="button button-quiet" disabled={busy} onClick={() => { if (window.confirm(`Xóa ${item.sku} khỏi giỏ?`)) change(() => cartApi.remove(item.cartItemId)); }}>Xóa</button>
      </article>)}
      <p><strong>Tạm tính: {money(data.subtotal)}</strong></p>
      {busy ? <p role="status">Đang cập nhật…</p> : <Link className="button" to="/checkout">Tiến hành checkout</Link>}
    </section> : <section className="panel commerce-section"><p>Giỏ hàng trống.</p><Link to="/products">Chọn sản phẩm</Link></section>)}
  </>;
}
function QuantityEditor({ item, disabled, save }) {
  const [value, setValue] = useState(item.quantity);
  return <form className="commerce-inline" onSubmit={(e) => { e.preventDefault(); save(Number(value)); }}>
    <label>Số lượng {item.sku}<input type="number" min="1" max="2147483647" step="1" required value={value} disabled={disabled} onChange={(e) => setValue(e.target.value)} /></label>
    <button className="button button-quiet" disabled={disabled || Number(value) === item.quantity}>Cập nhật</button>
  </form>;
}
