import { useCallback, useEffect } from 'react';
import { Link, useLocation, useSearchParams } from 'react-router';
import PageHeader from '../../../components/ui/PageHeader';
import { productApi } from '../api/productApi';
import useProductRequest from '../hooks/useProductRequest';
import ProductSkeleton from '../components/ProductSkeleton';
import ProductState from '../components/ProductState';
import '../products.css';

const PAGE_SIZE = 12;

export default function ProductsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const location = useLocation();
  const rawPage = Number(searchParams.get('page') || 1);
  const page = Number.isSafeInteger(rawPage) && rawPage > 0 && (rawPage - 1) * PAGE_SIZE <= 2147483647 ? rawPage - 1 : 0;
  const keyword = (searchParams.get('keyword') || '').trim();
  const rawStatus = searchParams.get('status') || '';
  const status = ['ACTIVE', 'INACTIVE'].includes(rawStatus) ? rawStatus : '';
  const load = useCallback((signal) => productApi.list({ page, size: PAGE_SIZE, keyword, status }, signal), [page, keyword, status]);
  const { data, error, isLoading, retry } = useProductRequest(JSON.stringify([page, keyword, status]), load);
  useEffect(() => { window.scrollTo(0, 0); }, [page, keyword, status]);

  function search(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const next = new URLSearchParams();
    const value = form.get('keyword').trim();
    if (value) next.set('keyword', value);
    if (form.get('status')) next.set('status', form.get('status'));
    setSearchParams(next);
  }

  function changePage(nextPage) {
    const next = new URLSearchParams(searchParams);
    next.set('page', String(nextPage + 1));
    setSearchParams(next);
  }

  return (
    <>
      <PageHeader title="Sản phẩm" description="Xem danh sách thiết bị và thông tin chi tiết." />
      <form className="panel product-filters" onSubmit={search} key={`${keyword}:${status}`} role="search" aria-label="Tìm sản phẩm">
        <label>Tên hoặc mô tả<input type="search" name="keyword" defaultValue={keyword} placeholder="Tìm sản phẩm…" /></label>
        <label>Trạng thái<select name="status" defaultValue={status}>
          <option value="">Tất cả</option><option value="ACTIVE">Đang hoạt động</option><option value="INACTIVE">Ngừng hoạt động</option>
        </select></label>
        <button className="button" type="submit">Tìm kiếm</button>
        {(keyword || status) && <button className="button button-quiet" type="button" onClick={() => setSearchParams({})}>Xóa bộ lọc</button>}
      </form>
      {isLoading ? <ProductSkeleton /> : error ? (
        <ProductState error title={error.status === 403 ? 'Không có quyền xem sản phẩm' : 'Chưa tải được danh sách'}
          message={error.status === 403 ? 'Tài khoản của bạn không có quyền truy cập dữ liệu này.' : error.message}
          retry={error.status === 403 ? undefined : retry} />
      ) : data?.content?.length ? (
        <>
          <p className="muted product-count" role="status">{data.totalElements} sản phẩm · Trang {data.page + 1}/{data.totalPages}</p>
          <div className="product-grid">
            {data.content.map((product) => (
              <article className="panel product-card" key={product.productId}>
                <span className="badge">{product.status === 'ACTIVE' ? 'Đang hoạt động' : 'Ngừng hoạt động'}</span>
                <h2>{product.productName}</h2>
                <dl className="product-card-meta">
                  <div><dt>Danh mục</dt><dd>{product.categoryName || '—'}</dd></div>
                  <div><dt>Thương hiệu</dt><dd>{product.brandName || '—'}</dd></div>
                </dl>
                <p className="muted product-summary">{product.description || 'Chưa có mô tả.'}</p>
                <Link className="button button-quiet product-detail-link" to={`/products/${product.productId}`}
                  state={{ listSearch: location.search }} aria-label={`Xem chi tiết ${product.productName}`}>Xem chi tiết</Link>
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
          message={keyword || status ? 'Không tìm thấy sản phẩm phù hợp. Hãy thử thay đổi từ khóa hoặc trạng thái.' : 'Danh sách hiện chưa có dữ liệu.'} />
      )}
      {!isLoading && !error && !data?.content?.length && page > 0 && (
        <button className="button button-quiet" onClick={() => changePage(0)}>Về trang đầu</button>
      )}
    </>
  );
}
