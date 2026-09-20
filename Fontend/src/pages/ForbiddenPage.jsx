import { Link } from 'react-router';
import PageHeader from '../components/ui/PageHeader';

export default function ForbiddenPage() {
  return <section className="narrow-page"><PageHeader eyebrow="403" title="Không có quyền truy cập" description="Vai trò hiện tại không được phép mở khu vực này." /><Link className="button" to="/">Về trang chủ</Link></section>;
}
