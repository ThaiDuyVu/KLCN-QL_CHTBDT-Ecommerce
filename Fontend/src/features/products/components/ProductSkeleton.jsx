export default function ProductSkeleton({ detail = false }) {
  return (
    <div role="status" aria-live="polite" aria-busy="true">
      <span className="sr-only">Đang tải {detail ? 'chi tiết sản phẩm' : 'danh sách sản phẩm'}…</span>
      <div className={detail ? 'product-detail-grid' : 'product-grid'} aria-hidden="true">
        {Array.from({ length: detail ? 2 : 6 }, (_, index) => (
          <div className="panel product-card product-skeleton" key={index}>
            <div className="skeleton-block skeleton-title" />
            <div className="skeleton-block" /><div className="skeleton-block" />
            <div className="skeleton-block skeleton-short" />
          </div>
        ))}
      </div>
    </div>
  );
}
