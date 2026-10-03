import { useCallback } from 'react';
import { Link } from 'react-router';
import useProductRequest from '../../products/hooks/useProductRequest';
import { money, date, statusLabels } from '../../orders/components/orderFormat';
import { formatDate, warrantyStatus } from '../../warranty/warrantyFormat';
import { customerApi } from '../api/customerApi';
import CustomerPagination from './CustomerPagination';
import CustomerStatus from './CustomerStatus';
import { installmentLabels } from './customerFormat';

const headings = { orders: 'Lịch sử đơn hàng', warranties: 'Thiết bị bảo hành', installments: 'Hồ sơ trả góp' };
export default function CustomerHistory({ customerId, resource, page, onPageChange }) {
  const load = useCallback((signal) => customerApi.history(customerId, resource, page, signal), [customerId, resource, page]);
  const result = useProductRequest(`customer-history:${customerId}:${resource}:${page}`, load);
  return <section className="panel customer-history" aria-busy={result.isLoading}>
    <header className="data-section-heading"><div><h2>{headings[resource]}</h2><p>{result.data ? `${result.data.totalElements} kết quả · chỉ dữ liệu của khách hàng này` : 'Dữ liệu được phân trang theo khách hàng'}</p></div></header>
    {result.isLoading ? <p className="customer-state" role="status">Đang tải {headings[resource].toLowerCase()}…</p> : result.error ? <div className="auth-alert" role="alert"><p>{result.error.message}</p><button className="button button-quiet" onClick={result.retry}>Thử lại</button></div> : !result.data.content.length ? <p className="customer-state">Chưa có {headings[resource].toLowerCase()}.</p> : <div className="customer-table-scroll">
      {resource === 'orders' ? <OrdersTable rows={result.data.content} /> : resource === 'warranties' ? <WarrantiesTable rows={result.data.content} /> : <InstallmentsTable rows={result.data.content} />}
    </div>}
    {!result.isLoading && !result.error && <CustomerPagination data={result.data} onChange={onPageChange} />}
  </section>;
}
function OrdersTable({ rows }) {
  return <table className="customer-table"><thead><tr><th>Đơn hàng</th><th>Ngày đặt</th><th>Chi nhánh</th><th>Trạng thái</th><th className="customer-number">Tổng tiền</th></tr></thead><tbody>{rows.map((row) => <tr key={row.orderId}><th scope="row"><Link to={`/orders/${row.orderId}`}>{row.orderCode}</Link></th><td>{date(row.orderDate)}</td><td>{row.warehouseName}</td><td><CustomerStatus value={row.status} label={statusLabels[row.status]} /></td><td className="customer-number">{money(row.totalAmount)}</td></tr>)}</tbody></table>;
}
function WarrantiesTable({ rows }) {
  return <table className="customer-table"><thead><tr><th>Thiết bị</th><th>Serial / IMEI</th><th>Thời hạn</th><th>Trạng thái</th><th>Chi tiết</th></tr></thead><tbody>{rows.map((row) => <tr key={row.warrantyId}><th scope="row">{row.productName}<small>{row.sku}</small></th><td>{row.serialNumber}{row.imeiNumbers.map((imei) => <small key={imei}>IMEI: {imei}</small>)}</td><td>{formatDate(row.startDate)}<small>Đến {formatDate(row.endDate)}</small></td><td><CustomerStatus value={row.status} label={warrantyStatus[row.status]} /></td><td><Link to={`/warranties/${row.warrantyId}`}>Xem bảo hành →</Link><small><Link to={`/orders/${row.orderId}`}>{row.orderCode}</Link></small></td></tr>)}</tbody></table>;
}
function InstallmentsTable({ rows }) {
  return <table className="customer-table"><thead><tr><th>Đơn / Nhà cung cấp</th><th className="customer-number">Tổng giá trị</th><th className="customer-number">Trả trước</th><th className="customer-number">Còn lại</th><th>Kỳ hạn</th><th>Trạng thái</th><th>Chi tiết</th></tr></thead><tbody>{rows.map((row) => <tr key={row.installmentId}><th scope="row"><Link to={`/orders/${row.orderId}`}>{row.orderCode}</Link><small>{row.providerName}</small></th><td className="customer-number">{money(row.totalAmount)}</td><td className="customer-number">{money(row.downPayment)}</td><td className="customer-number">{money(row.remainingAmount)}</td><td>{row.termMonths} tháng</td><td><CustomerStatus value={row.status} label={installmentLabels[row.status]} /></td><td><Link to={`/installments/${row.installmentId}`}>Xem hồ sơ →</Link></td></tr>)}</tbody></table>;
}
