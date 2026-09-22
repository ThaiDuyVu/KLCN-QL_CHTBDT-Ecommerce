import { Link } from 'react-router';
import PageHeader from '../components/ui/PageHeader';

export default function NotFoundPage() {
  return <section className="narrow-page"><PageHeader eyebrow="404" title="Không tìm thấy trang" description="Đường dẫn không tồn tại trong frontend hiện tại." /><Link className="button" to="/">Về trang chủ</Link></section>;
}
