import { useCallback, useState } from 'react';
import { productApi } from '../../products/api/productApi';
import useProductRequest from '../../products/hooks/useProductRequest';
import ProductImage from '../../products/components/ProductImage';
import CommerceState from '../../orders/components/CommerceState';
export default function ProductPicker({ value, onChange, disabled }) {
  const [keyword, setKeyword] = useState(''); const [page, setPage] = useState(0);
  const load = useCallback((signal) => productApi.list({ page, size: 12, keyword }, signal), [page, keyword]);
  const { data, error, isLoading, retry } = useProductRequest(`promotion-products:${page}:${keyword}`, load);
  function toggle(product) {
    onChange(value.some((item) => item.productId === product.productId)
      ? value.filter((item) => item.productId !== product.productId)
      : [...value, { productId: product.productId, productName: product.productName }]);
  }
  return <section className="panel promotion-picker" aria-label="Chọn sản phẩm áp dụng">
    <div className="promotion-section-heading"><span className="promotion-step" aria-hidden="true">02</span><div><h2>Sản phẩm áp dụng</h2><p>Mọi biến thể của sản phẩm được chọn đều hưởng ưu đãi.</p></div><span className="promotion-selection-count">{value.length} đã chọn</span></div>
    <label>Tìm sản phẩm<input type="search" placeholder="Tên sản phẩm…" disabled={disabled} onChange={(event) => { setKeyword(event.target.value); setPage(0); }} /></label>
    {value.length > 0 && <div className="promotion-selected">{value.map((item) => <button key={item.productId} type="button" disabled={disabled} onClick={() => toggle(item)} aria-label={`Bỏ ${item.productName}`}>{item.productName} ×</button>)}</div>}
    <CommerceState loading={isLoading} error={error} retry={retry} />
    {!isLoading && !error && <>
      {!data?.content?.length && <p>Không tìm thấy sản phẩm.</p>}
      <div className="promotion-product-options">{data?.content?.map((product) => <label key={product.productId}><input type="checkbox" disabled={disabled} checked={value.some((item) => item.productId === product.productId)} onChange={() => toggle(product)} /><span className="promotion-picker-image"><ProductImage src={product.primaryImageUrl} alt={product.productName} /></span><span className="promotion-picker-name">{product.productName}<small>{product.brandName} · {product.categoryName}</small></span></label>)}</div>
      <div className="promotion-pagination"><button type="button" className="button button-quiet" disabled={disabled || page === 0} onClick={() => setPage((value) => value - 1)}>Trước</button><span>Trang {page + 1} / {Math.max(1, data?.totalPages || 0)}</span><button type="button" className="button button-quiet" disabled={disabled || page + 1 >= (data?.totalPages || 0)} onClick={() => setPage((value) => value + 1)}>Sau</button></div>
    </>}
  </section>;
}
