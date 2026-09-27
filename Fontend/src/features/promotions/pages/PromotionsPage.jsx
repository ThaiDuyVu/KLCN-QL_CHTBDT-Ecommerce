import { useCallback, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import PageHeader from '../../../components/ui/PageHeader';
import StatusBadge from '../../../components/ui/StatusBadge';
import useProductRequest from '../../products/hooks/useProductRequest';
import CommerceState from '../../orders/components/CommerceState';
import { date, money } from '../../orders/components/orderFormat';
import { useAuth } from '../../../hooks/useAuth';
import { promotionApi } from '../api/promotionApi';
import '../promotions.css';
export default function PromotionsPage() {
  const [params, setParams] = useSearchParams(); const { invalidateSession } = useAuth();
  const raw = Number(params.get('page') || 1); const page = Number.isSafeInteger(raw) && raw > 0 ? raw - 1 : 0;
  const keyword = params.get('keyword') || ''; const status = params.get('status') || '';
  const load = useCallback((signal) => promotionApi.list({ page, keyword, status }, signal), [page, keyword, status]);
  const { data, error, isLoading, retry } = useProductRequest(`promotions:${page}:${keyword}:${status}`, load);
  const [busy, setBusy] = useState(''); const [actionError, setActionError] = useState('');
  async function toggle(promotion) {
    const next = promotion.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    if (!window.confirm(`${next === 'ACTIVE' ? 'Bật' : 'Tắt'} khuyến mãi ${promotion.promotionName}?`)) return;
    setBusy(promotion.promotionId); setActionError('');
    try { await promotionApi.status(promotion.promotionId, next); retry(); }
    catch (requestError) { if (requestError.status === 401) invalidateSession(); setActionError(requestError.message); }
    finally { setBusy(''); }
  }
  function search(event) {
    event.preventDefault(); const form = new FormData(event.currentTarget); const next = new URLSearchParams();
    if (String(form.get('keyword')).trim()) next.set('keyword', String(form.get('keyword')).trim());
    if (form.get('status')) next.set('status', form.get('status')); setParams(next);
  }
  function changePage(nextPage) { const next = new URLSearchParams(params); next.set('page', String(nextPage + 1)); setParams(next); }
  return <>
    <div className="promotion-page-header"><PageHeader eyebrow="Vận hành / Khuyến mãi" title="Khuyến mãi" description="Quản lý thời gian và sản phẩm áp dụng giảm giá." />
    <Link className="button" to="/promotions/new">+ Tạo khuyến mãi</Link></div>
    <form className="panel promotion-filters" onSubmit={search} key={`${keyword}:${status}`} role="search">
      <label>Tên khuyến mãi<input name="keyword" type="search" defaultValue={keyword} placeholder="Tìm tên chương trình…" /></label>
      <label>Trạng thái<select name="status" defaultValue={status}><option value="">Tất cả</option><option value="ACTIVE">Bật</option><option value="INACTIVE">Tắt</option></select></label><button className="button">Tìm kiếm</button>{(keyword || status) && <button type="button" className="button button-quiet" onClick={() => setParams({})}>Xóa bộ lọc</button>}
    </form>
    {actionError && <p className="auth-alert" role="alert">{actionError}</p>}
    <CommerceState loading={isLoading} error={error} retry={retry} />
    {!isLoading && !error && <>
      {!data?.content?.length ? <section className="panel commerce-empty-state"><h2>Không có khuyến mãi phù hợp.</h2><p>Tạo chương trình mới hoặc đổi bộ lọc.</p></section> : <div className="panel promotion-table-wrap"><div className="promotion-table-heading"><h2>Danh sách chương trình</h2><span>{data.totalElements} khuyến mãi</span></div><table className="promotion-table"><thead><tr><th>Chương trình</th><th>Mức giảm</th><th>Thời gian</th><th>Sản phẩm</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody>{data.content.map((promotion) => <tr key={promotion.promotionId}><td><Link to={`/promotions/${promotion.promotionId}`}>{promotion.promotionName}</Link><small className="promotion-table-description">{promotion.description || 'Giảm giá theo sản phẩm'}</small></td><td><span className="promotion-discount-pill">{promotion.discountType === 'PERCENTAGE' ? `${promotion.discountValue}%` : money(promotion.discountValue)}</span><small className="promotion-discount-type">{promotion.discountType === 'PERCENTAGE' ? 'Theo phần trăm' : 'Số tiền cố định'}</small></td><td className="promotion-period"><span><small>Từ</small>{date(promotion.startDate)}</span><span><small>Đến</small>{date(promotion.endDate)}</span></td><td><span className="promotion-product-count">{promotion.products.length} sản phẩm</span></td><td><StatusBadge status={promotion.status}>{promotion.status === 'ACTIVE' ? 'Bật' : 'Tắt'}</StatusBadge></td><td><div className="promotion-row-actions"><Link className="promotion-action-link" to={`/promotions/${promotion.promotionId}`}>Chi tiết</Link><Link className="promotion-action-link" to={`/promotions/${promotion.promotionId}/edit`}>Sửa</Link><button className="button button-quiet" disabled={Boolean(busy)} onClick={() => toggle(promotion)}>{busy === promotion.promotionId ? 'Đang lưu…' : promotion.status === 'ACTIVE' ? 'Tắt' : 'Bật'}</button></div></td></tr>)}</tbody></table></div>}
      <div className="promotion-pagination"><button className="button button-quiet" disabled={page === 0} onClick={() => changePage(page - 1)}>Trước</button><span>Trang {page + 1} / {Math.max(1, data?.totalPages || 0)} · {data?.totalElements || 0} khuyến mãi</span><button className="button button-quiet" disabled={page + 1 >= (data?.totalPages || 0)} onClick={() => changePage(page + 1)}>Sau</button></div>
    </>}
  </>;
}
