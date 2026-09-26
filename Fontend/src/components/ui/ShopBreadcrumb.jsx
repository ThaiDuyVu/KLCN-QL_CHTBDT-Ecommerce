import { Link } from 'react-router';

export default function ShopBreadcrumb({ items }) {
  return <nav className="shop-breadcrumb" aria-label="Đường dẫn trang"><Link to="/">Trang chủ</Link>{items.map((item, index) => <span key={`${item.label}:${index}`}><span aria-hidden="true">/</span>{item.to ? <Link to={item.to}>{item.label}</Link> : <span aria-current="page">{item.label}</span>}</span>)}</nav>;
}
