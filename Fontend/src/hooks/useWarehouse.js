import { useContext } from 'react';
import { WarehouseContext } from '../features/warehouses/warehouseContext';

export function useWarehouse() {
  const context = useContext(WarehouseContext);
  if (!context) throw new Error('useWarehouse phải được sử dụng bên trong WarehouseProvider.');
  return context;
}
