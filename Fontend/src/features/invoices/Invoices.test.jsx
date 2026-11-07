import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router';
import { AuthContext } from '../../auth/AuthContext';
import { amountInWords } from './amountInWords';
import { invoiceApi } from './api/invoiceApi';
import InvoicePage from './pages/InvoicePage';

vi.mock('./api/invoiceApi', () => ({ invoiceApi: { list: vi.fn(), detail: vi.fn(), mineForOrder: vi.fn() } }));
afterEach(() => cleanup());

it.each([
  [0, 'Không đồng'], [5, 'Năm đồng'], [15, 'Mười lăm đồng'], [21, 'Hai mươi mốt đồng'], [105, 'Một trăm lẻ năm đồng'],
  [1250000, 'Một triệu hai trăm năm mươi nghìn đồng'], [10400000, 'Mười triệu bốn trăm nghìn đồng'],
  [2005000, 'Hai triệu không trăm lẻ năm nghìn đồng'], [34990000, 'Ba mươi tư triệu chín trăm chín mươi nghìn đồng'],
  [1000000000, 'Một tỷ đồng'],
])('reads %s as "%s"', (value, words) => { expect(amountInWords(value)).toBe(words); });

it('customer sees the printable invoice of their order', async () => {
  invoiceApi.mineForOrder.mockResolvedValue({ invoiceId: 'i1', invoiceNumber: 'HD-20261106-0001', status: 'ISSUED', issuedDate: '2026-11-06T03:00:00Z',
    orderId: 'o1', orderCode: 'ORD-1', orderDate: '2026-11-05T03:00:00Z', warehouseName: 'Chi nhánh Quận 1', customerName: 'Nguyễn Văn An',
    recipientName: 'Nguyễn Văn An', recipientPhone: '0901234567', shippingAddress: 'TP.HCM', paymentMethod: 'COD', paymentStatus: 'PAID',
    subtotal: 1250000, discountAmount: 0, shippingFee: 0, totalAmount: 1250000, refundedAmount: 0,
    lines: [{ productName: 'Tai nghe', sku: 'TN-1', quantity: 1, unitPrice: 1250000, discountAmount: 0, finalUnitPrice: 1250000, lineTotal: 1250000, imeiNumbers: [] }] });
  render(<AuthContext.Provider value={{ user: { roleName: 'CUSTOMER' }, invalidateSession: vi.fn() }}><MemoryRouter initialEntries={['/my-orders/o1/invoice']}>
    <Routes><Route path="/my-orders/:orderId/invoice" element={<InvoicePage customer />} /></Routes></MemoryRouter></AuthContext.Provider>);
  expect(await screen.findByText('HD-20261106-0001')).toBeInTheDocument();
  expect(invoiceApi.mineForOrder).toHaveBeenCalledWith('o1', expect.anything());
  expect(screen.getByText('Một triệu hai trăm năm mươi nghìn đồng')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'In hóa đơn' })).toBeInTheDocument();
});
