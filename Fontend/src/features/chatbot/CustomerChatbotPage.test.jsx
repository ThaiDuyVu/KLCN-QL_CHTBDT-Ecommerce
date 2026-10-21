import { StrictMode } from 'react';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthContext } from '../../auth/AuthContext';
import CustomerChatbotPage from './CustomerChatbotPage';
import { chatbotApi } from './chatbotApi';

vi.mock('./chatbotApi', () => ({ chatbotApi: { sessions: vi.fn(), session: vi.fn(), history: vi.fn(), send: vi.fn(), close: vi.fn(), status: vi.fn(), traces: vi.fn(), trace: vi.fn(), rate: vi.fn() } }));

const S1 = '11111111-1111-4111-8111-111111111111';
const S2 = '22222222-2222-4222-8222-222222222222';
const customer = { userId: 'customer-a', displayName: 'Khách A', roleName: 'CUSTOMER' };
const session = (id, overrides = {}) => ({ id, customer_id: 'c', created_at: '2026-10-21T03:15:00Z', is_active: true, ended_at: null, ...overrides });
const row = (id, sender_type, message) => ({ id, session_id: S1, sender_type, message, rating: 0, created_at: '2026-10-21T03:16:00Z' });
const deferred = () => { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b; }); return { promise, resolve, reject }; };
const product = {
  product_id: 'p1', variant_id: 'v1', product_name: 'Laptop Dell Inspiron 15', sku: 'DEV-DELL-I15-512', original_price: '17990000',
  effective_price: '15990000', ram: '16GB', storage: '512GB', color: 'Bạc', available_quantity: 3, warranty_months: 24,
  image_url: '/images/dell.jpg', reasons: ['Danh mục: Laptop'],
};

function show(user = customer) {
  return render(<MemoryRouter initialEntries={['/chatbot']}><AuthContext.Provider value={{ user }}><CustomerChatbotPage /></AuthContext.Provider></MemoryRouter>);
}
const input = () => screen.getByLabelText('Câu hỏi cho Trợ lý AI');
const sendButton = () => screen.getByRole('button', { name: /^(Gửi|Đang trả lời…)$/ });
const log = () => screen.getByRole('log');

beforeEach(() => {
  sessionStorage.clear();
  chatbotApi.sessions.mockResolvedValue([]);
  chatbotApi.session.mockRejectedValue({ status: 404 });
  chatbotApi.history.mockRejectedValue({ status: 404 });
  chatbotApi.close.mockImplementation((id) => Promise.resolve(session(id, { is_active: false, ended_at: '2026-10-21T04:00:00Z' })));
});
afterEach(() => vi.useRealTimers());

describe('Customer chatbot conversations', () => {
  it('starts with a friendly empty state; starter prompts fill the input without sending', async () => {
    const user = userEvent.setup(); show();
    expect(screen.getByRole('heading', { level: 1, name: 'Trợ lý mua sắm Điện Việt' })).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Bạn cần tư vấn gì hôm nay?' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Tìm laptop dưới 20 triệu.' }));
    expect(input()).toHaveValue('Tìm laptop dưới 20 triệu.');
    expect(chatbotApi.send).not.toHaveBeenCalled();
    expect(screen.queryByText(/trace|QueryPlan|Kịch bản kiểm thử/i)).not.toBeInTheDocument();
    expect(chatbotApi.status).not.toHaveBeenCalled();
  });

  it('lists multiple sessions, opens an old one and shows only its history', async () => {
    const user = userEvent.setup();
    chatbotApi.sessions.mockResolvedValue([session(S1), session(S2, { created_at: '2026-10-20T08:00:00Z', is_active: false })]);
    chatbotApi.history.mockImplementation((id) => Promise.resolve(id === S1 ? [row('a', 'USER', 'Câu hỏi phiên một')] : [row('b', 'USER', 'Câu hỏi phiên hai'), row('c', 'ASSISTANT', 'Trả lời phiên hai')]));
    chatbotApi.session.mockImplementation((id) => Promise.resolve(id === S1 ? session(S1) : session(S2, { is_active: false })));
    sessionStorage.setItem('storefront-chatbot.session.v1.customer-a', S1);
    show();
    expect(await within(log()).findByText('Câu hỏi phiên một')).toBeInTheDocument();
    const list = screen.getByRole('complementary', { name: 'Các cuộc trò chuyện' });
    expect(within(list).getAllByRole('button', { name: /Cuộc trò chuyện · / })).toHaveLength(2);
    expect(within(list).getByText('Đã kết thúc')).toBeInTheDocument();
    await user.click(within(list).getByRole('button', { name: /20\/10\/2026/ }));
    expect(await within(log()).findByText('Trả lời phiên hai')).toBeInTheDocument();
    expect(within(log()).queryByText('Câu hỏi phiên một')).not.toBeInTheDocument();
    expect(chatbotApi.history).toHaveBeenLastCalledWith(S2);
    expect(sessionStorage.getItem('storefront-chatbot.session.v1.customer-a')).toBe(S2);
    expect(input()).toBeDisabled();
    expect(screen.getByText('Cuộc trò chuyện này đã kết thúc. Bạn vẫn có thể đọc lại lịch sử.')).toBeInTheDocument();
  });

  it('restores the selected session after refresh from the API, not from memory', async () => {
    chatbotApi.session.mockResolvedValue(session(S1));
    chatbotApi.sessions.mockResolvedValue([session(S1)]);
    chatbotApi.history.mockResolvedValue([row('a', 'USER', 'Tìm laptop'), row('b', 'ASSISTANT', 'Gợi ý cũ')]);
    sessionStorage.setItem('storefront-chatbot.session.v1.customer-a', S1);
    const first = show(); expect(await screen.findByText('Gợi ý cũ')).toBeInTheDocument(); first.unmount();
    show(); expect(await screen.findByText('Gợi ý cũ')).toBeInTheDocument();
    expect(chatbotApi.history.mock.calls.every(([id]) => id === S1)).toBe(true);
    expect(sessionStorage.getItem('storefront-chatbot.session.v1.customer-a')).toBe(S1);
    expect(JSON.stringify(sessionStorage)).not.toContain('Gợi ý cũ');
  });

  it('creates a new conversation locally without deleting or creating sessions on the backend', async () => {
    const user = userEvent.setup();
    chatbotApi.session.mockResolvedValue(session(S1)); chatbotApi.sessions.mockResolvedValue([session(S1)]);
    chatbotApi.history.mockResolvedValueOnce([row('a', 'ASSISTANT', 'Lịch sử cũ')]);
    sessionStorage.setItem('storefront-chatbot.session.v1.customer-a', S1);
    show(); await screen.findByText('Lịch sử cũ');
    chatbotApi.session.mockRejectedValue({ status: 404 }); chatbotApi.history.mockRejectedValue({ status: 404 });
    await user.click(screen.getByRole('button', { name: 'Cuộc trò chuyện mới' }));
    await waitFor(() => expect(screen.queryByText('Lịch sử cũ')).not.toBeInTheDocument());
    const stored = sessionStorage.getItem('storefront-chatbot.session.v1.customer-a');
    expect(stored).not.toBe(S1); expect(stored).toMatch(/^[0-9a-f-]{36}$/);
    expect(screen.getByText('Chưa gửi tin nhắn')).toBeInTheDocument();
    expect(chatbotApi.history.mock.calls.map(([id]) => id)).toEqual([S1]);
    expect(chatbotApi.close).not.toHaveBeenCalled();
    expect(chatbotApi.send).not.toHaveBeenCalled();
  });

  it('under StrictMode a brand-new conversation is not looked up on the server', async () => {
    render(<StrictMode><MemoryRouter initialEntries={['/chatbot']}><AuthContext.Provider value={{ user: customer }}><CustomerChatbotPage /></AuthContext.Provider></MemoryRouter></StrictMode>);
    await screen.findByRole('heading', { name: 'Bạn cần tư vấn gì hôm nay?' });
    expect(chatbotApi.history).not.toHaveBeenCalled(); expect(chatbotApi.session).not.toHaveBeenCalled();
    expect(sessionStorage.getItem('storefront-chatbot.session.v1.customer-a')).toMatch(/^[0-9a-f-]{36}$/);
  });

  it('paginates the session list', async () => {
    const user = userEvent.setup();
    const page0 = Array.from({ length: 20 }, (_, i) => session(`00000000-0000-4000-8000-${String(i).padStart(12, '0')}`));
    chatbotApi.sessions.mockImplementation((page) => Promise.resolve(page === 0 ? page0 : [session(S2, { created_at: '2026-09-01T01:00:00Z' })]));
    show();
    await user.click(await screen.findByRole('button', { name: 'Tải thêm cuộc trò chuyện' }));
    expect(chatbotApi.sessions).toHaveBeenLastCalledWith(1);
    expect(await screen.findByRole('button', { name: /01\/09\/2026/ })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Tải thêm cuộc trò chuyện' })).not.toBeInTheDocument();
  });

  it('loads older messages with the oldest server message as cursor', async () => {
    const user = userEvent.setup();
    const rows = Array.from({ length: 100 }, (_, i) => row(`m${i}`, i % 2 ? 'ASSISTANT' : 'USER', `Tin ${i}`));
    chatbotApi.session.mockResolvedValue(session(S1));
    chatbotApi.history.mockImplementation((id, before) => Promise.resolve(before ? [row('older', 'USER', 'Tin cũ nhất')] : rows));
    sessionStorage.setItem('storefront-chatbot.session.v1.customer-a', S1);
    show();
    await user.click(await screen.findByRole('button', { name: 'Xem tin nhắn cũ hơn' }));
    expect(chatbotApi.history).toHaveBeenLastCalledWith(S1, 'm0');
    expect(await screen.findByText('Tin cũ nhất')).toBeInTheDocument();
  });

  it('closes a session after confirmation and blocks further messages', async () => {
    const user = userEvent.setup();
    chatbotApi.session.mockResolvedValue(session(S1)); chatbotApi.sessions.mockResolvedValue([session(S1)]);
    chatbotApi.history.mockResolvedValue([row('a', 'USER', 'Xin chào')]);
    sessionStorage.setItem('storefront-chatbot.session.v1.customer-a', S1);
    show(); await screen.findByText('Xin chào');
    await user.click(screen.getByRole('button', { name: 'Kết thúc cuộc trò chuyện' }));
    expect(chatbotApi.close).not.toHaveBeenCalled();
    await user.click(within(screen.getByRole('group', { name: 'Xác nhận kết thúc cuộc trò chuyện' })).getByRole('button', { name: 'Kết thúc' }));
    expect(chatbotApi.close).toHaveBeenCalledWith(S1);
    expect(await screen.findByText('Cuộc trò chuyện này đã kết thúc. Bạn vẫn có thể đọc lại lịch sử.')).toBeInTheDocument();
    expect(input()).toBeDisabled(); expect(sendButton()).toBeDisabled();
    expect(screen.getByText('Xin chào')).toBeInTheDocument();
  });

  it('keeps accounts apart: another customer never sees the previous transcript or session', async () => {
    chatbotApi.session.mockResolvedValue(session(S1)); chatbotApi.sessions.mockResolvedValue([session(S1)]);
    chatbotApi.history.mockResolvedValue([row('a', 'USER', 'Đơn hàng của khách A')]);
    sessionStorage.setItem('storefront-chatbot.session.v1.customer-a', S1);
    const view = show(); await screen.findByText('Đơn hàng của khách A');
    chatbotApi.sessions.mockResolvedValue([]); chatbotApi.session.mockRejectedValue({ status: 404 }); chatbotApi.history.mockRejectedValue({ status: 404 });
    view.rerender(<MemoryRouter initialEntries={['/chatbot']}><AuthContext.Provider value={{ user: { userId: 'customer-b', roleName: 'CUSTOMER' } }}><CustomerChatbotPage /></AuthContext.Provider></MemoryRouter>);
    await screen.findByRole('heading', { name: 'Bạn cần tư vấn gì hôm nay?' });
    expect(screen.queryByText('Đơn hàng của khách A')).not.toBeInTheDocument();
    const fresh = sessionStorage.getItem('storefront-chatbot.session.v1.customer-b');
    expect(chatbotApi.history.mock.calls.map(([id]) => id)).toEqual([S1]); // the new account's fresh ID is never looked up
    expect(fresh).toMatch(/^[0-9a-f-]{36}$/); expect(fresh).not.toBe(S1);
  });
});

describe('Customer chatbot messaging', () => {
  it('sends once, shows USER then ASSISTANT with product card, sources and backend suggestions', async () => {
    const user = userEvent.setup(); show(); await screen.findByRole('heading', { name: 'Bạn cần tư vấn gì hôm nay?' });
    const pending = deferred(); chatbotApi.send.mockReturnValue(pending.promise);
    chatbotApi.sessions.mockResolvedValue([session(S1)]);
    await user.type(input(), '  Tìm laptop dưới 20 triệu  ');
    await user.dblClick(sendButton());
    expect(chatbotApi.send).toHaveBeenCalledTimes(1);
    expect(chatbotApi.send.mock.calls[0][1]).toBe('Tìm laptop dưới 20 triệu');
    expect(await screen.findByRole('status', { name: '' })).toHaveTextContent('Đang trả lời…');
    expect(sendButton()).toBeDisabled();
    pending.resolve({ session_id: S1, message: 'Mình gợi ý <script>alert(1)</script> Dell Inspiron 15.', products: [product],
      sources: [{ document_id: 'd1', chunk_id: 'c1', title: 'Chính sách đổi trả', source: 'https://dienviet.example/doi-tra' },
        { document_id: 'd2', chunk_id: 'c2', title: 'Tài liệu nội bộ', source: 'javascript:alert(1)' }],
      suggested_questions: ['Máy đầu tiên còn hàng không?'] });
    const answer = await within(log()).findByText(/Dell Inspiron 15\.$/);
    expect(answer.textContent).toContain('<script>alert(1)</script>');
    expect(document.querySelector('script')).toBeNull();
    const bubbles = log().querySelectorAll('.cchat-message');
    expect([...bubbles].map((b) => b.className.includes('user') ? 'USER' : 'ASSISTANT')).toEqual(['USER', 'ASSISTANT']);
    const card = screen.getByRole('article', { name: 'Laptop Dell Inspiron 15' });
    expect(within(card).getByRole('link', { name: 'Laptop Dell Inspiron 15' })).toHaveAttribute('href', '/products/p1');
    expect(within(card).getByRole('link', { name: 'Xem chi tiết Laptop Dell Inspiron 15' })).toHaveAttribute('href', '/products/p1');
    expect(card).toHaveTextContent(/15\.990\.000\s₫/); expect(card).toHaveTextContent(/17\.990\.000\s₫/);
    expect(card).toHaveTextContent('RAM 16GB · Bộ nhớ 512GB · Màu Bạc');
    expect(card).toHaveTextContent('Tồn kho: còn 3'); expect(card).toHaveTextContent('Bảo hành 24 tháng'); expect(card).toHaveTextContent('SKU DEV-DELL-I15-512');
    expect(within(card).getByRole('img')).toHaveAttribute('src', '/images/dell.jpg');
    expect(within(card).queryByRole('button', { name: /giỏ|mua/i })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Chính sách đổi trả' })).toHaveAttribute('href', 'https://dienviet.example/doi-tra');
    expect(screen.queryByRole('link', { name: 'Tài liệu nội bộ' })).not.toBeInTheDocument();
    expect(screen.getByText('Tài liệu nội bộ')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Máy đầu tiên còn hàng không?' }));
    expect(input()).toHaveValue('Máy đầu tiên còn hàng không?');
    expect(chatbotApi.send).toHaveBeenCalledTimes(1);
  });

  it('renders incomplete product data honestly', async () => {
    const user = userEvent.setup(); show(); await screen.findByRole('heading', { name: 'Bạn cần tư vấn gì hôm nay?' });
    chatbotApi.send.mockResolvedValue({ message: 'Có sản phẩm', products: [{ product_id: 'p2', product_name: 'Điện thoại thiếu dữ liệu', effective_price: null, available_quantity: null, image_url: 'javascript:alert(1)' },
      { product_id: 'p3', product_name: 'Điện thoại hết hàng', effective_price: '8490000', available_quantity: 0 }], sources: [], suggested_questions: [] });
    await user.type(input(), 'Điện thoại'); await user.click(sendButton());
    const missing = await screen.findByRole('article', { name: 'Điện thoại thiếu dữ liệu' });
    expect(missing).toHaveTextContent('Chưa có giá'); expect(missing).toHaveTextContent('Tồn kho: Chưa xác định');
    expect(missing).not.toHaveTextContent('0 ₫'); expect(within(missing).getByRole('img', { name: 'Chưa có ảnh: Điện thoại thiếu dữ liệu' })).toBeInTheDocument();
    const sold = screen.getByRole('article', { name: 'Điện thoại hết hàng' });
    expect(sold).toHaveTextContent(/8\.490\.000\s₫/); expect(sold).toHaveTextContent('Tồn kho: hết hàng');
  });

  it('does not send empty text; Enter sends and Shift+Enter adds a line', async () => {
    const user = userEvent.setup(); show(); await screen.findByRole('heading', { name: 'Bạn cần tư vấn gì hôm nay?' });
    await user.type(input(), '   ');
    expect(sendButton()).toBeDisabled();
    await user.type(input(), '{Enter}');
    expect(chatbotApi.send).not.toHaveBeenCalled();
    await user.clear(input());
    await user.type(input(), 'Dòng một{Shift>}{Enter}{/Shift}Dòng hai');
    expect(input()).toHaveValue('Dòng một\nDòng hai');
    chatbotApi.send.mockResolvedValue({ message: 'Đã nhận', products: [], sources: [], suggested_questions: [] });
    await user.type(input(), '{Enter}');
    expect(chatbotApi.send).toHaveBeenCalledWith(expect.any(String), 'Dòng một\nDòng hai', expect.any(AbortSignal));
    expect(await screen.findByText('Đã nhận')).toBeInTheDocument();
  });

  it.each([
    [401, 'Phiên đăng nhập đã hết hạn'], [403, 'không có quyền'], [404, 'không tồn tại'],
    [409, 'đang xử lý một câu hỏi khác'], [503, 'Trợ lý AI tạm thời chưa khả dụng'],
  ])('maps HTTP %s to a customer message and keeps the draft', async (status, text) => {
    const user = userEvent.setup(); show(); await screen.findByRole('heading', { name: 'Bạn cần tư vấn gì hôm nay?' });
    chatbotApi.send.mockRejectedValue({ status, data: { detail: 'Chưa thể lấy dữ liệu sản phẩm. Kiểm tra backend, kho hàng và dịch vụ embedding.' } });
    await user.type(input(), 'Giỏ hàng của tôi'); await user.click(sendButton());
    expect(await screen.findByRole('alert')).toHaveTextContent(text);
    expect(screen.queryByText(/embedding/)).not.toBeInTheDocument();
    expect(input()).toHaveValue('Giỏ hàng của tôi');
    if (status === 401) expect(screen.getByRole('link', { name: 'Đăng nhập lại' })).toHaveAttribute('href', '/login');
    expect(chatbotApi.send).toHaveBeenCalledTimes(1);
  });

  it('after a network failure confirms the saved answer from history instead of re-sending', async () => {
    const user = userEvent.setup(); show(); await screen.findByRole('heading', { name: 'Bạn cần tư vấn gì hôm nay?' });
    chatbotApi.send.mockRejectedValue(new Error('Không thể kết nối máy chủ.'));
    chatbotApi.history.mockResolvedValue([row('u', 'USER', 'Tìm laptop'), row('a', 'ASSISTANT', 'Câu trả lời đã lưu')]);
    await user.type(input(), 'Tìm laptop'); await user.click(sendButton());
    expect(await screen.findByText('Câu trả lời đã lưu')).toBeInTheDocument();
    expect(screen.getByText(/đã được lưu trên máy chủ/)).toBeInTheDocument();
    expect(input()).toHaveValue(''); expect(screen.queryByRole('button', { name: 'Đồng bộ lịch sử' })).not.toBeInTheDocument();
    expect(chatbotApi.send).toHaveBeenCalledTimes(1);
  });

  it('after an unverified network failure requires an explicit history sync before retry', async () => {
    const user = userEvent.setup(); show(); await screen.findByRole('heading', { name: 'Bạn cần tư vấn gì hôm nay?' });
    chatbotApi.send.mockRejectedValue(new Error('Không thể kết nối máy chủ.'));
    chatbotApi.history.mockResolvedValue([row('u', 'USER', 'Tìm laptop')]);
    await user.type(input(), 'Tìm laptop'); await user.click(sendButton());
    expect(await screen.findByRole('alert')).toHaveTextContent('Mất kết nối tới máy chủ');
    await screen.findByRole('button', { name: 'Đồng bộ lịch sử' });
    expect(input()).toHaveValue('Tìm laptop'); expect(sendButton()).toBeDisabled();
    expect(within(log()).getAllByText('Tìm laptop')).toHaveLength(1);
    await user.click(screen.getByRole('button', { name: 'Đồng bộ lịch sử' }));
    await waitFor(() => expect(sendButton()).toBeEnabled());
    expect(chatbotApi.send).toHaveBeenCalledTimes(1);
  });

  it('Enter cannot bypass a required history sync', async () => {
    const user = userEvent.setup(); show(); await screen.findByRole('heading', { name: 'Bạn cần tư vấn gì hôm nay?' });
    chatbotApi.send.mockRejectedValue(new Error('Không thể kết nối máy chủ.'));
    chatbotApi.history.mockResolvedValue([row('u', 'USER', 'Tìm laptop')]);
    await user.type(input(), 'Tìm laptop'); await user.click(sendButton());
    await screen.findByRole('button', { name: 'Đồng bộ lịch sử' });
    fireEvent.keyDown(input(), { key: 'Enter' });
    expect(chatbotApi.send).toHaveBeenCalledTimes(1);
  });

  it('two Enter presses in the same tick send a single request', async () => {
    const user = userEvent.setup(); show(); await screen.findByRole('heading', { name: 'Bạn cần tư vấn gì hôm nay?' });
    chatbotApi.send.mockReturnValue(deferred().promise);
    await user.type(input(), 'Xin chào');
    act(() => { fireEvent.keyDown(input(), { key: 'Enter' }); fireEvent.keyDown(input(), { key: 'Enter' }); });
    expect(chatbotApi.send).toHaveBeenCalledTimes(1);
  });

  it('times out on the client after 180s without claiming the server cancelled', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime }); show(); await screen.findByRole('heading', { name: 'Bạn cần tư vấn gì hôm nay?' });
    chatbotApi.send.mockImplementation((id, text, signal) => new Promise((_, reject) => signal.addEventListener('abort', () => reject(Object.assign(new Error('aborted'), { name: 'AbortError' })))));
    chatbotApi.history.mockResolvedValue([row('u', 'USER', 'Tìm laptop')]);
    await user.type(input(), 'Tìm laptop'); await user.click(sendButton());
    vi.advanceTimersByTime(179000);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    vi.advanceTimersByTime(2000);
    expect(await screen.findByRole('alert')).toHaveTextContent('Câu trả lời có thể vẫn đang được xử lý');
    expect(await screen.findByRole('button', { name: 'Đồng bộ lịch sử' })).toBeInTheDocument();
  });

  it('drops a late response from the previous session after switching', async () => {
    const user = userEvent.setup();
    chatbotApi.sessions.mockResolvedValue([session(S2, { created_at: '2026-10-20T08:00:00Z' })]);
    chatbotApi.history.mockImplementation((id) => id === S2 ? Promise.resolve([row('x', 'ASSISTANT', 'Lịch sử phiên hai')]) : Promise.reject({ status: 404 }));
    chatbotApi.session.mockImplementation((id) => id === S2 ? Promise.resolve(session(S2)) : Promise.reject({ status: 404 }));
    show(); await screen.findByRole('heading', { name: 'Bạn cần tư vấn gì hôm nay?' });
    const pending = deferred(); chatbotApi.send.mockReturnValue(pending.promise);
    await user.type(input(), 'Câu hỏi phiên cũ'); await user.click(sendButton());
    await user.click(screen.getByRole('button', { name: /20\/10\/2026/ }));
    expect(await screen.findByText('Lịch sử phiên hai')).toBeInTheDocument();
    pending.resolve({ message: 'Trả lời muộn của phiên cũ', products: [], sources: [], suggested_questions: [] });
    await new Promise((r) => setTimeout(r, 20));
    expect(screen.queryByText('Trả lời muộn của phiên cũ')).not.toBeInTheDocument();
    expect(screen.queryByText('Câu hỏi phiên cũ')).not.toBeInTheDocument();
    expect(sendButton()).not.toHaveTextContent('Đang trả lời…');
  });
});

describe('Customer chatbot accessibility and layout hooks', () => {
  it('exposes labelled controls, a polite live log and a mobile list toggle', async () => {
    const user = userEvent.setup(); show(); await screen.findByRole('heading', { name: 'Bạn cần tư vấn gì hôm nay?' });
    expect(log()).toHaveAttribute('aria-live', 'polite');
    expect(input()).toHaveAccessibleDescription(/Enter để gửi/);
    const toggle = screen.getByRole('button', { name: 'Danh sách trò chuyện' });
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
    await user.click(toggle);
    expect(screen.getByRole('button', { name: 'Ẩn danh sách' })).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByRole('complementary', { name: 'Các cuộc trò chuyện' })).toHaveClass('is-open');
    await user.tab();
    expect(document.activeElement).not.toBe(document.body);
  });
});
