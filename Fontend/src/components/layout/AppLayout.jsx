import { Link, NavLink, Outlet } from 'react-router';
import { MANAGEMENT_ROLES, projectConfig, ROLES } from '../../config/projectConfig';
import { useAuth } from '../../hooks/useAuth';
import { useState } from 'react';
import { useCart } from '../../hooks/useCart';

const navigation = [
  { to: '/', label: 'Trang chủ', end: true },
  { to: '/products', label: 'Sản phẩm' },
  { to: '/categories', label: 'Danh mục' },
  { to: '/cart', label: 'Giỏ hàng', roles: [ROLES.CUSTOMER] },
  { to: '/my-orders', label: 'Đơn của tôi', roles: [ROLES.CUSTOMER] },
  { to: '/orders', label: 'Đơn hàng', roles: MANAGEMENT_ROLES },
  { to: '/customers', label: 'Khách hàng', roles: MANAGEMENT_ROLES },
  { to: '/inventory', label: 'Kho hàng', roles: MANAGEMENT_ROLES },
  { to: '/user-management', label: 'Người dùng', roles: [ROLES.ADMIN] },
];

export default function AppLayout() {
  const { user, signOut, isSigningOut, isLoading } = useAuth();
  const { itemCount, isLoading: isCartLoading, error: cartError } = useCart(true);
  const [logoutError, setLogoutError] = useState('');
  async function handleLogout() {
    setLogoutError('');
    try {
      await signOut();
    } catch {
      setLogoutError('Đăng xuất chưa thành công. Vui lòng thử lại để kết thúc phiên trên máy chủ.');
    }
  }
  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">Đến nội dung chính</a>
      <aside className="sidebar">
        <Link to="/" className="brand"><span className="brand-mark" aria-hidden="true">Đ</span>{projectConfig.appName}</Link>
        <p className="sidebar-caption">THIẾT BỊ ĐIỆN & QUẢN LÝ</p>
        <nav aria-label="Điều hướng chính">
          {navigation.filter((item) => !item.roles || item.roles.includes(user?.roleName)).map((item) => (
            <NavLink key={item.to} to={item.to} end={item.end} className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}>
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-note">Điện Việt<br />Bán và quản lý thiết bị điện</div>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <span className="badge">Bản dựng skeleton</span>
          <div className="account-actions">
            {user ? <>
              {user.roleName === ROLES.CUSTOMER && <Link className="topbar-cart" to="/cart"
                aria-label={`Giỏ hàng, ${itemCount} sản phẩm`} title={cartError ? 'Chưa đồng bộ được giỏ hàng' : undefined}>
                <span aria-hidden="true">Giỏ hàng</span>
                <span className="topbar-cart-count" aria-hidden="true">{isCartLoading && !itemCount ? '…' : itemCount}</span>
              </Link>}
              <Link to="/account">{user.displayName || user.username} · {user.roleName}</Link>
              <button className="button button-quiet" onClick={handleLogout} disabled={isSigningOut}>
                {isSigningOut ? 'Đang đăng xuất…' : 'Đăng xuất'}
              </button>
            </> : isLoading ? <span role="status">Đang kiểm tra phiên…</span> : <Link className="button" to="/login">Đăng nhập</Link>}
          </div>
        </header>
        <main id="main-content" className="main-content">
          {logoutError && <p className="auth-alert" role="alert">{logoutError}</p>}
          <Outlet />
        </main>
        <footer className="footer">{projectConfig.appName} · Nền tảng bán và quản lý thiết bị điện</footer>
      </div>
    </div>
  );
}
