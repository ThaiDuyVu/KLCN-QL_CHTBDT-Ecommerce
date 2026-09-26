import { useCallback } from 'react';
import { Link, useSearchParams } from 'react-router';
import PageHeader from '../../../components/ui/PageHeader';
import StatusBadge from '../../../components/ui/StatusBadge';
import useProductRequest from '../../products/hooks/useProductRequest';
import CommerceState from '../components/CommerceState';
import { money } from '../components/orderFormat';
import { installmentApi } from '../api/installmentApi';
import '../installment.css';

const labels = { PENDING: 'Chờ tiếp nhận', PROCESSING: 'Đang xét duyệt', APPROVED: 'Đã duyệt', REJECTED: 'Đã từ chối' };

export default function InstallmentsPage() {
  const [params, setParams] = useSearchParams();
  const page = Math.max(0, Number(params.get('page') || 0));
  const status = Object.hasOwn(labels, params.get('status')) ? params.get('status') : '';
  const load = useCallback((signal) => installmentApi.list({ page, status }, signal), [page, status]);
  const { data, error, isLoading, retry } = useProductRequest(`installments:${page}:${status}`, load);

  return <>
    <PageHeader eyebrow="Vận hành / Thanh toán" title="Hồ sơ trả góp"
      description="MANAGER tiếp nhận và xét duyệt hồ sơ trước khi đơn hàng được xác nhận." />
    <div className="installment-page-actions"><Link className="button button-quiet" to="/installments/providers">Đơn vị trả góp</Link></div>
    <form className="panel installment-filters" onSubmit={(event) => { event.preventDefault(); const next = new FormData(event.currentTarget).get('status'); setParams(next ? { status: next, page: '0' } : { page: '0' }); }}>
      <label>Trạng thái<select name="status" defaultValue={status} key={status}><option value="">Tất cả</option>{Object.entries(labels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
      <button className="button">Lọc hồ sơ</button>
    </form>
    <CommerceState loading={isLoading} error={error} retry={retry} />
    {!isLoading && !error && (data?.content?.length ? <section className="panel installment-table-wrap">
      <div className="data-section-heading"><div><h2>Danh sách hồ sơ</h2><p>{data.totalElements} hồ sơ · Trang {data.page + 1}/{Math.max(data.totalPages, 1)}</p></div></div>
      <table className="product-table"><thead><tr><th>Đơn hàng</th><th>Khách hàng</th><th>Đơn vị</th><th>Tổng tiền</th><th>Trả trước</th><th>Còn lại</th><th>Kỳ hạn</th><th>Trạng thái</th><th></th></tr></thead>
        <tbody>{data.content.map((row) => <tr key={row.installmentId}><th scope="row">{row.orderCode}</th><td>{row.customerName}</td><td>{row.providerName}</td><td>{money(row.totalAmount)}</td><td>{money(row.downPayment)}</td><td><strong>{money(row.remainingAmount)}</strong></td><td>{row.termMonths} tháng</td><td><StatusBadge status={row.status}>{labels[row.status]}</StatusBadge></td><td><Link className="button button-quiet" to={`/installments/${row.installmentId}`}>Chi tiết</Link></td></tr>)}</tbody></table>
    </section> : <section className="panel empty-state"><h2>Chưa có hồ sơ phù hợp</h2><p>Thử chọn trạng thái khác hoặc chờ đơn trả góp mới.</p></section>)}
    {data && <nav className="inventory-pagination" aria-label="Phân trang hồ sơ trả góp"><button className="button button-quiet" disabled={page <= 0} onClick={() => setParams({ status, page: String(page - 1) })}>Trang trước</button><span>Trang {page + 1}/{Math.max(data.totalPages, 1)}</span><button className="button button-quiet" disabled={page + 1 >= data.totalPages} onClick={() => setParams({ status, page: String(page + 1) })}>Trang sau</button></nav>}
  </>;
}
