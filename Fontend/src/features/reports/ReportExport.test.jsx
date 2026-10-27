import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { encodeForExcel, reportSections, saveFile, sectionsToTsv } from './reportExport';
import { reportApi } from './api/reportApi';
import { inventoryApi } from '../inventory/api/inventoryApi';
import ReportsPage from './pages/ReportsPage';
import { AuthContext } from '../../auth/AuthContext';

vi.mock('./api/reportApi', () => ({ reportApi: { get: vi.fn() } }));
vi.mock('../inventory/api/inventoryApi', () => ({ inventoryApi: { warehouses: vi.fn() } }));
vi.mock('./reportExport', async (importOriginal) => ({ ...(await importOriginal()), saveFile: vi.fn() }));
vi.mock('recharts', async (importOriginal) => ({ ...(await importOriginal()), ResponsiveContainer: ({ children }) => <div>{children}</div> }));

const data = {
  summary: { revenue: 25990000.5, grossProfit: 3000000, totalDiscount: 0, totalOrders: 3, completedOrders: 2, cancelledOrders: 1, averageOrderValue: 12995000.25, timezone: 'Asia/Ho_Chi_Minh' },
  revenue: { granularity: 'DAY', points: [{ bucket: '2026-10-01', completedOrders: 2, revenue: 25990000 }] },
  'top-products': [{ productId: 'p1', productName: 'Laptop Dell XPS 13', quantity: 2, revenue: 25990000 }],
  warehouses: [{ warehouseId: 'w1', warehouseName: 'Chi nhánh Quận 1', count: 2, amount: 25990000 }],
  'order-status': [{ status: 'DELIVERED', count: 2 }, { status: 'CANCELLED', count: 1 }],
  payments: [{ paymentMethod: 'COD', status: 'PAID', count: 2, amount: 25990000 }],
  'goods-receipts': { count: 1, totalAmount: 50000000, warehouses: [{ warehouseId: 'w1', warehouseName: 'Chi nhánh Quận 1', count: 1, amount: 50000000 }] },
  'low-stock': [{ inventoryId: 'i1', warehouseName: 'Chi nhánh Quận 1', productName: 'iPhone 15', sku: 'IP15-128', quantity: 2, reservedQuantity: 1, availableQuantity: 1 }],
};

beforeEach(() => {
  vi.resetAllMocks();
  window.localStorage.clear();
  inventoryApi.warehouses.mockResolvedValue({ content: [{ warehouseId: 'w1', warehouseName: 'Chi nhánh Quận 1' }] });
  reportApi.get.mockImplementation((resource) => Promise.resolve(data[resource]));
});
afterEach(() => cleanup());
const mount = () => render(<AuthContext.Provider value={{ user: { userId: 'manager', roleName: 'MANAGER' }, invalidateSession: vi.fn() }}><ReportsPage /></AuthContext.Provider>);

it('builds one section per report with Vietnamese labels and whole-dong amounts', () => {
  const sections = reportSections(data, { from: '2026-10-01', to: '2026-10-27', warehouseName: '', threshold: 5 });
  expect(sections.map((s) => s.title)).toHaveLength(9);
  expect(sections[0].rows[0]).toEqual(['2026-10-01', '2026-10-27', 'Tất cả chi nhánh', 5]);
  expect(sections[1].rows[0][0]).toBe(25990001);
  expect(sections[5].rows).toContainEqual(['Đã hủy', 1]);
  expect(sections[6].rows[0]).toEqual(['COD', 'Đã thanh toán', 2, 25990000]);
  expect(sections[7].rows.at(-1)).toEqual(['Tổng cộng', 1, 50000000]);
  const tsv = sectionsToTsv(sections);
  expect(tsv).toContain('Tồn kho thấp (khả dụng ≤ 5)\r\nChi nhánh\tSản phẩm\tSKU');
  expect(tsv).toContain('Chi nhánh Quận 1\tiPhone 15\tIP15-128\t2\t1\t1');
});

it('encodes UTF-16LE with a byte order mark so Excel keeps Vietnamese text', () => {
  const bytes = encodeForExcel('Đã');
  expect([...bytes]).toEqual([0xff, 0xfe, 0x10, 0x01, 0xe3, 0x00]);
});

it('exports every report for the current filters and remembers the low-stock threshold', async () => {
  mount();
  fireEvent.change(screen.getByLabelText('Ngưỡng tồn khả dụng'), { target: { value: '8' } });
  fireEvent.click(screen.getByRole('button', { name: 'Áp dụng' }));
  expect(window.localStorage.getItem('reports.lowStockThreshold')).toBe('8');
  reportApi.get.mockClear();
  fireEvent.click(screen.getByRole('button', { name: 'Xuất file Excel (CSV)' }));
  await waitFor(() => expect(saveFile).toHaveBeenCalledTimes(1));
  const resources = reportApi.get.mock.calls.map(([resource]) => resource);
  expect(resources).toEqual(['summary', 'revenue', 'top-products', 'warehouses', 'order-status', 'payments', 'goods-receipts', 'low-stock']);
  expect(reportApi.get.mock.calls.find(([r]) => r === 'low-stock')[1]).toMatchObject({ threshold: 8, limit: 100 });
  expect(reportApi.get.mock.calls.find(([r]) => r === 'top-products')[1]).toMatchObject({ limit: 20 });
  expect(saveFile.mock.calls[0][1]).toMatch(/^bao-cao_\d{4}-\d{2}-\d{2}_\d{4}-\d{2}-\d{2}\.csv$/);
});

it('shows an error instead of saving when a report fails to load', async () => {
  mount();
  reportApi.get.mockRejectedValue(new Error('Mất kết nối'));
  fireEvent.click(screen.getByRole('button', { name: 'Xuất file Excel (CSV)' }));
  expect(await screen.findByText('Xuất báo cáo thất bại: Mất kết nối')).toBeTruthy();
  expect(saveFile).not.toHaveBeenCalled();
});
