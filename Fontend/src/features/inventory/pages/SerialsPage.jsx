import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import PageHeader from '../../../components/ui/PageHeader';
import { inventoryApi } from '../api/inventoryApi';
import '../inventory.css';

const statuses = ['', 'AVAILABLE', 'RESERVED', 'SOLD', 'RETURNED', 'DEFECTIVE'];
export default function SerialsPage() {
  const [params, setParams] = useSearchParams(); const page = Math.max(0, Number(params.get('page') || 0));
  const keyword = params.get('keyword') || ''; const status = params.get('status') || ''; const warehouseId = params.get('warehouseId') || '';
  const [data, setData] = useState(null); const [warehouses, setWarehouses] = useState([]); const [loading, setLoading] = useState(true); const [error, setError] = useState('');
  useEffect(() => { const c = new AbortController(); Promise.all([inventoryApi.serials({ page, keyword, status, warehouseId }, c.signal), inventoryApi.warehouses(c.signal)]).then(([result, w]) => { setData(result); setWarehouses(w.content || []); }).catch(e => { if (e.name !== 'AbortError') setError(e.message); }).finally(() => setLoading(false)); return () => c.abort(); }, [page, keyword, status, warehouseId]);
  function submit(e) { e.preventDefault(); const f = new FormData(e.currentTarget); setParams({ keyword: String(f.get('keyword') || '').trim(), status: String(f.get('status') || ''), warehouseId: String(f.get('warehouseId') || ''), page: '0' }); }
  return <><PageHeader title="Serial / IMEI" description="Tra cứu thiết bị vật lý theo serial, IMEI, SKU, kho và trạng thái." /><nav className="inventory-tabs"><Link to="/inventory">Tồn kho</Link><Link to="/goods-receipts">Phiếu nhập</Link><Link className="active" to="/serials">Serial / IMEI</Link></nav>
    <form className="panel inventory-filters" onSubmit={submit}><label>Serial / IMEI / SKU<input name="keyword" defaultValue={keyword} /></label><label>Trạng thái<select name="status" defaultValue={status}>{statuses.map(x => <option key={x} value={x}>{x || 'Tất cả'}</option>)}</select></label><label>Kho<select name="warehouseId" defaultValue={warehouseId}><option value="">Tất cả</option>{warehouses.map(w => <option key={w.warehouseId} value={w.warehouseId}>{w.warehouseName}</option>)}</select></label><button className="button">Tìm</button></form>
    {loading && <p role="status">Đang tải serial…</p>}{error && <p className="auth-alert" role="alert">{error}</p>}{!loading && !error && (data?.content?.length ? <section className="panel inventory-table-wrap"><table className="product-table"><thead><tr><th>Serial</th><th>IMEI</th><th>SKU</th><th>Kho</th><th>Trạng thái</th><th></th></tr></thead><tbody>{data.content.map(row => <tr key={row.serialId}><th>{row.serialNumber}</th><td>{row.imeis.map(i => i.imeiNumber).join(', ') || '—'}</td><td>{row.sku}</td><td>{row.warehouseName}</td><td>{row.status}</td><td><Link className="button button-quiet" to={`/serials/${row.serialId}`}>Chi tiết</Link></td></tr>)}</tbody></table></section> : <section className="panel empty-state"><p>Không có serial/IMEI phù hợp.</p></section>)}
    {data && <nav className="inventory-pagination"><button className="button button-quiet" disabled={page <= 0} onClick={() => setParams({ keyword, status, warehouseId, page: String(page - 1) })}>Trang trước</button><span>Trang {page + 1}/{Math.max(data.totalPages, 1)}</span><button className="button button-quiet" disabled={page + 1 >= data.totalPages} onClick={() => setParams({ keyword, status, warehouseId, page: String(page + 1) })}>Trang sau</button></nav>}</>;
}
