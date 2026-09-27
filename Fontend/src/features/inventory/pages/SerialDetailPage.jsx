import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import PageHeader from '../../../components/ui/PageHeader';
import StatusBadge from '../../../components/ui/StatusBadge';
import { inventoryApi } from '../api/inventoryApi';
import '../inventory.css';
export default function SerialDetailPage() {
  const { serialId } = useParams(); const [data, setData] = useState(null); const [error, setError] = useState('');
  useEffect(() => { const c = new AbortController(); inventoryApi.serialDetail(serialId, c.signal).then(setData).catch(e => { if (e.name !== 'AbortError') setError(e.message); }); return () => c.abort(); }, [serialId]);
  if (error) return <p className="auth-alert" role="alert">{error}</p>; if (!data) return <p role="status">Đang tải thiết bị…</p>;
  return <><PageHeader title={data.serialNumber} description={`${data.productName} · ${data.sku}`} /><div className="inventory-actions"><Link className="button button-quiet" to="/serials">← Danh sách serial</Link></div><section className="panel serial-detail"><dl><div><dt>Trạng thái</dt><dd><StatusBadge status={data.status}>{data.status}</StatusBadge></dd></div><div><dt>Tracking</dt><dd>{data.trackingType}</dd></div><div><dt>Kho</dt><dd>{data.warehouseName}</dd></div><div><dt>Variant ID</dt><dd>{data.variantId}</dd></div><div><dt>IMEI</dt><dd>{data.imeis.map(x => x.imeiNumber).join(', ') || 'Không có'}</dd></div></dl></section></>;
}
