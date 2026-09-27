import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import PageHeader from '../../../components/ui/PageHeader';
import { inventoryApi } from '../api/inventoryApi';
import '../inventory.css';

const blankDevice = () => ({
  key: crypto.randomUUID(),
  serialNumber: '',
  imeiText: '',
});

const blankLine = () => ({
  key: crypto.randomUUID(),
  variantId: '',
  quantity: 1,
  unitCost: 0,
  devices: [],
});

function resizeDevices(devices, quantity) {
  const count = Math.max(0, Math.floor(Number(quantity) || 0));
  return Array.from({ length: count }, (_, index) => devices[index] || blankDevice());
}

function responseDevices(devices) {
  return (devices || []).map((device) => ({
    key: device.receiptDeviceId || crypto.randomUUID(),
    serialNumber: device.serialNumber || '',
    imeiText: (device.imeiNumbers || []).join(', '),
  }));
}

function requestDevices(line, trackingType) {
  if (trackingType === 'NONE') return [];
  return resizeDevices(line.devices, line.quantity).map((device) => ({
    serialNumber: device.serialNumber.trim(),
    imeiNumbers: trackingType === 'IMEI'
      ? device.imeiText.split(',').map((value) => value.trim()).filter(Boolean)
      : [],
  }));
}

export default function GoodsReceiptFormPage() {
  const { receiptId } = useParams();
  const navigate = useNavigate();
  const editing = Boolean(receiptId);
  const [refs, setRefs] = useState({ warehouses: [], suppliers: [], variants: [] });
  const [form, setForm] = useState({
    receiptCode: '',
    supplierId: '',
    warehouseId: '',
    lines: [blankLine()],
  });
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    let active = true;
    const initial = editing
      ? inventoryApi.receipt(receiptId, controller.signal)
      : Promise.resolve(null);

    Promise.all([
      inventoryApi.warehouses(controller.signal),
      inventoryApi.suppliers(controller.signal),
      inventoryApi.variants(controller.signal),
      initial,
    ])
      .then(([warehouses, suppliers, variants, receipt]) => {
        if (!active) return;
        setRefs({
          warehouses: warehouses.content || [],
          suppliers: suppliers.content || [],
          variants: variants.content || [],
        });
        if (receipt) {
          setForm({
            receiptCode: receipt.receiptCode,
            supplierId: receipt.supplierId,
            warehouseId: receipt.warehouseId,
            lines: receipt.items.map((item) => ({
              key: item.receiptItemId,
              variantId: item.variantId,
              quantity: item.quantity,
              unitCost: item.unitCost,
              devices: responseDevices(item.devices),
            })),
          });
        }
      })
      .catch((requestError) => {
        if (active && requestError.name !== 'AbortError') setError(requestError.message);
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
      controller.abort();
    };
  }, [editing, receiptId]);

  const variantMap = useMemo(
    () => Object.fromEntries(refs.variants.map((variant) => [variant.variantId, variant])),
    [refs.variants],
  );

  function updateLine(key, patch) {
    setForm((current) => ({
      ...current,
      lines: current.lines.map((line) => (line.key === key ? { ...line, ...patch } : line)),
    }));
  }

  function updateDevice(lineKey, deviceKey, patch) {
    setForm((current) => ({
      ...current,
      lines: current.lines.map((line) => (
        line.key === lineKey
          ? {
              ...line,
              devices: line.devices.map((device) => (
                device.key === deviceKey ? { ...device, ...patch } : device
              )),
            }
          : line
      )),
    }));
  }

  async function submit(event) {
    event.preventDefault();
    setSubmitting(true);
    setError('');
    const body = {
      receiptCode: form.receiptCode.trim(),
      supplierId: form.supplierId,
      warehouseId: form.warehouseId,
      items: form.lines.map((line) => {
        const trackingType = variantMap[line.variantId]?.trackingType || 'NONE';
        return {
          variantId: line.variantId,
          quantity: Number(line.quantity),
          unitCost: Number(line.unitCost),
          devices: requestDevices(line, trackingType),
        };
      }),
    };

    try {
      const result = editing
        ? await inventoryApi.updateReceipt(receiptId, body)
        : await inventoryApi.createReceipt(body);
      navigate(`/goods-receipts/${result.receiptId}`);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) return <p role="status">Đang tải biểu mẫu…</p>;

  return (
    <>
      <PageHeader
        title={editing ? 'Sửa phiếu nhập' : 'Tạo phiếu nhập'}
        description="Mỗi thiết bị serialized phải khai báo đúng serial và IMEI theo tracking của sản phẩm."
      />
      <form className="panel receipt-form" onSubmit={submit}>
        <div className="receipt-grid">
          <label>
            Mã phiếu
            <input
              required
              maxLength="50"
              value={form.receiptCode}
              onChange={(event) => setForm({ ...form, receiptCode: event.target.value })}
            />
          </label>
          <label>
            Nhà cung cấp
            <select
              required
              value={form.supplierId}
              onChange={(event) => setForm({ ...form, supplierId: event.target.value })}
            >
              <option value="">Chọn nhà cung cấp</option>
              {refs.suppliers.map((supplier) => (
                <option key={supplier.supplierId} value={supplier.supplierId}>
                  {supplier.supplierName || supplier.supplierCode}
                </option>
              ))}
            </select>
          </label>
          <label>
            Kho nhập
            <select
              required
              value={form.warehouseId}
              onChange={(event) => setForm({ ...form, warehouseId: event.target.value })}
            >
              <option value="">Chọn kho</option>
              {refs.warehouses.map((warehouse) => (
                <option key={warehouse.warehouseId} value={warehouse.warehouseId}>
                  {warehouse.warehouseName}
                </option>
              ))}
            </select>
          </label>
        </div>

        <h2>Dòng hàng</h2>
        {form.lines.map((line, index) => {
          const variant = variantMap[line.variantId];
          const tracking = variant?.trackingType || 'NONE';
          return (
            <fieldset className="receipt-line" key={line.key}>
              <legend>Dòng {index + 1}</legend>
              <label>
                Biến thể
                <select
                  required
                  value={line.variantId}
                  onChange={(event) => {
                    const variantId = event.target.value;
                    const nextTracking = variantMap[variantId]?.trackingType || 'NONE';
                    updateLine(line.key, {
                      variantId,
                      devices: nextTracking === 'NONE'
                        ? []
                        : resizeDevices([], line.quantity),
                    });
                  }}
                >
                  <option value="">Chọn SKU</option>
                  {refs.variants.map((item) => (
                    <option key={item.variantId} value={item.variantId}>
                      {item.sku} · {item.productName} · {item.trackingType}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Số lượng
                <input
                  required
                  type="number"
                  min="1"
                  value={line.quantity}
                  onChange={(event) => {
                    const quantity = event.target.value;
                    updateLine(line.key, {
                      quantity,
                      devices: tracking === 'NONE'
                        ? []
                        : resizeDevices(line.devices, quantity),
                    });
                  }}
                />
              </label>
              <label>
                Giá vốn
                <input
                  required
                  type="number"
                  min="0"
                  step="0.01"
                  value={line.unitCost}
                  onChange={(event) => updateLine(line.key, { unitCost: event.target.value })}
                />
              </label>

              {tracking !== 'NONE' && (
                <div className="receipt-devices">
                  <p className="receipt-devices-title">
                    {tracking === 'IMEI'
                      ? 'Serial và IMEI theo từng thiết bị'
                      : 'Serial theo từng thiết bị'}
                  </p>
                  <div className="receipt-device-list">
                    {line.devices.map((device, deviceIndex) => (
                      <div className="receipt-device-row" key={device.key}>
                        <span className="receipt-device-index">Thiết bị {deviceIndex + 1}</span>
                        <label>
                          Serial
                          <input
                            required
                            maxLength="255"
                            value={device.serialNumber}
                            onChange={(event) => updateDevice(line.key, device.key, {
                              serialNumber: event.target.value,
                            })}
                            placeholder={`Serial thiết bị ${deviceIndex + 1}`}
                          />
                        </label>
                        {tracking === 'IMEI' && (
                          <>
                            <label>
                              IMEI
                              <input
                                required
                                value={device.imeiText}
                                onChange={(event) => updateDevice(line.key, device.key, {
                                  imeiText: event.target.value,
                                })}
                                placeholder="IMEI1, IMEI2 nếu có"
                              />
                            </label>
                            <button
                              type="button"
                              className="button button-quiet receipt-imei-scan"
                              title="Tính năng quét IMEI sẽ được bổ sung sau"
                            >
                              Quét IMEI
                            </button>
                          </>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              )}

              <button
                type="button"
                className="button button-quiet"
                disabled={form.lines.length === 1}
                onClick={() => setForm({
                  ...form,
                  lines: form.lines.filter((item) => item.key !== line.key),
                })}
              >
                Xóa dòng
              </button>
            </fieldset>
          );
        })}

        <div className="inventory-actions">
          <button
            type="button"
            className="button button-quiet"
            onClick={() => setForm({ ...form, lines: [...form.lines, blankLine()] })}
          >
            Thêm dòng
          </button>
          <button className="button" disabled={submitting}>
            {submitting ? 'Đang lưu…' : 'Lưu DRAFT'}
          </button>
          <Link
            className="button button-quiet"
            to={editing ? `/goods-receipts/${receiptId}` : '/goods-receipts'}
          >
            Hủy
          </Link>
        </div>
        {error && <p className="auth-alert" role="alert">{error}</p>}
      </form>
    </>
  );
}
