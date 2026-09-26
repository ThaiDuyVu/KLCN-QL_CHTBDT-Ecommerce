import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import PageHeader from '../../../components/ui/PageHeader';
import ShopBreadcrumb from '../../../components/ui/ShopBreadcrumb';
import { useAuth } from '../../../hooks/useAuth';
import { orderApi } from '../api/orderApi';
import { vnpayApi } from '../api/vnpayApi';
import { money } from '../components/orderFormat';
import { vnpayResponseMessages } from '../components/vnpayFormat';
import '../commerce.css';

export default function VnpayResultPage() {
  const [params] = useSearchParams();
  const orderId = params.get('orderId') || '';
  const valid = /^[a-f\d]{8}-[a-f\d]{4}-[a-f\d]{4}-[a-f\d]{4}-[a-f\d]{12}$/i.test(orderId) && params.get('gatewayResult') !== 'INVALID';
  const { invalidateSession } = useAuth();
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState(null);
  const key = `${orderId}:${attempt}`;
  const current = result?.key === key ? result : null;

  useEffect(() => {
    if (!valid) return;
    const controller = new AbortController(); let timer; let count = 0;
    async function check() {
      try {
        let syncError = null;
        // The backend return endpoint already queried VNPAY. Query again only on an explicit retry.
        if (count === 0 && attempt > 0) {
          try { await vnpayApi.synchronize(orderId, controller.signal); }
          catch (error) {
            if (controller.signal.aborted) return;
            if (error.status === 401) throw error;
            syncError = error.message;
          }
        }
        const order = await orderApi.detail(true, orderId, controller.signal);
        if (controller.signal.aborted) return;
        count += 1;
        setResult((previous) => ({ key, order, error: null, syncError: syncError || (previous?.key === key ? previous.syncError : null), checking: order.payment?.status === 'PENDING' && count < 10 }));
        if (order.payment?.status === 'PENDING' && count < 10) timer = window.setTimeout(check, 3000);
      } catch (error) {
        if (controller.signal.aborted) return;
        if (error.status === 401) invalidateSession();
        setResult({ key, order: null, error: error.message, checking: false });
      }
    }
    check();
    return () => { controller.abort(); window.clearTimeout(timer); };
  }, [valid, orderId, key, attempt, invalidateSession]);

  const paid = current?.order?.payment?.status === 'PAID';
  const failed = current?.order?.payment?.status === 'FAILED';
  // Query parameters are display hints only. Payment truth always comes from the owned Order API.
  return <>
    <ShopBreadcrumb items={[{ label: 'Đơn hàng của tôi', to: '/my-orders' }, { label: 'Kết quả VNPAY' }]} />
    <PageHeader title="Kết quả thanh toán VNPAY" description="Kết quả được đối chiếu với thanh toán của đơn hàng trong hệ thống." />
    <section className="panel vnpay-result-panel">
      {!valid ? <><h2>Không xác minh được đường dẫn thanh toán</h2><p>Vui lòng kiểm tra đơn hàng của bạn thay vì dùng kết quả trên đường dẫn này.</p></> : !current ? <p role="status">Đang kiểm tra kết quả thanh toán…</p> : current.error ? <p role="alert" className="auth-alert">{current.error}</p> : <>
        <span className={`vnpay-result-icon ${paid ? 'is-paid' : ''}`} aria-hidden="true">{paid ? '✓' : failed ? '×' : '…'}</span>
        <h2>{paid ? 'Thanh toán đã được xác nhận' : failed ? 'Thanh toán chưa thành công' : 'Đang chờ xác nhận thanh toán'}</h2>
        <p>{paid ? 'Thanh toán đã được ghi nhận. Cửa hàng sẽ tiếp tục xử lý đơn theo tiến độ hiện tại.' : failed ? 'Đơn chưa được thanh toán. Bạn có thể hủy đơn trước khi đặt lại.' : 'Hệ thống đang chờ kết quả xác nhận từ VNPAY. Không đặt lại hoặc thanh toán lại nếu giao dịch đã trừ tiền.'}</p>
        {!paid && vnpayResponseMessages[params.get('responseCode')] && <p className="muted">Thông báo từ cổng thanh toán: {vnpayResponseMessages[params.get('responseCode')]}</p>}
        <dl><div><dt>Đơn hàng</dt><dd>{current.order.orderCode}</dd></div><div><dt>Số tiền</dt><dd>{money(current.order.payment?.amount)}</dd></div><div><dt>Chi nhánh</dt><dd>{current.order.warehouseName || '—'}</dd></div></dl>
        {paid && current.order.status === 'CANCELLED' && <p className="auth-alert" role="alert">Đơn đã hủy trước khi nhận xác nhận thanh toán. Vui lòng liên hệ cửa hàng để đối soát/hoàn tiền.</p>}
        {current.checking && <p role="status" className="muted">Đang tự động kiểm tra xác nhận từ VNPAY…</p>}
        {!paid && current.syncError && <p role="alert" className="auth-alert">{current.syncError}</p>}
      </>}
      <div className="commerce-inline">
        {valid && <Link className="button" to={`/my-orders/${orderId}`}>Xem chi tiết đơn hàng</Link>}
        {valid && !paid && !current?.checking && <button className="button button-quiet" onClick={() => setAttempt((value) => value + 1)}>Kiểm tra lại</button>}
        <Link className="button button-quiet" to="/my-orders">Đơn hàng của tôi</Link>
      </div>
    </section>
  </>;
}
