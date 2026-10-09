import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import { useAuth } from '../../hooks/useAuth';
import { chatbotApi } from './chatbotApi';
import { scenarios } from './scenarios';
import './chatbot-lab.css';

const money = (value) => value == null ? 'Chưa có dữ liệu' : new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(Number(value));
function sessionFor(userId) {
  const key = `chatbot-lab.session.${userId}`;
  try {
    const stored = sessionStorage.getItem(key);
    if (/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(stored || '')) return stored;
  } catch { /* Browsers may disable storage. */ }
  const id = crypto.randomUUID();
  try { sessionStorage.setItem(key, id); } catch { /* Session still works in memory. */ }
  return id;
}
function errorText(error) {
  if (error.status === 401) return 'Phiên đăng nhập hết hạn. Đăng nhập lại rồi thử với cuộc trò chuyện mới.';
  if (error.status === 403) return 'Tài khoản không có quyền truy cập dữ liệu này hoặc cuộc trò chuyện thuộc tài khoản khác.';
  if (error.name === 'TimeoutError' || error.name === 'AbortError') return 'Đã hết thời gian chờ hoặc dừng chờ. Server có thể vẫn đang xử lý; tải lịch sử trước khi gửi lại.';
  return error.data?.detail || error.message || 'Không gửi được câu hỏi. Kiểm tra trạng thái dịch vụ.';
}
function safeSource(source) {
  try { const url = new URL(source); return ['http:', 'https:'].includes(url.protocol) ? url.href : null; } catch { return null; }
}

export default function ChatbotLabPage() {
  const { user } = useAuth();
  const [sessionId, setSessionId] = useState(() => sessionFor(user.userId));
  const [runtime, setRuntime] = useState(null);
  const [statusError, setStatusError] = useState('');
  const [messages, setMessages] = useState([]);
  const [draft, setDraft] = useState('');
  const [selected, setSelected] = useState(scenarios[0]);
  const [busy, setBusy] = useState(false);
  const [restoring, setRestoring] = useState(true);
  const [error, setError] = useState('');
  const [lastResult, setLastResult] = useState(null);
  const [elapsed, setElapsed] = useState(null);
  const [feedbackBusy, setFeedbackBusy] = useState(false);
  const controller = useRef(null);
  const end = useRef(null);

  useEffect(() => {
    let active = true;
    chatbotApi.status().then((data) => { if (active) setRuntime(data); })
      .catch((failure) => { if (active) setStatusError(errorText(failure)); });
    chatbotApi.history(sessionId).then((history) => {
      if (active) setMessages(history.map((m) => ({ id: m.id, role: m.sender_type, text: m.message, rating: m.rating })));
    }).catch((failure) => { if (active && failure.status !== 404) setError(errorText(failure)); })
      .finally(() => { if (active) setRestoring(false); });
    return () => { active = false; controller.current?.abort(); };
  }, [sessionId]);
  useEffect(() => { end.current?.scrollIntoView?.({ behavior: 'smooth', block: 'nearest' }); }, [messages, busy]);

  async function refreshStatus() {
    setStatusError('');
    try { setRuntime(await chatbotApi.status()); } catch (failure) { setStatusError(errorText(failure)); }
  }
  async function send(event) {
    event.preventDefault();
    const question = draft.trim();
    if (!question || busy || restoring) return;
    setBusy(true); setError(''); setDraft(''); setElapsed(null);
    setMessages((items) => [...items, { id: crypto.randomUUID(), role: 'USER', text: question }]);
    const started = performance.now();
    const abort = new AbortController(); controller.current = abort;
    const timer = setTimeout(() => abort.abort(), 180000);
    try {
      const result = await chatbotApi.send(sessionId, question, abort.signal);
      if (abort.signal.aborted) return;
      const duration = Math.round(performance.now() - started);
      setElapsed(duration); setLastResult(result);
      let saved = null;
      try {
        const history = await chatbotApi.history(sessionId);
        saved = history.filter((m) => m.sender_type === 'ASSISTANT' && m.message === result.message).at(-1);
      } catch { /* Keep successful answers even if reading history fails. */ }
      if (abort.signal.aborted) return;
      setMessages((items) => [...items, { id: saved?.id || crypto.randomUUID(), role: 'ASSISTANT', text: result.message,
        response: result, feedbackId: saved?.id, rating: saved?.rating || 0, duration }]);
    } catch (failure) {
      setError(errorText(failure)); setDraft(question);
    } finally {
      clearTimeout(timer); controller.current = null; setBusy(false);
    }
  }
  function newSession() {
    const id = crypto.randomUUID();
    try { sessionStorage.setItem(`chatbot-lab.session.${user.userId}`, id); } catch { /* No storage. */ }
    setSessionId(id); setMessages([]); setLastResult(null); setError(''); setElapsed(null); setRestoring(true);
  }
  async function rate(item, rating) {
    setFeedbackBusy(true); setError('');
    try {
      await chatbotApi.rate(sessionId, item.feedbackId || item.id, rating);
      setMessages((items) => items.map((m) => m.id === item.id ? { ...m, rating } : m));
    } catch (failure) { setError(errorText(failure)); } finally { setFeedbackBusy(false); }
  }
  function exportResult() {
    const blob = new Blob([JSON.stringify({ exported_at: new Date().toISOString(), session_id: sessionId, runtime, scenario: selected, messages }, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob); const link = document.createElement('a');
    link.href = url; link.download = `chatbot-lab-${sessionId}.json`; link.click(); URL.revokeObjectURL(url);
  }

  return <main className="chatbot-lab">
    <header className="lab-header">
      <div><span className="lab-eyebrow">ĐIỆN VIỆT / SANDBOX</span><h1>Phòng thử Chatbot</h1><p>Kiểm chứng câu trả lời với dữ liệu thật trước khi đưa vào cửa hàng.</p></div>
      <div className="lab-header-actions"><Link to="/">Về website</Link><Link to="/account">{user.displayName} · {user.roleName}</Link></div>
    </header>
    <div className="lab-notice">Trang kiểm thử riêng. Chatbot chỉ tư vấn và đọc dữ liệu; không đặt hàng, thanh toán hay sửa giỏ. Tài liệu DEMO không phải chính sách chính thức.</div>
    <div className="lab-grid">
      <aside className="lab-panel lab-scenarios"><h2>Kịch bản kiểm thử</h2><p>Chọn câu mẫu, đọc kỳ vọng, rồi nhấn Gửi.</p>
        {scenarios.map((scenario) => <button type="button" key={scenario.id} aria-pressed={selected.id === scenario.id} disabled={busy}
          onClick={() => { setSelected(scenario); setDraft(scenario.prompt); }}><span>{scenario.group}</span>{scenario.title}</button>)}
      </aside>
      <section className="lab-panel lab-chat" aria-label="Cuộc trò chuyện kiểm thử">
        <div className="lab-chat-heading"><div><h2>Cuộc trò chuyện</h2><small>Lịch sử lưu trong RAM của server</small></div>
          <button type="button" onClick={newSession} disabled={busy || restoring}>Cuộc trò chuyện mới</button></div>
        <div className="lab-expectation"><strong>Kỳ vọng · {selected.title}</strong><p>{selected.expected}</p></div>
        <div className="lab-transcript" role="log" aria-live="polite" aria-busy={busy || restoring}>
          {!messages.length && <div className="lab-empty"><span>✦</span><h3>Bắt đầu một phép thử</h3><p>Thử chào hỏi, tìm sản phẩm hoặc hỏi chính sách. Đối chiếu dữ liệu trước khi đánh giá câu trả lời.</p></div>}
          {messages.map((item) => <article className={`lab-message lab-message-${item.role.toLowerCase()}`} key={item.id}>
            <strong>{item.role === 'USER' ? 'Bạn' : 'Chatbot'}</strong><p>{item.text}</p>
            {item.response?.products?.length > 0 && <div className="lab-products">{item.response.products.map((product) => <div className="lab-product" key={`${product.product_id}-${product.variant_id}`}>
              <Link to={`/products/${product.product_id}`}>{product.product_name}</Link><b>{money(product.effective_price)}</b>
              <span>{[product.ram && `RAM ${product.ram}`, product.storage, product.color].filter(Boolean).join(' · ')}</span>
              <span>Tồn kho: {product.available_quantity ?? 'Chưa xác nhận'} · SKU: {product.sku || '—'}</span>
              {product.reasons?.map((reason) => <small key={reason}>{reason}</small>)}
            </div>)}</div>}
            {item.response?.sources?.length > 0 && <div className="lab-sources"><strong>Nguồn tham khảo</strong>{item.response.sources.map((source) => <div key={source.chunk_id || source.document_id}>
              {safeSource(source.source) ? <a href={safeSource(source.source)} target="_blank" rel="noreferrer">{source.title}</a> : <span>{source.title}</span>}
              <small>{source.source || 'Không có URL nguồn'}</small></div>)}</div>}
            {item.role === 'ASSISTANT' && <div className="lab-feedback">
              {(item.feedbackId || !item.response) && <><button type="button" aria-label="Câu trả lời đúng" aria-pressed={item.rating === 1} disabled={feedbackBusy} onClick={() => rate(item, item.rating === 1 ? 0 : 1)}>Đúng</button>
                <button type="button" aria-label="Câu trả lời cần sửa" aria-pressed={item.rating === -1} disabled={feedbackBusy} onClick={() => rate(item, item.rating === -1 ? 0 : -1)}>Cần sửa</button></>}
              {item.duration != null && <small>{(item.duration / 1000).toFixed(1)} giây</small>}
            </div>}
          </article>)}
          {busy && <p role="status" className="lab-working">Đang xử lý… Model lần đầu có thể cần tải vào bộ nhớ.</p>}<div ref={end} />
        </div>
        {error && <p role="alert" className="lab-error">{error}</p>}
        <form onSubmit={send} className="lab-composer"><label htmlFor="chat-question">Câu hỏi của bạn</label>
          <textarea id="chat-question" value={draft} onChange={(event) => setDraft(event.target.value)} maxLength={4000} rows={3} placeholder="Nhập câu hỏi cần kiểm chứng…" disabled={busy || restoring} />
          <div><small>{draft.length}/4000 ký tự · Enter xuống dòng</small><button type="submit" disabled={busy || restoring || !draft.trim()}>{busy ? 'Đang chờ…' : 'Gửi câu hỏi'}</button></div>
        </form>
      </section>
      <aside className="lab-panel lab-inspector"><h2>Trạng thái & kết quả</h2><button type="button" onClick={refreshStatus}>Kiểm tra dịch vụ</button>
        {statusError && <p role="alert" className="lab-error">{statusError}</p>}
        {runtime && <dl>
          <dt>Chế độ trả lời</dt><dd className={runtime.ai_enabled ? 'lab-good' : 'lab-warn'}>{runtime.ai_enabled ? 'AI thật' : 'Mock · câu trả lời mẫu'}</dd>
          <dt>Sản phẩm</dt><dd>{runtime.product_mode === 'real' ? 'Backend thật' : 'Mock · dữ liệu mẫu'}</dd>
          <dt>Ollama</dt><dd>{runtime.ollama_reachable ? 'Đã kết nối' : 'Chưa kết nối'}</dd>
          <dt>Model chat</dt><dd>{runtime.chat_model} · {runtime.chat_model_ready ? 'Sẵn sàng' : 'Chưa tải'}</dd>
          <dt>Model embedding</dt><dd>{runtime.embedding_model} · {runtime.embedding_model_ready ? 'Sẵn sàng' : 'Chưa tải'}</dd>
          <dt>Knowledge DB</dt><dd>{runtime.knowledge_configured ? `Đã cấu hình · ${runtime.knowledge_status}` : 'Chưa cấu hình'}</dd>
          <dt>Kho / index sản phẩm</dt><dd>{runtime.warehouse_configured ? 'Có kho' : 'Chưa có kho'} / {runtime.product_index_configured ? 'Đã cấu hình index' : 'Chưa cấu hình index'}</dd>
        </dl>}
        <small>“Đã cấu hình” không đảm bảo database đã có dữ liệu hoặc index đã chạy.</small>
        <hr /><h3>Phiên hiện tại</h3><code>{sessionId}</code><p>Thời gian gần nhất: {elapsed == null ? '—' : `${(elapsed / 1000).toFixed(1)} giây`}</p>
        <button type="button" onClick={exportResult} disabled={!messages.length}>Xuất kết quả JSON</button><small>File xuất chứa nội dung chat cá nhân. Chỉ chia sẻ dữ liệu kiểm thử.</small>
        <details><summary>Response JSON gần nhất</summary><pre>{lastResult ? JSON.stringify(lastResult, null, 2) : 'Chưa có response.'}</pre></details>
        <p className="lab-footnote">Reload khôi phục tối đa 100 message văn bản. Card/nguồn/JSON chỉ giữ trong lần mở trang này. Restart server sẽ mất lịch sử và feedback.</p>
      </aside>
    </div>
  </main>;
}
