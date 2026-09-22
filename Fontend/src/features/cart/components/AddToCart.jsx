import { useState } from 'react';
import { Link } from 'react-router';
import { cartApi } from '../api/cartApi';
import { useAuth } from '../../../hooks/useAuth';
import { useCart } from '../../../hooks/useCart';
import { useWarehouse } from '../../../hooks/useWarehouse';
export default function AddToCart({ variantId, availableQuantity, compact = false }) {
  const { invalidateSession } = useAuth();
  const { applyCart } = useCart();
  const { selectedWarehouseId, selectWarehouse } = useWarehouse();
  const [quantity, setQuantity] = useState(1);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [added, setAdded] = useState(false);
  async function submit(event) {
    event.preventDefault(); setBusy(true); setError(''); setAdded(false);
    try {
      if (!selectedWarehouseId) throw new Error('Vui lòng chọn chi nhánh trước khi thêm vào giỏ.');
      const synchronized = await selectWarehouse(selectedWarehouseId);
      if (!synchronized) return;
      const cart = await cartApi.add(variantId, compact ? 1 : Number(quantity));
      applyCart(cart);
      setAdded(true);
    }
    catch (e) { if (e.status === 401) invalidateSession(); setError(e.message); }
    finally { setBusy(false); }
  }
  return <form onSubmit={submit} className={`commerce-inline${compact ? ' commerce-inline-compact' : ''}`}>
    {!compact && <label>Số lượng<input type="number" min="1" max={availableQuantity ?? 2147483647} step="1" value={quantity} disabled={busy} onChange={(e) => setQuantity(e.target.value)} required /></label>}
    <button className="button" disabled={busy || !selectedWarehouseId || Number(availableQuantity ?? 1) < 1}>{busy ? 'Đang thêm…' : !selectedWarehouseId ? 'Chọn chi nhánh' : 'Thêm vào giỏ'}</button>
    {error && <p role="alert">{error}</p>}{added && <p role="status">Đã thêm. <Link to="/cart">Xem giỏ hàng</Link></p>}
  </form>;
}
