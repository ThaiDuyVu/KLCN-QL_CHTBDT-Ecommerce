import { useMemo, useState } from 'react';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Link, MemoryRouter, Route, Routes, useLocation, useParams } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthContext } from '../../auth/AuthContext';
import AppLayout from '../../components/layout/AppLayout';
import { useWarehouse } from '../../hooks/useWarehouse';
import CartProvider from '../cart/CartProvider';
import { cartApi } from '../cart/api/cartApi';
import WarehouseProvider from './WarehouseProvider';
import { warehouseApi } from './api/warehouseApi';

vi.mock('../cart/api/cartApi', () => ({ cartApi: { get: vi.fn(), selectWarehouse: vi.fn(), add: vi.fn(), quantity: vi.fn(), remove: vi.fn() } }));
vi.mock('./api/warehouseApi', () => ({ warehouseApi: { listAll: vi.fn(), list: vi.fn() } }));
vi.mock('../categories/api/categoryApi', () => ({ categoryApi: { list: vi.fn(() => Promise.resolve([])) } }));

const W1 = { warehouseId: 'w1', warehouseName: 'Chi nhánh Quận 1', address: '12 Lê Lợi, Quận 1', status: 'ACTIVE' };
const W2 = { warehouseId: 'w2', warehouseName: 'Chi nhánh Thủ Đức', address: '45 Võ Văn Ngân, Thủ Đức', status: 'ACTIVE' };
const W3 = { warehouseId: 'w3', warehouseName: 'Kho Bình Dương cũ', address: 'KCN Sóng Thần', status: 'INACTIVE' };
const NO_CART = { cartId: null, warehouseId: null, items: [], subtotal: 0 };
const cartAt = (warehouseId, items = []) => ({ cartId: 'c1', warehouseId, items, subtotal: 0 });
const ITEM = { cartItemId: 'i1', variantId: 'v1', productName: 'Xiaomi 15', sku: 'X15', quantity: 2 };
const CUSTOMER_A = { userId: 'cust-a', username: 'khach.a', displayName: 'Khách A', roleName: 'CUSTOMER' };
const CUSTOMER_B = { userId: 'cust-b', username: 'khach.b', displayName: 'Khách B', roleName: 'CUSTOMER' };
const confirmKey = (userId) => `storefrontBranchConfirmed.v1.${userId}`;
const deferred = () => { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b; }); return { promise, resolve, reject }; };

function Probe({ name }) {
  const { selectedWarehouseId } = useWarehouse();
  const { productId } = useParams();
  const location = useLocation();
  return <section><h1>{name}</h1><p data-testid="probe">{location.pathname}{location.search}|{productId || ''}|{selectedWarehouseId}</p>
    <Link to="/products?keyword=dell">Sang danh mục</Link></section>;
}

function TestAuth({ children, initialUser, invalidateSession }) {
  const [user, setUser] = useState(initialUser);
  const value = useMemo(() => ({
    user, isAuthenticated: Boolean(user), isLoading: false, isSigningOut: false, sessionError: null,
    signOut: async () => setUser(null), invalidateSession: invalidateSession || (() => setUser(null)), refreshSession: async () => user,
  }), [user, invalidateSession]);
  return <AuthContext.Provider value={value}>
    <button type="button" hidden onClick={() => setUser(CUSTOMER_A)}>__login-a</button>
    <button type="button" hidden onClick={() => setUser(CUSTOMER_B)}>__login-b</button>
    {children}
  </AuthContext.Provider>;
}

function show({ path = '/', user = CUSTOMER_A, invalidateSession } = {}) {
  return render(<MemoryRouter initialEntries={[path]}>
    <TestAuth initialUser={user} invalidateSession={invalidateSession}><CartProvider><WarehouseProvider>
      <Routes>
        <Route path="login" element={<h1>Đăng nhập</h1>} />
        <Route element={<AppLayout />}>
          <Route index element={<Probe name="Trang chủ" />} />
          <Route path="products" element={<Probe name="Cửa hàng" />} />
          <Route path="products/:productId" element={<Probe name="Chi tiết sản phẩm" />} />
          <Route path="account" element={<Probe name="Tài khoản" />} />
          <Route path="inventory" element={<Probe name="Kho hàng" />} />
          <Route path="forbidden" element={<h1>Không có quyền</h1>} />
        </Route>
      </Routes>
    </WarehouseProvider></CartProvider></TestAuth>
  </MemoryRouter>);
}

const dialog = () => screen.findByRole('dialog', { name: 'Chọn chi nhánh mua sắm' });
const continueButton = () => screen.getByRole('button', { name: /Tiếp tục mua sắm|Xóa giỏ và tiếp tục|Đang xác nhận…/ });
const loginAs = (who) => fireEvent.click(screen.getByText(`__login-${who}`, { selector: 'button' }));

beforeEach(() => {
  sessionStorage.clear(); localStorage.clear();
  warehouseApi.listAll.mockResolvedValue([W1, W2, W3]);
  cartApi.get.mockResolvedValue(NO_CART);
  cartApi.selectWarehouse.mockImplementation((warehouseId, clearItems) => Promise.resolve(cartAt(warehouseId, clearItems ? [] : [])));
});
afterEach(() => vi.restoreAllMocks());

describe('A. Mandatory branch dialog', () => {
  it('blocks the storefront for a customer, lists only ACTIVE branches with name and address, and needs a choice', async () => {
    show();
    const modal = await dialog();
    expect(modal).toHaveAttribute('aria-modal', 'true');
    expect(modal).toHaveAccessibleDescription(/Sản phẩm, tồn kho và giỏ hàng/);
    expect(screen.queryByRole('heading', { name: 'Trang chủ' })).not.toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: 'Điều hướng cửa hàng' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Giỏ hàng/ })).not.toBeInTheDocument();
    // The shop stays visible behind the dialog, but inert and hidden from assistive technology.
    const backdrop = document.querySelector('.storefront-shell.is-branch-gated');
    expect(backdrop).toHaveAttribute('inert');
    expect(backdrop).toHaveAttribute('aria-hidden', 'true');
    expect(backdrop.contains(modal)).toBe(false);
    const radios = within(modal).getAllByRole('radio');
    expect(radios).toHaveLength(2);
    expect(within(modal).getByRole('radio', { name: /Chi nhánh Quận 1.*12 Lê Lợi, Quận 1/ })).not.toBeChecked();
    expect(within(modal).getByText('45 Võ Văn Ngân, Thủ Đức')).toBeInTheDocument();
    expect(within(modal).queryByText(/Kho Bình Dương cũ/)).not.toBeInTheDocument();
    expect(continueButton()).toBeDisabled();
    expect(within(modal).queryByRole('button', { name: /Bỏ qua|Đóng/ })).not.toBeInTheDocument();
    expect(cartApi.selectWarehouse).not.toHaveBeenCalled();
  });

  it('cannot be dismissed with Escape or a backdrop click, and choosing only changes the draft', async () => {
    const user = userEvent.setup(); show();
    const modal = await dialog();
    await user.click(within(modal).getByRole('radio', { name: /Thủ Đức/ }));
    expect(cartApi.selectWarehouse).not.toHaveBeenCalled();
    await user.keyboard('{Escape}');
    fireEvent.mouseDown(document.querySelector('.branch-gate')); fireEvent.click(document.querySelector('.branch-gate'));
    expect(screen.getByRole('dialog', { name: 'Chọn chi nhánh mua sắm' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Trang chủ' })).not.toBeInTheDocument();
    expect(sessionStorage.getItem(confirmKey('cust-a'))).toBeNull();
  });

  it('moves focus into the dialog, traps Tab, and supports arrow keys between options', async () => {
    const user = userEvent.setup(); show();
    const modal = await dialog();
    await waitFor(() => expect(modal.contains(document.activeElement)).toBe(true));
    for (let i = 0; i < 8; i += 1) { await user.tab(); expect(modal.contains(document.activeElement)).toBe(true); }
    for (let i = 0; i < 8; i += 1) { await user.tab({ shift: true }); expect(modal.contains(document.activeElement)).toBe(true); }
    const first = within(modal).getByRole('radio', { name: /Quận 1/ });
    first.focus(); await user.keyboard(' ');
    expect(first).toBeChecked();
    await user.keyboard('{ArrowDown}');
    expect(within(modal).getByRole('radio', { name: /Thủ Đức/ })).toBeChecked();
    expect(continueButton()).toBeEnabled();
  });

  it('offers an accent-insensitive search by name or address for longer lists', async () => {
    const user = userEvent.setup();
    const many = Array.from({ length: 8 }, (_, i) => ({ warehouseId: `m${i}`, warehouseName: `Chi nhánh số ${i}`, address: i === 5 ? '99 Đường Nguyễn Huệ' : `Đường ${i}`, status: 'ACTIVE' }));
    warehouseApi.listAll.mockResolvedValue(many);
    show(); const modal = await dialog();
    await user.type(within(modal).getByLabelText('Tìm chi nhánh'), 'nguyen hue');
    expect(within(modal).getAllByRole('radio')).toHaveLength(1);
    expect(within(modal).getByRole('radio', { name: /Chi nhánh số 5/ })).toBeInTheDocument();
    await user.clear(within(modal).getByLabelText('Tìm chi nhánh')); await user.type(within(modal).getByLabelText('Tìm chi nhánh'), 'SO 7');
    expect(within(modal).getAllByRole('radio')).toHaveLength(1);
    await user.clear(within(modal).getByLabelText('Tìm chi nhánh')); await user.type(within(modal).getByLabelText('Tìm chi nhánh'), 'khong co');
    expect(within(modal).getByText('Không tìm thấy chi nhánh phù hợp.')).toBeInTheDocument();
  });
});

describe('B. Branch validity', () => {
  it('confirms a valid branch through the existing cart selection and opens the requested page', async () => {
    const user = userEvent.setup(); show({ path: '/products/abc?color=den' });
    const modal = await dialog();
    await user.click(within(modal).getByRole('radio', { name: /Thủ Đức/ }));
    await user.click(continueButton());
    expect(await screen.findByRole('heading', { name: 'Chi tiết sản phẩm' })).toBeInTheDocument();
    expect(cartApi.selectWarehouse).toHaveBeenCalledTimes(1); expect(cartApi.selectWarehouse).toHaveBeenCalledWith('w2', false);
    expect(screen.getByTestId('probe')).toHaveTextContent('/products/abc?color=den|abc|w2');
    expect(sessionStorage.getItem(confirmKey('cust-a'))).toBe('w2');
    expect(localStorage.getItem('shoppingWarehouseId')).toBe('w2');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(document.activeElement).toBe(document.getElementById('main-content'));
  });

  it('preselects a remembered ACTIVE branch but still asks for confirmation in a new session', async () => {
    localStorage.setItem('shoppingWarehouseId', 'w1');
    show(); const modal = await dialog();
    expect(within(modal).getByRole('radio', { name: /Quận 1/ })).toBeChecked();
    expect(continueButton()).toBeEnabled();
    expect(screen.queryByRole('heading', { name: 'Trang chủ' })).not.toBeInTheDocument();
  });

  it.each([['an unknown id', 'deadbeef-0000-4000-8000-000000000000'], ['an INACTIVE branch', 'w3']])('does not preselect %s from localStorage', async (_, id) => {
    localStorage.setItem('shoppingWarehouseId', id);
    show(); const modal = await dialog();
    expect(within(modal).getAllByRole('radio').every((radio) => !radio.checked)).toBe(true);
    expect(continueButton()).toBeDisabled();
  });

  it('does not trust a cart that points at an INACTIVE branch', async () => {
    cartApi.get.mockResolvedValue(cartAt('w3', [ITEM]));
    sessionStorage.setItem(confirmKey('cust-a'), 'w3');
    show(); const modal = await dialog();
    expect(within(modal).getAllByRole('radio').every((radio) => !radio.checked)).toBe(true);
  });

  it('shows a safe exit when no branch is ACTIVE', async () => {
    const user = userEvent.setup(); warehouseApi.listAll.mockResolvedValue([W3]);
    show(); const modal = await dialog();
    expect(within(modal).getByRole('alert')).toHaveTextContent('Hiện chưa có chi nhánh mua sắm khả dụng. Vui lòng liên hệ cửa hàng.');
    expect(within(modal).queryByRole('button', { name: 'Tiếp tục mua sắm' })).not.toBeInTheDocument();
    await user.click(within(modal).getByRole('button', { name: 'Đăng xuất' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('shows loading for a slow list, an error with retry for a failure, then recovers', async () => {
    const user = userEvent.setup(); const slow = deferred();
    warehouseApi.listAll.mockReturnValueOnce(slow.promise);
    show(); const modal = await dialog();
    expect(within(modal).getByRole('status')).toHaveTextContent('Đang tải danh sách chi nhánh…');
    act(() => slow.reject(Object.assign(new Error('Lỗi máy chủ'), { status: 500 })));
    expect(await within(modal).findByRole('alert')).toHaveTextContent('Không thể tải danh sách chi nhánh. Vui lòng thử lại.');
    await user.click(within(modal).getByRole('button', { name: 'Thử lại' }));
    expect(await within(modal).findByRole('radio', { name: /Quận 1/ })).toBeInTheDocument();
    expect(warehouseApi.listAll).toHaveBeenCalledTimes(2);
  });

  it.each([401, 403])('handles HTTP %s from the branch list without opening the shop', async (status) => {
    const invalidateSession = vi.fn();
    warehouseApi.listAll.mockRejectedValue(Object.assign(new Error('denied'), { status }));
    show({ invalidateSession }); const modal = await dialog();
    expect(await within(modal).findByRole('alert')).toHaveTextContent('Không thể tải danh sách chi nhánh');
    expect(invalidateSession).toHaveBeenCalledTimes(status === 401 ? 1 : 0);
    expect(screen.queryByRole('heading', { name: 'Trang chủ' })).not.toBeInTheDocument();
  });
});

describe('C. Cart synchronization', () => {
  it('keeps the gate (loading, no confirm) while the cart is still loading', async () => {
    const cart = deferred(); cartApi.get.mockReturnValue(cart.promise);
    show(); const modal = await dialog();
    expect(within(modal).getByRole('status')).toHaveTextContent('Đang tải danh sách chi nhánh…');
    expect(within(modal).queryByRole('button', { name: 'Tiếp tục mua sắm' })).not.toBeInTheDocument();
    act(() => cart.resolve(NO_CART));
    expect(await within(modal).findByRole('radio', { name: /Quận 1/ })).toBeInTheDocument();
  });

  it('a cart that failed to load is not treated as empty; retry reloads it', async () => {
    const user = userEvent.setup();
    cartApi.get.mockRejectedValueOnce(Object.assign(new Error('down'), { status: 503 }));
    show(); const modal = await dialog();
    expect(await within(modal).findByRole('alert')).toHaveTextContent('Không thể kiểm tra giỏ hàng hiện tại');
    cartApi.get.mockResolvedValue(cartAt('w1', [ITEM]));
    await user.click(within(modal).getByRole('button', { name: 'Thử lại' }));
    expect(await within(modal).findByRole('radio', { name: /Quận 1/ })).toBeChecked();
  });

  it('confirming the cart\'s own branch does not call the API or touch the items', async () => {
    const user = userEvent.setup(); cartApi.get.mockResolvedValue(cartAt('w1', [ITEM]));
    show(); const modal = await dialog();
    expect(within(modal).getByRole('radio', { name: /Quận 1/ })).toBeChecked();
    expect(within(modal).getByText('Chi nhánh của giỏ hàng hiện tại')).toBeInTheDocument();
    await user.click(continueButton());
    expect(await screen.findByRole('heading', { name: 'Trang chủ' })).toBeInTheDocument();
    expect(cartApi.selectWarehouse).not.toHaveBeenCalled();
  });

  it('switching away from a cart with items asks inside the dialog; keeping the cart changes nothing', async () => {
    const user = userEvent.setup(); cartApi.get.mockResolvedValue(cartAt('w1', [ITEM]));
    const nativeConfirm = vi.spyOn(window, 'confirm');
    show(); const modal = await dialog();
    await user.click(await within(modal).findByRole('radio', { name: /Thủ Đức/ }));
    await user.click(continueButton());
    expect(within(modal).getByRole('alert')).toHaveTextContent('Giỏ hàng đang có 1 sản phẩm thuộc chi nhánh Chi nhánh Quận 1');
    expect(cartApi.selectWarehouse).not.toHaveBeenCalled();
    await user.click(within(modal).getByRole('button', { name: 'Giữ giỏ hàng hiện tại' }));
    expect(cartApi.selectWarehouse).not.toHaveBeenCalled(); expect(nativeConfirm).not.toHaveBeenCalled();
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    await user.click(within(modal).getByRole('radio', { name: /Thủ Đức/ }));
    await user.click(continueButton());
    await user.click(screen.getByRole('button', { name: 'Xóa giỏ và tiếp tục' }));
    expect(await screen.findByRole('heading', { name: 'Trang chủ' })).toBeInTheDocument();
    expect(cartApi.selectWarehouse).toHaveBeenCalledTimes(1); expect(cartApi.selectWarehouse).toHaveBeenCalledWith('w2', true);
  });

  it('a failed selection keeps the dialog open with the backend message', async () => {
    const user = userEvent.setup();
    cartApi.selectWarehouse.mockRejectedValue(Object.assign(new Error('Chi nhánh đã ngừng hoạt động'), { status: 409 }));
    show(); const modal = await dialog();
    await user.click(within(modal).getByRole('radio', { name: /Quận 1/ })); await user.click(continueButton());
    expect(await within(modal).findByRole('alert')).toHaveTextContent('Chi nhánh đã ngừng hoạt động');
    expect(screen.getByRole('dialog')).toBeInTheDocument(); expect(sessionStorage.getItem(confirmKey('cust-a'))).toBeNull();
  });

  it('does not confirm when the returned cart is on a different branch', async () => {
    const user = userEvent.setup(); cartApi.selectWarehouse.mockResolvedValue(cartAt('w1'));
    show(); const modal = await dialog();
    await user.click(within(modal).getByRole('radio', { name: /Thủ Đức/ })); await user.click(continueButton());
    expect(await within(modal).findByRole('alert')).toHaveTextContent('Chưa xác minh được chi nhánh của giỏ hàng');
    expect(sessionStorage.getItem(confirmKey('cust-a'))).toBeNull();
  });

  it('sends a single request on double submit', async () => {
    const user = userEvent.setup(); const pending = deferred(); cartApi.selectWarehouse.mockReturnValue(pending.promise);
    show(); const modal = await dialog();
    await user.click(within(modal).getByRole('radio', { name: /Quận 1/ }));
    await user.dblClick(continueButton());
    fireEvent.submit(modal.querySelector('form'));
    expect(cartApi.selectWarehouse).toHaveBeenCalledTimes(1);
    expect(continueButton()).toHaveTextContent('Đang xác nhận…');
    act(() => pending.resolve(cartAt('w1')));
    expect(await screen.findByRole('heading', { name: 'Trang chủ' })).toBeInTheDocument();
  });
});

describe('D. Session lifecycle', () => {
  it('refresh in the same session skips the dialog when the confirmation still matches backend data', async () => {
    sessionStorage.setItem(confirmKey('cust-a'), 'w1'); cartApi.get.mockResolvedValue(cartAt('w1', [ITEM]));
    show();
    expect(await screen.findByRole('heading', { name: 'Trang chủ' })).toBeInTheDocument();
    expect(cartApi.selectWarehouse).not.toHaveBeenCalled();
  });

  it('a session marker is not enough when the cart itself is not synced to that branch', async () => {
    sessionStorage.setItem(confirmKey('cust-a'), 'w1'); localStorage.setItem('shoppingWarehouseId', 'w1'); cartApi.get.mockResolvedValue(NO_CART);
    show(); const modal = await dialog();
    expect(within(modal).getByRole('radio', { name: /Quận 1/ })).toBeChecked();
    expect(screen.queryByRole('heading', { name: 'Trang chủ' })).not.toBeInTheDocument();
  });

  it('a confirmation that no longer matches the cart branch asks again', async () => {
    sessionStorage.setItem(confirmKey('cust-a'), 'w1'); cartApi.get.mockResolvedValue(cartAt('w2'));
    show(); const modal = await dialog();
    expect(within(modal).getByRole('radio', { name: /Thủ Đức/ })).toBeChecked();
  });

  it('navigating between shop pages after confirming does not show the dialog again', async () => {
    const user = userEvent.setup(); show();
    const modal = await dialog();
    await user.click(within(modal).getByRole('radio', { name: /Quận 1/ })); await user.click(continueButton());
    await user.click(await screen.findByRole('link', { name: 'Sang danh mục' }));
    expect(await screen.findByRole('heading', { name: 'Cửa hàng' })).toBeInTheDocument();
    expect(screen.getByTestId('probe')).toHaveTextContent('/products?keyword=dell||w1');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(warehouseApi.listAll).toHaveBeenCalledTimes(1);
  });

  it('logout clears the confirmation; logging in again (same or other account) asks again', async () => {
    const user = userEvent.setup(); sessionStorage.setItem(confirmKey('cust-a'), 'w1'); cartApi.get.mockResolvedValue(cartAt('w1'));
    show();
    await screen.findByRole('heading', { name: 'Trang chủ' });
    await user.click(screen.getByRole('button', { name: 'Đăng xuất' }));
    await waitFor(() => expect(sessionStorage.getItem(confirmKey('cust-a'))).toBeNull());
    act(() => loginAs('a'));
    const again = await dialog();
    // Same account, new login: confirm again, then switch straight to another account.
    await user.click(within(again).getByRole('radio', { name: /Quận 1/ })); await user.click(continueButton());
    await screen.findByRole('heading', { name: 'Trang chủ' });
    expect(sessionStorage.getItem(confirmKey('cust-a'))).toBe('w1');
    act(() => loginAs('b'));
    expect(await dialog()).toBeInTheDocument();
    expect(sessionStorage.getItem(confirmKey('cust-a'))).toBeNull();
    expect(sessionStorage.getItem(confirmKey('cust-b'))).toBeNull();
  });

  it('a late selection result from the previous account confirms nothing', async () => {
    const user = userEvent.setup(); const pending = deferred(); cartApi.selectWarehouse.mockReturnValue(pending.promise);
    show(); const modal = await dialog();
    await user.click(within(modal).getByRole('radio', { name: /Quận 1/ })); await user.click(continueButton());
    act(() => loginAs('b'));
    await act(async () => { pending.resolve(cartAt('w1')); await pending.promise; });
    expect(await dialog()).toBeInTheDocument();
    expect(sessionStorage.getItem(confirmKey('cust-a'))).toBeNull();
    expect(sessionStorage.getItem(confirmKey('cust-b'))).toBeNull();
    expect(screen.queryByRole('heading', { name: 'Trang chủ' })).not.toBeInTheDocument();
  });

  it('gates the default post-login page (/account) but keeps /forbidden reachable', async () => {
    const view = show({ path: '/account' });
    expect(await dialog()).toBeInTheDocument();
    expect(screen.queryByRole('heading', { level: 1, name: 'Tài khoản' })).not.toBeInTheDocument();
    view.unmount();
    show({ path: '/forbidden' });
    expect(await screen.findByRole('heading', { name: 'Không có quyền' })).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});

describe('E. Non-regression', () => {
  it.each(['STAFF', 'ADMIN', 'MANAGER'])('%s uses the management layout without the shopping dialog', async (roleName) => {
    show({ path: '/inventory', user: { userId: `u-${roleName}`, roleName, displayName: roleName } });
    expect(await screen.findByRole('heading', { name: 'Kho hàng' })).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(cartApi.selectWarehouse).not.toHaveBeenCalled();
  });

  it('a guest sees the shop without the dialog and without calling the protected branch API', async () => {
    show({ user: null });
    expect(await screen.findByRole('heading', { name: 'Trang chủ' })).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(warehouseApi.listAll).not.toHaveBeenCalled(); expect(cartApi.get).not.toHaveBeenCalled();
  });

  it('the header selector still changes branch with the existing confirmation and no onboarding dialog', async () => {
    const user = userEvent.setup(); sessionStorage.setItem(confirmKey('cust-a'), 'w1'); cartApi.get.mockResolvedValue(cartAt('w1', [ITEM]));
    const nativeConfirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true);
    show(); await screen.findByRole('heading', { name: 'Trang chủ' });
    expect(screen.getByText('Chi nhánh mua sắm')).toBeInTheDocument();
    expect(screen.getByText(/12 Lê Lợi, Quận 1 · Tồn kho/)).toBeInTheDocument();
    const select = screen.getByLabelText('Đổi chi nhánh mua sắm');
    expect(select).toHaveValue('w1');
    await user.selectOptions(select, 'w2');
    expect(nativeConfirm).toHaveBeenCalledTimes(1); expect(cartApi.selectWarehouse).not.toHaveBeenCalled();
    await user.selectOptions(select, 'w2');
    await waitFor(() => expect(cartApi.selectWarehouse).toHaveBeenCalledWith('w2', true));
    expect(await screen.findByTestId('probe')).toHaveTextContent('|w2');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(sessionStorage.getItem(confirmKey('cust-a'))).toBe('w2');
  });
});
