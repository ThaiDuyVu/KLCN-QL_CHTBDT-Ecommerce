import { useCallback } from 'react';
import { Link, useSearchParams } from 'react-router';
import PageHeader from '../../../components/ui/PageHeader';
import StatusBadge from '../../../components/ui/StatusBadge';
import useProductRequest from '../../products/hooks/useProductRequest';
import CommerceState from '../../orders/components/CommerceState';
import { date, money } from '../../orders/components/orderFormat';
import { returnApi } from '../api/returnApi';
import { returnStatusLabels } from '../returnFormat';
import '../../orders/commerce.css';
import '../returns.css';

export default function ReturnsPage({ customer = false }) {
  const [params, setParams] = useSearchParams(); const n = Number(params.get('page') || 1);
  const page = Number.isSafeInteger(n) && n > 0 && (n - 1) * 20 <= 2147483647 ? n - 1 : 0;
  const status = Object.hasOwn(returnStatusLabels, params.get('status')) ? params.get('status') : '';
  const load = useCallback((signal) => (customer ? returnApi.mine(page, signal) : returnApi.list(page, status, signal)), [customer, page, status]);
  const { data, error, isLoading, retry } = useProductRequest(`returns:${customer}:${page}:${status}`, load);
  const base = customer ? '/my-returns' : '/returns';
  return <><PageHeader eyebrow={customer ? 'Tài khoản / Mua hàng' : 'Vận hành / Bán hàng'} title={customer ? 'Đổi trả của tôi' : 'Quản lý đổi trả'}
    description={customer ? 'Theo dõi các yêu cầu đổi trả đã gửi.' : 'Duyệt yêu cầu, nhận lại hàng và xác nhận hoàn tiền.'} />
    {!customer && <form className="panel return-filters" aria-label="Lọc đổi trả" onSubmit={(e) => { e.preventDefault(); const value = new FormData(e.currentTarget).get('status'); setParams(value ? { status: value } : {}); }}>
      <label>Trạng thái<select name="status" defaultValue={status}><option value="">Tất cả</option>{Object.entries(returnStatusLabels).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label>
      <button className="button">Lọc</button></form>}
    <CommerceState loading={isLoading} error={error} retry={retry} />
    {!isLoading && !error && (data?.content?.length ? <section className="panel commerce-section"><div className="commerce-scroll"><table className="product-table">
      <thead><tr><th>Mã đổi trả</th><th>Đơn hàng</th>{!customer && <th>Khách hàng</th>}<th>Ngày gửi</th><th>Tiền hoàn</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
      <tbody>{data.content.map((row) => <tr key={row.returnId}><th scope="row">{row.returnCode}</th><td>{row.orderCode}</td>{!customer && <td>{row.customerName}</td>}
        <td>{date(row.requestDate)}</td><td>{money(row.refundAmount)}</td><td><StatusBadge status={row.status}>{returnStatusLabels[row.status]}</StatusBadge></td>
        <td><Link className="button button-quiet" to={`${base}/${row.returnId}`}>Chi tiết</Link></td></tr>)}</tbody></table></div>
      <nav className="commerce-inline" aria-label="Phân trang"><button className="button button-quiet" disabled={page === 0} onClick={() => setParams({ ...(status ? { status } : {}), page })}>Trang trước</button><span>{page + 1}/{data.totalPages}</span><button className="button button-quiet" disabled={page + 1 >= data.totalPages} onClick={() => setParams({ ...(status ? { status } : {}), page: page + 2 })}>Trang sau</button></nav>
    </section> : <section className="panel commerce-section"><p>{customer ? 'Bạn chưa gửi yêu cầu đổi trả nào. Có thể gửi yêu cầu từ trang chi tiết đơn hàng đã giao.' : 'Không có yêu cầu đổi trả phù hợp.'}</p></section>)}
  </>;
}
