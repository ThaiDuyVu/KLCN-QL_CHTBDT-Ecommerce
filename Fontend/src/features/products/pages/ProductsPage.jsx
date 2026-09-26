import { useCallback, useEffect } from 'react';
import { Link, useLocation, useSearchParams } from 'react-router';
import PageHeader from '../../../components/ui/PageHeader';
import { productApi } from '../api/productApi';
import useProductRequest from '../hooks/useProductRequest';
import ProductSkeleton from '../components/ProductSkeleton';
import ProductState from '../components/ProductState';
import ProductCardCartAction from '../components/ProductCardCartAction';
import ProductImage from '../components/ProductImage';
import StorefrontProductCard from '../components/StorefrontProductCard';
import ShopBreadcrumb from '../../../components/ui/ShopBreadcrumb';
import { categoryApi } from '../../categories/api/categoryApi';
import { useAuth } from '../../../hooks/useAuth';
import { ROLES } from '../../../config/projectConfig';
import { useWarehouse } from '../../../hooks/useWarehouse';
import '../products.css';

const PAGE_SIZE = 12;

export default function ProductsPage() {
  const { user } = useAuth();
  const isCustomer = user?.roleName === ROLES.CUSTOMER;
  const { selectedWarehouse, selectedWarehouseId, error: warehouseError } = useWarehouse();
  const [searchParams, setSearchParams] = useSearchParams();
  const location = useLocation();
  const rawPage = Number(searchParams.get('page') || 1);
  const page = Number.isSafeInteger(rawPage) && rawPage > 0 && (rawPage - 1) * PAGE_SIZE <= 2147483647 ? rawPage - 1 : 0;
  const keyword = (searchParams.get('keyword') || '').trim();
  const categoryId = searchParams.get('categoryId') || '';
  const rawStatus = searchParams.get('status') || '';
  const status = ['ACTIVE', 'INACTIVE'].includes(rawStatus) ? rawStatus : '';
  const effectiveStatus = isCustomer ? 'ACTIVE' : status;
  const load = useCallback((signal) => productApi.list({ page, size: PAGE_SIZE, keyword, categoryId, status: effectiveStatus, warehouseId: selectedWarehouseId }, signal), [page, keyword, categoryId, selectedWarehouseId, effectiveStatus]);
  const { data, error, isLoading, retry } = useProductRequest(JSON.stringify([page, keyword, categoryId, effectiveStatus, selectedWarehouseId]), load);
  const loadCategories = useCallback((signal) => isCustomer ? categoryApi.list(signal) : Promise.resolve([]), [isCustomer]);
  const categories = useProductRequest(`catalog-categories:${isCustomer}`, loadCategories);
  useEffect(() => { window.scrollTo(0, 0); }, [page, keyword, categoryId, status]);

  function search(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const next = new URLSearchParams();
    const value = form.get('keyword').trim();
    if (value) next.set('keyword', value);
    const selectedCategory = isCustomer ? form.get('categoryId') : categoryId;
    if (selectedCategory) next.set('categoryId', selectedCategory);
    if (!isCustomer && form.get('status')) next.set('status', form.get('status'));
    setSearchParams(next);
  }

  function changePage(nextPage) {
    const next = new URLSearchParams(searchParams);
    next.set('page', String(nextPage + 1));
    setSearchParams(next);
  }

  return (
    <>
      {isCustomer && <ShopBreadcrumb items={[{ label: 'Cửa hàng' }]} />}
      <div className={isCustomer ? 'product-storefront-heading' : ''}>
        <PageHeader title={isCustomer ? 'Khám phá sản phẩm' : 'Sản phẩm'} description={isCustomer ? 'Chọn thiết bị phù hợp và kiểm tra tồn kho ngay tại chi nhánh của bạn.' : 'Xem danh sách thiết bị và thông tin chi tiết.'} />
      </div>
      {isCustomer && <p className={warehouseError ? 'auth-alert' : 'product-branch-note'}>
        <span aria-hidden="true">●</span> {warehouseError || (selectedWarehouse ? `Đang xem tồn kho tại ${selectedWarehouse.warehouseName}` : 'Chọn chi nhánh ở thanh trên để xem tồn kho và mua hàng')}
      </p>}
      {categoryId && <p className="product-category-filter">Đang lọc theo danh mục <button type="button" onClick={() => { const next = new URLSearchParams(searchParams); next.delete('categoryId'); next.delete('page'); setSearchParams(next); }}>Bỏ lọc</button></p>}
      <form className={`panel product-filters${isCustomer ? ' product-filters-storefront' : ''}`} onSubmit={search} key={`${keyword}:${status}:${categoryId}`} role="search" aria-label="Tìm sản phẩm">
        <label><span>{isCustomer ? 'Tìm thiết bị' : 'Tên hoặc mô tả'}</span><input type="search" name="keyword" defaultValue={keyword} placeholder="Tìm theo tên hoặc mô tả…" /></label>
        {isCustomer && <label>Danh mục<select name="categoryId" defaultValue={categoryId} disabled={categories.isLoading || Boolean(categories.error)}><option value="">Tất cả danh mục</option>{(categories.data || []).filter((category) => category.status === 'ACTIVE').map((category) => <option value={category.categoryId} key={category.categoryId}>{category.categoryName}</option>)}</select></label>}
        {!isCustomer && <label>Trạng thái<select name="status" defaultValue={status}>
          <option value="">Tất cả</option><option value="ACTIVE">Đang hoạt động</option><option value="INACTIVE">Ngừng hoạt động</option>
        </select></label>}
        <button className="button" type="submit">Tìm kiếm</button>
        {(keyword || categoryId || (!isCustomer && status)) && <button className="button button-quiet" type="button" onClick={() => setSearchParams({})}>Xóa bộ lọc</button>}
      </form>
      {isCustomer && categories.error && <p className="auth-alert" role="alert">Chưa tải được bộ lọc danh mục. <button type="button" onClick={categories.retry}>Thử lại</button></p>}
      {isLoading ? <ProductSkeleton /> : error ? (
        <ProductState error title={error.status === 403 ? 'Không có quyền xem sản phẩm' : 'Chưa tải được danh sách'}
          message={error.status === 403 ? 'Tài khoản của bạn không có quyền truy cập dữ liệu này.' : error.message}
          retry={error.status === 403 ? undefined : retry} />
      ) : data?.content?.length ? (
        <>
          <p className="muted product-count" role="status">{data.totalElements} sản phẩm · Trang {data.page + 1}/{data.totalPages}</p>
          <div className={isCustomer ? 'retail-catalog-grid' : 'product-grid'}>
            {data.content.map((product) => (
              isCustomer ? <StorefrontProductCard key={product.productId} product={product} warehouseId={selectedWarehouseId} listSearch={location.search} /> : <article className="panel product-card" key={product.productId}>
                <Link className="product-card-image-link" to={`/products/${product.productId}`} state={{ listSearch: location.search }} aria-label={`Xem ${product.productName}`}>
                  <ProductImage src={product.primaryImageUrl} alt={product.productName} />
                </Link>
                {!isCustomer && <span className="badge">{product.status === 'ACTIVE' ? 'Đang hoạt động' : 'Ngừng hoạt động'}</span>}
                {isCustomer && <p className="product-card-brand">{product.brandName || product.categoryName || 'Thiết bị công nghệ'}</p>}
                <h2><Link to={`/products/${product.productId}`} state={{ listSearch: location.search }}>{product.productName}</Link></h2>
                <dl className="product-card-meta">
                  <div><dt>Danh mục</dt><dd>{product.categoryName || '—'}</dd></div>
                  <div><dt>Thương hiệu</dt><dd>{product.brandName || '—'}</dd></div>
                </dl>
                <p className="muted product-summary">{product.description || 'Chưa có mô tả.'}</p>
                {isCustomer && <div className="product-card-price"><strong>Giá theo phiên bản</strong><span>Chọn cấu hình để xem giá</span></div>}
                {selectedWarehouseId && <p className="product-availability">
                  <span aria-hidden="true">●</span> {Number(product.availableQuantity || 0) > 0 ? `Còn ${product.availableQuantity} sản phẩm tại chi nhánh` : 'Hết hàng tại chi nhánh'}
                </p>}
                <div className="product-card-actions">
                  <Link className="button button-quiet" to={`/products/${product.productId}`}
                    state={{ listSearch: location.search }} aria-label={`Xem chi tiết ${product.productName}`}>Xem chi tiết</Link>
                  {user?.roleName === ROLES.CUSTOMER && product.status === 'ACTIVE' &&
                    <ProductCardCartAction key={`${product.productId}:${selectedWarehouseId}`} productId={product.productId} productName={product.productName}
                      disabled={!selectedWarehouseId || Number(product.availableQuantity || 0) < 1} />}
                </div>
              </article>
            ))}
          </div>
          <nav className="product-pagination" aria-label="Phân trang sản phẩm">
            <button className="button button-quiet" disabled={data.page === 0} onClick={() => changePage(data.page - 1)}>Trang trước</button>
            <span>Trang {data.page + 1}/{data.totalPages}</span>
            <button className="button button-quiet" disabled={data.page + 1 >= data.totalPages} onClick={() => changePage(data.page + 1)}>Trang sau</button>
          </nav>
        </>
      ) : (
        <ProductState title={page > 0 ? 'Trang này không còn sản phẩm' : 'Chưa có sản phẩm'}
          message={keyword || categoryId || status ? 'Không tìm thấy sản phẩm phù hợp. Hãy thử thay đổi bộ lọc.' : 'Danh sách hiện chưa có dữ liệu.'} />
      )}
      {!isLoading && !error && !data?.content?.length && page > 0 && (
        <button className="button button-quiet" onClick={() => changePage(0)}>Về trang đầu</button>
      )}
    </>
  );
}
