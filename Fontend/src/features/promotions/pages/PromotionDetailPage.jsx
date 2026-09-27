import { useCallback, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router';
import PageHeader from '../../../components/ui/PageHeader';
import StatusBadge from '../../../components/ui/StatusBadge';
import useProductRequest from '../../products/hooks/useProductRequest';
import CommerceState from '../../orders/components/CommerceState';
import { date, money } from '../../orders/components/orderFormat';
import { promotionApi } from '../api/promotionApi';
import { useAuth } from '../../../hooks/useAuth';
import '../promotions.css';
export default function PromotionDetailPage() {
  const { promotionId } = useParams(); const navigate = useNavigate(); const location = useLocation(); const { invalidateSession } = useAuth();
  const load = useCallback((signal) => promotionApi.detail(promotionId, signal), [promotionId]);
  const { data, error, isLoading, retry } = useProductRequest(`promotion:${promotionId}`, load);
  const [busy, setBusy] = useState(false); const [actionError, setActionError] = useState('');
  async function change(remove) {
    if (!window.confirm(remove ? `Xóa khuyến mãi ${data.promotionName}? Giá đã lưu trong đơn cũ không thay đổi.` : `${data.status === 'ACTIVE' ? 'Tắt' : 'Bật'} khuyến mãi ${data.promotionName}?`)) return;
    setBusy(true); setActionError('');
    try {
      if (remove) { await promotionApi.remove(promotionId); navigate('/promotions'); }
      else { await promotionApi.status(promotionId, data.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'); retry(); }
    } catch (requestError) { if (requestError.status === 401) invalidateSession(); setActionError(requestError.message); }
    finally { setBusy(false); }
  }
  return <>
    <PageHeader title="Chi tiết khuyến mãi" description="Chương trình chỉ áp dụng khi bật và trong khoảng thời gian đã khai báo." />
    <Link className="commerce-back-link" to="/promotions">← Danh sách khuyến mãi</Link>
    {location.state?.saved && <p className="commerce-notice" role="status">Đã lưu khuyến mãi.</p>}
    {actionError && <p className="auth-alert" role="alert">{actionError}</p>}
    <CommerceState loading={isLoading} error={error} retry={retry} />
    {!isLoading && !error && data && <section className="panel promotion-detail"><div className="promotion-toolbar"><h2>{data.promotionName}</h2><StatusBadge status={data.status}>{data.status === 'ACTIVE' ? 'Bật' : 'Tắt'}</StatusBadge></div>
      <p>{data.description || 'Không có mô tả.'}</p>
      <dl className="promotion-detail-fields"><div><dt>Mức giảm</dt><dd>{data.discountType === 'PERCENTAGE' ? `${data.discountValue}%` : `${money(data.discountValue)} / sản phẩm`}</dd></div><div><dt>Bắt đầu</dt><dd>{date(data.startDate)}</dd></div><div><dt>Kết thúc</dt><dd>{date(data.endDate)}</dd></div></dl>
      <h3>Sản phẩm áp dụng · {data.products.length}</h3>
      {data.products.length ? <ul>{data.products.map((product) => <li key={product.productId}><Link to={`/products/${product.productId}`}>{product.productName}</Link></li>)}</ul> : <p>Chưa chọn sản phẩm áp dụng.</p>}
      <div className="promotion-row-actions"><Link className="button" to={`/promotions/${promotionId}/edit`}>Sửa</Link><button className="button button-quiet" disabled={busy} onClick={() => change(false)}>{busy ? 'Đang xử lý…' : data.status === 'ACTIVE' ? 'Tắt khuyến mãi' : 'Bật khuyến mãi'}</button><button className="button button-quiet" disabled={busy} onClick={() => change(true)}>Xóa</button></div>
    </section>}
  </>;
}
