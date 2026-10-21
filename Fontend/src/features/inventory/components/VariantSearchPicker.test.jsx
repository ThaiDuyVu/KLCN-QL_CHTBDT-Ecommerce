import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { inventoryApi } from '../api/inventoryApi';
import VariantSearchPicker from './VariantSearchPicker';

vi.mock('../api/inventoryApi', () => ({ inventoryApi: { variants: vi.fn() } }));

const CATEGORIES = [
  { categoryId: 'c-gaming', categoryName: 'Laptop Gaming' },
  { categoryId: 'c-office', categoryName: 'Laptop Văn phòng' },
  { categoryId: 'c-phone', categoryName: 'Điện thoại' },
];
const BRANDS = [{ brandId: 'b-dell', brandName: 'Dell' }, { brandId: 'b-apple', brandName: 'Apple' }];
const VARIANT = { variantId: 'v-1', sku: 'DELL-512', productName: 'Dell Inspiron 15', brandName: 'Dell', categoryName: 'Laptop Văn phòng', color: 'Bạc', ram: '16GB', storage: '512GB', trackingType: 'SERIAL' };
const lastFilters = () => inventoryApi.variants.mock.calls.at(-1)[0];

function picker(props = {}) {
  return <VariantSearchPicker categories={CATEGORIES} brands={BRANDS} onSelect={() => {}} {...props} />;
}

beforeEach(() => {
  inventoryApi.variants.mockImplementation((filters) => Promise.resolve({ content: [{ ...VARIANT, variantId: `v-${filters.page}` , sku: `SKU-P${filters.page}` }], totalPages: 3 }));
});

describe('VariantSearchPicker category filter', () => {
  it('sends the chosen categoryId (not the name), resets the page and keeps keyword/brand/tracking', async () => {
    const user = userEvent.setup(); render(picker());
    await screen.findByText('SKU-P0');
    await user.type(screen.getByLabelText('Tìm SKU hoặc tên sản phẩm'), 'Inspiron');
    await user.selectOptions(screen.getByLabelText('Thương hiệu'), 'b-dell');
    await user.selectOptions(screen.getByLabelText('Quản lý thiết bị'), 'SERIAL');
    await waitFor(() => expect(lastFilters()).toMatchObject({ keyword: 'Inspiron', brandId: 'b-dell', trackingType: 'SERIAL', page: 0 }));
    await user.click(screen.getByRole('button', { name: 'Sau' }));
    await screen.findByText('SKU-P1');
    expect(lastFilters().page).toBe(1);

    await user.click(screen.getByRole('button', { name: /Danh mục/ }));
    await user.type(screen.getByRole('combobox', { name: 'Tìm danh mục' }), 'van phong');
    await user.click(screen.getByRole('option', { name: 'Laptop Văn phòng' }));
    await screen.findByText('SKU-P0');
    expect(lastFilters()).toEqual({ page: 0, size: 20, keyword: 'Inspiron', categoryId: 'c-office', brandId: 'b-dell', trackingType: 'SERIAL' });
    expect(inventoryApi.variants.mock.calls.some(([filters]) => filters.categoryId === 'Laptop Văn phòng')).toBe(false);
    expect(screen.getByLabelText('Tìm SKU hoặc tên sản phẩm')).toHaveValue('Inspiron');
    expect(screen.getByLabelText('Thương hiệu')).toHaveValue('b-dell');
    expect(screen.getByLabelText('Quản lý thiết bị')).toHaveValue('SERIAL');

    await user.click(screen.getByRole('button', { name: /Danh mục/ }));
    await user.click(within(screen.getByRole('listbox')).getByRole('option', { name: 'Tất cả' }));
    await waitFor(() => expect(lastFilters()).toMatchObject({ categoryId: '', keyword: 'Inspiron', page: 0 }));
  });

  it('typing in the category search sends no SKU request', async () => {
    const user = userEvent.setup(); render(picker());
    await screen.findByText('SKU-P0');
    const before = inventoryApi.variants.mock.calls.length;
    await user.click(screen.getByRole('button', { name: /Danh mục/ }));
    await user.type(screen.getByRole('combobox', { name: 'Tìm danh mục' }), 'dien thoai laptop xyz');
    await user.keyboard('{Escape}');
    await new Promise((resolve) => setTimeout(resolve, 400)); // longer than the keyword debounce
    expect(inventoryApi.variants.mock.calls.length).toBe(before);
    expect(lastFilters().categoryId).toBe('');
  });

  it('selecting a SKU still passes the original variant object', async () => {
    const user = userEvent.setup(); const onSelect = vi.fn();
    inventoryApi.variants.mockResolvedValue({ content: [VARIANT], totalPages: 1 });
    render(picker({ onSelect }));
    await user.click(screen.getByRole('button', { name: /Danh mục/ }));
    await user.click(screen.getByRole('option', { name: 'Laptop Văn phòng' }));
    await user.click(await screen.findByRole('button', { name: /DELL-512/ }));
    expect(onSelect).toHaveBeenCalledTimes(1); expect(onSelect.mock.calls[0][0]).toBe(VARIANT);
  });

  it('keeps the selected SKU card and returns to it after browsing categories', async () => {
    const user = userEvent.setup(); const onSelect = vi.fn();
    render(picker({ value: VARIANT, onSelect }));
    expect(screen.getByText('DELL-512')).toBeInTheDocument();
    expect(inventoryApi.variants).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', { name: 'Đổi SKU' }));
    await user.click(await screen.findByRole('button', { name: /Danh mục/ }));
    await user.click(screen.getByRole('option', { name: 'Điện thoại' }));
    await waitFor(() => expect(lastFilters().categoryId).toBe('c-phone'));
    await user.click(screen.getByRole('button', { name: 'Giữ SKU hiện tại' }));
    expect(screen.getByText('DELL-512')).toBeInTheDocument(); expect(screen.getByText('Dell Inspiron 15')).toBeInTheDocument();
    expect(onSelect).not.toHaveBeenCalled();
  });

  it('two pickers keep independent category state and requests', async () => {
    const user = userEvent.setup();
    render(<><fieldset aria-label="Dòng 1">{picker()}</fieldset><fieldset aria-label="Dòng 2">{picker()}</fieldset></>);
    const [line1, line2] = screen.getAllByRole('group');
    await within(line1).findByText('SKU-P0'); await within(line2).findByText('SKU-P0');
    await user.click(within(line1).getByRole('button', { name: /Danh mục/ }));
    await user.click(screen.getByRole('option', { name: 'Điện thoại' }));
    await waitFor(() => expect(lastFilters().categoryId).toBe('c-phone'));
    expect(within(line1).getByRole('button', { name: /Danh mục/ })).toHaveTextContent('Điện thoại');
    expect(within(line2).getByRole('button', { name: /Danh mục/ })).toHaveTextContent('Tất cả');
    expect(inventoryApi.variants.mock.calls.filter(([filters]) => filters.categoryId === 'c-phone')).toHaveLength(1);
    await user.click(within(line2).getByRole('button', { name: /Danh mục/ }));
    expect(screen.getAllByRole('listbox')).toHaveLength(1);
    expect(within(screen.getByRole('listbox')).getByRole('option', { name: 'Tất cả' })).toHaveAttribute('aria-selected', 'true');
  });
});
