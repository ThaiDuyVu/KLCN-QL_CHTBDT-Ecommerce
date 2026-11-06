import { useCallback } from 'react';
import { Link, useSearchParams } from 'react-router';
import PageHeader from '../../../components/ui/PageHeader';
import StatusBadge from '../../../components/ui/StatusBadge';
import useProductRequest from '../../products/hooks/useProductRequest';
import { orderApi } from '../api/orderApi';
import CommerceState from '../components/CommerceState';
import { date, money, shipmentLabels } from '../components/orderFormat';
import '../commerce.css';

/** Orders handed to the signed-in delivery employee, newest hand-over first. */
export default function DeliveriesPage() {
  const [params, setParams] = useSearchParams(); const n = Number(params.get('page') || 1);
  const page = Number.isSafeInteger(n) && n > 0 && (n - 1) * 20 <= 2147483647 ? n - 1 : 0;
  const load = useCallback((signal) => orderApi.deliveries(page, signal), [page]);
  const { data, error, isLoading, retry } = useProductRequest(`deliveries:${page}`, load);
  return <><PageHeader eyebrow="Vận hành / Giao hàng" title="Đơn tôi cần giao" description="Các đơn được bàn giao cho bạn. Mở đơn để xác nhận đã giao hoặc ghi nhận giao không thành công." />
    <CommerceState loading={isLoading} error={error} retry={retry} />
    {!isLoading && !error && (data?.content?.length ? <section className="panel commerce-section">
      <div className="commerce-scroll"><table className="product-table"><thead><tr><th>Mã đơn</th><th>Người nhận</th><th>Địa chỉ</th><th>Thu hộ</th><th>Mã vận đơn</th><th>Ngày gửi</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>
        {data.content.map((order) => <tr key={order.orderId}>
          <th scope="row">{order.orderCode}</th><td>{order.recipientName}<br /><small>{order.recipientPhone}</small></td><td>{order.shippingAddress}</td>
          <td>{money(order.totalAmount)}</td><td>{order.shipment?.trackingNumber}</td><td>{date(order.shipment?.shippedDate)}</td>
          <td><StatusBadge status={order.shipment?.status === 'SHIPPING' ? 'SHIPPED' : order.shipment?.status}>{shipmentLabels[order.shipment?.status]}</StatusBadge></td>
          <td><Link className="button button-quiet" to={`/orders/${order.orderId}`}>Chi tiết</Link></td></tr>)}
      </tbody></table></div>
      <nav className="commerce-inline" aria-label="Phân trang"><button className="button button-quiet" disabled={page === 0} onClick={() => setParams({ page })}>Trang trước</button><span>{page + 1}/{data.totalPages}</span><button className="button button-quiet" disabled={page + 1 >= data.totalPages} onClick={() => setParams({ page: page + 2 })}>Trang sau</button></nav>
    </section> : <section className="panel commerce-section"><p>Chưa có đơn nào được bàn giao cho bạn.</p></section>)}
  </>;
}
