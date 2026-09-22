import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import PageHeader from '../../../components/ui/PageHeader';
import { inventoryApi } from '../api/inventoryApi';
import '../inventory.css';

export default function InventoryPage() {
  const [params, setParams] = useSearchParams();
  const [data, setData] = useState(null); const [warehouses, setWarehouses] = useState([]);
  const [loading, setLoading] = useState(true); const [error, setError] = useState('');
  const page = Math.max(0, Number(params.get('page') || 0));
  const keyword = params.get('keyword') || ''; const warehouseId = params.get('warehouseId') || '';
  useEffect(() => {
    const controller = new AbortController();
    Promise.all([inventoryApi.inventory({ page, keyword, warehouseId }, controller.signal), inventoryApi.warehouses(controller.signal)])
      .then(([result, warehousePage]) => { setData(result); setWarehouses(warehousePage.content || []); })
      .catch((e) => { if (e.name !== 'AbortError') setError(e.message); }).finally(() => setLoading(false));
    return () => controller.abort();
  }, [page, keyword, warehouseId]);
  function submit(event) {
    event.preventDefault(); const form = new FormData(event.currentTarget);
    setParams({ keyword: String(form.get('keyword') || '').trim(), warehouseId: String(form.get('warehouseId') || ''), page: '0' });
  }
  return <><PageHeader title="Tồn kho" description="Số lượng theo từng kho và SKU; tồn khả dụng được tính từ quantity - reservedQuantity." />
    <nav className="inventory-tabs"><Link className="active" to="/inventory">Tồn kho</Link><Link to="/goods-receipts">Phiếu nhập</Link><Link to="/serials">Serial / IMEI</Link></nav>
    <form className="panel inventory-filters" onSubmit={submit}><label>Tìm SKU / sản phẩm<input name="keyword" defaultValue={keyword} /></label><label>Kho<select name="warehouseId" defaultValue={warehouseId}><option value="">Tất cả kho</option>{warehouses.map(w => <option key={w.warehouseId} value={w.warehouseId}>{w.warehouseName}</option>)}</select></label><button className="button">Lọc</button></form>
    {loading && <p role="status">Đang tải tồn kho…</p>}{error && <p className="auth-alert" role="alert">{error}</p>}
    {!loading && !error && (data?.content?.length ? <section className="panel inventory-table-wrap"><table className="product-table"><thead><tr><th>Kho</th><th>SKU</th><th>Sản phẩm</th><th>Tracking</th><th>Tồn thực</th><th>Đã giữ</th><th>Khả dụng</th></tr></thead><tbody>{data.content.map(row => <tr key={row.inventoryId}><td>{row.warehouseName}</td><th scope="row">{row.sku}</th><td>{row.productName}</td><td>{row.trackingType}</td><td>{row.quantity}</td><td>{row.reservedQuantity}</td><td><strong>{row.availableQuantity}</strong></td></tr>)}</tbody></table></section> : <section className="panel empty-state"><p>Không có dữ liệu tồn kho phù hợp.</p></section>)}
    {data && <nav className="inventory-pagination"><button className="button button-quiet" disabled={page <= 0} onClick={() => setParams({ keyword, warehouseId, page: String(page - 1) })}>Trang trước</button><span>Trang {page + 1}/{Math.max(data.totalPages, 1)}</span><button className="button button-quiet" disabled={page + 1 >= data.totalPages} onClick={() => setParams({ keyword, warehouseId, page: String(page + 1) })}>Trang sau</button></nav>}
  </>;
}
