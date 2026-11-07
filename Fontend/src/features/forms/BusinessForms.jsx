import PrintForm, { PrintFields } from '../../components/print/PrintForm';
import { amountInWords } from '../invoices/amountInWords';
import { date, money } from '../orders/components/orderFormat';
import { conditionLabels } from '../returns/returnFormat';

const today = () => new Date().toLocaleDateString('vi-VN');
const devicesText = (devices) => devices?.map((d) => `${d.serialNumber}${d.imeiNumbers?.length ? ` (IMEI ${d.imeiNumbers.join(', ')})` : ''}`).join('; ');

/** Phiếu nhập kho: goods received from a supplier, with serial/IMEI per unit. */
export function GoodsReceiptForm({ receipt, supplierName, warehouseName }) {
  const draft = receipt.status !== 'CONFIRMED';
  return <PrintForm title={`PHIẾU NHẬP KHO${draft ? ' (CHƯA XÁC NHẬN)' : ''}`} number={receipt.receiptCode} date={date(receipt.receiptDate)} branch={warehouseName}
    signatures={['Người giao hàng', 'Thủ kho', 'Người lập phiếu']}>
    <PrintFields rows={[['Nhà cung cấp', supplierName], ['Nhập vào kho', warehouseName]]} />
    <table><thead><tr><th>STT</th><th>SKU</th><th>Sản phẩm</th><th className="num">SL</th><th className="num">Đơn giá</th><th className="num">Thành tiền</th><th>Serial / IMEI</th></tr></thead>
      <tbody>{(receipt.items || []).map((item, index) => <tr key={item.receiptItemId}><td>{index + 1}</td><td>{item.sku}</td><td>{item.productName}</td>
        <td className="num">{item.quantity}</td><td className="num">{money(item.unitCost)}</td><td className="num">{money(Number(item.unitCost) * item.quantity)}</td><td>{devicesText(item.devices) || '—'}</td></tr>)}</tbody></table>
    <PrintFields rows={[['Tổng tiền hàng', money(receipt.totalAmount)], ['Bằng chữ', amountInWords(receipt.totalAmount)]]} />
  </PrintForm>;
}

/** Phiếu giao hàng: what the delivery employee carries and how much to collect. */
export function DeliveryNoteForm({ order }) {
  const s = order.shipment;
  const collect = order.payment?.paymentMethod === 'COD' && order.payment?.status !== 'PAID';
  return <PrintForm title="PHIẾU GIAO HÀNG" number={s.trackingNumber} date={date(s.shippedDate)} branch={order.warehouseName}
    signatures={['Nhân viên giao hàng', 'Người nhận hàng']}
    note="Người nhận kiểm tra tình trạng, serial/IMEI của sản phẩm trước khi ký nhận.">
    <PrintFields rows={[['Đơn hàng', order.orderCode], ['Đơn vị vận chuyển', s.shippingProvider], ['Nhân viên giao', s.employeeName], ['Người nhận', order.recipientName],
      ['Điện thoại', order.recipientPhone], ['Địa chỉ giao', order.shippingAddress]]} />
    <table><thead><tr><th>STT</th><th>Sản phẩm</th><th>SKU</th><th>Serial / IMEI</th><th className="num">SL</th></tr></thead>
      <tbody>{order.items.map((item, index) => <tr key={item.orderItemId}><td>{index + 1}</td><td>{item.productName}</td><td>{item.sku}</td>
        <td>{item.serialNumber ? `${item.serialNumber}${item.imeiNumbers?.length ? ` (IMEI ${item.imeiNumbers.join(', ')})` : ''}` : '—'}</td><td className="num">{item.quantity}</td></tr>)}</tbody></table>
    <PrintFields rows={[[collect ? 'Thu hộ (COD)' : 'Thanh toán', collect ? money(order.totalAmount) : 'Đã thanh toán, không thu tiền'],
      ...(collect ? [['Bằng chữ', amountInWords(order.totalAmount)]] : [])]} />
  </PrintForm>;
}

/** Phiếu bảo hành: warranty card handed to the customer with the device. */
export function WarrantyCardForm({ warranty }) {
  return <PrintForm title="PHIẾU BẢO HÀNH" number={warranty.serialNumber} date={today()} signatures={['Đại diện cửa hàng']}
    note="Bảo hành theo serial/IMEI trong thời hạn trên. Không bảo hành khi rơi vỡ, vào nước, tự ý sửa chữa hoặc tem/serial bị tẩy xóa. Xuất trình phiếu này hoặc số serial khi yêu cầu bảo hành.">
    <PrintFields rows={[['Sản phẩm', warranty.productName], ['SKU', warranty.sku], ['Serial', warranty.serialNumber], ['IMEI', warranty.imeiNumbers?.join(', ')],
      ['Khách hàng', warranty.customerName], ['Đơn hàng', warranty.orderCode], ['Bắt đầu', date(warranty.startDate)], ['Kết thúc', date(warranty.endDate)]]} />
  </PrintForm>;
}

/** Phiếu tiếp nhận bảo hành: receipt for a device left with the technician. */
export function WarrantyTicketForm({ ticket, statusLabel }) {
  return <PrintForm title="PHIẾU TIẾP NHẬN BẢO HÀNH" number={ticket.ticketCode} date={date(ticket.createdAt)} signatures={['Khách hàng', 'Kỹ thuật viên tiếp nhận']}
    note="Khách hàng giữ phiếu này để nhận lại thiết bị. Cửa hàng không chịu trách nhiệm dữ liệu cá nhân trên thiết bị.">
    <PrintFields rows={[['Khách hàng', ticket.customerName], ['Đơn hàng', ticket.orderCode], ['Sản phẩm', ticket.productName], ['SKU', ticket.sku],
      ['Serial', ticket.serialNumber], ['IMEI', ticket.imeiNumbers?.join(', ')], ['Kỹ thuật viên', ticket.employeeName], ['Trạng thái', statusLabel]]} />
    <PrintFields rows={[['Tình trạng lỗi', ticket.issueDescription], ['Kết quả xử lý', ticket.resolutionNote], ['Ngày hoàn tất', ticket.resolvedAt ? date(ticket.resolvedAt) : undefined]]} />
  </PrintForm>;
}

/** Phiếu đổi trả: goods returned by the customer and the amount refunded. */
export function ReturnForm({ request, statusLabel }) {
  const exchange = request.requestType === 'EXCHANGE';
  return <PrintForm title={exchange ? 'PHIẾU ĐỔI HÀNG' : 'PHIẾU TRẢ HÀNG'} number={request.returnCode} date={date(request.requestDate)} signatures={['Khách hàng', 'Nhân viên bán hàng', 'Thủ kho']}>
    <PrintFields rows={[['Khách hàng', request.customerName], ['Đơn hàng', request.orderCode], ['Trạng thái', statusLabel], ['Nhân viên xử lý', request.employeeName]]} />
    <table><thead><tr><th>STT</th><th>Sản phẩm</th><th>SKU</th><th>Serial</th><th className="num">SL</th><th className="num">Đơn giá</th><th>Ghi chú</th></tr></thead>
      <tbody>{request.items.map((line, index) => <tr key={line.returnItemId}><td>{index + 1}</td><td>{line.productName}</td><td>{line.sku}</td><td>{line.serialNumber || '—'}</td>
        <td className="num">{line.quantity}</td><td className="num">{money(line.unitPrice)}</td><td>{[line.reason, line.receivedCondition && conditionLabels[line.receivedCondition]].filter(Boolean).join(' · ')}</td></tr>)}</tbody></table>
    {exchange && request.exchangeItems?.length > 0 && <table><thead><tr><th>Sản phẩm đổi sang</th><th>SKU</th><th className="num">SL</th></tr></thead>
      <tbody>{request.exchangeItems.map((line) => <tr key={line.variantId}><td>{line.productName}</td><td>{line.sku}</td><td className="num">{line.quantity}</td></tr>)}</tbody></table>}
    <PrintFields rows={[['Lý do', request.reason], ...(request.rejectionReason ? [['Lý do từ chối', request.rejectionReason]] : []),
      [exchange ? 'Giá trị hàng trả' : 'Số tiền hoàn', money(request.refundAmount)], ['Bằng chữ', amountInWords(request.refundAmount)],
      ...(exchange && request.exchangeOrderCode ? [['Đơn đổi hàng', request.exchangeOrderCode],
        [Number(request.exchangeDifference) >= 0 ? 'Khách trả thêm' : 'Cửa hàng hoàn lại', money(Math.abs(Number(request.exchangeDifference)))]] : [])]} />
  </PrintForm>;
}
