import { statusLabels } from '../orders/components/orderFormat';
import { paymentLabels, paymentStatuses } from './reportLabels';

export const EXPORT_RESOURCES = ['summary', 'revenue', 'top-products', 'warehouses', 'order-status', 'payments', 'goods-receipts', 'low-stock'];

// Excel cần số nguyên đồng; số thập phân dạng "1.5" bị Excel vùng Việt Nam đọc sai.
const vnd = (value) => Math.round(Number(value) || 0);

export function reportSections(data, { from, to, warehouseName, threshold }) {
  const s = data.summary;
  return [
    { title: 'Phạm vi báo cáo', header: ['Từ ngày', 'Đến ngày', 'Chi nhánh', 'Ngưỡng tồn khả dụng'], rows: [[from, to, warehouseName || 'Tất cả chi nhánh', threshold]] },
    { title: 'Kết quả kinh doanh (Order DELIVERED)', header: ['Doanh thu (đ)', 'Lợi nhuận gộp (đ)', 'Tổng giảm giá (đ)', 'Tổng số đơn', 'Đơn hoàn tất', 'Đơn đã hủy', 'Giá trị đơn trung bình (đ)'],
      rows: [[vnd(s.revenue), vnd(s.grossProfit), vnd(s.totalDiscount), s.totalOrders, s.completedOrders, s.cancelledOrders, vnd(s.averageOrderValue)]] },
    { title: `Doanh thu theo ${data.revenue.granularity === 'MONTH' ? 'tháng' : 'ngày'}`, header: ['Kỳ', 'Đơn hoàn tất', 'Doanh thu (đ)'],
      rows: data.revenue.points.map((p) => [p.bucket, p.completedOrders, vnd(p.revenue)]) },
    { title: 'Sản phẩm bán chạy', header: ['Sản phẩm', 'Số lượng bán', 'Doanh thu hàng hóa (đ)'],
      rows: data['top-products'].map((r) => [r.productName, r.quantity, vnd(r.revenue)]) },
    { title: 'Doanh thu theo chi nhánh', header: ['Chi nhánh', 'Đơn hoàn tất', 'Doanh thu (đ)'],
      rows: data.warehouses.map((r) => [r.warehouseName, r.count, vnd(r.amount)]) },
    { title: 'Trạng thái đơn hàng', header: ['Trạng thái', 'Số đơn'],
      rows: data['order-status'].map((r) => [statusLabels[r.status] || r.status, r.count]) },
    { title: 'Phương thức thanh toán', header: ['Phương thức', 'Trạng thái', 'Số record', 'Giá trị (đ)'],
      rows: data.payments.map((r) => [paymentLabels[r.paymentMethod] || r.paymentMethod, paymentStatuses[r.status] || r.status, r.count, vnd(r.amount)]) },
    { title: 'Giá trị nhập hàng (phiếu CONFIRMED)', header: ['Chi nhánh', 'Số phiếu', 'Giá trị nhập (đ)'],
      rows: [...data['goods-receipts'].warehouses.map((r) => [r.warehouseName, r.count, vnd(r.amount)]), ['Tổng cộng', data['goods-receipts'].count, vnd(data['goods-receipts'].totalAmount)]] },
    { title: `Tồn kho thấp (khả dụng ≤ ${threshold})`, header: ['Chi nhánh', 'Sản phẩm', 'SKU', 'Tồn thực', 'Đã giữ', 'Khả dụng'],
      rows: data['low-stock'].map((r) => [r.warehouseName, r.productName, r.sku, r.quantity, r.reservedQuantity, r.availableQuantity]) },
  ];
}

const cell = (value) => String(value ?? '').replace(/[\t\r\n]+/g, ' ');

export function sectionsToTsv(sections) {
  return sections.map(({ title, header, rows }) => [title, header.map(cell).join('\t'), ...rows.map((r) => r.map(cell).join('\t'))].join('\r\n')).join('\r\n\r\n');
}

/** UTF-16LE có BOM: Excel tự nhận Unicode và cột phân cách bằng tab, không phụ thuộc vùng ngôn ngữ. */
export function encodeForExcel(text) {
  const bytes = new Uint8Array(2 + text.length * 2);
  bytes[0] = 0xff; bytes[1] = 0xfe;
  for (let i = 0; i < text.length; i += 1) {
    const code = text.charCodeAt(i);
    bytes[2 + i * 2] = code & 0xff;
    bytes[3 + i * 2] = code >> 8;
  }
  return bytes;
}

export function saveFile(bytes, filename) {
  const url = URL.createObjectURL(new Blob([bytes], { type: 'text/csv' }));
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.append(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
