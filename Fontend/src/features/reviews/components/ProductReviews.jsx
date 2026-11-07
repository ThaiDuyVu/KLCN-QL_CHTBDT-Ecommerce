import { useCallback, useState } from 'react';
import useProductRequest from '../../products/hooks/useProductRequest';
import { date } from '../../orders/components/orderFormat';
import { reviewApi } from '../api/reviewApi';
import Stars from './Stars';
import '../reviews.css';

/** Visible reviews of a product with the average rating. Customers write reviews from their delivered orders. */
export default function ProductReviews({ productId }) {
  const [page, setPage] = useState(0);
  const load = useCallback((signal) => reviewApi.forProduct(productId, page, signal), [productId, page]);
  const { data, error, isLoading } = useProductRequest(`reviews:${productId}:${page}`, load);
  return <section className="panel product-section product-section-spaced" aria-label="Đánh giá sản phẩm">
    <h2>Đánh giá sản phẩm</h2>
    {isLoading && <p role="status">Đang tải đánh giá…</p>}
    {error && <p className="muted">Chưa tải được đánh giá: {error.message}</p>}
    {data && (data.totalReviews ? <>
      <p className="review-summary"><strong>{data.averageRating}</strong>/5 <Stars value={data.averageRating} /> · {data.totalReviews} đánh giá</p>
      {data.content.map((review) => <article className="review-item" key={review.reviewId}>
        <div><strong>{review.customerName}</strong> <Stars value={review.rating} /> <span className="muted">{date(review.createdAt)}</span></div>
        {review.reviewText && <p>{review.reviewText}</p>}
        {review.replyText && <p className="review-reply"><strong>Cửa hàng phản hồi:</strong> {review.replyText}</p>}
      </article>)}
      {data.totalPages > 1 && <nav className="commerce-inline" aria-label="Phân trang đánh giá"><button className="button button-quiet" disabled={page === 0} onClick={() => setPage(page - 1)}>Trước</button><span>{page + 1}/{data.totalPages}</span><button className="button button-quiet" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Sau</button></nav>}
    </> : <p className="muted">Chưa có đánh giá. Khách hàng đã nhận hàng có thể đánh giá từ trang chi tiết đơn hàng.</p>)}
  </section>;
}
