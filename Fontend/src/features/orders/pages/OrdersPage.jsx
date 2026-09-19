import { useCallback } from 'react';
import { Link, useSearchParams } from 'react-router';
import PageHeader from '../../../components/ui/PageHeader';
import { orderApi } from '../api/orderApi';
import useProductRequest from '../../products/hooks/useProductRequest';
import CommerceState from '../components/CommerceState';
import { statusLabels, money, date } from '../components/orderFormat';
import '../commerce.css';
export default function OrdersPage({ customer = false }) {
  const [params, setParams] = useSearchParams(); const n = Number(params.get('page') || 1);
  const page = Number.isSafeInteger(n) && n > 0 && (n - 1) * 20 <= 2147483647 ? n - 1 : 0;
  const load = useCallback((signal) => orderApi.list(customer, page, signal), [customer, page]);
  const { data, error, isLoading, retry } = useProductRequest(`${customer}:${page}`, load);
  const base = customer ? '/my-orders' : '/orders';
  return <><PageHeader title={customer ? 'Đơn hàng của tôi' : 'Quản lý đơn hàng'} /><CommerceState loading={isLoading} error={error} retry={retry} />
    {!isLoading && !error && (data?.content?.length ? <section className="panel commerce-section">
      <p>{data.totalElements} đơn hàng · Trang {data.page + 1}/{data.totalPages}</p>
      <div className="commerce-scroll"><table className="product-table"><thead><tr><th>Mã đơn</th><th>Ngày đặt</th><th>Người nhận</th><th>Trạng thái</th><th>Tổng tiền</th><th>Thao tác</th></tr></thead><tbody>
        {data.content.map((order) => <tr key={order.orderId}><th scope="row">{order.orderCode}</th><td>{date(order.orderDate)}</td><td>{order.recipientName}</td><td>{statusLabels[order.status]}</td><td>{money(order.totalAmount)}</td><td><Link className="button button-quiet" to={`${base}/${order.orderId}`}>Chi tiết</Link></td></tr>)}
      </tbody></table></div>
      <nav className="commerce-inline" aria-label="Phân trang đơn hàng"><button className="button button-quiet" disabled={page === 0} onClick={() => setParams({ page: page })}>Trang trước</button><span>{page + 1}/{data.totalPages}</span><button className="button button-quiet" disabled={page + 1 >= data.totalPages} onClick={() => setParams({ page: page + 2 })}>Trang sau</button></nav>
    </section> : <section className="panel commerce-section"><p>Chưa có đơn hàng.</p>{page > 0 && <button className="button" onClick={() => setParams({})}>Về trang đầu</button>}{customer && <Link to="/products">Chọn sản phẩm</Link>}</section>)}
  </>;
}
