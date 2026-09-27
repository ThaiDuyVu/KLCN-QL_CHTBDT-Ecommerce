import { useCallback, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { cartApi } from '../api/cartApi';
import { orderApi } from '../../orders/api/orderApi';
import { installmentApi } from '../../orders/api/installmentApi';
import { vnpayApi } from '../../orders/api/vnpayApi';
import { redirectToVnpay } from '../../orders/components/vnpayFormat';
import useProductRequest from '../../products/hooks/useProductRequest';
import CommerceState from '../../orders/components/CommerceState';
import { money } from '../../orders/components/orderFormat';
import PageHeader from '../../../components/ui/PageHeader';
import { useAuth } from '../../../hooks/useAuth';
import { useCart } from '../../../hooks/useCart';
import ShopBreadcrumb from '../../../components/ui/ShopBreadcrumb';
import PurchaseSteps from '../../../components/ui/PurchaseSteps';
import '../../orders/commerce.css';

export default function CheckoutPage() {
  const navigate = useNavigate();
  const { user, invalidateSession } = useAuth();
  const { clearCart } = useCart();
  const load = useCallback((signal) => cartApi.get(signal), []);
  const { data, error, isLoading, retry } = useProductRequest('checkout-cart', load);
  const [busy, setBusy] = useState(false);
  const [submitError, setSubmitError] = useState('');
  const [paymentMethod, setPaymentMethod] = useState('COD');
  const [downPayment, setDownPayment] = useState('0');
  const loadVnpay = useCallback((signal) => vnpayApi.config(signal), []);
  const { data: vnpayConfig, error: vnpayError, isLoading: vnpayLoading, retry: retryVnpay } = useProductRequest('checkout-vnpay-config', loadVnpay);
  const loadProviders = useCallback((signal) => paymentMethod === 'INSTALLMENT' ? installmentApi.activeProviders(signal) : Promise.resolve([]), [paymentMethod]);
  const { data: providers, error: providerError, isLoading: providersLoading, retry: retryProviders } = useProductRequest(`checkout-installment-providers:${paymentMethod}`, loadProviders);

  async function checkout(event) {
    event.preventDefault(); if (busy || (paymentMethod === 'VNPAY' && !vnpayConfig?.enabled) || (paymentMethod === 'INSTALLMENT' && (providersLoading || providerError || !providers?.length))) return;
    const form = new FormData(event.currentTarget); setBusy(true); setSubmitError('');
    try {
      const method = form.get('paymentMethod');
      const installment = method === 'INSTALLMENT'
        ? { providerId: form.get('providerId'), termMonths: Number(form.get('termMonths')), downPayment: Number(form.get('downPayment')) }
        : {};
      const order = await orderApi.checkout({ recipientName: form.get('recipientName').trim(), recipientPhone: form.get('recipientPhone').trim(), shippingAddress: form.get('shippingAddress').trim(), note: form.get('note').trim() || null, paymentMethod: method, ...installment });
      clearCart();
      navigate(`/my-orders/${order.orderId}`, { replace: true, state: { checkoutComplete: true, paymentMethod: method } });
      if (method === 'VNPAY') {
        try { redirectToVnpay(order.payment?.paymentUrl); }
        catch (redirectError) { navigate(`/my-orders/${order.orderId}`, { replace: true, state: { paymentRedirectError: redirectError.message } }); }
      }
    } catch (e) { if (e.status === 401) invalidateSession(); setSubmitError(e.message); }
    finally { setBusy(false); }
  }

  return <>
    <ShopBreadcrumb items={[{ label: 'Giỏ hàng', to: '/cart' }, { label: 'Thanh toán' }]} />
    <PurchaseSteps checkout />
    <Link className="commerce-back-link" to="/cart">← Quay lại giỏ hàng</Link>
    <div className="commerce-page-heading"><PageHeader title="Thanh toán" description="Hoàn tất thông tin nhận hàng và chọn phương thức thanh toán." /></div>
    <CommerceState loading={isLoading} error={error} retry={retry} />
    {!isLoading && !error && (data?.items?.length ? <div className="checkout-layout">
      <form className="checkout-form" onSubmit={checkout}>
        {submitError && <p className="auth-alert" role="alert">{submitError}</p>}
        <fieldset className="panel checkout-section" disabled={busy}>
          <legend className="sr-only">Thông tin giao hàng</legend>
          <div className="checkout-section-heading"><span>1</span><div><h2>Thông tin giao hàng</h2><p>Thông tin người nhận đơn hàng</p></div></div>
          <div className="checkout-fields two-columns">
            <label>Tên người nhận<input name="recipientName" required maxLength={255} autoComplete="name" defaultValue={user?.displayName || ''} /></label>
            <label>Số điện thoại<input name="recipientPhone" type="tel" required maxLength={30} autoComplete="tel" placeholder="Ví dụ: 0901234567" /></label>
          </div>
          <div className="checkout-fields">
            <label>Địa chỉ giao hàng<textarea name="shippingAddress" required rows={3} autoComplete="street-address" placeholder="Số nhà, đường, phường/xã, quận/huyện, tỉnh/thành" /></label>
            <label>Ghi chú <span>(không bắt buộc)</span><textarea name="note" rows={2} placeholder="Ghi chú cho người giao hàng" /></label>
          </div>
        </fieldset>

        <fieldset className="panel checkout-section" disabled={busy}>
          <legend className="sr-only">Phương thức thanh toán</legend>
          <div className="checkout-section-heading"><span>2</span><div><h2>Phương thức thanh toán</h2><p>Chọn cách thanh toán phù hợp</p></div></div>
          <label className="payment-method-card">
            <span className="payment-method-icon" aria-hidden="true">{paymentMethod === 'COD' ? 'COD' : paymentMethod === 'VNPAY' ? 'VN' : 'TG'}</span>
            <span><strong>{paymentMethod === 'COD' ? 'Thanh toán khi nhận hàng' : paymentMethod === 'VNPAY' ? 'VNPAY Sandbox' : 'Trả góp nội bộ'}</strong><small>{paymentMethod === 'COD' ? 'Thanh toán trực tiếp khi đơn được giao đến bạn' : paymentMethod === 'VNPAY' ? 'Bạn sẽ được chuyển đến cổng thanh toán thử nghiệm' : 'Đơn chỉ được xác nhận sau khi hồ sơ được duyệt'}</small></span>
            <select name="paymentMethod" value={paymentMethod} onChange={(event) => setPaymentMethod(event.target.value)} aria-label="Phương thức thanh toán">
              <option value="COD">COD · Thanh toán khi nhận hàng</option>
              <option value="VNPAY" disabled={vnpayLoading || !vnpayConfig?.enabled}>VNPAY · Sandbox{vnpayLoading ? ' · Đang kiểm tra' : !vnpayConfig?.enabled ? ' · Chưa bật' : ''}</option>
              <option value="INSTALLMENT">Trả góp nội bộ</option>
            </select>
          </label>
          {vnpayError && <p className="auth-alert" role="alert">Chưa kiểm tra được cấu hình VNPAY. Bạn vẫn có thể chọn COD hoặc trả góp. <button className="button button-quiet" type="button" onClick={retryVnpay}>Thử lại</button></p>}
          {paymentMethod === 'VNPAY' && <p className="muted payment-sandbox-note">Đây là môi trường thử nghiệm, không dùng tài khoản/thẻ thật. Đơn được giữ hàng khi tạo; thanh toán thành công không tự xác nhận đơn.</p>}
          {paymentMethod === 'INSTALLMENT' && <InstallmentFields totalAmount={data.subtotal} downPayment={downPayment} setDownPayment={setDownPayment} providers={providers} error={providerError} isLoading={providersLoading} retry={retryProviders} />}
        </fieldset>

        <button className="button checkout-submit" disabled={busy || !data.warehouseId || (paymentMethod === 'INSTALLMENT' && (providersLoading || providerError || !providers?.length)) || (paymentMethod === 'VNPAY' && !vnpayConfig?.enabled)}>{busy ? 'Đang tạo đơn…' : paymentMethod === 'COD' ? 'Đặt hàng COD' : paymentMethod === 'VNPAY' ? 'Đặt hàng và thanh toán VNPAY' : 'Gửi hồ sơ trả góp'}</button>
        <p className="checkout-recovery">Nếu request bị gián đoạn, hãy kiểm tra <Link to="/my-orders">đơn của tôi</Link> trước khi đặt lại.</p>
      </form>

      <aside className="panel order-summary checkout-summary" aria-labelledby="checkout-summary-title">
        <h2 id="checkout-summary-title">Đơn hàng của bạn</h2>
        <div className="checkout-summary-items">
          {data.items.map((item) => <article key={item.cartItemId}>
            <div className="summary-item-visual" aria-hidden="true">{item.productName?.slice(0, 1) || 'Đ'}</div>
            <div><strong>{item.productName}</strong><span>{item.sku} · SL {item.quantity}</span></div>
            <b>{money(item.lineTotal)}</b>
          </article>)}
        </div>
        <div className="summary-branch"><span>Chi nhánh xử lý</span><strong>{data.warehouseName || 'Chưa chọn chi nhánh'}</strong></div>
        <dl className="summary-lines">
          <div><dt>Tạm tính</dt><dd>{money(data.subtotal)}</dd></div>
          <div><dt>Giảm giá</dt><dd>{money(0)}</dd></div>
          <div><dt>Phí giao hàng</dt><dd>{money(0)}</dd></div>
        </dl>
        <div className="summary-total"><span>Tổng thanh toán</span><strong>{money(data.subtotal)}</strong></div>
        <p className="summary-assurance">Giá và tồn kho tại chi nhánh được xác nhận lại khi bạn đặt hàng.</p>
      </aside>
    </div> : <section className="panel commerce-empty-state">
      <span aria-hidden="true">□</span><h2>Giỏ hàng trống, không thể checkout.</h2><p>Hãy thêm sản phẩm trước khi tiếp tục.</p>
      <Link className="button" to="/products">Chọn sản phẩm</Link><Link to="/my-orders">Kiểm tra đơn đã tạo</Link>
    </section>)}
  </>;
}

function InstallmentFields({ totalAmount, downPayment, setDownPayment, providers, error, isLoading, retry }) {
  const remaining = Math.max(0, Number(totalAmount) - Number(downPayment || 0));

  return <div className="installment-checkout-fields">
    <p className="muted">Hồ sơ sẽ được quản lý cửa hàng tiếp nhận và xét duyệt trong hệ thống.</p>
    {isLoading && <p role="status">Đang tải đơn vị trả góp…</p>}
    {error && <div className="auth-alert" role="alert">{error} <button className="button button-quiet" type="button" onClick={retry}>Thử lại</button></div>}
    {!isLoading && !error && <div className="checkout-fields two-columns">
      <label>Đơn vị trả góp<select name="providerId" required defaultValue=""><option value="" disabled>Chọn đơn vị</option>{providers?.map((provider) => <option key={provider.providerId} value={provider.providerId}>{provider.providerName}</option>)}</select></label>
      <label>Kỳ hạn (tháng)<input name="termMonths" type="number" min="1" step="1" required placeholder="Ví dụ: 12" /></label>
      <label>Tiền trả trước<input name="downPayment" type="number" min="0" max={totalAmount} step="0.01" required value={downPayment} onChange={(event) => setDownPayment(event.target.value)} /></label>
      <div className="installment-remaining"><span>Số tiền còn lại</span><strong>{money(remaining)}</strong></div>
    </div>}
    {!isLoading && !error && !providers?.length && <p className="auth-alert" role="alert">Chưa có đơn vị trả góp đang hoạt động. Vui lòng liên hệ cửa hàng.</p>}
  </div>;
}
