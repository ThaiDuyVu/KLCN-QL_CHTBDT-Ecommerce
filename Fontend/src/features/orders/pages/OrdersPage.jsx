import { useCallback } from 'react';
import { Link, useSearchParams } from 'react-router';
import PageHeader from '../../../components/ui/PageHeader';
import { orderApi } from '../api/orderApi';
import useProductRequest from '../../products/hooks/useProductRequest';
import CommerceState from '../components/CommerceState';
import StatusBadge from '../../../components/ui/StatusBadge';
import { statusLabels, money, date } from '../components/orderFormat';
import ShopBreadcrumb from '../../../components/ui/ShopBreadcrumb';
import '../commerce.css';
export default function OrdersPage({ customer = false }) {
  const [params, setParams] = useSearchParams(); const n = Number(params.get('page') || 1);
  const page = Number.isSafeInteger(n) && n > 0 && (n - 1) * 20 <= 2147483647 ? n - 1 : 0;
  const load = useCallback((signal) => orderApi.list(customer, page, signal), [customer, page]);
  const { data, error, isLoading, retry } = useProductRequest(`${customer}:${page}`, load);
  const base = customer ? '/my-orders' : '/orders';
  return <>{customer && <ShopBreadcrumb items={[{ label: 'Đơn hàng của tôi' }]} />}<PageHeader eyebrow={customer ? 'Tài khoản / Mua hàng' : 'Vận hành / Bán hàng'} title={customer ? 'Đơn hàng của tôi' : 'Quản lý đơn hàng'} description={customer ? 'Theo dõi đơn hàng và chi nhánh xử lý.' : 'Theo dõi tiến độ và xử lý các đơn hàng hiện có.'} /><CommerceState loading={isLoading} error={error} retry={retry} />
    {!isLoading && !error && (data?.content?.length ? customer ? <section className="customer-orders-section">
      <div className="customer-orders-heading"><strong>{data.totalElements} đơn hàng</strong><span>Trang {data.page + 1}/{data.totalPages}</span></div>
      <div className="customer-orders-grid">{data.content.map((order) => <article className="customer-order-card" key={order.orderId}>
        <div className="customer-order-top"><span>Đơn hàng <strong>{order.orderCode}</strong></span><StatusBadge status={order.status}>{statusLabels[order.status]}</StatusBadge></div>
        <div className="customer-order-body"><div><small>Ngày đặt</small><strong>{date(order.orderDate)}</strong></div><div><small>Chi nhánh</small><strong>{order.warehouseName || '—'}</strong></div><div><small>Người nhận</small><strong>{order.recipientName}</strong></div></div>
        <div className="customer-order-footer"><div><span>Tổng thanh toán</span><strong>{money(order.totalAmount)}</strong></div><Link className="button" to={`${base}/${order.orderId}`}>Xem đơn hàng →</Link></div>
      </article>)}</div>
      <nav className="commerce-inline" aria-label="Phân trang đơn hàng"><button className="button button-quiet" disabled={page === 0} onClick={() => setParams({ page: page })}>Trang trước</button><span>{page + 1}/{data.totalPages}</span><button className="button button-quiet" disabled={page + 1 >= data.totalPages} onClick={() => setParams({ page: page + 2 })}>Trang sau</button></nav>
    </section> : <section className="panel commerce-section">
      <div className="data-section-heading"><div><h2>Danh sách đơn hàng</h2><p>{data.totalElements} đơn hàng · Trang {data.page + 1}/{data.totalPages}</p></div></div>
      <div className="commerce-scroll"><table className="product-table"><thead><tr><th>Mã đơn</th><th>Ngày đặt</th><th>Chi nhánh</th><th>Người nhận</th><th>Trạng thái</th><th>Tổng tiền</th><th>Thao tác</th></tr></thead><tbody>
        {data.content.map((order) => <tr key={order.orderId}><th scope="row">{order.orderCode}</th><td>{date(order.orderDate)}</td><td>{order.warehouseName || '—'}</td><td>{order.recipientName}</td><td><StatusBadge status={order.status}>{statusLabels[order.status]}</StatusBadge></td><td><strong>{money(order.totalAmount)}</strong></td><td><Link className="button button-quiet" to={`${base}/${order.orderId}`}>Chi tiết</Link></td></tr>)}
      </tbody></table></div>
      <nav className="commerce-inline" aria-label="Phân trang đơn hàng"><button className="button button-quiet" disabled={page === 0} onClick={() => setParams({ page: page })}>Trang trước</button><span>{page + 1}/{data.totalPages}</span><button className="button button-quiet" disabled={page + 1 >= data.totalPages} onClick={() => setParams({ page: page + 2 })}>Trang sau</button></nav>
    </section> : <section className="panel commerce-section"><p>Chưa có đơn hàng.</p>{page > 0 && <button className="button" onClick={() => setParams({})}>Về trang đầu</button>}{customer && <Link to="/products">Chọn sản phẩm</Link>}</section>)}
  </>;
}
