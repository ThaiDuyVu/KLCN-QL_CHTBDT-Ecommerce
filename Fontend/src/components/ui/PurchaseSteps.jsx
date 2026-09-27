import { Link } from 'react-router';

export default function PurchaseSteps({ checkout = false }) {
  return <ol className="shop-purchase-steps" aria-label="Các bước mua hàng">
    <li className={checkout ? 'is-done' : 'is-current'} aria-current={!checkout ? 'step' : undefined}><span>{checkout ? '✓' : '1'}</span>{checkout ? <Link to="/cart">Giỏ hàng</Link> : 'Giỏ hàng'}</li>
    <li className={checkout ? 'is-current' : ''} aria-current={checkout ? 'step' : undefined}><span>2</span>Thanh toán</li>
    <li><span>3</span>Hoàn tất đơn hàng</li>
  </ol>;
}
