import { useCallback, useState } from 'react';
import { Link } from 'react-router';
import PageHeader from '../../../components/ui/PageHeader';
import { cartApi } from '../api/cartApi';
import useProductRequest from '../../products/hooks/useProductRequest';
import CommerceState from '../../orders/components/CommerceState';
import { money } from '../../orders/components/orderFormat';
import { useAuth } from '../../../hooks/useAuth';
import { useCart } from '../../../hooks/useCart';
import ShopBreadcrumb from '../../../components/ui/ShopBreadcrumb';
import PurchaseSteps from '../../../components/ui/PurchaseSteps';
import ComingSoonButton from '../../../components/ui/ComingSoonButton';
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

  return <>
    <ShopBreadcrumb items={[{ label: 'Cửa hàng', to: '/products' }, { label: 'Giỏ hàng' }]} />
    <PurchaseSteps />
    <div className="commerce-page-heading"><PageHeader title="Giỏ hàng của bạn" description="Kiểm tra sản phẩm và số lượng trước khi thanh toán." /></div>
    {actionError && <p role="alert" className="auth-alert">{actionError}</p>}
    {notice && <p role="status" className="commerce-notice">{notice}</p>}
    <CommerceState loading={isLoading} error={error} retry={retry} />
    {!isLoading && !error && (data?.items?.length ? <div className="cart-layout">
      <section className="panel cart-items-panel" aria-labelledby="cart-items-title">
        <div className="cart-panel-heading">
          <div><h2 id="cart-items-title">Sản phẩm</h2><p>{data.items.length} dòng sản phẩm trong giỏ</p></div>
          <Link to="/products">Tiếp tục mua sắm</Link>
        </div>
        {data.items.map((item) => <article className="commerce-cart-item" key={item.cartItemId}>
          <div className="cart-item-visual" aria-hidden="true">{item.productName?.slice(0, 1) || 'Đ'}</div>
          <div className="cart-item-main">
            <strong>{item.productName}</strong>
            <p className="cart-item-sku">SKU: {item.sku}</p>
            <p className="cart-item-stock">Khả dụng tại chi nhánh: {item.availableQuantity ?? '—'}</p>
          </div>
          <div className="cart-item-price"><span>Đơn giá</span><strong>{money(item.unitPrice)}</strong></div>
          <QuantityEditor key={`${item.cartItemId}:${item.quantity}`} item={item} disabled={busy}
            save={(quantity) => change(() => cartApi.quantity(item.cartItemId, quantity))} />
          <div className="cart-item-total"><span>Thành tiền</span><strong>{money(item.lineTotal)}</strong></div>
          <button className="cart-remove" disabled={busy} onClick={() => {
            if (window.confirm(`Xóa ${item.sku} khỏi giỏ?`)) change(() => cartApi.remove(item.cartItemId));
          }}>Xóa</button>
        </article>)}
      </section>

      <aside className="panel order-summary cart-summary" aria-labelledby="cart-summary-title">
        <h2 id="cart-summary-title">Tóm tắt đơn hàng</h2>
        <div className="summary-branch"><span>Chi nhánh xử lý</span><strong>{data.warehouseName || 'Chưa chọn chi nhánh'}</strong></div>
        <dl className="summary-lines">
          <div><dt>Tạm tính</dt><dd>{money(data.subtotal)}</dd></div>
          <div><dt>Giảm giá</dt><dd>{money(0)}</dd></div>
          <div><dt>Phí giao hàng</dt><dd>{money(0)}</dd></div>
        </dl>
        <div className="summary-total"><span>Tổng cộng</span><strong>{money(data.subtotal)}</strong></div>
        {busy ? <p role="status" className="muted">Đang cập nhật…</p> : data.warehouseId ?
          <Link className="button summary-primary-action" to="/checkout">Tiến hành thanh toán</Link> :
          <p className="auth-alert">Hãy chọn chi nhánh trước khi checkout.</p>}
        <p className="summary-assurance">Giá và tồn kho tại chi nhánh được xác nhận lại khi đặt hàng.</p>
        <div className="shop-coupon"><label htmlFor="cart-coupon">Mã ưu đãi · Sắp có</label><div><input id="cart-coupon" placeholder="Nhập mã ưu đãi" disabled /><ComingSoonButton>Áp dụng</ComingSoonButton></div></div>
      </aside>
    </div> : <section className="panel commerce-empty-state">
      <span aria-hidden="true">□</span><h2>Giỏ hàng trống.</h2><p>Khám phá sản phẩm và chọn phiên bản phù hợp với bạn.</p>
      <Link className="button" to="/products">Chọn sản phẩm</Link>
    </section>)}
  </>;
}

function QuantityEditor({ item, disabled, save }) {
  const [value, setValue] = useState(item.quantity);
  const max = item.availableQuantity ?? 2147483647;
  function setBounded(next) { setValue(Math.max(1, Math.min(max, next))); }
  return <form className="cart-quantity" onSubmit={(event) => { event.preventDefault(); save(Number(value)); }}>
    <span>Số lượng</span>
    <div className="quantity-stepper">
      <button type="button" aria-label={`Giảm số lượng ${item.sku}`} disabled={disabled || Number(value) <= 1}
        onClick={() => setBounded(Number(value) - 1)}>−</button>
      <label><span className="sr-only">Số lượng {item.sku}</span><input type="number" min="1" max={max} step="1" required
        value={value} disabled={disabled} onChange={(event) => setValue(event.target.value)} /></label>
      <button type="button" aria-label={`Tăng số lượng ${item.sku}`} disabled={disabled || Number(value) >= max}
        onClick={() => setBounded(Number(value) + 1)}>+</button>
    </div>
    <button className="cart-quantity-save" disabled={disabled || Number(value) === item.quantity}>Cập nhật</button>
  </form>;
}
