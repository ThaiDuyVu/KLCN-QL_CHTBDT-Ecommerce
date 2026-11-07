import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router';
import { AuthContext } from '../../auth/AuthContext';
import { posApi } from './posApi';
import { warehouseApi } from '../warehouses/api/warehouseApi';
import PosPage from './PosPage';

vi.mock('./posApi', () => ({ posApi: { searchVariants: vi.fn(), findCustomers: vi.fn(), createWalkIn: vi.fn(), sell: vi.fn() } }));
vi.mock('../warehouses/api/warehouseApi', () => ({ warehouseApi: { listAll: vi.fn() } }));
beforeEach(() => { vi.resetAllMocks(); warehouseApi.listAll.mockResolvedValue([{ warehouseId: 'w1', warehouseName: 'Chi nhánh Quận 1' }]); vi.spyOn(window, 'confirm').mockReturnValue(true); });
afterEach(() => { cleanup(); vi.restoreAllMocks(); });

it('registers a walk-in, adds a scanned phone and a case, and sells for cash', async () => {
  posApi.createWalkIn.mockResolvedValue({ customerId: 'c1', fullName: 'Khách lẻ Hoàng', phone: '0912000111' });
  posApi.searchVariants.mockResolvedValue({ content: [
    { variantId: 'v-phone', sku: 'DT-X', productName: 'Điện thoại X', price: 10000000, trackingType: 'SERIAL' },
    { variantId: 'v-case', sku: 'OP-X', productName: 'Ốp lưng', price: 200000, effectivePrice: 180000, trackingType: 'NONE' }] });
  posApi.sell.mockResolvedValue({ orderId: 'o1', orderCode: 'POS-1', totalAmount: 10360000, invoice: { invoiceId: 'i1', invoiceNumber: 'HD-20261106-0001' } });
  render(<AuthContext.Provider value={{ user: { roleName: 'SALES_STAFF' }, invalidateSession: vi.fn() }}><MemoryRouter><PosPage /></MemoryRouter></AuthContext.Provider>);
  fireEvent.change(screen.getByLabelText('Họ tên khách'), { target: { value: 'Khách lẻ Hoàng' } });
  fireEvent.change(screen.getByLabelText('Số điện thoại khách'), { target: { value: '0912000111' } });
  fireEvent.click(screen.getByRole('button', { name: 'Tạo khách vãng lai' }));
  expect(await screen.findByText('Khách lẻ Hoàng')).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText('Tìm sản phẩm'), { target: { value: 'X' } });
  fireEvent.submit(screen.getByLabelText('Tìm sản phẩm').closest('form'));
  fireEvent.click(await screen.findByRole('button', { name: /DT-X/ }));
  fireEvent.click(screen.getByRole('button', { name: /OP-X/ }));
  fireEvent.click(screen.getByRole('button', { name: /OP-X/ }));
  fireEvent.change(screen.getByLabelText('Serial DT-X'), { target: { value: ' SN-2 ' } });
  expect(screen.getByText('10.360.000 ₫', { exact: false })).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: 'Thu tiền mặt & hoàn tất' }));
  await waitFor(() => expect(posApi.sell).toHaveBeenCalledWith({ customerId: 'c1', items: [
    { variantId: 'v-phone', quantity: 1, serialNumbers: ['SN-2'] }, { variantId: 'v-case', quantity: 2 }] }));
  expect(await screen.findByRole('link', { name: 'In hóa đơn HD-20261106-0001' })).toHaveAttribute('href', '/invoices/i1');
});
