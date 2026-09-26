import { Link, NavLink, Outlet, useNavigate } from 'react-router';
import { MANAGEMENT_ROLES, projectConfig, ROLES } from '../../config/projectConfig';
import { useAuth } from '../../hooks/useAuth';
import { useCallback, useState } from 'react';
import { useCart } from '../../hooks/useCart';
import { useWarehouse } from '../../hooks/useWarehouse';
import ShopIcon from '../ui/ShopIcon';
import ComingSoonButton from '../ui/ComingSoonButton';
import useProductRequest from '../../features/products/hooks/useProductRequest';
import { categoryApi } from '../../features/categories/api/categoryApi';

const managementNavigation = [
  { to: '/', label: 'Tổng quan', end: true },
  { to: '/products', label: 'Sản phẩm' },
  { to: '/categories', label: 'Danh mục' },
  { to: '/orders', label: 'Đơn hàng', roles: MANAGEMENT_ROLES },
  { to: '/installments', label: 'Trả góp', roles: [ROLES.ADMIN, ROLES.MANAGER] },
  { to: '/customers', label: 'Khách hàng', roles: MANAGEMENT_ROLES },
  { to: '/inventory', label: 'Kho hàng', roles: MANAGEMENT_ROLES },
  { to: '/goods-receipts', label: 'Nhập hàng', roles: MANAGEMENT_ROLES },
  { to: '/serials', label: 'Serial / IMEI', roles: MANAGEMENT_ROLES },
  { to: '/warranties', label: 'Bảo hành', roles: MANAGEMENT_ROLES },
  { to: '/warranty-tickets', label: 'Warranty Ticket', roles: MANAGEMENT_ROLES },
  { to: '/user-management', label: 'Người dùng', roles: [ROLES.ADMIN] },
];

const storefrontNavigation = [
  { to: '/', label: 'Trang chủ', end: true },
  { to: '/products', label: 'Cửa hàng' },
  { to: '/my-orders', label: 'Đơn hàng' },
  { to: '/my-warranties', label: 'Bảo hành' },
];

export default function AppLayout() {
  const navigate = useNavigate();
  const { user, signOut, isSigningOut, isLoading } = useAuth();
  const { itemCount, isLoading: isCartLoading, error: cartError } = useCart(true);
  const { warehouses, selectedWarehouseId, selectWarehouse, isLoading: isWarehouseLoading,
    isChanging: isWarehouseChanging, error: warehouseError } = useWarehouse();
  const [logoutError, setLogoutError] = useState('');
  const [managementMenuOpen, setManagementMenuOpen] = useState(false);
  const isStorefront = !user || user.roleName === ROLES.CUSTOMER;
  const loadCategories = useCallback((signal) => user?.roleName === ROLES.CUSTOMER ? categoryApi.list(signal) : Promise.resolve([]), [user?.roleName]);
  const categories = useProductRequest(`storefront-search-categories:${user?.roleName}`, loadCategories);

  async function handleLogout() {
    setLogoutError('');
    try {
      await signOut();
    } catch {
      setLogoutError('Đăng xuất chưa thành công. Vui lòng thử lại để kết thúc phiên trên máy chủ.');
    }
  }

  function searchStorefront(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const query = new URLSearchParams();
    const keyword = form.get('keyword')?.trim();
    if (keyword) query.set('keyword', keyword);
    if (form.get('categoryId')) query.set('categoryId', form.get('categoryId'));
    navigate(`/products${query.size ? `?${query}` : ''}`);
  }

  const outlet = <>
    {logoutError && <p className="auth-alert" role="alert">{logoutError}</p>}
    <Outlet />
  </>;

  if (isStorefront) {
    return (
      <div className="storefront-shell">
        <a className="skip-link" href="#main-content">Đến nội dung chính</a>
        <header className="storefront-header">
          <div className="storefront-header-main">
            <Link to="/" className="brand storefront-brand"><span className="brand-mark" aria-hidden="true">Đ</span>{projectConfig.appName}</Link>
            <form className="storefront-search" role="search" onSubmit={searchStorefront}>
              <label className="sr-only" htmlFor="storefront-search-category">Danh mục tìm kiếm</label>
              <select id="storefront-search-category" name="categoryId" aria-describedby={categories.error ? 'storefront-category-error' : undefined}>
                <option value="">Tất cả danh mục</option>
                {(categories.data || []).filter((category) => category.status === 'ACTIVE').map((category) => <option value={category.categoryId} key={category.categoryId}>{category.categoryName}</option>)}
              </select>
              <label className="sr-only" htmlFor="storefront-search-input">Tìm sản phẩm</label>
              <input id="storefront-search-input" name="keyword" type="search" placeholder="Tìm điện thoại, laptop, phụ kiện…" />
              <button type="submit" aria-label="Tìm sản phẩm"><ShopIcon name="search" /></button>
            </form>
            <nav className="storefront-nav" aria-label="Điều hướng cửa hàng">
              {storefrontNavigation.filter((item) => user || !item.to.startsWith('/my-')).map((item) => (
                <NavLink key={item.to} to={item.to} end={item.end} className={({ isActive }) => isActive ? 'storefront-nav-link active' : 'storefront-nav-link'}>
                  {item.label}
                </NavLink>
              ))}
              <ComingSoonButton className="storefront-nav-future">Ưu đãi</ComingSoonButton>
              <ComingSoonButton className="storefront-nav-future">Tin tức</ComingSoonButton>
              <ComingSoonButton className="storefront-nav-future">Liên hệ</ComingSoonButton>
            </nav>
            <div className="storefront-actions">
              <ComingSoonButton className="storefront-future-action" icon="heart">Yêu thích</ComingSoonButton>
              <ComingSoonButton className="storefront-future-action" icon="compare">So sánh</ComingSoonButton>
              {user?.roleName === ROLES.CUSTOMER && <Link className="storefront-cart" to="/cart"
                aria-label={`Giỏ hàng, ${itemCount} sản phẩm`} title={cartError ? 'Chưa đồng bộ được giỏ hàng' : undefined}>
                <ShopIcon name="cart" /><span>Giỏ hàng</span>
                <span className="topbar-cart-count" aria-hidden="true">{isCartLoading && !itemCount ? '…' : itemCount}</span>
              </Link>}
              {user ? <>
                <Link className="storefront-account" to="/account" aria-label={`Tài khoản: ${user.displayName || user.username}`}><ShopIcon name="user" /><span>{user.displayName || user.username}</span></Link>
                <button className="button button-quiet storefront-logout" onClick={handleLogout} disabled={isSigningOut}>
                  {isSigningOut ? 'Đang thoát…' : 'Đăng xuất'}
                </button>
              </> : isLoading ? <span role="status">Đang kiểm tra phiên…</span> : <Link className="button" to="/login">Đăng nhập</Link>}
            </div>
          </div>
          {user?.roleName === ROLES.CUSTOMER && <div className="storefront-context">
            <div className="storefront-context-inner">
              <span className="storefront-context-label"><ShopIcon name="pin" /> Chọn chi nhánh</span>
              <label className="storefront-warehouse">
                <span className="sr-only">Chi nhánh mua hàng</span>
                <select id="shopping-warehouse" value={selectedWarehouseId} disabled={isWarehouseLoading || isWarehouseChanging}
                  title={warehouseError || undefined} onChange={(event) => selectWarehouse(event.target.value)}>
                  <option value="" disabled>Chọn chi nhánh</option>
                  {warehouses.map((warehouse) => <option key={warehouse.warehouseId} value={warehouse.warehouseId}>
                    {warehouse.warehouseName}
                  </option>)}
                </select>
              </label>
              <span className="storefront-context-help">Tồn kho và giỏ hàng được tính theo chi nhánh này</span>
            </div>
          </div>}
        </header>
        <main id="main-content" className="storefront-content">{categories.error && <p id="storefront-category-error" className="auth-alert" role="alert">Chưa tải được danh mục tìm kiếm. <button type="button" onClick={categories.retry}>Thử lại</button></p>}{warehouseError && <p className="auth-alert" role="alert">{warehouseError}</p>}{outlet}</main>
        <footer className="storefront-footer"><div className="shop-footer-inner">
          <div><Link className="brand" to="/"><span className="brand-mark" aria-hidden="true">Đ</span>{projectConfig.appName}</Link><p>Khám phá thiết bị công nghệ.<br />Mua sắm tại chi nhánh bạn chọn.</p></div>
          <div><h2>Mua sắm</h2><Link to="/products">Tất cả sản phẩm</Link><Link to={user ? '/cart' : '/login'}>Giỏ hàng</Link><ComingSoonButton>Ưu đãi</ComingSoonButton></div>
          <div><h2>Tài khoản</h2><Link to={user ? '/my-orders' : '/login'}>Đơn hàng của tôi</Link><Link to={user ? '/my-warranties' : '/login'}>Bảo hành của tôi</Link><Link to={user ? '/account' : '/login'}>Thông tin tài khoản</Link></div>
          <div><h2>Hỗ trợ</h2><ComingSoonButton>Liên hệ cửa hàng</ComingSoonButton><ComingSoonButton>Hướng dẫn mua hàng</ComingSoonButton><ComingSoonButton>Chính sách giao hàng</ComingSoonButton></div>
        </div><div className="shop-footer-bottom"><span>{projectConfig.appName} · Thiết bị công nghệ</span><span>COD · Trả góp nội bộ</span></div></footer>
      </div>
    );
  }

  return (
    <div className="app-shell management-shell">
      <a className="skip-link" href="#main-content">Đến nội dung chính</a>
      <aside className="sidebar">
        <div className="sidebar-heading">
          <Link to="/" className="brand"><span className="brand-mark" aria-hidden="true">Đ</span>{projectConfig.appName}</Link>
          <button type="button" className="sidebar-toggle" aria-expanded={managementMenuOpen} aria-controls="management-navigation"
            onClick={() => setManagementMenuOpen((current) => !current)}>{managementMenuOpen ? 'Đóng' : 'Menu'}</button>
        </div>
        <p className="sidebar-caption">HỆ THỐNG QUẢN LÝ</p>
        <nav id="management-navigation" className={managementMenuOpen ? 'sidebar-nav-open' : ''} aria-label="Điều hướng quản trị">
          {managementNavigation.filter((item) => !item.roles || item.roles.includes(user?.roleName)).map((item) => (
            <NavLink key={item.to} to={item.to} end={item.end} onClick={() => setManagementMenuOpen(false)} className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}>
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-note">Điện Việt<br />Vận hành cửa hàng thiết bị điện</div>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <span className="management-label">Khu vực quản trị</span>
          <div className="account-actions">
            <Link to="/account">{user.displayName || user.username} · {user.roleName}</Link>
            <button className="button button-quiet" onClick={handleLogout} disabled={isSigningOut}>
              {isSigningOut ? 'Đang đăng xuất…' : 'Đăng xuất'}
            </button>
          </div>
        </header>
        <main id="main-content" className="main-content">{outlet}</main>
        <footer className="footer">{projectConfig.appName} · Hệ thống quản lý</footer>
      </div>
    </div>
  );
}
