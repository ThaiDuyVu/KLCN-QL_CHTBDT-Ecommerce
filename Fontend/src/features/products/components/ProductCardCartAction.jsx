import { useEffect, useRef, useState } from 'react';
import AddToCart from '../../cart/components/AddToCart';
import { useAuth } from '../../../hooks/useAuth';
import { productApi } from '../api/productApi';
import { useWarehouse } from '../../../hooks/useWarehouse';

const money = new Intl.NumberFormat('vi-VN', {
  style: 'currency', currency: 'VND', minimumFractionDigits: 0, maximumFractionDigits: 2,
});

function variantLabel(variant) {
  const options = [variant.sku, variant.color, variant.storage, variant.ram].filter(Boolean).join(' · ');
  return `${options} · ${money.format(variant.price)}`;
}

export default function ProductCardCartAction({ productId, productName, disabled = false }) {
  const { invalidateSession } = useAuth();
  const [isOpen, setIsOpen] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [variants, setVariants] = useState([]);
  const [variantId, setVariantId] = useState('');
  const controller = useRef(null);
  const { selectedWarehouseId } = useWarehouse();

  useEffect(() => () => controller.current?.abort(), []);
  async function open() {
    setIsOpen(true);
    if (variants.length || isLoading) return;
    controller.current?.abort();
    controller.current = new AbortController();
    setIsLoading(true);
    setError('');
    try {
      const detail = await productApi.detail(productId, selectedWarehouseId, controller.current.signal);
      const available = (detail?.variants || []).filter((variant) => variant.status === 'ACTIVE' && Number(variant.availableQuantity || 0) > 0);
      setVariants(available);
      setVariantId(available[0]?.variantId || '');
    } catch (nextError) {
      if (nextError.name !== 'AbortError') {
        if (nextError.status === 401) invalidateSession();
        setError(nextError.message);
      }
    } finally {
      if (!controller.current?.signal.aborted) setIsLoading(false);
    }
  }

  if (!isOpen) {
    return <button className="button" type="button" disabled={disabled} onClick={open} aria-label={`Thêm ${productName} vào giỏ`}>
      {!selectedWarehouseId ? 'Chọn chi nhánh' : disabled ? 'Hết hàng' : 'Thêm vào giỏ'}
    </button>;
  }

  return <div className="product-card-cart">
    {isLoading ? <p role="status" className="muted">Đang tải phiên bản…</p> : error ? <>
      <p role="alert" className="auth-alert">{error}</p>
      <button className="button button-quiet" type="button" onClick={open}>Thử lại</button>
    </> : variants.length ? <>
      <label>Phiên bản
        <select value={variantId} onChange={(event) => setVariantId(event.target.value)}>
          {variants.map((variant) => <option key={variant.variantId} value={variant.variantId}>{variantLabel(variant)}</option>)}
        </select>
      </label>
      <AddToCart key={variantId} variantId={variantId} availableQuantity={variants.find((variant) => variant.variantId === variantId)?.availableQuantity} compact />
    </> : <p className="muted">Sản phẩm chưa có phiên bản đang bán.</p>}
    <button className="product-card-cart-close" type="button" onClick={() => setIsOpen(false)}>Đóng</button>
  </div>;
}
