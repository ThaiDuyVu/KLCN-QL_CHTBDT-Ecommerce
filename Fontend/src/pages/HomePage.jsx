import { Link } from 'react-router';
import PageHeader from '../components/ui/PageHeader';

export default function HomePage() {
  return (
    <section>
      <PageHeader eyebrow="Điện Việt / Frontend starter" title="Thiết bị điện cho mọi công trình."
        description="Không gian giao diện cho khách hàng và đội ngũ quản lý cửa hàng. Các nghiệp vụ sẽ được phát triển theo từng domain." />
      <div className="hero panel">
        <div>
          <span className="badge">Nền tảng đang được xây dựng</span>
          <h2>Từ lựa chọn thiết bị<br />đến vận hành cửa hàng.</h2>
          <p>Khởi đầu với cấu trúc rõ ràng cho sản phẩm, danh mục, đơn hàng và kho hàng.</p>
          <Link to="/products" className="button">Khám phá khu vực sản phẩm →</Link>
        </div>
        <div className="hero-illustration" aria-hidden="true"><span>01</span><p>PRODUCT<br />ORDER<br />INVENTORY</p></div>
      </div>
      <div className="card-grid">
        <Link className="panel overview-card" to="/products"><span className="eyebrow">01 / Catalogue</span><h2>Sản phẩm</h2><p className="muted">Không gian giới thiệu và quản lý thiết bị điện.</p></Link>
        <Link className="panel overview-card" to="/categories"><span className="eyebrow">02 / Organisation</span><h2>Danh mục</h2><p className="muted">Tổ chức các nhóm sản phẩm của cửa hàng.</p></Link>
        <Link className="panel overview-card" to="/login"><span className="eyebrow">03 / Management</span><h2>Quản lý cửa hàng</h2><p className="muted">Đăng nhập tài khoản để truy cập khu vực quản lý.</p></Link>
      </div>
    </section>
  );
}
