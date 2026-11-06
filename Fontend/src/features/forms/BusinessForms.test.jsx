import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { PrintButton } from '../../components/print/PrintForm';
import { DeliveryNoteForm, GoodsReceiptForm, ReturnForm, WarrantyCardForm, WarrantyTicketForm } from './BusinessForms';

let printed;
beforeEach(() => { printed = []; vi.spyOn(window, 'print').mockImplementation(() => { printed.push(document.querySelector('.print-form')?.textContent || ''); }); });
afterEach(() => { cleanup(); vi.restoreAllMocks(); });
function printOnce(form) {
  cleanup(); printed = []; window.print.mockClear();
  render(<PrintButton label="In">{form}</PrintButton>);
  expect(document.querySelector('.print-form')).toBeNull();
  fireEvent.click(screen.getByRole('button', { name: 'In' }));
  expect(window.print).toHaveBeenCalledTimes(1);
  expect(document.querySelector('.print-form')).toBeNull();
  return printed[0];
}

it('goods receipt form lists every unit with serial and IMEI and the total in words', () => {
  const text = printOnce(<GoodsReceiptForm supplierName="Công ty Phân phối A" warehouseName="Chi nhánh Quận 1" receipt={{ receiptCode: 'PN-1', receiptDate: '2026-11-06T03:00:00Z', status: 'CONFIRMED', totalAmount: 20000000,
    items: [{ receiptItemId: 'i', sku: 'DT-X', productName: 'Điện thoại X', quantity: 2, unitCost: 10000000, devices: [{ serialNumber: 'SN-1', imeiNumbers: ['356000000000001'] }, { serialNumber: 'SN-2', imeiNumbers: [] }] }] }} />);
  expect(text).toContain('PHIẾU NHẬP KHO');
  expect(text).not.toContain('CHƯA XÁC NHẬN');
  expect(text).toContain('SN-1 (IMEI 356000000000001); SN-2');
  expect(text).toContain('Hai mươi triệu đồng');
  expect(text).toContain('Thủ kho');
});

it('delivery note asks to collect cash only for unpaid COD orders', () => {
  const order = { orderCode: 'ORD-1', warehouseName: 'Chi nhánh Quận 1', recipientName: 'An', recipientPhone: '0901234567', shippingAddress: 'TP.HCM', totalAmount: 1250000,
    payment: { paymentMethod: 'COD', status: 'PENDING' }, items: [{ orderItemId: 'a', productName: 'Tai nghe', sku: 'TN', quantity: 1 }],
    shipment: { trackingNumber: 'GH-1', shippedDate: '2026-11-06T03:00:00Z', shippingProvider: 'Giao nội bộ', employeeName: 'Phan Văn An' } };
  expect(printOnce(<DeliveryNoteForm order={order} />)).toContain('Thu hộ (COD)');
  expect(printOnce(<DeliveryNoteForm order={{ ...order, payment: { paymentMethod: 'VNPAY', status: 'PAID' } }} />)).toContain('Đã thanh toán, không thu tiền');
});

it('warranty, ticket and return forms carry their identifiers', () => {
  expect(printOnce(<WarrantyCardForm warranty={{ productName: 'Laptop', serialNumber: 'SN-9', imeiNumbers: [], startDate: '2026-11-01', endDate: '2027-11-01' }} />)).toContain('PHIẾU BẢO HÀNH');
  expect(printOnce(<WarrantyTicketForm ticket={{ ticketCode: 'WT-1', issueDescription: 'Không lên nguồn', createdAt: '2026-11-06T03:00:00Z' }} statusLabel="Đã tiếp nhận" />)).toContain('Không lên nguồn');
  const text = printOnce(<ReturnForm statusLabel="Đã hoàn tiền" request={{ returnCode: 'TH-1', requestDate: '2026-11-06T03:00:00Z', refundAmount: 200000, reason: 'Đổi ý',
    items: [{ returnItemId: 'r', productName: 'Ốp lưng', sku: 'OP', quantity: 1, unitPrice: 200000 }] }} />);
  expect(text).toContain('TH-1');
  expect(text).toContain('Hai trăm nghìn đồng');
});
