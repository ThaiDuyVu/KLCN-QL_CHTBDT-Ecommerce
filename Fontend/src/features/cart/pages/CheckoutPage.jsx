import { useCallback, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { cartApi } from '../api/cartApi';
import { orderApi } from '../../orders/api/orderApi';
import useProductRequest from '../../products/hooks/useProductRequest';
import CommerceState from '../../orders/components/CommerceState';
import { money } from '../../orders/components/orderFormat';
import PageHeader from '../../../components/ui/PageHeader';
import { useAuth } from '../../../hooks/useAuth';
import { useCart } from '../../../hooks/useCart';
import '../../orders/commerce.css';
export default function CheckoutPage() {
  const navigate = useNavigate(); const { user, invalidateSession } = useAuth();
  const { clearCart } = useCart();
  const load = useCallback((signal) => cartApi.get(signal), []);
  const { data, error, isLoading, retry } = useProductRequest('checkout-cart', load);
  const [busy, setBusy] = useState(false); const [submitError, setSubmitError] = useState('');
  async function checkout(event) {
    event.preventDefault(); if (busy) return;
    const form = new FormData(event.currentTarget); setBusy(true); setSubmitError('');
    try {
      const order = await orderApi.checkout({ recipientName: form.get('recipientName').trim(), recipientPhone: form.get('recipientPhone').trim(), shippingAddress: form.get('shippingAddress').trim(), note: form.get('note').trim() || null, paymentMethod: form.get('paymentMethod') });
      clearCart();
      navigate(`/my-orders/${order.orderId}`, { replace: true, state: { checkoutComplete: true } });
    } catch (e) { if (e.status === 401) invalidateSession(); setSubmitError(e.message); }
    finally { setBusy(false); }
  }
  return <><PageHeader title="Checkout" description="Thanh toán khi nhận hàng (COD)." /><Link to="/cart">← Giỏ hàng</Link>
    <CommerceState loading={isLoading} error={error} retry={retry} />
    {!isLoading && !error && (data?.items?.length ? <div className="commerce-grid">
      <form className="panel commerce-section commerce-form" onSubmit={checkout}>
        {submitError && <p className="auth-alert" role="alert">{submitError}</p>}
        <fieldset disabled={busy}>
          <label>Tên người nhận<input name="recipientName" required maxLength={255} defaultValue={user?.displayName || ''} /></label>
          <label>Số điện thoại<input name="recipientPhone" type="tel" required maxLength={30} /></label>
          <label>Địa chỉ giao hàng<textarea name="shippingAddress" required rows={3} /></label>
          <label>Ghi chú<textarea name="note" rows={2} /></label>
          <label>Phương thức thanh toán<select name="paymentMethod" defaultValue="COD"><option value="COD">COD · Thanh toán khi nhận hàng</option><option value="VNPAY" disabled>VNPAY · Coming soon</option><option value="INSTALLMENT" disabled>Trả góp · Coming soon</option></select></label>
        </fieldset>
        <button className="button" disabled={busy}>{busy ? 'Đang tạo đơn…' : 'Đặt hàng COD'}</button>
        <p className="muted">Nếu request bị gián đoạn, kiểm tra <Link to="/my-orders">đơn của tôi</Link> trước khi đặt lại.</p>
      </form>
      <section className="panel commerce-section"><h2>Đơn hàng</h2>{data.items.map((item) => <p key={item.cartItemId}>{item.productName} · {item.sku} × {item.quantity}: {money(item.lineTotal)}</p>)}
        <p>Giảm giá: {money(0)} · Phí giao hàng: {money(0)}</p><strong>Tổng dự kiến: {money(data.subtotal)}</strong><p>Backend xác nhận giá và stock khi đặt hàng.</p>
      </section>
    </div> : <section className="panel commerce-section"><p>Giỏ hàng trống, không thể checkout.</p><Link to="/products">Chọn sản phẩm</Link><p><Link to="/my-orders">Kiểm tra đơn đã tạo</Link></p></section>)}
  </>;
}
