import { useCallback } from 'react';
import { Link } from 'react-router';
import { useWarehouse } from '../../../hooks/useWarehouse';
import { productApi } from '../api/productApi';
import useProductRequest from '../hooks/useProductRequest';
import StorefrontProductCard from './StorefrontProductCard';
import ShopIcon from '../../../components/ui/ShopIcon';

export default function SaleProductsSection() {
  const { selectedWarehouse, selectedWarehouseId } = useWarehouse();
  const load = useCallback((signal) => productApi.list({ page: 0, size: 5, status: 'ACTIVE', onSale: true, warehouseId: selectedWarehouseId }, signal), [selectedWarehouseId]);
  const { data, isLoading, error, retry } = useProductRequest(`home-sale:${selectedWarehouseId}`, load);
  const products = (data?.content || []).filter((product) => Number(product.discountAmount || 0) > 0);
  return <section className="home-section home-sale-section" id="sale-products" aria-labelledby="sale-products-heading">
    <div className="sale-campaign"><div className="sale-campaign-copy"><span className="sale-campaign-kicker"><ShopIcon name="tag" /> TECH DEALS</span><h2>Nâng cấp công nghệ.<br /><em>Giá tốt hơn mỗi ngày.</em></h2><p>Khám phá thiết bị yêu thích với giá ưu đãi đang áp dụng.</p><Link to="/products?onSale=true">Khám phá ưu đãi <ShopIcon name="arrow" /></Link></div><div className="sale-campaign-art" aria-hidden="true"><span className="sale-campaign-orbit" /><span className="sale-campaign-symbol">%</span><span className="sale-campaign-label">CÔNG NGHỆ MỚI<br /><strong>GIÁ HẤP DẪN</strong></span></div></div>
    <div className="home-section-heading"><div><span className="home-sale-eyebrow"><ShopIcon name="tag" />ƯU ĐÃI ĐANG DIỄN RA</span><h2 id="sale-products-heading">Sản phẩm đang giảm giá</h2><p>{selectedWarehouse ? `Giá ưu đãi · Tồn kho tại ${selectedWarehouse.warehouseName}` : 'Khám phá ưu đãi và chọn chi nhánh để kiểm tra tồn kho.'}</p></div><Link className="home-sale-view-all" to="/products?onSale=true">Xem tất cả ưu đãi <ShopIcon name="arrow" /></Link></div>
    {isLoading ? <div className="home-products-grid" role="status" aria-label="Đang tải sản phẩm giảm giá">{Array.from({ length: 5 }, (_, index) => <div className="home-product-skeleton skeleton-block" key={index} />)}</div> : error ? <p className="auth-alert" role="alert">{error.message} <button type="button" onClick={retry}>Thử lại</button></p> : products.length ? <div className="home-products-grid">{products.map((product) => <StorefrontProductCard key={product.productId} product={product} warehouseId={selectedWarehouseId} listSearch="?onSale=true" />)}</div> : <div className="home-sale-empty"><ShopIcon name="tag" /><div><strong>Hiện chưa có sản phẩm đang giảm giá</strong><p>Các ưu đãi mới sẽ được cập nhật tại đây khi chương trình bắt đầu.</p></div><Link to="/products">Khám phá sản phẩm →</Link></div>}
  </section>;
}
