import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import PageHeader from '../../../components/ui/PageHeader';
import { warrantyApi } from '../api/warrantyApi';
import { formatDate, ticketStatus } from '../warrantyFormat';
import '../warranty.css';

export default function WarrantyTicketsPage({ customer = false }) {
  const [params, setParams] = useSearchParams(); const page = Math.max(0, Number(params.get('page') || 0)); const keyword = params.get('keyword') || ''; const status = params.get('status') || '';
  const [data, setData] = useState(null); const [loading, setLoading] = useState(true); const [error, setError] = useState('');
  useEffect(() => { const c = new AbortController(); const request = customer ? warrantyApi.myTickets(page, c.signal) : warrantyApi.tickets({ page, keyword, status }, c.signal);
    request.then(setData).catch(e => { if (e.name !== 'AbortError') setError(e.message); }).finally(() => setLoading(false)); return () => c.abort(); }, [customer, keyword, page, status]);
  function submit(e) { e.preventDefault(); const f = new FormData(e.currentTarget); setParams({ keyword: String(f.get('keyword') || '').trim(), status: String(f.get('status') || ''), page: '0' }); }
  return <><PageHeader title={customer ? 'Yêu cầu bảo hành của tôi' : 'Warranty Ticket'} description="Theo dõi lịch sử tiếp nhận và xử lý bảo hành." /><div className="warranty-links"><Link className="button button-quiet" to={customer ? '/my-warranties' : '/warranties'}>{customer ? 'Bảo hành của tôi' : 'Tra cứu bảo hành'}</Link></div>
    {!customer && <form className="panel warranty-toolbar" onSubmit={submit}><label>Mã ticket / mô tả<input name="keyword" defaultValue={keyword} /></label><label>Trạng thái<select name="status" defaultValue={status}><option value="">Tất cả</option>{Object.entries(ticketStatus).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label><button className="button">Lọc</button></form>}
    {loading && <p role="status">Đang tải ticket…</p>}{error && <p className="auth-alert" role="alert">{error}</p>}{!loading && !error && (data?.content?.length ? <section className="panel warranty-table-wrap"><table className="product-table"><thead><tr><th>Ticket</th><th>Thiết bị</th><th>Serial</th><th>Khách hàng</th><th>Trạng thái</th><th>Ngày tạo</th><th></th></tr></thead><tbody>{data.content.map(row => <tr key={row.ticketId}><th>{row.ticketCode}</th><td>{row.productName}<br /><span className="muted">{row.sku}</span></td><td>{row.serialNumber}</td><td>{row.customerName}</td><td>{ticketStatus[row.status]}</td><td>{formatDate(row.createdAt)}</td><td><Link className="button button-quiet" to={customer ? `/my-warranty-tickets/${row.ticketId}` : `/warranty-tickets/${row.ticketId}`}>Chi tiết</Link></td></tr>)}</tbody></table></section> : <section className="panel empty-state"><p>Chưa có Warranty Ticket.</p></section>)}
    {data && <nav className="inventory-pagination"><button className="button button-quiet" disabled={page <= 0} onClick={() => setParams({ keyword, status, page: String(page - 1) })}>Trang trước</button><span>Trang {page + 1}/{Math.max(data.totalPages, 1)}</span><button className="button button-quiet" disabled={page + 1 >= data.totalPages} onClick={() => setParams({ keyword, status, page: String(page + 1) })}>Trang sau</button></nav>}</>;
}
