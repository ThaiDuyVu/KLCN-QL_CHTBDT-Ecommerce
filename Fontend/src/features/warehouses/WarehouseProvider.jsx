import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ROLES } from '../../config/projectConfig';
import { useAuth } from '../../hooks/useAuth';
import { useCart } from '../../hooks/useCart';
import { cartApi } from '../cart/api/cartApi';
import { warehouseApi } from './api/warehouseApi';
import { WarehouseContext } from './warehouseContext';

const STORAGE_KEY = 'shoppingWarehouseId';
// Per-tab, per-user marker that this shopping session confirmed a branch. It is only a hint:
// the gate opens when it matches a freshly loaded ACTIVE branch and the loaded cart.
const confirmKey = (userKey) => `storefrontBranchConfirmed.v1.${userKey}`;
const CLEAR_MESSAGE = 'Giỏ hàng đang thuộc chi nhánh khác. Đổi chi nhánh sẽ xóa toàn bộ sản phẩm trong giỏ. Bạn có tiếp tục?';

function readConfirmed(userKey) {
  try { return sessionStorage.getItem(confirmKey(userKey)) || ''; } catch { return ''; }
}

export default function WarehouseProvider({ children }) {
  const { user, isLoading: isAuthLoading, invalidateSession } = useAuth();
  const { cart, applyCart, isLoading: isCartLoading, error: cartError } = useCart();
  // Same owner identity as CartProvider so cart and branch state always belong to one account.
  const userKey = user?.userId || user?.id || user?.username || null;
  const isCustomer = user?.roleName === ROLES.CUSTOMER;
  const [list, setList] = useState({ owner: null, status: 'loading', warehouses: [], error: '' });
  const [reloadToken, setReloadToken] = useState(0);
  const [selectedWarehouseId, setSelectedWarehouseId] = useState(() => localStorage.getItem(STORAGE_KEY) || '');
  const [confirmVersion, setConfirmVersion] = useState(0);
  const [isChanging, setIsChanging] = useState(false);
  const [selectError, setSelectError] = useState({ owner: null, message: '' });
  const epoch = useRef(0);
  const changing = useRef(false);
  const previousUser = useRef(null);

  useEffect(() => {
    // Account change, logout or expired session: drop the old account's confirmation and
    // invalidate any selection request still in flight for it.
    epoch.current += 1;
    if (previousUser.current && previousUser.current !== userKey) {
      try { sessionStorage.removeItem(confirmKey(previousUser.current)); } catch { /* Storage may be disabled. */ }
    }
    previousUser.current = userKey;
  }, [userKey]);

  useEffect(() => {
    if (isAuthLoading || !userKey) return undefined;
    const controller = new AbortController();
    const owner = userKey;
    warehouseApi.listAll(controller.signal).then((rows) => {
      if (controller.signal.aborted) return;
      setList({ owner, status: 'ready', warehouses: rows.filter((warehouse) => warehouse.status === 'ACTIVE'), error: '' });
    }).catch((nextError) => {
      if (controller.signal.aborted || nextError.name === 'AbortError') return;
      setList({ owner, status: 'error', warehouses: [], error: nextError.message });
      if (nextError.status === 401) invalidateSession();
    });
    return () => controller.abort();
  }, [invalidateSession, isAuthLoading, userKey, reloadToken]);

  const ownList = list.owner === userKey;
  const listStatus = !userKey ? 'idle' : ownList ? list.status : 'loading';
  const warehouses = useMemo(() => (userKey && ownList ? list.warehouses : []), [list.warehouses, ownList, userKey]);
  const effectiveWarehouseId = isCustomer && cart?.warehouseId ? cart.warehouseId : selectedWarehouseId;
  const selectedWarehouse = warehouses.find((warehouse) => warehouse.warehouseId === effectiveWarehouseId) || null;
  // Reads only this tab's storage; confirmVersion re-reads it after a successful selection.
  const confirmedId = useMemo(() => (userKey && confirmVersion >= 0 ? readConfirmed(userKey) : ''), [userKey, confirmVersion]);
  const cartReady = !isCustomer || (!isCartLoading && !cartError);
  const branchConfirmed = isCustomer && listStatus === 'ready' && cartReady && Boolean(selectedWarehouse)
    && cart?.warehouseId === selectedWarehouse.warehouseId && confirmedId === selectedWarehouse.warehouseId;

  const markConfirmed = useCallback((warehouseId) => {
    try { sessionStorage.setItem(confirmKey(userKey), warehouseId); } catch { /* Gate then stays per render. */ }
    setConfirmVersion((version) => version + 1);
  }, [userKey]);

  const clearRequiredFor = useCallback((warehouseId) => (
    Boolean(cart?.items?.length && cart.warehouseId && cart.warehouseId !== warehouseId)
  ), [cart]);

  const reloadWarehouses = useCallback(() => {
    setList((current) => ({ ...current, status: 'loading', error: '' }));
    setReloadToken((token) => token + 1);
  }, []);

  async function selectWarehouse(warehouseId, { confirmClear } = {}) {
    if (changing.current) return false;
    const run = epoch.current;
    const active = !ownList || listStatus !== 'ready' || warehouses.some((warehouse) => warehouse.warehouseId === warehouseId);
    if (warehouseId && !active) {
      setSelectError({ owner: userKey, message: 'Chi nhánh này hiện không khả dụng. Vui lòng chọn chi nhánh khác.' });
      return false;
    }
    if (!warehouseId || warehouseId === cart?.warehouseId) {
      setSelectedWarehouseId(warehouseId || '');
      if (warehouseId && isCustomer) markConfirmed(warehouseId);
      return true;
    }
    const mustClear = clearRequiredFor(warehouseId);
    if (mustClear && !(await (confirmClear ? confirmClear() : window.confirm(CLEAR_MESSAGE)))) return false;
    changing.current = true;
    setIsChanging(true);
    setSelectError({ owner: userKey, message: '' });
    try {
      let nextCart = null;
      if (isCustomer) nextCart = await cartApi.selectWarehouse(warehouseId, mustClear);
      if (run !== epoch.current) return false; // result belongs to a previous account/session
      if (isCustomer) {
        applyCart(nextCart);
        if (nextCart?.warehouseId !== warehouseId) {
          setSelectError({ owner: userKey, message: 'Chưa xác minh được chi nhánh của giỏ hàng. Vui lòng thử lại.' });
          return false;
        }
      }
      setSelectedWarehouseId(warehouseId);
      localStorage.setItem(STORAGE_KEY, warehouseId);
      if (isCustomer) markConfirmed(warehouseId);
      return true;
    } catch (nextError) {
      if (run !== epoch.current) return false;
      setSelectError({ owner: userKey, message: nextError.message });
      if (nextError.status === 401) invalidateSession();
      return false;
    } finally {
      changing.current = false;
      setIsChanging(false);
    }
  }

  const ownSelectError = selectError.owner === userKey ? selectError.message : '';
  const value = {
    warehouses,
    selectedWarehouse,
    selectedWarehouseId: selectedWarehouse?.warehouseId || '',
    selectWarehouse,
    isLoading: Boolean(user) && listStatus === 'loading',
    isChanging,
    error: ownSelectError || (ownList ? list.error : ''),
    listStatus,
    reloadWarehouses,
    clearRequiredFor,
    cartReady,
    branchConfirmed,
  };

  return <WarehouseContext.Provider value={value}>{children}</WarehouseContext.Provider>;
}
