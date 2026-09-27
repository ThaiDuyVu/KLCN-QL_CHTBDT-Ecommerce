import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import PageHeader from '../../../components/ui/PageHeader';
import { warrantyApi } from '../api/warrantyApi';
import { formatDate, warrantyStatus } from '../warrantyFormat';
import '../warranty.css';

export default function WarrantyLookupPage() {
  const [params, setParams] = useSearchParams(); const page = Math.max(0, Number(params.get('page') || 0));
  const [data, setData] = useState(null); const [result, setResult] = useState(null); const [loading, setLoading] = useState(true);
  const [error, setError] = useState(''); const [looking, setLooking] = useState(false); const navigate = useNavigate();
  useEffect(() => { const c = new AbortController(); warrantyApi.warranties(page, c.signal).then(setData)
    .catch(e => { if (e.name !== 'AbortError') setError(e.message); }).finally(() => setLoading(false)); return () => c.abort(); }, [page]);
  async function lookup(e) { e.preventDefault(); const code = String(new FormData(e.currentTarget).get('code') || '').trim(); if (!code) return;
    setLooking(true); setError(''); setResult(null); try { setResult(await warrantyApi.lookup(code)); } catch (nextError) { setError(nextError.message); } finally { setLooking(false); } }
  return <><PageHeader title="Tra cứu bảo hành" description="Tìm đúng thiết bị đã bán bằng Serial hoặc IMEI." /><div className="warranty-links"><Link className="button button-quiet" to="/warranty-tickets">Warranty Ticket</Link></div>
    <form className="panel warranty-toolbar" onSubmit={lookup}><label>Serial hoặc IMEI<input name="code" required placeholder="Nhập chính xác Serial / IMEI" /></label><button className="button" disabled={looking}>{looking ? 'Đang tìm…' : 'Tra cứu'}</button></form>
    {error && <p className="auth-alert" role="alert">{error}</p>}{result && <section className="panel warranty-device"><h2>{result.productName} · {result.sku}</h2><p>Serial: <strong>{result.serialNumber}</strong></p><p>IMEI: {result.imeiNumbers?.join(', ') || 'Không có'}</p><p>Khách hàng: {result.customerName}</p><p>Đơn hàng: {result.orderCode}</p><p>Thời hạn: {formatDate(result.startDate)} – {formatDate(result.endDate)}</p><p className={`warranty-status ${result.status === 'EXPIRED' ? 'expired' : ''}`}>{warrantyStatus[result.status]}</p><button className="button button-quiet" onClick={() => navigate(`/warranties/${result.warrantyId}`)}>Xem lịch sử</button></section>}
    <h2>Danh sách quyền bảo hành</h2>{loading && <p role="status">Đang tải…</p>}{!loading && !error && (data?.content?.length ? <section className="panel warranty-table-wrap"><table className="product-table"><thead><tr><th>Thiết bị</th><th>Serial / IMEI</th><th>Khách hàng</th><th>Đơn hàng</th><th>Thời hạn</th><th>Trạng thái</th><th></th></tr></thead><tbody>{data.content.map(row => <tr key={row.warrantyId}><td>{row.productName}<br /><span className="muted">{row.sku}</span></td><td>{row.serialNumber}<br /><span className="muted">{row.imeiNumbers?.join(', ') || '—'}</span></td><td>{row.customerName}</td><td>{row.orderCode}</td><td>{formatDate(row.startDate)} – {formatDate(row.endDate)}</td><td>{warrantyStatus[row.status]}</td><td><Link to={`/warranties/${row.warrantyId}`}>Chi tiết</Link></td></tr>)}</tbody></table></section> : <section className="panel empty-state"><p>Chưa có quyền bảo hành.</p></section>)}
    {data && <nav className="inventory-pagination"><button className="button button-quiet" disabled={page <= 0} onClick={() => setParams({ page: String(page - 1) })}>Trang trước</button><span>Trang {page + 1}/{Math.max(data.totalPages, 1)}</span><button className="button button-quiet" disabled={page + 1 >= data.totalPages} onClick={() => setParams({ page: String(page + 1) })}>Trang sau</button></nav>}</>;
}
