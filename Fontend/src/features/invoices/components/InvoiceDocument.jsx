import { projectConfig } from '../../../config/projectConfig';
import { date, money } from '../../orders/components/orderFormat';
import { paymentStatusLabels } from '../../orders/components/vnpayFormat';
import { amountInWords } from '../amountInWords';

const paymentMethods = { COD: 'Tiền mặt khi nhận hàng (COD)', VNPAY: 'VNPAY', INSTALLMENT: 'Trả góp', CASH: 'Tiền mặt tại quầy' };

/** Printable sales invoice (A4). Prices are the order snapshot. */
export default function InvoiceDocument({ invoice }) {
  return <article className="invoice-document" aria-label={`Hóa đơn ${invoice.invoiceNumber}`}>
    <header className="invoice-header">
      <div><h2>{projectConfig.appName}</h2><p>{invoice.warehouseName}</p>{invoice.warehouseAddress && <p>{invoice.warehouseAddress}</p>}</div>
      <div className="invoice-title"><h1>HÓA ĐƠN BÁN HÀNG</h1><p>Số: <strong>{invoice.invoiceNumber}</strong></p><p>Ngày lập: {date(invoice.issuedDate)}</p>
        {invoice.status === 'CANCELLED' && <p className="invoice-cancelled">ĐÃ HỦY (khách trả lại toàn bộ hàng)</p>}</div>
    </header>
    <dl className="invoice-parties">
      <div><dt>Khách hàng</dt><dd>{invoice.customerName}</dd></div>
      <div><dt>Người nhận</dt><dd>{invoice.recipientName} · {invoice.recipientPhone}</dd></div>
      <div><dt>Địa chỉ</dt><dd>{invoice.shippingAddress}</dd></div>
      <div><dt>Đơn hàng</dt><dd>{invoice.orderCode} · {date(invoice.orderDate)}</dd></div>
      <div><dt>Thanh toán</dt><dd>{paymentMethods[invoice.paymentMethod] || invoice.paymentMethod} · {paymentStatusLabels[invoice.paymentStatus] || invoice.paymentStatus}</dd></div>
    </dl>
    <table className="invoice-lines"><thead><tr><th>STT</th><th>Sản phẩm</th><th>SL</th><th>Đơn giá</th><th>Giảm</th><th>Thành tiền</th></tr></thead>
      <tbody>{invoice.lines.map((line, index) => <tr key={`${line.sku}-${index}`}><td>{index + 1}</td>
        <td><strong>{line.productName}</strong><br /><small>{line.sku}{line.serialNumber ? ` · Serial: ${line.serialNumber}` : ''}{line.imeiNumbers?.length ? ` · IMEI: ${line.imeiNumbers.join(', ')}` : ''}</small></td>
        <td>{line.quantity}</td><td>{money(line.unitPrice)}</td><td>{money(line.discountAmount)}</td><td>{money(line.lineTotal)}</td></tr>)}</tbody></table>
    <dl className="invoice-totals">
      <div><dt>Tạm tính</dt><dd>{money(invoice.subtotal)}</dd></div>
      <div><dt>Giảm giá</dt><dd>- {money(invoice.discountAmount)}</dd></div>
      <div><dt>Phí giao hàng</dt><dd>{money(invoice.shippingFee)}</dd></div>
      <div className="invoice-grand"><dt>Tổng thanh toán</dt><dd>{money(invoice.totalAmount)}</dd></div>
      {Number(invoice.refundedAmount) > 0 && <div><dt>Đã hoàn do đổi trả</dt><dd>- {money(invoice.refundedAmount)}</dd></div>}
    </dl>
    <p className="invoice-words">Bằng chữ: <em>{amountInWords(invoice.totalAmount)}</em></p>
    <footer className="invoice-signatures"><div><strong>Khách hàng</strong><span>(Ký, ghi rõ họ tên)</span></div><div><strong>Người bán hàng</strong><span>(Ký, ghi rõ họ tên)</span></div></footer>
  </article>;
}
