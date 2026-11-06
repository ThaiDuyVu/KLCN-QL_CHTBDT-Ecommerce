import { useCallback, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import PageHeader from '../../../components/ui/PageHeader';
import useProductRequest from '../../products/hooks/useProductRequest';
import CommerceState from '../../orders/components/CommerceState';
import { date } from '../../orders/components/orderFormat';
import { reviewApi } from '../api/reviewApi';
import Stars from '../components/Stars';
import '../../orders/commerce.css';
import '../reviews.css';

const statusLabels = { VISIBLE: 'Đang hiển thị', HIDDEN: 'Đã ẩn' };

/** Staff answer customer reviews and hide inappropriate ones. */
export default function ReviewsPage() {
  const [params, setParams] = useSearchParams(); const n = Number(params.get('page') || 1);
  const page = Number.isSafeInteger(n) && n > 0 && (n - 1) * 20 <= 2147483647 ? n - 1 : 0;
  const status = Object.hasOwn(statusLabels, params.get('status')) ? params.get('status') : '';
  const load = useCallback((signal) => reviewApi.list(page, status, signal), [page, status]);
  const { data, error, isLoading, retry } = useProductRequest(`reviews:${page}:${status}`, load);
  const [replying, setReplying] = useState(null);
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState('');
  async function moderate(id, body) {
    setBusy(true); setActionError('');
    try { await reviewApi.moderate(id, body); setReplying(null); retry(); }
    catch (e) { setActionError(e.message); } finally { setBusy(false); }
  }
  return <><PageHeader eyebrow="Vận hành / Bán hàng" title="Đánh giá sản phẩm" description="Phản hồi đánh giá của khách và ẩn nội dung không phù hợp." />
    <form className="panel review-filters" aria-label="Lọc đánh giá" onSubmit={(e) => { e.preventDefault(); const value = new FormData(e.currentTarget).get('status'); setParams(value ? { status: value } : {}); }}>
      <label>Trạng thái<select name="status" defaultValue={status}><option value="">Tất cả</option>{Object.entries(statusLabels).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label>
      <button className="button">Lọc</button></form>
    {actionError && <p role="alert" className="auth-alert">{actionError}</p>}
    <CommerceState loading={isLoading} error={error} retry={retry} />
    {!isLoading && !error && (data?.content?.length ? <section className="panel commerce-section">
      {data.content.map((review) => <article className="review-item" key={review.reviewId}>
        <div><Link to={`/products/${review.productId}`}><strong>{review.productName}</strong></Link> · {review.customerName} · <Stars value={review.rating} /> · <span className="muted">{date(review.createdAt)} · {statusLabels[review.status]}</span></div>
        {review.reviewText && <p>{review.reviewText}</p>}
        {review.replyText && <p className="review-reply"><strong>Phản hồi{review.repliedBy ? ` (${review.repliedBy})` : ''}:</strong> {review.replyText}</p>}
        {replying === review.reviewId ? <form className="review-moderation" onSubmit={(e) => { e.preventDefault(); moderate(review.reviewId, { replyText: String(new FormData(e.currentTarget).get('replyText') || '').trim() }); }}>
          <label>Phản hồi khách hàng<textarea name="replyText" required maxLength={2000} defaultValue={review.replyText || ''} disabled={busy} /></label>
          <div className="order-detail-actions"><button className="button" disabled={busy}>Gửi phản hồi</button><button type="button" className="button button-quiet" disabled={busy} onClick={() => setReplying(null)}>Hủy</button></div>
        </form> : <div className="order-detail-actions">
          <button className="button button-quiet" disabled={busy} onClick={() => setReplying(review.reviewId)}>{review.replyText ? 'Sửa phản hồi' : 'Phản hồi'}</button>
          <button className="button button-quiet" disabled={busy} onClick={() => moderate(review.reviewId, { status: review.status === 'VISIBLE' ? 'HIDDEN' : 'VISIBLE' })}>{review.status === 'VISIBLE' ? 'Ẩn đánh giá' : 'Hiện lại'}</button>
        </div>}
      </article>)}
      <nav className="commerce-inline" aria-label="Phân trang"><button className="button button-quiet" disabled={page === 0} onClick={() => setParams({ ...(status ? { status } : {}), page })}>Trang trước</button><span>{page + 1}/{data.totalPages}</span><button className="button button-quiet" disabled={page + 1 >= data.totalPages} onClick={() => setParams({ ...(status ? { status } : {}), page: page + 2 })}>Trang sau</button></nav>
    </section> : <section className="panel commerce-section"><p>Chưa có đánh giá phù hợp.</p></section>)}
  </>;
}
