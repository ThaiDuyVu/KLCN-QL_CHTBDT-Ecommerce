import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import PageHeader from '../../../components/ui/PageHeader';
import ShopBreadcrumb from '../../../components/ui/ShopBreadcrumb';
import { warrantyApi } from '../api/warrantyApi';
import { formatDate, warrantyStatus } from '../warrantyFormat';
import '../warranty.css';

export default function MyWarrantiesPage() {
  const [params, setParams] = useSearchParams(); const page = Math.max(0, Number(params.get('page') || 0));
  const [data, setData] = useState(null); const [error, setError] = useState(''); const [loading, setLoading] = useState(true);
  const [lookupError, setLookupError] = useState(''); const [looking, setLooking] = useState(false); const navigate = useNavigate();
  useEffect(() => { const c = new AbortController(); warrantyApi.myWarranties(page, c.signal)
    .then(setData).catch(e => { if (e.name !== 'AbortError') setError(e.message); }).finally(() => setLoading(false)); return () => c.abort(); }, [page]);
  async function lookup(e) { e.preventDefault(); const code = String(new FormData(e.currentTarget).get('code') || '').trim(); if (!code) return;
    setLooking(true); setLookupError(''); try { const result = await warrantyApi.myLookup(code); navigate(`/my-warranties/${result.warrantyId}`); }
    catch (nextError) { setLookupError(nextError.message); } finally { setLooking(false); } }
  return <><ShopBreadcrumb items={[{ label: 'Bảo hành của tôi' }]} /><PageHeader title="Bảo hành của tôi" description="Tra cứu thời hạn và gửi yêu cầu bảo hành cho thiết bị đã được giao thành công." />
    <div className="warranty-links"><Link className="button button-quiet" to="/my-warranty-tickets">Yêu cầu bảo hành của tôi</Link></div>
    <form className="panel warranty-toolbar" onSubmit={lookup}><label>Tra Serial hoặc IMEI<input name="code" placeholder="Nhập Serial / IMEI" /></label><button className="button" disabled={looking}>{looking ? 'Đang tìm…' : 'Tra cứu'}</button></form>
    {lookupError && <p className="auth-alert" role="alert">{lookupError}</p>}{loading && <p role="status">Đang tải bảo hành…</p>}{error && <p className="auth-alert" role="alert">{error}</p>}
    {!loading && !error && (data?.content?.length ? <section className="customer-warranty-grid" aria-label="Thiết bị bảo hành">{data.content.map(row => <article className="panel customer-warranty-card" key={row.warrantyId}>
      <div className="customer-warranty-heading"><div><p className="eyebrow">{row.sku}</p><h2>{row.productName}</h2></div><span className={`warranty-status ${row.status === 'EXPIRED' ? 'expired' : ''}`}>{warrantyStatus[row.status]}</span></div>
      <dl><div><dt>Serial</dt><dd>{row.serialNumber}</dd></div><div><dt>IMEI</dt><dd>{row.imeiNumbers?.join(', ') || 'Không có IMEI'}</dd></div><div><dt>Bắt đầu bảo hành</dt><dd>{formatDate(row.startDate)}</dd></div><div><dt>Hết hạn</dt><dd>{formatDate(row.endDate)}</dd></div></dl>
      <Link className="button button-quiet" to={`/my-warranties/${row.warrantyId}`}>Xem bảo hành và yêu cầu →</Link>
    </article>)}</section> : <section className="panel empty-state"><p>Chưa có thiết bị được cấp quyền bảo hành.</p></section>)}
    {data && <nav className="inventory-pagination"><button className="button button-quiet" disabled={page <= 0} onClick={() => setParams({ page: String(page - 1) })}>Trang trước</button><span>Trang {page + 1}/{Math.max(data.totalPages, 1)}</span><button className="button button-quiet" disabled={page + 1 >= data.totalPages} onClick={() => setParams({ page: String(page + 1) })}>Trang sau</button></nav>}</>;
}
