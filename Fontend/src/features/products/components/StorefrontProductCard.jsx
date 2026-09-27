import { money } from '../../orders/components/orderFormat';
import { Link } from 'react-router';
import ProductImage from './ProductImage';
import PriceDisplay from '../../../components/ui/PriceDisplay';
import ProductCardCartAction from './ProductCardCartAction';
import ComingSoonButton from '../../../components/ui/ComingSoonButton';

export default function StorefrontProductCard({ product, warehouseId, listSearch = '' }) {
  const detail = `/products/${product.productId}`;
  const inStock = Number(product.availableQuantity || 0) > 0;
  const discounted = Number(product.discountAmount || 0) > 0;
  return <article className={`retail-product-card${discounted ? ' retail-product-card-sale' : ''}`}>
    <div className="retail-product-visual">
      {discounted && <span className="retail-sale-sticker">{product.promotion?.discountType === 'PERCENTAGE' ? `−${product.promotion.discountValue}%` : 'Ưu đãi'}</span>}
      <Link to={detail} state={{ listSearch }} aria-label={`Xem ${product.productName}`}><ProductImage src={product.primaryImageUrl} alt={product.productName} /></Link>
      <div className="retail-product-tools"><ComingSoonButton icon="heart">Yêu thích</ComingSoonButton><ComingSoonButton icon="compare">So sánh</ComingSoonButton></div>
    </div>
    <div className="retail-product-body">
      <p className="retail-product-brand">{product.brandName || product.categoryName || 'Thiết bị công nghệ'}</p>
      <h3><Link to={detail} state={{ listSearch }}>{product.productName}</Link></h3>
      <Link className="retail-product-price" to={detail} state={{ listSearch }}>{product.effectivePrice != null ? <PriceDisplay originalPrice={product.originalPrice} effectivePrice={product.effectivePrice} discountAmount={product.discountAmount} promotion={product.promotion} from /> : <>Xem giá phiên bản <span aria-hidden="true">↗</span></>}</Link>
      {discounted && <p className="retail-sale-saving">Tiết kiệm <strong>{money(product.discountAmount)}</strong></p>}
      <p className={`retail-product-stock${warehouseId && !inStock ? ' is-empty' : ''}`}><span aria-hidden="true">●</span> {warehouseId ? inStock ? `Còn ${product.availableQuantity} tại chi nhánh` : 'Hết hàng tại chi nhánh' : 'Chọn chi nhánh để xem tồn kho'}</p>
      <div className="retail-product-actions"><ProductCardCartAction key={`${product.productId}:${warehouseId}`} productId={product.productId} productName={product.productName} disabled={!warehouseId || !inStock || product.status !== 'ACTIVE'} /><Link to={detail} state={{ listSearch }}>Xem chi tiết →</Link></div>
      <p className="retail-product-description">{product.description || product.categoryName || 'Chọn phiên bản để xem cấu hình sản phẩm.'}</p>
    </div>
  </article>;
}
