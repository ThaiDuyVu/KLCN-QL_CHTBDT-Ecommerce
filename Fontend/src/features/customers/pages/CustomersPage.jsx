import { Link } from 'react-router';
import PageHeader from '../../../components/ui/PageHeader';
import { useAuth } from '../../../hooks/useAuth';
import { ROLES } from '../../../config/projectConfig';

export default function CustomersPage() {
  const { user } = useAuth();

  return <>
    <PageHeader eyebrow="Vận hành / Khách hàng" title="Khách hàng"
      description="Tra cứu đơn hàng và tài khoản khách hàng từ các khu vực đang được hệ thống hỗ trợ." />
    <section className="panel empty-state customer-workspace">
      <span className="customer-workspace-mark" aria-hidden="true">KH</span>
      <h2>Thông tin khách hàng</h2>
      <p className="muted">Hệ thống hiện chưa cung cấp API danh sách khách hàng riêng. Bạn có thể xem người nhận và thông tin giao hàng trong từng đơn hàng.</p>
      <div className="customer-workspace-actions">
        <Link className="button" to="/orders">Xem đơn hàng</Link>
        {user?.roleName === ROLES.ADMIN && <Link className="button button-quiet" to="/user-management">Quản lý tài khoản</Link>}
      </div>
    </section>
  </>;
}
