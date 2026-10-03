export default function CustomerPagination({ data, onChange }) {
  if (!data) return null;
  return <nav className="customer-pagination" aria-label="Phân trang">
    <span>{data.totalElements} kết quả · Trang {data.page + 1}/{Math.max(data.totalPages, 1)}</span>
    <div><button className="button button-quiet" disabled={data.page <= 0} onClick={() => onChange(data.page - 1)}>Trang trước</button><button className="button button-quiet" disabled={data.page + 1 >= data.totalPages} onClick={() => onChange(data.page + 1)}>Trang sau</button></div>
  </nav>;
}
