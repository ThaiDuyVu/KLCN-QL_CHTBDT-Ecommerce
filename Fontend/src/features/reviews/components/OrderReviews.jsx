import { useCallback, useState } from 'react';
import useProductRequest from '../../products/hooks/useProductRequest';
import { reviewApi } from '../api/reviewApi';
import Stars from './Stars';
import '../reviews.css';

/** Customer rates each product line of a delivered order once. */
export default function OrderReviews({ order }) {
  const load = useCallback((signal) => reviewApi.mineForOrder(order.orderId, signal), [order.orderId]);
  const reviews = useProductRequest(`order-reviews:${order.orderId}`, load);
  const [writing, setWriting] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const byLine = Object.fromEntries((reviews.data || []).map((review) => [review.orderItemId, review]));
  async function submit(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setBusy(true); setError('');
    try {
      await reviewApi.create({ orderItemId: writing, rating: Number(form.get('rating')), reviewText: String(form.get('reviewText') || '').trim() || null });
      setWriting(null); reviews.retry();
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  return <section className="panel commerce-section" aria-label="Đánh giá sản phẩm đã mua"><h3>Đánh giá sản phẩm</h3>
    {order.items.map((item) => <div className="review-line" key={item.orderItemId}>
      <span>{item.productName}{item.serialNumber ? ` · ${item.serialNumber}` : ''}</span>
      {byLine[item.orderItemId] ? <span><Stars value={byLine[item.orderItemId].rating} /> Đã đánh giá</span>
        : writing !== item.orderItemId && <button className="button button-quiet" disabled={!reviews.data} onClick={() => { setWriting(item.orderItemId); setError(''); }}>Đánh giá</button>}
      {writing === item.orderItemId && <form className="review-form" onSubmit={submit} aria-label={`Đánh giá ${item.productName}`}>
        {error && <p role="alert" className="auth-alert">{error}</p>}
        <label>Số sao<select name="rating" defaultValue="5" disabled={busy}>{[5, 4, 3, 2, 1].map((n) => <option key={n} value={n}>{n} sao</option>)}</select></label>
        <label>Nhận xét<textarea name="reviewText" maxLength={2000} disabled={busy} /></label>
        <div className="order-detail-actions"><button className="button" disabled={busy}>{busy ? 'Đang gửi…' : 'Gửi đánh giá'}</button><button type="button" className="button button-quiet" disabled={busy} onClick={() => setWriting(null)}>Hủy</button></div>
      </form>}
    </div>)}
  </section>;
}
