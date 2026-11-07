import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router';
import { AuthContext } from '../../auth/AuthContext';
import { customerApi } from './api/customerApi';
import CustomersPage from './pages/CustomersPage';
import CustomerDetailPage from './pages/CustomerDetailPage';

vi.mock('./api/customerApi', () => ({ customerApi: { list: vi.fn(), detail: vi.fn(), history: vi.fn(), create: vi.fn(), update: vi.fn() } }));

const summary = { totalOrders: 0, deliveredOrders: 0, cancelledOrders: 0, totalSpend: 0 };
const customer = {
  customerId: 'c1', userId: 'u1', fullName: 'Lê Văn Cường', username: '0987654321', email: 'noemail-x@customer.invalid',
  phone: '0987654321', address: '12 Lê Lợi', accountStatus: 'ACTIVE', loyaltyPoint: 0, createdAt: '2026-11-06T03:00:00Z', orderSummary: summary,
};

function mount(roleName, path = '/customers') {
  return render(<MemoryRouter initialEntries={[path]}><AuthContext.Provider value={{ user: { userId: 'staff', roleName }, invalidateSession: vi.fn() }}>
    <Routes><Route path="/customers" element={<CustomersPage />} /><Route path="/customers/:customerId" element={<CustomerDetailPage />} /></Routes>
  </AuthContext.Provider></MemoryRouter>);
}

beforeEach(() => {
  vi.resetAllMocks();
  customerApi.list.mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  customerApi.detail.mockResolvedValue(customer);
  customerApi.history.mockResolvedValue({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 });
});
afterEach(() => cleanup());

it('sales staff registers a walk-in customer and lands on the new profile', async () => {
  customerApi.create.mockResolvedValue(customer);
  mount('SALES_STAFF');
  fireEvent.click(await screen.findByRole('button', { name: '+ Thêm khách hàng' }));
  fireEvent.change(screen.getByLabelText('Họ và tên'), { target: { value: ' Lê Văn Cường ' } });
  fireEvent.change(screen.getByLabelText('Số điện thoại'), { target: { value: '0987654321' } });
  fireEvent.change(screen.getByLabelText('Mật khẩu ban đầu'), { target: { value: 'KhachHang1' } });
  fireEvent.click(screen.getByRole('button', { name: 'Tạo khách hàng' }));
  await waitFor(() => expect(customerApi.create).toHaveBeenCalledWith({
    fullName: 'Lê Văn Cường', phone: '0987654321', email: null, address: null, username: null, password: 'KhachHang1' }));
  expect(await screen.findByText('Đã tạo khách hàng.')).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: 'Lê Văn Cường' })).toBeInTheDocument();
});

it('shows the server conflict when the phone number already belongs to another customer', async () => {
  customerApi.create.mockRejectedValue(Object.assign(new Error('Số điện thoại đã thuộc về một khách hàng khác'), { status: 409 }));
  mount('MANAGER');
  fireEvent.click(await screen.findByRole('button', { name: '+ Thêm khách hàng' }));
  fireEvent.change(screen.getByLabelText('Họ và tên'), { target: { value: 'Người khác' } });
  fireEvent.change(screen.getByLabelText('Số điện thoại'), { target: { value: '0987654321' } });
  fireEvent.change(screen.getByLabelText('Mật khẩu ban đầu'), { target: { value: 'KhachHang1' } });
  fireEvent.click(screen.getByRole('button', { name: 'Tạo khách hàng' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Số điện thoại đã thuộc về một khách hàng khác');
});

it('warranty technicians can look customers up but not create them', async () => {
  mount('WARRANTY_TECHNICIAN');
  await screen.findByText('Không có khách hàng phù hợp');
  expect(screen.queryByRole('button', { name: '+ Thêm khách hàng' })).not.toBeInTheDocument();
});

it('edits contact details without exposing the placeholder email', async () => {
  customerApi.update.mockResolvedValue({ ...customer, address: '5 Nguyễn Huệ' });
  mount('SALES_STAFF', '/customers/c1');
  expect(await screen.findByText('Chưa có')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Sửa thông tin' }));
  expect(screen.getByLabelText('Email (không bắt buộc)')).toHaveValue('');
  expect(screen.queryByLabelText('Mật khẩu ban đầu')).not.toBeInTheDocument();
  fireEvent.change(screen.getByLabelText('Địa chỉ (không bắt buộc)'), { target: { value: '5 Nguyễn Huệ' } });
  fireEvent.click(screen.getByRole('button', { name: 'Lưu thay đổi' }));
  await waitFor(() => expect(customerApi.update).toHaveBeenCalledWith('c1', {
    fullName: 'Lê Văn Cường', phone: '0987654321', email: null, address: '5 Nguyễn Huệ' }));
  expect(await screen.findByText('Đã cập nhật thông tin khách hàng.')).toBeInTheDocument();
});
