import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthContext } from '../../auth/AuthContext';
import ChatbotLabPage from './ChatbotLabPage';
import { chatbotApi } from './chatbotApi';
vi.mock('./chatbotApi', () => ({ chatbotApi: { status: vi.fn(), send: vi.fn(), history: vi.fn(), sessions: vi.fn(), session: vi.fn(), close: vi.fn(), rate: vi.fn() } }));

function show() {
  return render(<MemoryRouter><AuthContext.Provider value={{ user: { userId: 'test-user', displayName: 'Khách thử', roleName: 'CUSTOMER' } }}><ChatbotLabPage /></AuthContext.Provider></MemoryRouter>);
}
beforeEach(() => {
  sessionStorage.clear();
  chatbotApi.sessions.mockResolvedValue([]);
  chatbotApi.session.mockRejectedValue({ status: 404 });
  chatbotApi.close.mockResolvedValue({ is_active: false });
  chatbotApi.status.mockResolvedValue({ ai_enabled: true, product_mode: 'real', chat_model: 'chat-test', embedding_model: 'embed-test', ollama_reachable: true });
  chatbotApi.history.mockResolvedValue([]);
  chatbotApi.rate.mockResolvedValue({ rating: 1 });
});
describe('Chatbot lab', () => {
  it('shows real mode and sends only after explicit submit', async () => {
    const user = userEvent.setup(); show();
    await screen.findByText('AI thật');
    await user.click(screen.getByRole('button', { name: /Tìm theo ngân sách/ }));
    expect(chatbotApi.send).not.toHaveBeenCalled();
    expect(screen.getByLabelText('Câu hỏi của bạn')).toHaveValue('Tìm laptop giá dưới 20 triệu, RAM ít nhất 16GB.');
    chatbotApi.send.mockResolvedValue({ message: 'Laptop đáp ứng điều kiện.', products: [{ product_id: 'p1', variant_id: 'v1', product_name: 'Laptop thử', effective_price: '19000000', ram: '16GB', sku: 'SKU', reasons: [] }], sources: [{ document_id: 'd1', chunk_id: 'c1', title: 'Chính sách DEMO', source: 'javascript:alert(1)' }] });
    chatbotApi.history.mockResolvedValue([{ id: 'assistant-id', sender_type: 'ASSISTANT', message: 'Laptop đáp ứng điều kiện.', rating: 0 }]);
    await user.click(screen.getByRole('button', { name: 'Gửi câu hỏi' }));
    expect(await screen.findByText('Laptop đáp ứng điều kiện.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Laptop thử' })).toHaveAttribute('href', '/products/p1');
    expect(screen.queryByRole('link', { name: 'Chính sách DEMO' })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Câu trả lời đúng' }));
    await waitFor(() => expect(chatbotApi.rate).toHaveBeenCalledWith(expect.any(String), 'assistant-id', 1));
  });

  it('shows backend failure and keeps the question for correction', async () => {
    const user = userEvent.setup(); show();
    await screen.findByText('AI thật');
    chatbotApi.send.mockRejectedValue({ status: 503, data: { detail: 'Backend chưa sẵn sàng' } });
    await user.type(screen.getByLabelText('Câu hỏi của bạn'), 'Đơn của tôi?');
    await user.click(screen.getByRole('button', { name: 'Gửi câu hỏi' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Backend chưa sẵn sàng');
    expect(screen.getByLabelText('Câu hỏi của bạn')).toHaveValue('Đơn của tôi?');
  });

  it('starts an independent session and restores history text', async () => {
    const user = userEvent.setup();
    chatbotApi.history.mockResolvedValueOnce([{ id: 'a', sender_type: 'ASSISTANT', message: 'Lịch sử cũ', rating: 0 }]);
    show(); await screen.findByText('Lịch sử cũ');
    const oldId = sessionStorage.getItem('chatbot-lab.session.v2.test-user');
    await user.click(screen.getByRole('button', { name: 'Cuộc trò chuyện mới' }));
    await waitFor(() => expect(screen.queryByText('Lịch sử cũ')).not.toBeInTheDocument());
    expect(sessionStorage.getItem('chatbot-lab.session.v2.test-user')).not.toBe(oldId);
  });
});


describe('durable session selection', () => {
  it('loads a previous owned session without leaking assistant metadata', async () => {
    const previous = '11111111-1111-4111-8111-111111111111';
    chatbotApi.sessions.mockResolvedValue([{ id: previous, created_at: '2026-10-13T00:00:00Z', is_active: true }]);
    chatbotApi.history.mockImplementation((id) => Promise.resolve(id === previous ? [{ id: 'persisted', sender_type: 'ASSISTANT', message: 'Tin đã lưu qua restart', rating: 0 }] : []));
    const user = userEvent.setup(); show();
    await screen.findByRole('option', { name: /Đang mở/ });
    await user.selectOptions(screen.getByLabelText('Lịch sử phiên'), previous);
    expect(await screen.findByText('Tin đã lưu qua restart')).toBeInTheDocument();
    expect(chatbotApi.history).toHaveBeenCalledWith(previous);
  });
  it('reads a closed session but allows a fresh conversation', async () => {
    const previous = '22222222-2222-4222-8222-222222222222';
    chatbotApi.sessions.mockResolvedValue([{ id: previous, created_at: '2026-10-13T00:00:00Z', is_active: false }]);
    chatbotApi.session.mockImplementation((id) => id === previous ? Promise.resolve({ is_active: false }) : Promise.reject({ status: 404 }));
    const user = userEvent.setup(); show();
    await screen.findByRole('option', { name: /Đã đóng/ });
    await user.selectOptions(screen.getByLabelText('Lịch sử phiên'), previous);
    expect(await screen.findByText('Phiên đã đóng. Bạn có thể đọc lịch sử hoặc tạo phiên mới.')).toBeInTheDocument();
    expect(screen.getByLabelText('Câu hỏi của bạn')).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Cuộc trò chuyện mới' }));
    await waitFor(() => expect(screen.getByLabelText('Câu hỏi của bạn')).not.toBeDisabled());
  });
});


it('keeps a selected old persisted session even outside the first list page', async () => {
  const oldId = '33333333-3333-4333-8333-333333333333';
  sessionStorage.setItem('chatbot-lab.session.v2.test-user', oldId);
  chatbotApi.sessions.mockResolvedValue([]);
  chatbotApi.session.mockResolvedValue({ id: oldId, created_at: '2026-10-01T00:00:00Z', is_active: true });
  show();
  await screen.findByRole('option', { name: /Đang mở/ });
  expect(screen.getByLabelText('Lịch sử phiên')).toHaveValue(oldId);
  expect(screen.getByRole('button', { name: 'Đóng phiên' })).not.toBeDisabled();
});

function deferred() {
  let resolve;
  const promise = new Promise((done) => { resolve = done; });
  return { promise, resolve };
}

it('ignores a close response after selecting a different session', async () => {
  const a = '44444444-4444-4444-8444-444444444444';
  const b = '55555555-5555-4555-8555-555555555555';
  sessionStorage.setItem('chatbot-lab.session.v2.test-user', a);
  const rows = [a, b].map((id) => ({ id, created_at: '2026-10-13T00:00:00Z', is_active: true }));
  chatbotApi.sessions.mockResolvedValue(rows);
  chatbotApi.session.mockImplementation((id) => Promise.resolve(rows.find((r) => r.id === id)));
  const pending = deferred(); chatbotApi.close.mockReturnValueOnce(pending.promise);
  const user = userEvent.setup(); show();
  await waitFor(() => expect(screen.getByRole('button', { name: 'Đóng phiên' })).not.toBeDisabled());
  await user.click(screen.getByRole('button', { name: 'Đóng phiên' }));
  await user.selectOptions(screen.getByLabelText('Lịch sử phiên'), b);
  await waitFor(() => expect(screen.getByLabelText('Câu hỏi của bạn')).not.toBeDisabled());
  await act(async () => { pending.resolve({ id: a, is_active: false }); });
  await waitFor(() => expect(screen.getByLabelText('Lịch sử phiên')).toHaveValue(b));
  expect(screen.queryByText('Phiên đã đóng. Bạn có thể đọc lịch sử hoặc tạo phiên mới.')).not.toBeInTheDocument();
  expect(screen.getByLabelText('Câu hỏi của bạn')).not.toBeDisabled();
});

it('removes an unpersisted optimistic user message before retry', async () => {
  const user = userEvent.setup(); show(); await screen.findByText('AI thật');
  await user.type(screen.getByLabelText('Câu hỏi của bạn'), 'Câu hỏi retry');
  chatbotApi.send.mockRejectedValueOnce({ status: 503, data: { detail: 'Write unavailable' } });
  await user.click(screen.getByRole('button', { name: 'Gửi câu hỏi' }));
  await screen.findByRole('alert');
  expect(within(screen.getByRole('log')).queryAllByText('Câu hỏi retry')).toHaveLength(0);
  chatbotApi.send.mockResolvedValueOnce({ message: 'Đã trả lời', products: [], sources: [] });
  await user.click(screen.getByRole('button', { name: 'Gửi câu hỏi' }));
  await screen.findByText('Đã trả lời');
  expect(within(screen.getByRole('log')).getAllByText('Câu hỏi retry')).toHaveLength(1);
});

it('labels postgres history using reported runtime storage', async () => {
  chatbotApi.status.mockResolvedValue({ ai_enabled: true, conversation_storage: 'postgres' });
  show(); await screen.findByText('AI thật');
  expect(screen.queryByText('Lịch sử lưu trong RAM của server')).not.toBeInTheDocument();
});

it('does not duplicate session-page requests while a page is pending', async () => {
  const rows = Array.from({ length: 20 }, (_, i) => ({ id: `session-${i}`, created_at: '2026-10-13T00:00:00Z', is_active: true }));
  const pending = deferred();
  chatbotApi.sessions.mockImplementation((page = 0) => page === 0 ? Promise.resolve(rows) : pending.promise);
  const user = userEvent.setup(); show();
  const button = await screen.findByRole('button', { name: 'Tải thêm phiên cũ' });
  await user.dblClick(button);
  expect(chatbotApi.sessions.mock.calls.filter(([page]) => page === 1)).toHaveLength(1);
  await act(async () => { pending.resolve([]); });
});

it('requires a successful history reload after an ambiguous persistence failure', async () => {
  const user = userEvent.setup(); show(); await screen.findByText('AI thật');
  await user.type(screen.getByLabelText('Câu hỏi của bạn'), 'Thử lưu');
  chatbotApi.send.mockRejectedValueOnce({ status: 503 });
  chatbotApi.history.mockRejectedValueOnce({ status: 503 });
  await user.click(screen.getByRole('button', { name: 'Gửi câu hỏi' }));
  await screen.findByText('Chưa xác minh lượt gửi vừa rồi. Đọc lại lịch sử trước khi gửi lại.');
  expect(screen.getByRole('button', { name: 'Gửi câu hỏi' })).toBeDisabled();
  await user.click(screen.getByRole('button', { name: 'Đọc lại lịch sử' }));
  await waitFor(() => expect(screen.getByRole('button', { name: 'Gửi câu hỏi' })).not.toBeDisabled());
  expect(chatbotApi.send).toHaveBeenCalledTimes(1);
});

it('deduplicates cursor history without creating historical cards or sources', async () => {
  const rows = Array.from({ length: 100 }, (_, i) => ({ id: `message-${i}`, sender_type: i % 2 ? 'ASSISTANT' : 'USER', message: `Lịch sử ${i}`, rating: 0 }));
  chatbotApi.history.mockResolvedValueOnce(rows).mockResolvedValueOnce([{ id: 'older', sender_type: 'USER', message: 'Tin cũ hơn', rating: 0 }, rows[0]]);
  const user = userEvent.setup(); show();
  await screen.findByText('Lịch sử 99');
  await user.click(screen.getByRole('button', { name: 'Tải tin nhắn cũ hơn' }));
  await screen.findByText('Tin cũ hơn');
  expect(within(screen.getByRole('log')).getAllByText('Lịch sử 0')).toHaveLength(1);
  expect(document.querySelectorAll('.lab-product, .lab-sources')).toHaveLength(0);
});
