import { date, shipmentLabels } from './orderFormat';

/** Delivery leg of an order: carrier, delivery employee and timestamps. */
export default function ShipmentInfo({ shipment }) {
  if (!shipment) return null;
  return <section className="panel commerce-section" aria-label="Thông tin giao hàng"><h3>Vận chuyển · {shipmentLabels[shipment.status] || shipment.status}</h3>
    <dl className="order-detail-fields">
      <div><dt>Đơn vị vận chuyển</dt><dd>{shipment.shippingProvider}</dd></div>
      <div><dt>Mã vận đơn</dt><dd>{shipment.trackingNumber}</dd></div>
      <div><dt>Nhân viên giao</dt><dd>{shipment.employeeName || '—'}{shipment.employeePhone ? ` · ${shipment.employeePhone}` : ''}</dd></div>
      <div><dt>Ngày gửi hàng</dt><dd>{date(shipment.shippedDate)}</dd></div>
      <div><dt>Ngày giao thành công</dt><dd>{shipment.deliveredDate ? date(shipment.deliveredDate) : '—'}</dd></div>
    </dl>
  </section>;
}
