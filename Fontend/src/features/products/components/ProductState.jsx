import { Link } from 'react-router';

export default function ProductState({ title, message, error, retry, backTo }) {
  return (
    <section className="panel product-state" role={error ? 'alert' : undefined}>
      <h2>{title}</h2>
      <p className="muted">{message}</p>
      <div className="product-actions">
        {retry && <button className="button" onClick={retry}>Thử lại</button>}
        {backTo && <Link className="button button-quiet" to={backTo}>Về danh sách sản phẩm</Link>}
      </div>
    </section>
  );
}
