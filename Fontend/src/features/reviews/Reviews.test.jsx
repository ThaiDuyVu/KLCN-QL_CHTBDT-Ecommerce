import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router';
import { AuthContext } from '../../auth/AuthContext';
import { reviewApi } from './api/reviewApi';
import OrderReviews from './components/OrderReviews';
import ProductReviews from './components/ProductReviews';

vi.mock('./api/reviewApi', () => ({ reviewApi: { forProduct: vi.fn(), create: vi.fn(), mineForOrder: vi.fn(), list: vi.fn(), moderate: vi.fn() } }));
const mount = (child) => render(<AuthContext.Provider value={{ user: { roleName: 'CUSTOMER' }, invalidateSession: vi.fn() }}><MemoryRouter>{child}</MemoryRouter></AuthContext.Provider>);
beforeEach(() => vi.resetAllMocks());
afterEach(() => cleanup());

it('product page shows the average rating, reviews and store replies', async () => {
  reviewApi.forProduct.mockResolvedValue({ averageRating: 4.5, totalReviews: 2, totalPages: 1, content: [
    { reviewId: 'r1', customerName: 'Nguyễn Văn An', rating: 5, reviewText: 'Rất tốt', createdAt: '2026-11-06T03:00:00Z' },
    { reviewId: 'r2', customerName: 'Trần Thị Bình', rating: 4, reviewText: 'Ổn', replyText: 'Cảm ơn chị', createdAt: '2026-11-05T03:00:00Z' },
  ] });
  mount(<ProductReviews productId="p1" />);
  expect(await screen.findByText('4.5')).toBeInTheDocument();
  expect(screen.getByText(/2 đánh giá/)).toBeInTheDocument();
  expect(screen.getByText('Cảm ơn chị')).toBeInTheDocument();
});

it('customer rates each delivered line once', async () => {
  const order = { orderId: 'o1', items: [{ orderItemId: 'a', productName: 'Tai nghe' }, { orderItemId: 'b', productName: 'Sạc nhanh' }] };
  reviewApi.mineForOrder.mockResolvedValueOnce([{ orderItemId: 'a', rating: 5 }]).mockResolvedValue([{ orderItemId: 'a', rating: 5 }, { orderItemId: 'b', rating: 3 }]);
  reviewApi.create.mockResolvedValue({});
  mount(<OrderReviews order={order} />);
  fireEvent.click(await screen.findByRole('button', { name: 'Đánh giá' }));
  fireEvent.change(screen.getByLabelText('Số sao'), { target: { value: '3' } });
  fireEvent.change(screen.getByLabelText('Nhận xét'), { target: { value: ' Sạc hơi nóng ' } });
  fireEvent.click(screen.getByRole('button', { name: 'Gửi đánh giá' }));
  await waitFor(() => expect(reviewApi.create).toHaveBeenCalledWith({ orderItemId: 'b', rating: 3, reviewText: 'Sạc hơi nóng' }));
  await waitFor(() => expect(screen.getAllByText('Đã đánh giá')).toHaveLength(2));
  expect(screen.queryByRole('button', { name: 'Đánh giá' })).not.toBeInTheDocument();
});
