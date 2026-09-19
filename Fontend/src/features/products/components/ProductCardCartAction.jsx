import { useEffect, useRef, useState } from 'react';
import AddToCart from '../../cart/components/AddToCart';
import { useAuth } from '../../../hooks/useAuth';
import { productApi } from '../api/productApi';

const money = new Intl.NumberFormat('vi-VN', {
  style: 'currency', currency: 'VND', minimumFractionDigits: 0, maximumFractionDigits: 2,
});

function variantLabel(variant) {
  const options = [variant.sku, variant.color, variant.storage, variant.ram].filter(Boolean).join(' · ');
  return `${options} · ${money.format(variant.price)}`;
}

export default function ProductCardCartAction({ productId, productName }) {
  const { invalidateSession } = useAuth();
  const [isOpen, setIsOpen] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [variants, setVariants] = useState([]);
  const [variantId, setVariantId] = useState('');
  const controller = useRef(null);

  useEffect(() => () => controller.current?.abort(), []);

  async function open() {
    setIsOpen(true);
    if (variants.length || isLoading) return;
    controller.current?.abort();
    controller.current = new AbortController();
    setIsLoading(true);
    setError('');
    try {
      const detail = await productApi.detail(productId, controller.current.signal);
      const available = (detail?.variants || []).filter((variant) => variant.status === 'ACTIVE');
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
    return <button className="button" type="button" onClick={open} aria-label={`Thêm ${productName} vào giỏ`}>Thêm vào giỏ</button>;
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
      <AddToCart key={variantId} variantId={variantId} compact />
    </> : <p className="muted">Sản phẩm chưa có phiên bản đang bán.</p>}
    <button className="product-card-cart-close" type="button" onClick={() => setIsOpen(false)}>Đóng</button>
  </div>;
}
