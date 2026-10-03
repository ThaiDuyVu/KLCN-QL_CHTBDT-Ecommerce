import { money, statusLabels } from '../../orders/components/orderFormat';

export function SummaryCards({ data }) {
  const metrics = [['Doanh thu', money(data.revenue), 'Order DELIVERED · tổng thanh toán'], ['Lợi nhuận gộp', money(data.grossProfit), 'Giá bán sau giảm − giá vốn snapshot'], ['Đơn hoàn tất', data.completedOrders, `${data.totalOrders} đơn trong phạm vi lọc`], ['Tổng giảm giá', money(data.totalDiscount), 'Discount snapshot · Order DELIVERED']];
  return <><div className="report-kpis">{metrics.map(([title, value, note]) => <div className="report-kpi" key={title}><span>{title}</span><strong>{value}</strong><small>{note}</small></div>)}</div><div className="report-summary-meta"><span>Giá trị đơn hoàn tất trung bình: <strong>{money(data.averageOrderValue)}</strong></span><span>Đã hủy: <strong>{data.cancelledOrders}</strong></span><span>Múi giờ: {data.timezone}</span></div></>;
}

export function OrderStatusSummary({ data }) {
  const counts = Object.fromEntries(data.map((row) => [row.status, row.count]));
  return <div className="report-status-grid">{Object.entries(statusLabels).map(([status, label]) => <div key={status}><span>{label}</span><strong>{counts[status] || 0}</strong></div>)}</div>;
}

const paymentLabels = { COD: 'COD', VNPAY: 'VNPAY', INSTALLMENT: 'Trả góp' };
const paymentStatuses = { PENDING: 'Chờ thanh toán', PAID: 'Đã thanh toán', FAILED: 'Thất bại', REFUNDED: 'Đã hoàn tiền', CANCELLED: 'Đã hủy' };
export function PaymentSummary({ data }) {
  if (!data.length) return <p className="report-empty">Chưa có payment của đơn trong phạm vi lọc.</p>;
  return <div className="report-table-scroll"><table className="product-table"><thead><tr><th>Phương thức</th><th>Trạng thái</th><th className="report-number">Số record</th><th className="report-number">Giá trị</th></tr></thead><tbody>{data.map((row) => <tr key={`${row.paymentMethod}:${row.status}`}><th scope="row">{paymentLabels[row.paymentMethod] || row.paymentMethod}</th><td>{paymentStatuses[row.status] || row.status}</td><td className="report-number">{row.count}</td><td className="report-number">{money(row.amount)}</td></tr>)}</tbody></table></div>;
}

export function GoodsReceiptSummary({ data }) {
  return <><div className="report-receipt-totals"><span><small>Phiếu đã xác nhận</small><strong>{data.count}</strong></span><span><small>Tổng giá trị nhập</small><strong>{money(data.totalAmount)}</strong></span></div>{!data.warehouses.length ? <p className="report-empty">Chưa có phiếu nhập CONFIRMED trong khoảng ngày.</p> : <div className="report-table-scroll"><table className="product-table"><thead><tr><th>Chi nhánh</th><th className="report-number">Số phiếu</th><th className="report-number">Giá trị nhập</th></tr></thead><tbody>{data.warehouses.map((row) => <tr key={row.warehouseId}><th scope="row">{row.warehouseName}</th><td className="report-number">{row.count}</td><td className="report-number">{money(row.amount)}</td></tr>)}</tbody></table></div>}</>;
}

export function LowStockTable({ data }) {
  if (!data.length) return <p className="report-empty">Không có tồn kho dưới ngưỡng đã chọn.</p>;
  return <div className="report-table-scroll"><table className="product-table"><thead><tr><th>Chi nhánh</th><th>Sản phẩm / SKU</th><th className="report-number">Tồn thực</th><th className="report-number">Đã giữ</th><th className="report-number">Khả dụng</th></tr></thead><tbody>{data.map((row) => <tr key={row.inventoryId}><td>{row.warehouseName}</td><th scope="row">{row.productName}<small className="report-sku">{row.sku}</small></th><td className="report-number">{row.quantity}</td><td className="report-number">{row.reservedQuantity}</td><td className="report-number"><span className={`report-stock-value${row.availableQuantity <= 0 ? ' is-zero' : ''}`}>{row.availableQuantity}</span></td></tr>)}</tbody></table></div>;
}
