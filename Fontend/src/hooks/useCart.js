import { useContext } from 'react';
import { CartContext } from '../features/cart/cartContext';

const emptyCart = Object.freeze({ itemCount: 0, isLoading: false, error: null });

export function useCart(optional = false) {
  const context = useContext(CartContext);
  if (!context && optional) return emptyCart;
  if (!context) throw new Error('useCart phải được sử dụng bên trong CartProvider.');
  return context;
}
