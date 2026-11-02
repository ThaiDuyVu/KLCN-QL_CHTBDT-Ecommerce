import { useMemo, useState } from 'react';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../../App';
import { AuthContext } from '../../auth/AuthContext';
import { WarehouseContext } from '../warehouses/warehouseContext';
import { chatbotApi } from './chatbotApi';

vi.mock('./chatbotApi', () => ({ chatbotApi: { sessions: vi.fn(), session: vi.fn(), history: vi.fn(), send: vi.fn(), close: vi.fn(), status: vi.fn(), traces: vi.fn(), trace: vi.fn(), rate: vi.fn() } }));

const warehouseValue = { warehouses: [], selectedWarehouse: null, selectedWarehouseId: '', selectWarehouse: vi.fn(), isLoading: false, isChanging: false, error: '',
  // A customer session whose branch is already confirmed (the gate itself is covered in BranchSelectionGate.test.jsx).
  listStatus: 'ready', cartReady: true, branchConfirmed: true, reloadWarehouses: vi.fn(), clearRequiredFor: () => false };

function TestAuth({ children, role }) {
  const [user, setUser] = useState(role ? { userId: `u-${role}`, username: role.toLowerCase(), displayName: 'Người dùng thử', roleName: role } : null);
  const value = useMemo(() => ({
    user, isAuthenticated: Boolean(user), isLoading: false, sessionError: null, isSigningOut: false,
    signIn: async ({ username }) => { const next = { userId: `u-${username}`, username, displayName: 'Khách', roleName: 'CUSTOMER' }; setUser(next); return next; },
    signOut: async () => setUser(null), invalidateSession: () => setUser(null), refreshSession: async () => user,
  }), [user]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

function renderApp(path, role = null) {
  return render(<MemoryRouter initialEntries={[path]}><TestAuth role={role}>
    <WarehouseContext.Provider value={warehouseValue}><App /></WarehouseContext.Provider>
  </TestAuth></MemoryRouter>);
}
const storefrontNav = () => screen.getByRole('navigation', { name: 'Điều hướng cửa hàng' });

beforeEach(() => {
  // Layout widgets (cart badge, categories) may call the network; keep them offline.
  vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new Error('offline test'))));
  chatbotApi.sessions.mockResolvedValue([]);
  chatbotApi.session.mockRejectedValue({ status: 404 });
  chatbotApi.history.mockRejectedValue({ status: 404 });
});
afterEach(() => vi.unstubAllGlobals());

describe('Customer chatbot routing and navigation', () => {
  it('shows "Trợ lý AI" to customers and opens /chatbot inside the storefront layout', async () => {
    const user = userEvent.setup(); renderApp('/', 'CUSTOMER');
    const link = within(storefrontNav()).getByRole('link', { name: 'Trợ lý AI' });
    expect(link).toHaveAttribute('href', '/chatbot');
    expect(link).not.toHaveAttribute('target');
    await user.click(link);
    expect(await screen.findByRole('heading', { level: 1, name: 'Trợ lý mua sắm Điện Việt' })).toBeInTheDocument();
    const active = within(storefrontNav()).getByRole('link', { name: 'Trợ lý AI' });
    expect(active).toHaveClass('active'); expect(active).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('contentinfo')).toBeInTheDocument();
    expect(screen.getByRole('banner')).toBeInTheDocument();
  });

  it('is reachable by keyboard from the storefront navigation', async () => {
    const user = userEvent.setup(); renderApp('/', 'CUSTOMER');
    const link = within(storefrontNav()).getByRole('link', { name: 'Trợ lý AI' });
    link.focus(); await user.keyboard('{Enter}');
    expect(await screen.findByRole('heading', { level: 1, name: 'Trợ lý mua sắm Điện Việt' })).toBeInTheDocument();
  });

  it('sends guests through login and returns them to /chatbot', async () => {
    const user = userEvent.setup(); renderApp('/');
    await user.click(within(storefrontNav()).getByRole('link', { name: 'Trợ lý AI' }));
    expect(screen.getByRole('heading', { name: 'Chào mừng trở lại' })).toBeInTheDocument();
    expect(chatbotApi.sessions).not.toHaveBeenCalled();
    await user.type(screen.getByLabelText('Tên đăng nhập'), 'khach');
    await user.type(screen.getByLabelText('Mật khẩu'), 'matkhau');
    await user.click(screen.getByRole('button', { name: 'Đăng nhập' }));
    expect(await screen.findByRole('heading', { level: 1, name: 'Trợ lý mua sắm Điện Việt' })).toBeInTheDocument();
  });

  it.each(['STAFF', 'MANAGER', 'ADMIN'])('denies %s direct access and never loads customer chat data', (role) => {
    renderApp('/chatbot', role);
    expect(screen.getByRole('heading', { name: 'Không có quyền truy cập' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Trợ lý AI' })).not.toBeInTheDocument();
    expect(chatbotApi.sessions).not.toHaveBeenCalled(); expect(chatbotApi.history).not.toHaveBeenCalled();
  });

  it('protects the page again after logout without showing the previous transcript', async () => {
    const user = userEvent.setup();
    chatbotApi.session.mockResolvedValue({ id: 'x', is_active: true, created_at: '2026-10-21T03:00:00Z' });
    chatbotApi.history.mockResolvedValue([{ id: 'm1', session_id: 'x', sender_type: 'USER', message: 'Đơn hàng riêng tư', created_at: '2026-10-21T03:00:00Z' }]);
    renderApp('/chatbot', 'CUSTOMER');
    expect(await screen.findByText('Đơn hàng riêng tư')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Đăng xuất' }));
    expect(await screen.findByRole('heading', { name: 'Chào mừng trở lại' })).toBeInTheDocument();
    expect(screen.queryByText('Đơn hàng riêng tư')).not.toBeInTheDocument();
  });

  it('keeps /chatbot-lab as a separate developer page', async () => {
    chatbotApi.status.mockResolvedValue({ ai_enabled: true });
    renderApp('/chatbot-lab', 'CUSTOMER');
    expect(await screen.findByRole('heading', { level: 1, name: 'Phòng thử Chatbot' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Trợ lý mua sắm Điện Việt' })).not.toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: 'Điều hướng cửa hàng' })).not.toBeInTheDocument();
  });
});
