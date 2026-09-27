import { Link } from 'react-router';
import ProductImage from './ProductImage';
import ProductCardCartAction from './ProductCardCartAction';
import ComingSoonButton from '../../../components/ui/ComingSoonButton';

export default function StorefrontProductCard({ product, warehouseId, listSearch = '' }) {
  const detail = `/products/${product.productId}`;
  const inStock = Number(product.availableQuantity || 0) > 0;
  return <article className="retail-product-card">
    <div className="retail-product-visual">
      <Link to={detail} state={{ listSearch }} aria-label={`Xem ${product.productName}`}><ProductImage src={product.primaryImageUrl} alt={product.productName} /></Link>
      <div className="retail-product-tools"><ComingSoonButton icon="heart">Yêu thích</ComingSoonButton><ComingSoonButton icon="compare">So sánh</ComingSoonButton></div>
    </div>
    <div className="retail-product-body">
      <p className="retail-product-brand">{product.brandName || product.categoryName || 'Thiết bị công nghệ'}</p>
      <h3><Link to={detail} state={{ listSearch }}>{product.productName}</Link></h3>
      <Link className="retail-product-price" to={detail} state={{ listSearch }}>Xem giá phiên bản <span aria-hidden="true">↗</span></Link>
      <p className={`retail-product-stock${warehouseId && !inStock ? ' is-empty' : ''}`}><span aria-hidden="true">●</span> {warehouseId ? inStock ? `Còn ${product.availableQuantity} tại chi nhánh` : 'Hết hàng tại chi nhánh' : 'Chọn chi nhánh để xem tồn kho'}</p>
      <div className="retail-product-actions"><ProductCardCartAction key={`${product.productId}:${warehouseId}`} productId={product.productId} productName={product.productName} disabled={!warehouseId || !inStock || product.status !== 'ACTIVE'} /><Link to={detail} state={{ listSearch }}>Xem chi tiết →</Link></div>
      <p className="retail-product-description">{product.description || product.categoryName || 'Chọn phiên bản để xem cấu hình sản phẩm.'}</p>
    </div>
  </article>;
}
