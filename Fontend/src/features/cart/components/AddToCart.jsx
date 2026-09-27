import { useState } from 'react';
import { cartApi } from '../api/cartApi';
import { useAuth } from '../../../hooks/useAuth';
import { useCart } from '../../../hooks/useCart';
import { useWarehouse } from '../../../hooks/useWarehouse';
export default function AddToCart({ variantId, availableQuantity, compact = false }) {
  const { invalidateSession } = useAuth();
  const { applyCart, notifyAdded } = useCart();
  const { selectedWarehouseId, selectWarehouse } = useWarehouse();
  const [quantity, setQuantity] = useState(1);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function submit(event) {
    event.preventDefault(); if (busy) return; setBusy(true); setError('');
    try {
      if (!selectedWarehouseId) throw new Error('Vui lòng chọn chi nhánh trước khi thêm vào giỏ.');
      const synchronized = await selectWarehouse(selectedWarehouseId);
      if (!synchronized) return;
      const cart = await cartApi.add(variantId, compact ? 1 : Number(quantity));
      applyCart(cart);
      notifyAdded?.(cart, variantId, compact ? 1 : Number(quantity));
    }
    catch (e) { if (e.status === 401) invalidateSession(); setError(e.message); }
    finally { setBusy(false); }
  }
  return <form onSubmit={submit} className={`commerce-inline${compact ? ' commerce-inline-compact' : ''}`}>
    {!compact && <label>Số lượng<input type="number" min="1" max={availableQuantity ?? 2147483647} step="1" value={quantity} disabled={busy} onChange={(e) => setQuantity(e.target.value)} required /></label>}
    <button className="button" disabled={busy || !selectedWarehouseId || Number(availableQuantity ?? 1) < 1}>{busy ? 'Đang thêm…' : !selectedWarehouseId ? 'Chọn chi nhánh' : 'Thêm vào giỏ'}</button>
    {error && <p className="auth-alert" role="alert">{error}</p>}
  </form>;
}
