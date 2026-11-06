import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router';
import { AuthContext } from '../../auth/AuthContext';
import { returnApi } from './api/returnApi';
import OrderReturns from './components/OrderReturns';
import ReturnDetailPage from './pages/ReturnDetailPage';

vi.mock('./api/returnApi', () => ({ returnApi: { create: vi.fn(), mine: vi.fn(), myReturn: vi.fn(), forOrder: vi.fn(), list: vi.fn(), detail: vi.fn(), status: vi.fn() } }));

const order = { orderId: 'o1', orderCode: 'ORD-1', status: 'DELIVERED', items: [
  { orderItemId: 'phone', productName: 'Điện thoại X', serialNumber: 'SN-1', quantity: 1 },
  { orderItemId: 'case', productName: 'Ốp lưng', quantity: 2 },
] };
const request = { returnId: 'r1', returnCode: 'TH-1', orderId: 'o1', orderCode: 'ORD-1', customerName: 'Nguyễn Văn An', status: 'APPROVED',
  reason: 'Máy lỗi', requestDate: '2026-11-06T03:00:00Z', refundAmount: 10000000, allowedStatuses: ['RECEIVED', 'REJECTED'],
  items: [{ returnItemId: 'ri1', orderItemId: 'phone', productName: 'Điện thoại X', sku: 'DT-X', serialNumber: 'SN-1', quantity: 1, unitPrice: 10000000 }] };

beforeEach(() => { vi.resetAllMocks(); });
const auth = { user: { userId: 'u', roleName: 'SALES_STAFF' }, invalidateSession: vi.fn() };
const withAuth = (child) => <AuthContext.Provider value={auth}>{child}</AuthContext.Provider>;
afterEach(() => cleanup());

it('customer requests a return for the units not yet claimed', async () => {
  returnApi.forOrder.mockResolvedValue([{ ...request, status: 'REQUESTED', items: [{ ...request.items[0] }] }]);
  returnApi.create.mockResolvedValue({ returnCode: 'TH-2' });
  render(withAuth(<MemoryRouter><OrderReturns order={order} /></MemoryRouter>));
  expect(await screen.findByText('TH-1')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Yêu cầu đổi trả' }));
  expect(screen.queryByLabelText('Trả Điện thoại X (SN-1)')).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Gửi yêu cầu' }));
  fireEvent.change(screen.getByLabelText('Lý do đổi trả'), { target: { value: 'Không vừa' } });
  fireEvent.click(screen.getByRole('button', { name: 'Gửi yêu cầu' }));
  expect(await screen.findByText('Chọn ít nhất một sản phẩm cần trả.')).toBeInTheDocument();
  fireEvent.click(screen.getByLabelText('Trả Ốp lưng'));
  fireEvent.change(screen.getByLabelText('Số lượng trả Ốp lưng'), { target: { value: '1' } });
  fireEvent.click(screen.getByRole('button', { name: 'Gửi yêu cầu' }));
  await waitFor(() => expect(returnApi.create).toHaveBeenCalledWith({ orderId: 'o1', reason: 'Không vừa', items: [{ orderItemId: 'case', quantity: 1 }] }));
  expect(await screen.findByText(/Đã gửi yêu cầu đổi trả TH-2/)).toBeInTheDocument();
});

it('warehouse receives the goods with a condition for every line', async () => {
  returnApi.detail.mockResolvedValue(request);
  returnApi.status.mockResolvedValue({});
  render(withAuth(<MemoryRouter initialEntries={['/returns/r1']}><Routes><Route path="/returns/:returnId" element={<ReturnDetailPage />} /></Routes></MemoryRouter>));
  fireEvent.click(await screen.findByRole('button', { name: 'Xác nhận đã nhận hàng' }));
  fireEvent.change(screen.getByLabelText('Tình trạng Điện thoại X (SN-1)'), { target: { value: 'DEFECTIVE' } });
  fireEvent.click(screen.getByRole('button', { name: 'Xác nhận' }));
  await waitFor(() => expect(returnApi.status).toHaveBeenCalledWith('r1', { status: 'RECEIVED', note: null, conditions: [{ returnItemId: 'ri1', condition: 'DEFECTIVE' }] }));
  expect(await screen.findByText('Đã cập nhật yêu cầu đổi trả.')).toBeInTheDocument();
});

it('customer detail is read-only', async () => {
  returnApi.myReturn.mockResolvedValue({ ...request, allowedStatuses: [] });
  render(withAuth(<MemoryRouter initialEntries={['/my-returns/r1']}><Routes><Route path="/my-returns/:returnId" element={<ReturnDetailPage customer />} /></Routes></MemoryRouter>));
  expect(await screen.findByText('Đã duyệt, chờ nhận hàng')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Xác nhận đã nhận hàng' })).not.toBeInTheDocument();
  expect(screen.queryByText('Nguyễn Văn An')).not.toBeInTheDocument();
});
