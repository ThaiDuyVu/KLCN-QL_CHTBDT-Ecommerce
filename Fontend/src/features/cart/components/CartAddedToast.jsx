import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { Link } from 'react-router';
import ShopIcon from '../../../components/ui/ShopIcon';
import './cart-added-toast.css';

export default function CartAddedToast({ notification, onClose }) {
  const [paused, setPaused] = useState(false);
  useEffect(() => {
    if (paused) return;
    const timer = window.setTimeout(onClose, 6500);
    return () => window.clearTimeout(timer);
  }, [paused, onClose]);
  useEffect(() => {
    const dismiss = (event) => { if (event.key === 'Escape') onClose(); };
    window.addEventListener('keydown', dismiss);
    return () => window.removeEventListener('keydown', dismiss);
  }, [onClose]);
  return createPortal(<aside className="cart-added-toast" aria-label="Thông báo giỏ hàng"
    onMouseEnter={() => setPaused(true)} onMouseLeave={() => setPaused(false)}
    onFocus={() => setPaused(true)} onBlur={(event) => { if (!event.currentTarget.contains(event.relatedTarget)) setPaused(false); }}>
    <span className="cart-added-toast-icon"><ShopIcon name="check" /></span>
    <div className="cart-added-toast-content">
      <div role="status" aria-live="polite" aria-atomic="true"><strong>Đã thêm vào giỏ hàng</strong><p>{notification.productName}</p><small>{notification.quantity} sản phẩm{notification.sku ? ` · ${notification.sku}` : ''}</small></div>
      <Link to="/cart" onClick={onClose}>Xem giỏ hàng <ShopIcon name="arrow" /></Link>
    </div>
    <button type="button" className="cart-added-toast-close" onClick={onClose} aria-label="Đóng thông báo giỏ hàng"><ShopIcon name="close" /></button>
  </aside>, document.body);
}
