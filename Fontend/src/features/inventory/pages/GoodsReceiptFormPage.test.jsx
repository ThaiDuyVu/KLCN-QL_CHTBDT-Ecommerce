import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { inventoryApi } from '../api/inventoryApi';
import GoodsReceiptFormPage from './GoodsReceiptFormPage';

vi.mock('../api/inventoryApi', () => ({ inventoryApi: {
  warehouses: vi.fn(), suppliers: vi.fn(), categories: vi.fn(), brands: vi.fn(), variants: vi.fn(), variant: vi.fn(),
  receipt: vi.fn(), createReceipt: vi.fn(), updateReceipt: vi.fn(),
} }));

const LAPTOP = { variantId: 'v-laptop', sku: 'DELL-512', productName: 'Dell Inspiron 15', categoryName: 'Laptop', trackingType: 'NONE' };
const PHONE = { variantId: 'v-phone', sku: 'X15-256', productName: 'Xiaomi 15', categoryName: 'Điện thoại', trackingType: 'NONE' };

function show(path = '/goods-receipts/new') {
  return render(<MemoryRouter initialEntries={[path]}><Routes>
    <Route path="/goods-receipts/new" element={<GoodsReceiptFormPage />} />
    <Route path="/goods-receipts/:receiptId/edit" element={<GoodsReceiptFormPage />} />
    <Route path="/goods-receipts/:receiptId" element={<p>Đã lưu</p>} />
  </Routes></MemoryRouter>);
}
const line = (n) => screen.getByRole('group', { name: `Dòng ${n}` });

beforeEach(() => {
  inventoryApi.warehouses.mockResolvedValue({ content: [{ warehouseId: 'w1', warehouseName: 'Kho 1' }] });
  inventoryApi.suppliers.mockResolvedValue({ content: [{ supplierId: 's1', supplierName: 'NCC 1' }] });
  inventoryApi.categories.mockResolvedValue([{ categoryId: 'c-laptop', categoryName: 'Laptop' }, { categoryId: 'c-phone', categoryName: 'Điện thoại' }]);
  inventoryApi.brands.mockResolvedValue({ content: [] });
  inventoryApi.variants.mockImplementation((filters) => Promise.resolve({ content: filters.categoryId === 'c-phone' ? [PHONE] : [LAPTOP, PHONE], totalPages: 1 }));
  inventoryApi.createReceipt.mockResolvedValue({ receiptId: 'r-new' });
  inventoryApi.updateReceipt.mockResolvedValue({ receiptId: 'r-1' });
});

describe('Goods receipt form with searchable category filter', () => {
  it('creates a receipt whose items keep the variantId chosen per line', async () => {
    const user = userEvent.setup(); show();
    await screen.findByRole('heading', { name: 'Tạo phiếu nhập' });
    await user.type(screen.getByLabelText('Mã phiếu'), 'PN-TEST-01');
    await user.selectOptions(screen.getByLabelText('Nhà cung cấp'), 's1');
    await user.selectOptions(screen.getByLabelText('Kho nhập'), 'w1');
    await user.click(screen.getByRole('button', { name: 'Thêm dòng' }));

    await user.click(within(line(1)).getByRole('button', { name: /Danh mục/ }));
    await user.type(screen.getByRole('combobox', { name: 'Tìm danh mục' }), 'dien thoai');
    await user.click(screen.getByRole('option', { name: 'Điện thoại' }));
    await user.click(await within(line(1)).findByRole('button', { name: /X15-256/ }));
    expect(within(line(2)).getByRole('button', { name: /Danh mục/ })).toHaveTextContent('Tất cả');

    await user.click(await within(line(2)).findByRole('button', { name: /DELL-512/ }));
    await user.clear(within(line(1)).getByLabelText('Số lượng')); await user.type(within(line(1)).getByLabelText('Số lượng'), '3');
    await user.clear(within(line(1)).getByLabelText('Giá vốn')); await user.type(within(line(1)).getByLabelText('Giá vốn'), '9000000');
    // Browsing categories again on line 1 must not touch either line's SKU or numbers.
    await user.click(within(line(1)).getByRole('button', { name: 'Đổi SKU' }));
    await user.click(within(line(1)).getByRole('button', { name: /Danh mục/ }));
    await user.click(screen.getByRole('option', { name: 'Laptop' }));
    await user.click(within(line(1)).getByRole('button', { name: 'Giữ SKU hiện tại' }));
    expect(within(line(1)).getByLabelText('Số lượng')).toHaveValue(3);

    await user.click(screen.getByRole('button', { name: 'Lưu DRAFT' }));
    await waitFor(() => expect(inventoryApi.createReceipt).toHaveBeenCalledTimes(1));
    expect(inventoryApi.createReceipt.mock.calls[0][0]).toEqual({
      receiptCode: 'PN-TEST-01', supplierId: 's1', warehouseId: 'w1',
      items: [{ variantId: 'v-phone', quantity: 3, unitCost: 9000000, devices: [] }, { variantId: 'v-laptop', quantity: 1, unitCost: 0, devices: [] }],
    });
    expect(await screen.findByText('Đã lưu')).toBeInTheDocument();
  });

  it('editing keeps existing items when the category filter is changed without picking a new SKU', async () => {
    const user = userEvent.setup();
    inventoryApi.receipt.mockResolvedValue({ receiptId: 'r-1', receiptCode: 'PN-1', supplierId: 's1', warehouseId: 'w1',
      items: [{ receiptItemId: 'i1', variantId: 'v-laptop', quantity: 2, unitCost: 15000000, devices: [] }] });
    inventoryApi.variant.mockResolvedValue(LAPTOP);
    show('/goods-receipts/r-1/edit');
    await screen.findByRole('heading', { name: 'Sửa phiếu nhập' });
    expect(within(line(1)).getByText('DELL-512')).toBeInTheDocument();
    await user.click(within(line(1)).getByRole('button', { name: 'Đổi SKU' }));
    await user.click(within(line(1)).getByRole('button', { name: /Danh mục/ }));
    await user.type(screen.getByRole('combobox', { name: 'Tìm danh mục' }), 'dien');
    await user.keyboard('{ArrowDown}{ArrowDown}{Enter}');
    await waitFor(() => expect(inventoryApi.variants.mock.calls.at(-1)[0].categoryId).toBe('c-phone'));
    await user.click(screen.getByRole('button', { name: 'Lưu DRAFT' }));
    await waitFor(() => expect(inventoryApi.updateReceipt).toHaveBeenCalledTimes(1));
    expect(inventoryApi.updateReceipt).toHaveBeenCalledWith('r-1', { receiptCode: 'PN-1', supplierId: 's1', warehouseId: 'w1',
      items: [{ variantId: 'v-laptop', quantity: 2, unitCost: 15000000, devices: [] }] });
  });
});
