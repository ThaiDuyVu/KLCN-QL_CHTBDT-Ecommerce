import { beforeEach, describe, expect, it, vi } from 'vitest';
import { commerceRequest } from '../orders/api/orderApi';
import { warehouseApi } from './api/warehouseApi';

vi.mock('../orders/api/orderApi', () => ({ commerceRequest: vi.fn() }));

const page = (index, ids, totalElements, totalPages) => ({ content: ids.map((id) => ({ warehouseId: id, warehouseName: `CN ${id}`, status: 'ACTIVE' })), page: index, size: 100, totalElements, totalPages });

// Block body: a function returned from beforeEach would be run by Vitest as teardown.
beforeEach(() => { commerceRequest.mockReset(); });

describe('warehouseApi.listAll', () => {
  it('loads every page instead of assuming size=100 covers all branches', async () => {
    const first = Array.from({ length: 100 }, (_, i) => `a${i}`);
    commerceRequest.mockImplementation((path) => Promise.resolve(path.includes('page=0') ? page(0, first, 102, 2) : page(1, ['b1', 'b2'], 102, 2)));
    const rows = await warehouseApi.listAll();
    expect(rows).toHaveLength(102);
    expect(commerceRequest.mock.calls.map(([path]) => path)).toEqual(['/warehouses?page=0&size=100', '/warehouses?page=1&size=100']);
  });

  it('rejects a list whose pages shifted (duplicates/missing rows) rather than returning it as complete', async () => {
    const first = Array.from({ length: 100 }, (_, i) => `a${i}`);
    commerceRequest.mockImplementation((path) => Promise.resolve(path.includes('page=0') ? page(0, first, 102, 2) : page(1, ['a99', 'b2'], 102, 2)));
    await expect(warehouseApi.listAll()).rejects.toMatchObject({ name: 'IncompleteWarehouseList' });
  });

  it('rejects when the total changes between pages', async () => {
    commerceRequest.mockImplementation((path) => Promise.resolve(path.includes('page=0') ? page(0, ['a'], 2, 2) : page(1, ['b'], 3, 2)));
    await expect(warehouseApi.listAll()).rejects.toMatchObject({ name: 'IncompleteWarehouseList' });
  });

  it('passes the abort signal to every page request', async () => {
    const controller = new AbortController();
    commerceRequest.mockImplementation((path) => Promise.resolve(path.includes('page=0') ? page(0, ['a'], 2, 2) : page(1, ['b'], 2, 2)));
    await warehouseApi.listAll(controller.signal);
    expect(commerceRequest.mock.calls.every(([, options]) => options.signal === controller.signal)).toBe(true);
  });
});
