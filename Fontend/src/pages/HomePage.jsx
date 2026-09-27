import { useCallback } from 'react';
import { Link } from 'react-router';
import PageHeader from '../components/ui/PageHeader';
import { useAuth } from '../hooks/useAuth';
import { useWarehouse } from '../hooks/useWarehouse';
import { ROLES } from '../config/projectConfig';
import { categoryApi } from '../features/categories/api/categoryApi';
import { productApi } from '../features/products/api/productApi';
import useProductRequest from '../features/products/hooks/useProductRequest';
import ProductImage from '../features/products/components/ProductImage';
import StorefrontProductCard from '../features/products/components/StorefrontProductCard';
import ShopIcon from '../components/ui/ShopIcon';
import ComingSoonButton from '../components/ui/ComingSoonButton';
import '../features/products/products.css';
import './storefront-home.css';

const hero = '/images/hero/';

export default function HomePage() {
  const { user } = useAuth();
  if (user && user.roleName !== ROLES.CUSTOMER) return <ManagementHome />;
  return <StorefrontHome customer={user?.roleName === ROLES.CUSTOMER} />;
}

function StorefrontHome({ customer }) {
  const { selectedWarehouse, selectedWarehouseId } = useWarehouse();
  const loadProducts = useCallback((signal) => customer
    ? productApi.list({ page: 0, size: 10, status: 'ACTIVE', warehouseId: selectedWarehouseId }, signal)
    : Promise.resolve(null), [customer, selectedWarehouseId]);
  const loadCategories = useCallback((signal) => customer ? categoryApi.list(signal) : Promise.resolve([]), [customer]);
  const products = useProductRequest(`home-products:${customer}:${selectedWarehouseId}`, loadProducts);
  const categories = useProductRequest(`home-categories:${customer}`, loadCategories);
  const activeCategories = (categories.data || []).filter((category) => category.status === 'ACTIVE');
  const roots = activeCategories.filter((category) => !category.parentId || !activeCategories.some((parent) => parent.categoryId === category.parentId));
  const categoryCards = roots.slice(0, 8);
  const featured = products.data?.content || [];
  const brands = [...new Set(featured.map((product) => product.brandName).filter(Boolean))];

  return <div className="storefront-home">
    <h1 className="sr-only">Công nghệ cho từng nhu cầu.</h1>
    <section className="home-hero-grid" aria-label="Bộ sưu tập nổi bật">
      <Link className="home-banner home-banner-main" to="/products" aria-label="Khám phá thiết bị công nghệ">
        <img src={`${hero}Herobanner.png`} alt="Ưu đãi công nghệ và thiết bị thông minh" fetchPriority="high" width="1610" height="803" />
      </Link>
      <Link className="home-banner home-banner-side" to="/products?keyword=Laptop" aria-label="Khám phá laptop">
        <img src={`${hero}MiniHerobanner1.png`} alt="Bộ sưu tập laptop" width="1429" height="594" />
      </Link>
      <Link className="home-banner home-banner-side" to="/products" aria-label="Khám phá phụ kiện">
        <img src={`${hero}MiniHerobanner2.png`} alt="Bộ sưu tập phụ kiện điện thoại" width="1441" height="602" />
      </Link>
    </section>

    <section className="home-section" id="featured-categories">
      <div className="home-section-heading"><div><h2>Danh mục nổi bật</h2><p>Tìm thiết bị phù hợp từ các danh mục của cửa hàng.</p></div>
        {brands.length > 0 && <div className="home-brand-rail" aria-label="Thương hiệu sản phẩm">{brands.map((brand) => <span key={brand}>{brand}</span>)}</div>}
      </div>
      {categories.isLoading && customer && <div className="home-category-grid" aria-label="Đang tải danh mục" role="status">{Array.from({ length: 4 }, (_, index) => <div key={index} className="home-category-skeleton skeleton-block" />)}</div>}
      {categories.error && <p role="alert" className="auth-alert">{categories.error.message} <button type="button" onClick={categories.retry}>Thử lại</button></p>}
      {categoryCards.length > 0 && <div className="home-category-grid">{categoryCards.map((category) => {
        const children = activeCategories.filter((child) => child.parentId === category.categoryId);
        const product = featured.find((item) => item.categoryId === category.categoryId || children.some((child) => child.categoryId === item.categoryId));
        return <article className="home-category-card" key={category.categoryId}>
          <Link className="home-category-visual" to={`/products?categoryId=${category.categoryId}`} aria-label={`Xem ${category.categoryName}`}>{product?.primaryImageUrl ? <ProductImage src={product.primaryImageUrl} alt={category.categoryName} /> : <ShopIcon name="device" />}</Link>
          <div><h3><Link to={`/products?categoryId=${category.categoryId}`}>{category.categoryName}</Link></h3>
            {children.length ? <ul>{children.slice(0, 4).map((child) => <li key={child.categoryId}><Link to={`/products?categoryId=${child.categoryId}`}>{child.categoryName}</Link></li>)}</ul> : <p>Khám phá các thiết bị<br />và phiên bản phù hợp.</p>}
          </div><Link className="home-category-view" to={`/products?categoryId=${category.categoryId}`}>Xem tất cả →</Link>
        </article>;
      })}</div>}
      {!customer && <div className="home-category-guest"><p>Đăng nhập để xem danh mục và tồn kho tại chi nhánh của bạn.</p><Link className="button" to="/login">Đăng nhập</Link></div>}
      {customer && !categories.isLoading && !categories.error && !categoryCards.length && <p className="muted">Chưa có danh mục để hiển thị.</p>}
    </section>

    <section className="home-promo-grid" aria-label="Khám phá thêm">
      <Link className="home-banner" to="/products"><img src={`${hero}MiniHerobanner3.png`} alt="Khám phá thiết bị giải trí" loading="lazy" width="1060" height="490" /></Link>
      <Link className="home-banner" to="/products"><img src={`${hero}MiniHerobanner4.png`} alt="Khám phá thiết bị gia dụng" loading="lazy" width="1064" height="498" /></Link>
    </section>

    <section className="home-section" id="featured-products">
      <div className="home-section-heading home-products-heading"><div><h2>Sản phẩm nổi bật</h2><p>{selectedWarehouse ? `Tồn kho tại ${selectedWarehouse.warehouseName}` : 'Chọn chi nhánh để xem số lượng sẵn có.'}</p></div>
        <div className="home-product-tabs"><span aria-current="true">Tất cả</span><ComingSoonButton>Bán chạy</ComingSoonButton><ComingSoonButton>Mới nhất</ComingSoonButton><ComingSoonButton>Ưu đãi</ComingSoonButton></div>
      </div>
      {products.isLoading && customer && <div className="home-products-grid" role="status" aria-label="Đang tải sản phẩm">{Array.from({ length: 5 }, (_, index) => <div className="home-product-skeleton skeleton-block" key={index} />)}</div>}
      {products.error && <p role="alert" className="auth-alert">{products.error.message} <button type="button" onClick={products.retry}>Thử lại</button></p>}
      {featured.length > 0 && <><div className="home-products-grid">{featured.map((product) => <StorefrontProductCard key={product.productId} product={product} warehouseId={selectedWarehouseId} />)}</div><div className="home-view-all"><Link className="button button-quiet" to="/products">Khám phá tất cả sản phẩm <ShopIcon name="arrow" /></Link></div></>}
      {!customer && <div className="home-category-guest"><p>Đăng nhập và chọn chi nhánh để xem sản phẩm và tồn kho thực tế.</p><Link className="button" to="/login">Đăng nhập để mua sắm</Link></div>}
      {customer && !products.isLoading && !products.error && !featured.length && <div className="home-category-guest"><p>Chưa có sản phẩm để hiển thị.</p><Link to="/products">Mở cửa hàng</Link></div>}
    </section>
    <div className="home-service-strip" aria-label="Thông tin mua sắm">
      <div><ShopIcon name="pin" /><strong>Mua đúng chi nhánh</strong><small>Tồn kho tại nơi bạn chọn</small></div>
      <div><ShopIcon name="device" /><strong>Chọn đúng phiên bản</strong><small>Màu sắc, dung lượng và cấu hình</small></div>
      <div><ShopIcon name="box" /><strong>Theo dõi đơn hàng</strong><small>Xem tiến độ ngay trên tài khoản</small></div>
    </div>
    <section className="home-newsletter"><div><h2>Không bỏ lỡ thông tin công nghệ</h2><p>Đăng ký nhận tin sẽ được bổ sung trong thời gian tới.</p></div><div><label className="sr-only" htmlFor="newsletter-email">Email nhận tin (sắp có)</label><input id="newsletter-email" type="email" placeholder="Địa chỉ email của bạn" disabled /><ComingSoonButton>Đăng ký nhận tin</ComingSoonButton></div></section>
  </div>;
}

function ManagementHome() {
  return <section>
    <PageHeader eyebrow="Điện Việt / Vận hành" title="Không gian vận hành cửa hàng" description="Truy cập nhanh các nghiệp vụ hàng ngày." />
    <div className="hero panel"><div><span className="badge">Bảng điều hướng</span><h2>Mọi nghiệp vụ trong một nơi.</h2><p>Theo dõi đơn hàng, tồn kho, Serial/IMEI và bảo hành.</p><Link to="/orders" className="button">Xem đơn hàng →</Link></div><div className="hero-illustration" aria-hidden="true"><span>ĐV</span><p>PRODUCT<br />ORDER<br />INVENTORY</p></div></div>
    <div className="card-grid"><Link className="panel overview-card" to="/products"><span className="eyebrow">Sản phẩm</span><h2>Danh mục thiết bị</h2><p className="muted">Xem sản phẩm và phiên bản.</p></Link><Link className="panel overview-card" to="/inventory"><span className="eyebrow">Kho hàng</span><h2>Tồn kho theo chi nhánh</h2><p className="muted">Theo dõi số lượng khả dụng.</p></Link><Link className="panel overview-card" to="/warranties"><span className="eyebrow">Hậu mãi</span><h2>Bảo hành thiết bị</h2><p className="muted">Tra cứu Serial/IMEI.</p></Link></div>
  </section>;
}
