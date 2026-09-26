import { useMemo, useState } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi, afterEach } from 'vitest';
import App from './App';
import { AuthContext } from './auth/AuthContext';
import { WarehouseContext } from './features/warehouses/warehouseContext';

const warehouseValue = {
  warehouses: [], selectedWarehouse: null, selectedWarehouseId: '', selectWarehouse: vi.fn(),
  isLoading: false, isChanging: false, error: '',
};

function TestAuthProvider({ children, initialRole = null }) {
  const [user, setUser] = useState(initialRole ? {
    userId: 'test', username: initialRole.toLowerCase(), displayName: 'Người dùng demo', roleName: initialRole,
  } : null);
  const value = useMemo(() => ({
    user, isAuthenticated: Boolean(user), isLoading: false, sessionError: null, isSigningOut: false,
    signIn: async ({ username }) => {
      const roleName = username.toLowerCase().includes('admin') ? 'ADMIN' : 'STAFF';
      const session = { userId: 'test', username, displayName: 'Người dùng demo', roleName };
      setUser(session); return session;
    },
    signOut: async () => setUser(null), invalidateSession: () => setUser(null), refreshSession: async () => user,
  }), [user]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

function renderApp(path = '/', role = null) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <TestAuthProvider initialRole={role}>
        <WarehouseContext.Provider value={warehouseValue}><App /></WarehouseContext.Provider>
      </TestAuthProvider>
    </MemoryRouter>,
  );
}

afterEach(() => vi.unstubAllGlobals());

describe('Application routes and role layouts', () => {
  it('opens the public home without API calls', () => {
    const fetchMock = vi.fn(); vi.stubGlobal('fetch', fetchMock); renderApp('/');
    expect(screen.getByRole('heading', { level: 1, name: 'Công nghệ cho từng nhu cầu.' })).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it.each(['/products', '/categories', '/inventory'])('redirects anonymous users from %s to login', (path) => {
    renderApp(path); expect(screen.getByRole('heading', { name: 'Chào mừng trở lại' })).toBeInTheDocument();
  });

  it('returns to the requested protected page after login', async () => {
    const user = userEvent.setup(); renderApp('/inventory');
    await user.type(screen.getByLabelText('Tên đăng nhập'), 'staff');
    await user.type(screen.getByLabelText('Mật khẩu'), 'password');
    await user.click(screen.getByRole('button', { name: 'Đăng nhập' }));
    expect(await screen.findByRole('heading', { level: 1, name: 'Tồn kho' })).toBeInTheDocument();
  });

  it('uses the storefront layout for customers and denies management pages', () => {
    renderApp('/inventory', 'CUSTOMER');
    expect(screen.getByRole('heading', { name: 'Không có quyền truy cập' })).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Điều hướng cửa hàng' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Kho hàng' })).not.toBeInTheDocument();
  });

  it('denies staff direct access to user management', () => {
    renderApp('/user-management', 'STAFF');
    expect(screen.getByRole('heading', { name: 'Không có quyền truy cập' })).toBeInTheDocument();
  });

  it('keeps the management sidebar for admins', () => {
    renderApp('/user-management', 'ADMIN');
    expect(screen.getByRole('heading', { level: 1, name: 'Quản lý người dùng' })).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Điều hướng quản trị' })).toBeInTheDocument();
  });

  it('signs out and protects the account page again', async () => {
    const user = userEvent.setup(); renderApp('/account', 'ADMIN');
    await user.click(screen.getByRole('button', { name: 'Đăng xuất' }));
    expect(await screen.findByRole('heading', { name: 'Chào mừng trở lại' })).toBeInTheDocument();
  });

  it('shows 404 for unknown routes', () => {
    renderApp('/unknown-route');
    expect(screen.getByRole('heading', { name: 'Không tìm thấy trang' })).toBeInTheDocument();
  });
});
