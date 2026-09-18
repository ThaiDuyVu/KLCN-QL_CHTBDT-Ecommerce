import PageHeader from '../components/ui/PageHeader';
import { useAuth } from '../hooks/useAuth';

export default function AccountPage() {
  const { user } = useAuth();
  return (
    <section className="narrow-page">
      <PageHeader title="Tài khoản" description="Thông tin từ phiên đăng nhập trên máy chủ." />
      <dl className="panel account-details">
        <dt>Tên hiển thị</dt><dd>{user.displayName || user.username}</dd>
        <dt>Tên đăng nhập</dt><dd>{user.username}</dd>
        <dt>Vai trò</dt><dd>{user.roleName}</dd>
        <dt>Trạng thái</dt><dd>Đã đăng nhập</dd>
      </dl>
    </section>
  );
}
