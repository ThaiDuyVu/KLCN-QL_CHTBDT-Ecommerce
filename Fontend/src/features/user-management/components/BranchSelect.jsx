import { useCallback } from 'react';
import useManagementRequest from '../hooks/useManagementRequest';
import { warehouseApi } from '../../warehouses/api/warehouseApi';

/** Branch of an employee (V12). Empty value = all branches. */
export default function BranchSelect({ defaultValue = '', disabled }) {
  const load = useCallback((signal) => warehouseApi.listAll(signal), []);
  const { data, error } = useManagementRequest('employee-branches', load);
  return <label>Chi nhánh làm việc<select name="warehouseId" defaultValue={defaultValue || ''} disabled={disabled || !data}>
    <option value="">Tất cả chi nhánh</option>
    {(data || []).filter((w) => w.status !== 'INACTIVE' || w.warehouseId === defaultValue)
      .map((w) => <option key={w.warehouseId} value={w.warehouseId}>{w.warehouseName}</option>)}
  </select>{error && <small role="alert">Chưa tải được chi nhánh</small>}</label>;
}
