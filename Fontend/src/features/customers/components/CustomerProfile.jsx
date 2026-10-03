import { Link } from 'react-router';
import { date, money, statusLabels } from '../../orders/components/orderFormat';
import CustomerStatus from './CustomerStatus';
import { accountLabels } from './customerFormat';

export default function CustomerProfile({ customer, canManageUsers }) {
  const summary = customer.orderSummary;
  return <><section className="panel customer-profile"><div className="customer-profile-heading"><div><span className="eyebrow">Hồ sơ khách hàng</span><h2>{customer.fullName}</h2><p>@{customer.username}</p></div><CustomerStatus value={customer.accountStatus} label={accountLabels[customer.accountStatus]} /></div>
    <dl className="customer-profile-fields"><div><dt>Email</dt><dd>{customer.email || 'Chưa có'}</dd></div><div><dt>Điện thoại</dt><dd>{customer.phone || 'Chưa có'}</dd></div><div><dt>Địa chỉ hồ sơ</dt><dd>{customer.address || 'Chưa có địa chỉ'}</dd></div><div><dt>Tên hiển thị tài khoản</dt><dd>{customer.displayName || '—'}</dd></div><div><dt>Ngày tạo tài khoản</dt><dd>{date(customer.createdAt)}</dd></div><div><dt>Điểm tích lũy hiện có</dt><dd>{customer.loyaltyPoint}</dd></div><div className="customer-id-field"><dt>Customer ID</dt><dd>{customer.customerId}</dd></div></dl>
    <footer className="customer-profile-footer"><span>Hồ sơ chỉ đọc. Thông tin giao nhận từng đơn được lưu riêng trong đơn hàng.</span>{canManageUsers && <Link to="/user-management">Quản lý tài khoản →</Link>}</footer>
  </section><section className="customer-summary-cards" aria-label="Tổng quan khách hàng">{[['Tổng đơn', summary.totalOrders], ['Đã giao', summary.deliveredOrders], ['Đã hủy', summary.cancelledOrders], ['Tổng chi tiêu', money(summary.totalSpend)]].map(([label, value]) => <div className="panel customer-summary-card" key={label}><span>{label}</span><strong>{value}</strong>{label === 'Tổng chi tiêu' && <small>Chỉ đơn DELIVERED · giá snapshot</small>}</div>)}</section>
    <div className="customer-latest-order">{summary.latestOrderId ? <><span>Đơn gần nhất</span><Link to={`/orders/${summary.latestOrderId}`}>{summary.latestOrderCode}</Link><span>{date(summary.latestOrderDate)}</span><CustomerStatus value={summary.latestOrderStatus} label={statusLabels[summary.latestOrderStatus]} /></> : <span>Khách hàng chưa có đơn hàng.</span>}</div>
  </>;
}
