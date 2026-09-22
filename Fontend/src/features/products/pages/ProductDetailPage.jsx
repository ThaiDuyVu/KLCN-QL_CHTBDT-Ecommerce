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
import '../products.css';

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
  const [selectedImageId, setSelectedImageId] = useState(null);
  const load = useCallback((signal) => productApi.detail(productId, signal), [productId]);
  const { data, error, isLoading, retry } = useProductRequest(productId, load);
  useEffect(() => { window.scrollTo(0, 0); }, [productId]);
  const canSeeCost = [ROLES.ADMIN, ROLES.MANAGER].includes(user?.roleName);
  const images = data?.images || [];
  const image = images.find((item) => item.imageId === selectedImageId) || images.find((item) => item.isPrimary) || images[0];

  return (
    <>
      <Link className="button button-quiet product-back" to={backTo}>← Danh sách sản phẩm</Link>
      {isLoading ? <><PageHeader title="Chi tiết sản phẩm" /><ProductSkeleton detail /></> : error ? (
        <ProductState error title={error.status === 404 ? 'Không tìm thấy sản phẩm' : error.status === 403 ? 'Không có quyền xem sản phẩm' : 'Chưa tải được sản phẩm'}
          message={error.status === 404 ? 'Sản phẩm có thể đã bị xóa hoặc đường dẫn không còn đúng.' : error.status === 403 ? 'Tài khoản của bạn không có quyền truy cập dữ liệu này.' : error.message}
          retry={[403, 404].includes(error.status) ? undefined : retry} />
      ) : data?.product ? (
        <>
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
                <th scope="col">SKU</th><th scope="col">Màu</th><th scope="col">Dung lượng</th><th scope="col">RAM</th><th scope="col">Giá bán</th>
                {canSeeCost && <th scope="col">Giá vốn</th>}<th scope="col">Trạng thái</th>
              </tr></thead><tbody>{data.variants.map((variant) => <tr key={variant.variantId}>
                <th scope="row">{variant.sku}</th><td>{variant.color || '—'}</td><td>{variant.storage || '—'}</td><td>{variant.ram || '—'}</td>
                <td>{formatPrice(variant.price)}</td>{canSeeCost && <td>{formatPrice(variant.costPrice)}</td>}
                <td>{variant.status === 'ACTIVE' ? 'Đang hoạt động' : 'Ngừng hoạt động'}</td>
              </tr>)}</tbody></table>
            </div> : <p className="muted">Chưa có phiên bản sản phẩm.</p>}
          </section>
          <section className="panel product-section product-section-spaced">
            <h2>Thông số kỹ thuật</h2>
            {data.specifications?.length ? <dl className="product-info product-specs">
              {data.specifications.map((spec) => <div key={spec.specificationId}><dt>{spec.specKey}</dt><dd>{spec.specValue}</dd></div>)}
            </dl> : <p className="muted">Chưa có thông số kỹ thuật.</p>}
          </section>
        </>
      ) : <ProductState title="Chưa có thông tin sản phẩm" message="Vui lòng tải lại dữ liệu." retry={retry} />}
    </>
  );
}
