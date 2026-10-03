import { useCallback } from 'react';
import { Link, useSearchParams } from 'react-router';
import PageHeader from '../../../components/ui/PageHeader';
import useProductRequest from '../../products/hooks/useProductRequest';
import { money, date } from '../../orders/components/orderFormat';
import { customerApi } from '../api/customerApi';
import CustomerPagination from '../components/CustomerPagination';
import CustomerStatus from '../components/CustomerStatus';
import { accountLabels, pageNumber } from '../components/customerFormat';
import '../customers.css';

export default function CustomersPage() {
  const [params, setParams] = useSearchParams();
  const page = pageNumber(params.get('page'));
  const keyword = (params.get('keyword') || '').trim();
  const status = Object.hasOwn(accountLabels, params.get('status')) ? params.get('status') : '';
  const load = useCallback((signal) => customerApi.list({ page, size: 20, keyword, status }, signal), [page, keyword, status]);
  const result = useProductRequest(`customers:${page}:${keyword}:${status}`, load);
  function search(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setParams({ keyword: String(form.get('keyword') || '').trim(), status: String(form.get('status') || ''), page: '0' });
  }
  return <div className="customers-page">
    <PageHeader eyebrow="Vận hành / Khách hàng" title="Khách hàng" description="Tra cứu hồ sơ, chi tiêu và lịch sử giao dịch. Chi tiêu chỉ ghi nhận đơn đã giao thành công." />
    <form className="panel customer-filters" onSubmit={search} key={`${keyword}:${status}`} role="search" aria-label="Tìm khách hàng">
      <label>Tìm khách hàng<input name="keyword" type="search" defaultValue={keyword} maxLength={255} placeholder="Tên, username, email hoặc điện thoại" /></label>
      <label>Trạng thái tài khoản<select name="status" defaultValue={status}><option value="">Tất cả trạng thái</option>{Object.entries(accountLabels).map(([key, label]) => <option value={key} key={key}>{label}</option>)}</select></label>
      <button className="button" type="submit">Tìm kiếm</button>{(keyword || status) && <button className="button button-quiet" type="button" onClick={() => setParams({})}>Xóa bộ lọc</button>}
    </form>
    <section className="panel customer-results" aria-busy={result.isLoading} aria-label="Danh sách khách hàng">
      <header className="data-section-heading"><div><h2>Danh sách khách hàng</h2><p>{result.data ? `${result.data.totalElements} khách hàng trong phạm vi lọc` : 'Thông tin tài khoản và hồ sơ kinh doanh'}</p></div></header>
      {result.isLoading ? <p className="customer-state" role="status">Đang tải khách hàng…</p> : result.error ? <div className="auth-alert" role="alert"><p>{result.error.message}</p><button className="button button-quiet" onClick={result.retry}>Thử lại</button></div> : !result.data.content.length ? <div className="customer-state"><h3>Không có khách hàng phù hợp</h3><p>Thử thay đổi từ khóa hoặc trạng thái tài khoản.</p></div> : <div className="customer-table-scroll"><table className="customer-table"><thead><tr><th>Khách hàng</th><th className="customer-list-secondary">Email</th><th className="customer-list-secondary">Điện thoại</th><th>Trạng thái</th><th className="customer-number customer-list-secondary">Tổng đơn</th><th className="customer-number">Tổng chi tiêu</th><th className="customer-list-secondary">Đơn gần nhất</th></tr></thead><tbody>{result.data.content.map((row) => <tr key={row.customerId}>
        <th scope="row"><Link className="customer-name" to={`/customers/${row.customerId}`} state={{ listSearch: params.size ? `?${params}` : '' }}>{row.fullName}</Link><small>{row.username}</small><div className="customer-mobile-contact">{row.email && <small>{row.email}</small>}{row.phone && <small>{row.phone}</small>}</div><Link className="customer-detail-link" to={`/customers/${row.customerId}`} state={{ listSearch: params.size ? `?${params}` : '' }}>Xem hồ sơ →</Link></th>
        <td className="customer-list-secondary">{row.email || '—'}</td><td className="customer-list-secondary">{row.phone || '—'}</td>
        <td><CustomerStatus value={row.accountStatus} label={accountLabels[row.accountStatus]} /></td><td className="customer-number customer-list-secondary">{row.orderSummary.totalOrders}</td><td className="customer-number"><strong>{money(row.orderSummary.totalSpend)}</strong></td>
        <td className="customer-list-secondary">{row.orderSummary.latestOrderId ? <><Link to={`/orders/${row.orderSummary.latestOrderId}`}>{row.orderSummary.latestOrderCode}</Link><small>{date(row.orderSummary.latestOrderDate)}</small></> : <span className="muted">Chưa có đơn</span>}</td>
      </tr>)}</tbody></table></div>}
      {!result.isLoading && !result.error && <CustomerPagination data={result.data} onChange={(next) => setParams({ keyword, status, page: String(next) })} />}
    </section>
  </div>;
}
