import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi, afterEach } from 'vitest';
import App from './App';
import { AuthProvider } from './auth/AuthContext';

function renderApp(path = '/', role = null) {
  const initialUser = role ? { id: 'test', name: 'Người dùng demo', role } : null;
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider initialUser={initialUser}><App /></AuthProvider>
    </MemoryRouter>,
  );
}

afterEach(() => vi.unstubAllGlobals());

describe('Skeleton routes', () => {
  it.each([
    ['/', 'Thiết bị điện cho mọi công trình.'],
    ['/products', 'Sản phẩm'],
    ['/categories', 'Danh mục'],
  ])('opens public route %s without API calls', (path, heading) => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    renderApp(path);
    expect(screen.getByRole('heading', { level: 1, name: heading })).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('redirects anonymous users to login', () => {
    renderApp('/inventory');
    expect(screen.getByRole('heading', { name: 'Đăng nhập demo' })).toBeInTheDocument();
  });

  it('returns to the requested protected page after demo login', async () => {
    const user = userEvent.setup();
    renderApp('/inventory');
    await user.selectOptions(screen.getByLabelText('Vai trò demo'), 'STAFF');
    await user.click(screen.getByRole('button', { name: 'Vào phiên demo' }));
    expect(screen.getByRole('heading', { level: 1, name: 'Kho hàng' })).toBeInTheDocument();
  });

  it('denies customers access to management pages', () => {
    renderApp('/inventory', 'CUSTOMER');
    expect(screen.getByRole('heading', { name: 'Không có quyền truy cập' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Kho hàng' })).not.toBeInTheDocument();
  });

  it('denies staff direct access to user management', () => {
    renderApp('/user-management', 'STAFF');
    expect(screen.getByRole('heading', { name: 'Không có quyền truy cập' })).toBeInTheDocument();
  });

  it('allows admins to access user management', () => {
    renderApp('/user-management', 'ADMIN');
    expect(screen.getByRole('heading', { level: 1, name: 'Quản lý người dùng' })).toBeInTheDocument();
  });

  it('ends the demo session and protects the account page again', async () => {
    const user = userEvent.setup();
    renderApp('/account', 'ADMIN');
    await user.click(screen.getByRole('button', { name: 'Đăng xuất' }));
    expect(screen.getByRole('heading', { name: 'Đăng nhập demo' })).toBeInTheDocument();
  });

  it('shows 404 for unknown routes', () => {
    renderApp('/unknown-route');
    expect(screen.getByRole('heading', { name: 'Không tìm thấy trang' })).toBeInTheDocument();
  });
});
