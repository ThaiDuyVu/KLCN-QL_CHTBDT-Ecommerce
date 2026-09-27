import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import PageHeader from '../../../components/ui/PageHeader';
import StatusBadge from '../../../components/ui/StatusBadge';
import { inventoryApi } from '../api/inventoryApi';
import '../inventory.css';

export default function GoodsReceiptDetailPage() {
  const { receiptId } = useParams();
  const [data, setData] = useState(null);
  const [refs, setRefs] = useState({});
  const [loadedReceiptId, setLoadedReceiptId] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const loading = loadedReceiptId !== receiptId;

  useEffect(() => {
    const controller = new AbortController();
    let active = true;

    Promise.all([
      inventoryApi.receipt(receiptId, controller.signal),
      inventoryApi.warehouses(controller.signal),
      inventoryApi.suppliers(controller.signal),
    ])
      .then(([receipt, warehouses, suppliers]) => {
        if (!active) return;
        setData(receipt);
        setError('');
        setRefs({
          warehouse: (warehouses.content || []).find(
            (item) => item.warehouseId === receipt.warehouseId,
          )?.warehouseName,
          supplier: (suppliers.content || []).find(
            (item) => item.supplierId === receipt.supplierId,
          )?.supplierName,
        });
      })
      .catch((requestError) => {
        if (active && requestError.name !== 'AbortError') setError(requestError.message);
      })
      .finally(() => {
        if (active) setLoadedReceiptId(receiptId);
      });

    return () => {
      active = false;
      controller.abort();
    };
  }, [receiptId]);

  async function changeStatus(status) {
    const message = status === 'CONFIRMED'
      ? 'Xác nhận phiếu và nhập tồn kho/serial?'
      : 'Hủy phiếu nhập này?';
    if (!window.confirm(message)) return;

    setSubmitting(true);
    setError('');
    try {
      setData(await inventoryApi.receiptStatus(receiptId, status));
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) return <p role="status">Đang tải phiếu nhập…</p>;
  if (!data) {
    return (
      <section className="panel empty-state">
        <p className="auth-alert" role="alert">
          {error || 'Không tải được thông tin phiếu nhập.'}
        </p>
        <Link className="button button-quiet" to="/goods-receipts">Quay lại danh sách</Link>
      </section>
    );
  }

  return (
    <>
      <PageHeader
        title={`Phiếu nhập ${data.receiptCode}`}
        description={`${refs.supplier || data.supplierId} · ${refs.warehouse || data.warehouseId}`}
      />
      <div className="inventory-actions">
        <Link className="button button-quiet" to="/goods-receipts">Danh sách</Link>
        {data.status === 'DRAFT' && (
          <>
            <Link className="button button-quiet" to={`/goods-receipts/${receiptId}/edit`}>Sửa</Link>
            <button
              className="button"
              disabled={submitting}
              onClick={() => changeStatus('CONFIRMED')}
            >
              Xác nhận nhập kho
            </button>
            <button
              className="button button-danger"
              disabled={submitting}
              onClick={() => changeStatus('CANCELLED')}
            >
              Hủy phiếu
            </button>
          </>
        )}
      </div>
      {error && <p className="auth-alert" role="alert">{error}</p>}
      <section className="panel receipt-detail">
        <dl>
          <div><dt>Trạng thái</dt><dd><StatusBadge status={data.status}>{data.status === 'DRAFT' ? 'Bản nháp' : data.status === 'CONFIRMED' ? 'Đã xác nhận' : 'Đã hủy'}</StatusBadge></dd></div>
          <div><dt>Ngày tạo</dt><dd>{new Date(data.receiptDate).toLocaleString('vi-VN')}</dd></div>
          <div><dt>Tổng tiền</dt><dd>{Number(data.totalAmount).toLocaleString('vi-VN')} đ</dd></div>
        </dl>
        {(data.items || []).map((item) => (
          <article className="receipt-detail-line" key={item.receiptItemId}>
            <h2>{item.sku} · {item.productName}</h2>
            <p>
              {item.quantity} × {Number(item.unitCost).toLocaleString('vi-VN')} đ
              {' · '}Tracking: {item.trackingType}
            </p>
            {item.devices?.length > 0 && (
              <table className="product-table">
                <thead><tr><th>Serial</th><th>IMEI</th></tr></thead>
                <tbody>
                  {item.devices.map((device) => (
                    <tr key={device.receiptDeviceId || device.serialNumber}>
                      <td>{device.serialNumber}</td>
                      <td>{device.imeiNumbers?.join(', ') || '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </article>
        ))}
      </section>
    </>
  );
}
