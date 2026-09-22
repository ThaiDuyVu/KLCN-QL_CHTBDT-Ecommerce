import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ROLES } from '../../config/projectConfig';
import { useAuth } from '../../hooks/useAuth';
import { cartApi } from './api/cartApi';
import { CartContext } from './cartContext';

function countItems(cart) {
  return (cart?.items || []).reduce((total, item) => total + Number(item.quantity || 0), 0);
}

export default function CartProvider({ children }) {
  const { user, invalidateSession } = useAuth();
  const [cart, setCart] = useState(null);
  const [cartOwner, setCartOwner] = useState(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);
  const operation = useRef(0);
  const isCustomer = user?.roleName === ROLES.CUSTOMER;
  const ownerKey = user?.userId || user?.id || user?.username || null;

  const applyCart = useCallback((nextCart) => {
    operation.current += 1;
    setCart(nextCart);
    setCartOwner(ownerKey);
    setError(null);
    setIsLoading(false);
  }, [ownerKey]);

  const clearCart = useCallback(() => {
    operation.current += 1;
    setCart((current) => current ? { ...current, items: [], subtotal: 0 } : null);
    setCartOwner(ownerKey);
    setError(null);
    setIsLoading(false);
  }, [ownerKey]);

  const refreshCart = useCallback(async () => {
    if (!isCustomer) return null;
    const version = ++operation.current;
    setIsLoading(true);
    setError(null);
    try {
      const nextCart = await cartApi.get();
      if (operation.current === version) {
        setCart(nextCart);
        setCartOwner(ownerKey);
      }
      return nextCart;
    } catch (nextError) {
      if (operation.current === version) {
        setError(nextError);
        if (nextError.status === 401) invalidateSession();
      }
      throw nextError;
    } finally {
      if (operation.current === version) setIsLoading(false);
    }
  }, [invalidateSession, isCustomer, ownerKey]);

  useEffect(() => {
    if (!isCustomer) {
      operation.current += 1;
      return;
    }
    const controller = new AbortController();
    const version = ++operation.current;
    cartApi.get(controller.signal).then((nextCart) => {
      if (!controller.signal.aborted && operation.current === version) {
        setCart(nextCart);
        setCartOwner(ownerKey);
        setError(null);
      }
    }).catch((nextError) => {
      if (controller.signal.aborted || operation.current !== version) return;
      setCart(null);
      setCartOwner(ownerKey);
      setError(nextError);
      if (nextError.status === 401) invalidateSession();
    }).finally(() => {
      if (!controller.signal.aborted && operation.current === version) setIsLoading(false);
    });
    return () => controller.abort();
  }, [invalidateSession, isCustomer, ownerKey]);

  const value = useMemo(() => ({
    cart: cartOwner === ownerKey ? cart : null,
    itemCount: cartOwner === ownerKey ? countItems(cart) : 0,
    isLoading: isCustomer && (isLoading || cartOwner !== ownerKey),
    error: cartOwner === ownerKey ? error : null,
    applyCart,
    clearCart,
    refreshCart,
  }), [applyCart, cart, cartOwner, clearCart, error, isCustomer, isLoading, ownerKey, refreshCart]);

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}
