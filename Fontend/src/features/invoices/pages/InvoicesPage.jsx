import { useCallback } from 'react';
import { Link, useSearchParams } from 'react-router';
import PageHeader from '../../../components/ui/PageHeader';
import StatusBadge from '../../../components/ui/StatusBadge';
import useProductRequest from '../../products/hooks/useProductRequest';
import CommerceState from '../../orders/components/CommerceState';
import { date, money } from '../../orders/components/orderFormat';
import { invoiceApi } from '../api/invoiceApi';
import '../../orders/commerce.css';
import '../invoices.css';

const statusLabels = { ISSUED: 'Đã phát hành', CANCELLED: 'Đã hủy' };

export default function InvoicesPage() {
  const [params, setParams] = useSearchParams(); const n = Number(params.get('page') || 1);
  const page = Number.isSafeInteger(n) && n > 0 && (n - 1) * 20 <= 2147483647 ? n - 1 : 0;
  const keyword = (params.get('keyword') || '').trim();
  const load = useCallback((signal) => invoiceApi.list(page, keyword, signal), [page, keyword]);
  const { data, error, isLoading, retry } = useProductRequest(`invoices:${page}:${keyword}`, load);
  return <><PageHeader eyebrow="Vận hành / Bán hàng" title="Hóa đơn" description="Hóa đơn được lập tự động khi đơn hàng giao thành công." />
    <form className="panel invoice-filters" role="search" onSubmit={(e) => { e.preventDefault(); const value = String(new FormData(e.currentTarget).get('keyword') || '').trim(); setParams(value ? { keyword: value } : {}); }}>
      <label>Số hóa đơn<input name="keyword" type="search" defaultValue={keyword} placeholder="HD-20261106-0001" /></label><button className="button">Tìm</button></form>
    <CommerceState loading={isLoading} error={error} retry={retry} />
    {!isLoading && !error && (data?.content?.length ? <section className="panel commerce-section"><div className="commerce-scroll"><table className="product-table">
      <thead><tr><th>Số hóa đơn</th><th>Ngày lập</th><th>Đơn hàng</th><th>Khách hàng</th><th>Tổng tiền</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
      <tbody>{data.content.map((row) => <tr key={row.invoiceId}><th scope="row">{row.invoiceNumber}</th><td>{date(row.issuedDate)}</td>
        <td><Link to={`/orders/${row.orderId}`}>{row.orderCode}</Link></td><td>{row.customerName}</td><td>{money(row.totalAmount)}</td>
        <td><StatusBadge status={row.status === 'ISSUED' ? 'ACTIVE' : 'CANCELLED'}>{statusLabels[row.status]}</StatusBadge></td>
        <td><Link className="button button-quiet" to={`/invoices/${row.invoiceId}`}>Xem / In</Link></td></tr>)}</tbody></table></div>
      <nav className="commerce-inline" aria-label="Phân trang"><button className="button button-quiet" disabled={page === 0} onClick={() => setParams({ ...(keyword ? { keyword } : {}), page })}>Trang trước</button><span>{page + 1}/{data.totalPages}</span><button className="button button-quiet" disabled={page + 1 >= data.totalPages} onClick={() => setParams({ ...(keyword ? { keyword } : {}), page: page + 2 })}>Trang sau</button></nav>
    </section> : <section className="panel commerce-section"><p>Chưa có hóa đơn phù hợp.</p></section>)}
  </>;
}
