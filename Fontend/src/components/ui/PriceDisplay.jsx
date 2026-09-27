import { money } from '../../features/orders/components/orderFormat';
import './price-display.css';
export default function PriceDisplay({ originalPrice, effectivePrice, discountAmount = 0, promotion, from = false }) {
  if (originalPrice == null || effectivePrice == null) return <span>Chưa có giá bán</span>;
  const discounted = Number(discountAmount) > 0;
  return <span className="price-display">
    <strong className="price-display-current">{from ? 'Từ ' : ''}{money(effectivePrice)}</strong>
    {discounted && <><del className="price-display-original" aria-label={`Giá gốc ${money(originalPrice)}`}>{money(originalPrice)}</del><span className="price-display-badge">{promotion?.discountType === 'PERCENTAGE' ? `−${promotion.discountValue}%` : `Giảm ${money(discountAmount)}`}</span></>}
  </span>;
}
