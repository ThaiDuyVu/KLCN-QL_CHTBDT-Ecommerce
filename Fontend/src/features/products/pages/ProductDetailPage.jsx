import { useCallback, useEffect, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router';
import PageHeader from '../../../components/ui/PageHeader';
import { useAuth } from '../../../hooks/useAuth';
import { ROLES } from '../../../config/projectConfig';
import { productApi } from '../api/productApi';
import useProductRequest from '../hooks/useProductRequest';
import ProductSkeleton from '../components/ProductSkeleton';
import ProductState from '../components/ProductState';
import ProductImage from '../components/ProductImage';
import AddToCart from '../../cart/components/AddToCart';
import { useWarehouse } from '../../../hooks/useWarehouse';
import ShopBreadcrumb from '../../../components/ui/ShopBreadcrumb';
import ComingSoonButton from '../../../components/ui/ComingSoonButton';
import '../products.css';
import '../../orders/commerce.css';

const money = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', minimumFractionDigits: 0, maximumFractionDigits: 2 });
function formatPrice(value) { return value == null ? '—' : money.format(value); }
function formatDate(value) {
  if (!value || Number.isNaN(Date.parse(value))) return '—';
  return new Date(value).toLocaleString('vi-VN');
}

export default function ProductDetailPage() {
  const { productId } = useParams();
  const location = useLocation();
  const listSearch = typeof location.state?.listSearch === 'string' && location.state.listSearch.startsWith('?') ? location.state.listSearch : '';
  const backTo = `/products${listSearch}`;
  if (!/^[\da-f]{8}-[\da-f]{4}-[\da-f]{4}-[\da-f]{4}-[\da-f]{12}$/i.test(productId || '')) {
    return <ProductState title="Không tìm thấy sản phẩm" message="Đường dẫn sản phẩm không hợp lệ." backTo={backTo} />;
  }
  return <ProductDetail key={productId} productId={productId} backTo={backTo} />;
}

function ProductDetail({ productId, backTo }) {
  const { user } = useAuth();
  const { selectedWarehouse, selectedWarehouseId } = useWarehouse();
  const [selectedImageId, setSelectedImageId] = useState(null);
  const [selectedVariantId, setSelectedVariantId] = useState('');
  const load = useCallback((signal) => productApi.detail(productId, selectedWarehouseId, signal), [productId, selectedWarehouseId]);
  const { data, error, isLoading, retry } = useProductRequest(`${productId}:${selectedWarehouseId}`, load);
  useEffect(() => { window.scrollTo(0, 0); }, [productId]);

  const images = data?.images || [];
  const image = images.find((item) => item.imageId === selectedImageId) || images.find((item) => item.isPrimary) || images[0];
  const activeVariants = (data?.variants || []).filter((variant) => variant.status === 'ACTIVE');
  const selectedVariant = activeVariants.find((variant) => variant.variantId === selectedVariantId)
    || activeVariants.find((variant) => Number(variant.availableQuantity || 0) > 0)
    || activeVariants[0];

  return (
    <>
      {user?.roleName === ROLES.CUSTOMER && <ShopBreadcrumb items={[{ label: 'Cửa hàng', to: '/products' }, { label: data?.product?.productName || 'Chi tiết sản phẩm' }]} />}
      <Link className="product-back-link" to={backTo}>← Quay lại danh sách</Link>
      {isLoading ? <><PageHeader title="Chi tiết sản phẩm" /><ProductSkeleton detail /></> : error ? (
        <ProductState error title={error.status === 404 ? 'Không tìm thấy sản phẩm' : error.status === 403 ? 'Không có quyền xem sản phẩm' : 'Chưa tải được sản phẩm'}
          message={error.status === 404 ? 'Sản phẩm có thể đã bị xóa hoặc đường dẫn không còn đúng.' : error.status === 403 ? 'Tài khoản của bạn không có quyền truy cập dữ liệu này.' : error.message}
          retry={[403, 404].includes(error.status) ? undefined : retry} />
      ) : data?.product ? user?.roleName === ROLES.CUSTOMER ? (
        <CustomerProductDetail data={data} images={images} image={image} setSelectedImageId={setSelectedImageId}
          selectedVariant={selectedVariant} setSelectedVariantId={setSelectedVariantId}
          selectedWarehouse={selectedWarehouse} selectedWarehouseId={selectedWarehouseId} />
      ) : (
        <ManagementProductDetail data={data} images={images} image={image} setSelectedImageId={setSelectedImageId}
          selectedWarehouseId={selectedWarehouseId} user={user} />
      ) : <ProductState title="Chưa có thông tin sản phẩm" message="Vui lòng tải lại dữ liệu." retry={retry} />}
    </>
  );
}

function CustomerProductDetail({ data, images, image, setSelectedImageId, selectedVariant, setSelectedVariantId,
  selectedWarehouse, selectedWarehouseId }) {
  const purchasable = data.product.status === 'ACTIVE' && selectedVariant;
  const inStock = Number(selectedVariant?.availableQuantity || 0) > 0;
  return <>
    <div className="customer-product-detail">
      <section className="product-gallery-panel" aria-label="Ảnh sản phẩm">
        <div className="product-gallery-main"><ProductImage src={image?.imageUrl} alt={data.product.productName} /></div>
        {images.length > 1 && <div className="product-gallery-thumbnails" aria-label="Chọn ảnh">
          {images.map((item, index) => <button key={item.imageId} type="button"
            onClick={() => setSelectedImageId(item.imageId)} aria-pressed={item.imageId === image?.imageId}
            aria-label={`Xem ảnh ${index + 1}`}>
            <img src={item.imageUrl} alt="" loading="lazy" />
          </button>)}
        </div>}
      </section>

      <section className="product-buy-box">
        <p className="product-detail-brand">{data.brand?.brandName || data.product.brandName || 'Thiết bị công nghệ'}</p>
        <h1>{data.product.productName}</h1>
        <p className="product-detail-category">{data.category?.categoryName || data.product.categoryName || 'Sản phẩm công nghệ'}</p>
        <p className="product-buy-price">{selectedVariant ? formatPrice(selectedVariant.price) : 'Chưa có giá bán'}</p>
        <div className={`product-stock-callout${inStock ? '' : ' out-of-stock'}`}>
          <span aria-hidden="true">●</span>
          <div><strong>{selectedWarehouse ? selectedWarehouse.warehouseName : 'Chưa chọn chi nhánh'}</strong>
            <p>{!selectedWarehouseId ? 'Chọn chi nhánh để xem tồn kho và mua hàng.' : inStock ? `Còn ${selectedVariant.availableQuantity} sản phẩm sẵn sàng bán.` : 'Phiên bản này đang hết hàng tại chi nhánh.'}</p>
          </div>
        </div>

        <fieldset className="variant-picker" disabled={!purchasable}>
          <legend>Chọn phiên bản</legend>
          <div className="variant-chip-grid">
            {(data.variants || []).filter((variant) => variant.status === 'ACTIVE').map((variant) => {
              const available = Number(variant.availableQuantity || 0) > 0;
              const selected = variant.variantId === selectedVariant?.variantId;
              return <button type="button" className="variant-chip" key={variant.variantId}
                aria-pressed={selected} disabled={selectedWarehouseId && !available}
                onClick={() => setSelectedVariantId(variant.variantId)}>
                <strong>{[variant.color, variant.storage, variant.ram].filter(Boolean).join(' · ') || variant.sku}</strong>
                <span>{formatPrice(variant.price)}</span>
                {selectedWarehouseId && <small>{available ? `Còn ${variant.availableQuantity}` : 'Hết hàng'}</small>}
              </button>;
            })}
          </div>
        </fieldset>

        {selectedVariant && <dl className="selected-variant-meta">
          <div><dt>SKU</dt><dd>{selectedVariant.sku}</dd></div>
          <div><dt>Bảo hành</dt><dd>{selectedVariant.warrantyMonths > 0 ? `${selectedVariant.warrantyMonths} tháng` : 'Không bảo hành'}</dd></div>
        </dl>}
        {purchasable ? <div className="product-purchase-action">
          <AddToCart key={`${selectedVariant.variantId}:${selectedWarehouseId}`} variantId={selectedVariant.variantId}
            availableQuantity={selectedVariant.availableQuantity} />
        </div> : <p className="auth-alert">Sản phẩm hiện chưa thể đặt mua.</p>}
        <div className="product-future-actions"><ComingSoonButton icon="heart">Thêm vào yêu thích</ComingSoonButton><ComingSoonButton icon="compare">So sánh sản phẩm</ComingSoonButton></div>
        <div className="product-purchase-info"><p>Giao hàng từ chi nhánh đã chọn</p><p>Thanh toán COD hoặc gửi hồ sơ trả góp khi checkout</p></div>
      </section>
    </div>

    <div className="product-detail-content-grid">
      <section className="panel product-section">
        <h2>Giới thiệu sản phẩm</h2>
        <p className="product-description">{data.product.description || 'Chưa có mô tả.'}</p>
      </section>
      <section className="panel product-section">
        <h2>Thông số kỹ thuật</h2>
        {data.specifications?.length ? <dl className="product-info product-specs">
          {data.specifications.map((spec) => <div key={spec.specificationId}><dt>{spec.specKey}</dt><dd>{spec.specValue}</dd></div>)}
        </dl> : <p className="muted">Chưa có thông số kỹ thuật.</p>}
      </section>
    </div>
    <section className="panel product-reviews-placeholder"><div><h2>Đánh giá sản phẩm</h2><p>Chức năng đánh giá sẽ được bổ sung sau.</p></div><ComingSoonButton>Viết đánh giá</ComingSoonButton></section>
  </>;
}

function ManagementProductDetail({ data, images, image, setSelectedImageId, selectedWarehouseId, user }) {
  const canSeeCost = [ROLES.ADMIN, ROLES.MANAGER].includes(user?.roleName);
  return <>
    <PageHeader title={data.product.productName} />
    <div className="product-detail-grid">
      <section className="panel product-section" aria-label="Ảnh sản phẩm">
        <ProductImage src={image?.imageUrl} alt={data.product.productName} />
        {images.length > 1 && <div className="product-gallery" aria-label="Chọn ảnh">
          {images.map((item, index) => <button className="button button-quiet" key={item.imageId}
            onClick={() => setSelectedImageId(item.imageId)} aria-pressed={item.imageId === image?.imageId}>
            Ảnh {index + 1}{item.isPrimary ? ' · Chính' : ''}
          </button>)}
        </div>}
      </section>
      <section className="panel product-section">
        <h2>Thông tin sản phẩm</h2>
        <dl className="product-info">
          <div><dt>Danh mục</dt><dd>{data.category?.categoryName || data.product.categoryName || '—'}</dd></div>
          <div><dt>Thương hiệu</dt><dd>{data.brand?.brandName || data.product.brandName || '—'}</dd></div>
          <div><dt>Trạng thái</dt><dd>{data.product.status === 'ACTIVE' ? 'Đang hoạt động' : 'Ngừng hoạt động'}</dd></div>
          <div><dt>Ngày tạo</dt><dd>{formatDate(data.product.createdAt)}</dd></div>
          <div><dt>Cập nhật</dt><dd>{formatDate(data.product.updatedAt)}</dd></div>
        </dl>
        <h3>Mô tả</h3><p className="product-description">{data.product.description || 'Chưa có mô tả.'}</p>
      </section>
    </div>
    <section className="panel product-section product-section-spaced">
      <h2>Phiên bản và giá bán</h2>
      {data.variants?.length ? <div className="product-table-scroll" role="region" aria-label="Các phiên bản sản phẩm" tabIndex={0}>
        <table className="product-table"><thead><tr>
          <th scope="col">SKU</th><th scope="col">Màu</th><th scope="col">Dung lượng</th><th scope="col">RAM</th><th scope="col">Bảo hành</th><th scope="col">Giá bán</th>
          {canSeeCost && <th scope="col">Giá vốn</th>}<th scope="col">Trạng thái</th>{selectedWarehouseId && <th scope="col">Khả dụng</th>}
        </tr></thead><tbody>{data.variants.map((variant) => <tr key={variant.variantId}>
          <th scope="row">{variant.sku}</th><td>{variant.color || '—'}</td><td>{variant.storage || '—'}</td><td>{variant.ram || '—'}</td><td>{variant.warrantyMonths > 0 ? `${variant.warrantyMonths} tháng` : 'Không bảo hành'}</td>
          <td>{formatPrice(variant.price)}</td>{canSeeCost && <td>{formatPrice(variant.costPrice)}</td>}
          <td>{variant.status === 'ACTIVE' ? 'Đang hoạt động' : 'Ngừng hoạt động'}</td>
          {selectedWarehouseId && <td>{Number(variant.availableQuantity || 0) > 0 ? variant.availableQuantity : 'Hết hàng'}</td>}
        </tr>)}</tbody></table>
      </div> : <p className="muted">Chưa có phiên bản sản phẩm.</p>}
    </section>
    <section className="panel product-section product-section-spaced">
      <h2>Thông số kỹ thuật</h2>
      {data.specifications?.length ? <dl className="product-info product-specs">
        {data.specifications.map((spec) => <div key={spec.specificationId}><dt>{spec.specKey}</dt><dd>{spec.specValue}</dd></div>)}
      </dl> : <p className="muted">Chưa có thông số kỹ thuật.</p>}
    </section>
  </>;
}
