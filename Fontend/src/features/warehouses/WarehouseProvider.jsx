import { useEffect, useState } from 'react';
import { ROLES } from '../../config/projectConfig';
import { useAuth } from '../../hooks/useAuth';
import { useCart } from '../../hooks/useCart';
import { cartApi } from '../cart/api/cartApi';
import { warehouseApi } from './api/warehouseApi';
import { WarehouseContext } from './warehouseContext';

const STORAGE_KEY = 'shoppingWarehouseId';

export default function WarehouseProvider({ children }) {
  const { user, isLoading: isAuthLoading, invalidateSession } = useAuth();
  const { cart, applyCart } = useCart();
  const [warehouses, setWarehouses] = useState([]);
  const [selectedWarehouseId, setSelectedWarehouseId] = useState(() => localStorage.getItem(STORAGE_KEY) || '');
  const [isLoading, setIsLoading] = useState(true);
  const [isChanging, setIsChanging] = useState(false);
  const [error, setError] = useState('');
  const isCustomer = user?.roleName === ROLES.CUSTOMER;

  useEffect(() => {
    if (isAuthLoading) return undefined;
    if (!user) return undefined;
    const controller = new AbortController();
    warehouseApi.list(controller.signal).then((response) => {
      setWarehouses((response?.content || []).filter((warehouse) => warehouse.status === 'ACTIVE'));
      setError('');
    }).catch((nextError) => {
      if (nextError.name !== 'AbortError') {
        setError(nextError.message);
        if (nextError.status === 401) invalidateSession();
      }
    }).finally(() => {
      if (!controller.signal.aborted) setIsLoading(false);
    });
    return () => controller.abort();
  }, [invalidateSession, isAuthLoading, user]);

  async function selectWarehouse(warehouseId) {
    if (!warehouseId || warehouseId === cart?.warehouseId) {
      setSelectedWarehouseId(warehouseId || '');
      return true;
    }
    const mustClear = Boolean(cart?.items?.length && cart.warehouseId && cart.warehouseId !== warehouseId);
    if (mustClear && !window.confirm('Giỏ hàng đang thuộc chi nhánh khác. Đổi chi nhánh sẽ xóa toàn bộ sản phẩm trong giỏ. Bạn có tiếp tục?')) {
      return false;
    }
    setIsChanging(true);
    setError('');
    try {
      if (isCustomer) {
        const nextCart = await cartApi.selectWarehouse(warehouseId, mustClear);
        applyCart(nextCart);
      }
      setSelectedWarehouseId(warehouseId);
      localStorage.setItem(STORAGE_KEY, warehouseId);
      return true;
    } catch (nextError) {
      setError(nextError.message);
      if (nextError.status === 401) invalidateSession();
      return false;
    } finally {
      setIsChanging(false);
    }
  }

  const visibleWarehouses = user ? warehouses : [];
  const effectiveWarehouseId = isCustomer && cart?.warehouseId ? cart.warehouseId : selectedWarehouseId;
  const selectedWarehouse = visibleWarehouses.find((warehouse) => warehouse.warehouseId === effectiveWarehouseId) || null;
  const value = {
    warehouses: visibleWarehouses,
    selectedWarehouse,
    selectedWarehouseId: selectedWarehouse?.warehouseId || '',
    selectWarehouse,
    isLoading: Boolean(user) && isLoading,
    isChanging,
    error,
  };

  return <WarehouseContext.Provider value={value}>{children}</WarehouseContext.Provider>;
}
