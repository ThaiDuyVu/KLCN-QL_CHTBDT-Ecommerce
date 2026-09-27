import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import PageHeader from '../../../components/ui/PageHeader';
import StatusBadge from '../../../components/ui/StatusBadge';
import { inventoryApi } from '../api/inventoryApi';
import '../inventory.css';

export default function GoodsReceiptsPage() {
  const [params, setParams] = useSearchParams(); const page = Math.max(0, Number(params.get('page') || 0));
  const [data, setData] = useState(null); const [refs, setRefs] = useState({ warehouses: {}, suppliers: {} });
  const [loading, setLoading] = useState(true); const [error, setError] = useState('');
  useEffect(() => { const c = new AbortController();
    Promise.all([inventoryApi.receipts(page, c.signal), inventoryApi.warehouses(c.signal), inventoryApi.suppliers(c.signal)])
      .then(([result, warehouses, suppliers]) => { setData(result); setRefs({ warehouses: Object.fromEntries((warehouses.content || []).map(x => [x.warehouseId, x.warehouseName])), suppliers: Object.fromEntries((suppliers.content || []).map(x => [x.supplierId, x.supplierName || x.supplierCode])) }); })
      .catch(e => { if (e.name !== 'AbortError') setError(e.message); }).finally(() => setLoading(false)); return () => c.abort();
  }, [page]);
  return <><PageHeader title="Phiếu nhập hàng" description="Serial/IMEI ở phiếu DRAFT chỉ là dữ liệu khai báo; tồn kho và thiết bị thật chỉ được tạo khi xác nhận." />
    <nav className="inventory-tabs"><Link to="/inventory">Tồn kho</Link><Link className="active" to="/goods-receipts">Phiếu nhập</Link><Link to="/serials">Serial / IMEI</Link></nav>
    <div className="inventory-actions"><Link className="button" to="/goods-receipts/new">Tạo phiếu nhập</Link></div>
    {loading && <p role="status">Đang tải phiếu nhập…</p>}{error && <p className="auth-alert" role="alert">{error}</p>}
    {!loading && !error && (data?.content?.length ? <section className="panel inventory-table-wrap"><div className="data-section-heading"><div><h2>Danh sách phiếu nhập</h2><p>{data.totalElements} phiếu nhập</p></div></div><table className="product-table"><thead><tr><th>Mã phiếu</th><th>Ngày</th><th>Nhà cung cấp</th><th>Kho</th><th>Tổng tiền</th><th>Trạng thái</th><th></th></tr></thead><tbody>{data.content.map(row => <tr key={row.receiptId}><th scope="row">{row.receiptCode}</th><td>{new Date(row.receiptDate).toLocaleString('vi-VN')}</td><td>{refs.suppliers[row.supplierId] || row.supplierId}</td><td>{refs.warehouses[row.warehouseId] || row.warehouseId}</td><td><strong>{Number(row.totalAmount).toLocaleString('vi-VN')} đ</strong></td><td><StatusBadge status={row.status}>{row.status === 'DRAFT' ? 'Bản nháp' : row.status === 'CONFIRMED' ? 'Đã xác nhận' : 'Đã hủy'}</StatusBadge></td><td><Link className="button button-quiet" to={`/goods-receipts/${row.receiptId}`}>Chi tiết</Link></td></tr>)}</tbody></table></section> : <section className="panel empty-state"><p>Chưa có phiếu nhập.</p></section>)}
    {data && <nav className="inventory-pagination"><button className="button button-quiet" disabled={page <= 0} onClick={() => setParams({ page: String(page - 1) })}>Trang trước</button><span>Trang {page + 1}/{Math.max(data.totalPages, 1)}</span><button className="button button-quiet" disabled={page + 1 >= data.totalPages} onClick={() => setParams({ page: String(page + 1) })}>Trang sau</button></nav>}
  </>;
}
