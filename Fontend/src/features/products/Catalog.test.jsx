import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router';
import { AuthContext } from '../../auth/AuthContext';
import { WarehouseContext } from '../warehouses/warehouseContext';
import { CartContext } from '../cart/cartContext';
import { productApi } from './api/productApi';
import { categoryApi } from '../categories/api/categoryApi';
import { cartApi } from '../cart/api/cartApi';
import ProductsPage from './pages/ProductsPage';
import ProductDetailPage from './pages/ProductDetailPage';
import HomePage from '../../pages/HomePage';
import { Route, Routes } from 'react-router';

vi.mock('./api/productApi', () => ({ productApi: { list: vi.fn(), detail: vi.fn() } }));
vi.mock('../categories/api/categoryApi', () => ({ categoryApi: { list: vi.fn() } }));
vi.mock('../cart/api/cartApi', () => ({ cartApi: { add: vi.fn() } }));

const product = { productId: '11111111-1111-1111-1111-111111111111', productName: 'Điện thoại demo', brandName: 'Apple', categoryId: 'phones', categoryName: 'Điện thoại', status: 'ACTIVE', primaryImageUrl: '/images/products/demo/front.webp', availableQuantity: 5 };
const variants = [
  { variantId: 'v1', sku: 'DEMO-BLUE', color: 'Xanh', storage: '128GB', price: 10000000, status: 'ACTIVE', availableQuantity: 3, warrantyMonths: 12 },
  { variantId: 'v2', sku: 'DEMO-WHITE', color: 'Trắng', storage: '256GB', price: 12000000, status: 'ACTIVE', availableQuantity: 2, warrantyMonths: 12 },
];
const warehouse = { selectedWarehouseId: 'warehouse-a', selectedWarehouse: { warehouseId: 'warehouse-a', warehouseName: 'Chi nhánh A' }, warehouses: [], error: '', selectWarehouse: vi.fn() };
const cart = { applyCart: vi.fn(), itemCount: 0 };

function mount(component, path = '/', role = 'CUSTOMER', branch = warehouse) {
  return render(<MemoryRouter initialEntries={[path]}><AuthContext.Provider value={{ user: { roleName: role }, invalidateSession: vi.fn() }}><WarehouseContext.Provider value={branch}><CartContext.Provider value={cart}>{component}</CartContext.Provider></WarehouseContext.Provider></AuthContext.Provider></MemoryRouter>);
}

beforeEach(() => {
  vi.resetAllMocks();
  vi.spyOn(window, 'scrollTo').mockImplementation(() => {});
  warehouse.selectWarehouse.mockResolvedValue(true);
  categoryApi.list.mockResolvedValue([{ categoryId: 'phones', categoryName: 'Điện thoại', status: 'ACTIVE' }]);
  productApi.list.mockResolvedValue({ content: [product], totalElements: 20, page: 0, totalPages: 2 });
  productApi.detail.mockResolvedValue({ product, variants, images: [{ imageId: 'image', imageUrl: product.primaryImageUrl, isPrimary: true }], specifications: [] });
  cartApi.add.mockResolvedValue({ items: [] });
});
afterEach(() => vi.restoreAllMocks());

it('searches and paginates with the selected warehouse and active customer catalog', async () => {
  mount(<ProductsPage />);
  await screen.findByRole('heading', { name: product.productName });
  fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'demo' } });
  fireEvent.change(screen.getByRole('combobox'), { target: { value: 'phones' } });
  fireEvent.click(screen.getByRole('button', { name: 'Tìm kiếm' }));
  await waitFor(() => expect(productApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: 'demo', categoryId: 'phones', warehouseId: 'warehouse-a', status: 'ACTIVE', page: 0 }), expect.any(AbortSignal)));
  fireEvent.click(await screen.findByRole('button', { name: 'Trang sau' }));
  await waitFor(() => expect(productApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, keyword: 'demo', categoryId: 'phones', warehouseId: 'warehouse-a' }), expect.any(AbortSignal)));
  expect(screen.queryByText('Đang hoạt động')).not.toBeInTheDocument();
});

it('keeps supplied hero and product image paths and marks future features unavailable', async () => {
  mount(<HomePage />);
  expect(screen.getByAltText('Ưu đãi công nghệ và thiết bị thông minh')).toHaveAttribute('src', '/images/hero/Herobanner.png');
  expect(await screen.findByAltText(product.productName)).toHaveAttribute('src', product.primaryImageUrl);
  expect(screen.getByRole('button', { name: 'Bán chạy (sắp có)' })).toBeDisabled();
  expect(screen.getByRole('button', { name: 'Đăng ký nhận tin (sắp có)' })).toBeDisabled();
  expect(screen.getByText('Còn 5 tại chi nhánh')).toBeInTheDocument();
});

it('selects an actual variant and adds its id without sending a price', async () => {
  mount(<Routes><Route path="products/:productId" element={<ProductDetailPage />} /></Routes>, `/products/${product.productId}`);
  const option = await screen.findByRole('button', { name: /Trắng · 256GB/ });
  fireEvent.click(option);
  expect(option).toHaveAttribute('aria-pressed', 'true');
  fireEvent.click(screen.getByRole('button', { name: 'Thêm vào giỏ' }));
  await waitFor(() => expect(cartApi.add).toHaveBeenCalledWith('v2', 1));
  expect(warehouse.selectWarehouse).toHaveBeenCalledWith('warehouse-a');
  expect(screen.getByRole('button', { name: 'Viết đánh giá (sắp có)' })).toBeDisabled();
});

it('requires a warehouse before purchasing and preserves management status controls', async () => {
  const view = mount(<ProductsPage />, '/', 'CUSTOMER', { ...warehouse, selectedWarehouseId: '', selectedWarehouse: null });
  expect(await screen.findByRole('button', { name: `Thêm ${product.productName} vào giỏ` })).toBeDisabled();
  expect(screen.getByText('Chọn chi nhánh để xem tồn kho')).toBeInTheDocument();
  view.unmount();
  mount(<ProductsPage />, '/', 'STAFF');
  expect(await screen.findByText('Đang hoạt động', { selector: '.badge' })).toBeInTheDocument();
  expect(screen.getByRole('combobox')).toHaveAttribute('name', 'status');
});
