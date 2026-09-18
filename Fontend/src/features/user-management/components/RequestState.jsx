export default function RequestState({ loading, error, retry }) {
  if (loading) return <div className="panel management-state" role="status" aria-busy="true">Đang tải dữ liệu…</div>;
  if (!error) return null;
  return <div className="panel management-state" role="alert">
    <p>{error.status === 403 ? 'Bạn không có quyền thực hiện thao tác này.' : error.message}</p>
    {retry && <button className="button button-quiet" onClick={retry}>Thử lại</button>}
  </div>;
}
