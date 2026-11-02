import { useMemo, useState } from 'react';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { AuthContext } from '../auth/AuthContext';
import { ApiError } from '../api/apiClient';
import RegisterPage from './RegisterPage';

function TestAuth({ children, signUp }) {
  const [user, setUser] = useState(null);
  const value = useMemo(() => ({
    user, isAuthenticated: Boolean(user), isLoading: false, sessionError: null,
    signUp: async (details) => { const session = await signUp(details); setUser(session); return session; },
  }), [user, signUp]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

function show(signUp = vi.fn(), state) {
  render(<MemoryRouter initialEntries={[{ pathname: '/register', state }]}>
    <TestAuth signUp={signUp}>
      <Routes>
        <Route path="register" element={<RegisterPage />} />
        <Route path="account" element={<h1>Tài khoản</h1>} />
        <Route path="cart" element={<h1>Giỏ hàng</h1>} />
        <Route path="login" element={<h1>Đăng nhập</h1>} />
      </Routes>
    </TestAuth>
  </MemoryRouter>);
  return signUp;
}

async function fill(user, { fullName = 'Trần Thị B', username = 'tranthib', password = 'matkhau123', confirm = password } = {}) {
  if (fullName) await user.type(screen.getByLabelText('Họ và tên'), fullName);
  if (username) await user.type(screen.getByLabelText('Tên đăng nhập'), username);
  if (password) await user.type(screen.getByLabelText('Mật khẩu'), password);
  if (confirm) await user.type(screen.getByLabelText('Nhập lại mật khẩu'), confirm);
}

describe('RegisterPage', () => {
  it('shows the password rules as they are met', async () => {
    const user = userEvent.setup(); show();
    const rules = screen.getByRole('list', { name: 'Yêu cầu mật khẩu' });
    expect(within(rules).getAllByText(/chưa đạt/)).toHaveLength(3);
    await user.type(screen.getByLabelText('Mật khẩu'), 'abc');
    expect(within(rules).getByText('Có ít nhất một chữ cái').closest('li')).toHaveClass('is-met');
    expect(within(rules).getByText('Ít nhất 8 ký tự').closest('li')).not.toHaveClass('is-met');
    await user.type(screen.getByLabelText('Mật khẩu'), '12345');
    expect(within(rules).queryAllByText(/chưa đạt/)).toHaveLength(0);
  });

  it.each([
    ['a password shorter than 8 characters', { password: 'abc1234' }, /ít nhất 8 ký tự, gồm cả chữ và số/],
    ['a letter-only password', { password: 'matkhaumoi' }, /gồm cả chữ và số/],
    ['a digit-only password', { password: '12345678' }, /gồm cả chữ và số/],
    ['mismatched passwords', { confirm: 'matkhau124' }, /không khớp/],
    ['a username with spaces', { username: 'tran thi b' }, /Tên đăng nhập gồm 4–50 ký tự/],
    ['a missing full name', { fullName: '' }, /họ và tên/],
  ])('rejects %s without calling the server', async (_, values, message) => {
    const user = userEvent.setup(); const signUp = show();
    await fill(user, values);
    await user.click(screen.getByRole('button', { name: 'Tạo tài khoản' }));
    expect(screen.getByRole('alert')).toHaveTextContent(message);
    expect(signUp).not.toHaveBeenCalled();
  });

  it('sends trimmed details and returns to the page that required sign-in', async () => {
    const user = userEvent.setup();
    const signUp = show(vi.fn().mockResolvedValue({ userId: 'u1', username: 'tranthib', roleName: 'CUSTOMER' }),
      { from: { pathname: '/cart' } });
    await fill(user, { fullName: '  Trần Thị B ', username: ' tranthib ' });
    await user.click(screen.getByRole('button', { name: 'Tạo tài khoản' }));
    expect(signUp).toHaveBeenCalledWith({ username: 'tranthib', fullName: 'Trần Thị B', password: 'matkhau123' });
    expect(await screen.findByRole('heading', { name: 'Giỏ hàng' })).toBeInTheDocument();
  });

  it('explains a taken username and clears the passwords', async () => {
    const user = userEvent.setup();
    show(vi.fn().mockRejectedValue(new ApiError('Tên đăng nhập đã được sử dụng', { status: 409 })));
    await fill(user);
    await user.click(screen.getByRole('button', { name: 'Tạo tài khoản' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Tên đăng nhập đã được sử dụng. Vui lòng chọn tên khác.');
    expect(screen.getByLabelText('Mật khẩu')).toHaveValue('');
    expect(screen.getByLabelText('Nhập lại mật khẩu')).toHaveValue('');
    expect(screen.getByLabelText('Tên đăng nhập')).toHaveValue('tranthib');
  });

  it('links back to login', () => {
    show();
    expect(screen.getByRole('link', { name: 'Đăng nhập' })).toHaveAttribute('href', '/login');
  });
});
