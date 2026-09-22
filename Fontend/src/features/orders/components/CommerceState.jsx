export default function CommerceState({ loading, error, retry }) {
  if (loading) return <p role="status" aria-busy="true">Đang tải…</p>;
  if (error) return <section className="panel"><p role="alert">{error.message}</p><button className="button" onClick={retry}>Thử lại</button></section>;
  return null;
}
