import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import { useAuth } from '../../hooks/useAuth';
import { chatbotApi } from './chatbotApi';
import { scenarios } from './scenarios';
import './chatbot-lab.css';
import TracePanel from './TracePanel';

const money = (value) => value == null ? 'Chưa có dữ liệu' : new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(Number(value));
function sessionFor(userId) {
  const key = `chatbot-lab.session.v2.${userId}`;
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

function restoredMessages(rows, current = []) {
  const known = new Map(current.map((m) => [m.id, m]));
  return rows.map((m) => ({ ...known.get(m.id), id: m.id, role: m.sender_type, text: m.message, rating: m.rating }));
}

export default function ChatbotLabPage() {
  const { user } = useAuth();
  const [sessionId, setSessionId] = useState(() => sessionFor(user.userId));
  const [traceId, setTraceId] = useState(null);
  const [runtime, setRuntime] = useState(null);
  const [statusError, setStatusError] = useState('');
  const [currentSession, setCurrentSession] = useState(null);
  const [sessions, setSessions] = useState([]);
  const [sessionPage, setSessionPage] = useState(0);
  const [hasOlder, setHasOlder] = useState(false);
  const [closed, setClosed] = useState(false);
  const [messages, setMessages] = useState([]);
  const [draft, setDraft] = useState('');
  const [selected, setSelected] = useState(scenarios[0]);
  const [busy, setBusy] = useState(false);
  const [restoring, setRestoring] = useState(true);
  const [error, setError] = useState('');
  const [lastResult, setLastResult] = useState(null);
  const [elapsed, setElapsed] = useState(null);
  const [feedbackBusy, setFeedbackBusy] = useState(false);
  const [needsSync, setNeedsSync] = useState(false);
  const [closing, setClosing] = useState(false);
  const [loadingSessions, setLoadingSessions] = useState(false);
  const version = useRef(0);
  const sessionLoad = useRef(false);
  const controller = useRef(null);
  const end = useRef(null);

  useEffect(() => {
    let active = true;
    const token = version.current;
    const current = () => active && version.current === token;
    chatbotApi.status().then((data) => { if (current()) setRuntime(data); })
      .catch((failure) => { if (current()) setStatusError(errorText(failure)); });
    chatbotApi.sessions().then((rows) => { if (current()) { setSessions(rows); setSessionPage(0); } })
      .catch((failure) => { if (current()) setError(errorText(failure)); });
    chatbotApi.session(sessionId).then((session) => { if (current()) { setClosed(!session.is_active); setCurrentSession(session); } })
      .catch((failure) => { if (current()) { if (failure.status === 404) setCurrentSession(null); else setError(errorText(failure)); } });
    chatbotApi.history(sessionId).then((history) => {
      if (current()) { setMessages(restoredMessages(history)); setHasOlder(history.length === 100); }
    }).catch((failure) => { if (current() && failure.status !== 404) setError(errorText(failure)); })
      .finally(() => { if (current()) setRestoring(false); });
    return () => { active = false; version.current += 1; controller.current?.abort(); };
  }, [sessionId]);
  useEffect(() => { end.current?.scrollIntoView?.({ behavior: 'smooth', block: 'nearest' }); }, [messages, busy]);

  async function refreshStatus() {
    setStatusError('');
    try { setRuntime(await chatbotApi.status()); } catch (failure) { setStatusError(errorText(failure)); }
  }
  async function send(event) {
    event.preventDefault();
    const question = draft.trim();
    if (!question || busy || restoring || closed || needsSync || controller.current) return;
    const token = version.current;
    setBusy(true); setError(''); setDraft(''); setElapsed(null);
    setMessages((items) => [...items, { id: crypto.randomUUID(), role: 'USER', text: question }]);
    const requestTraceId = runtime?.trace_enabled ? crypto.randomUUID() : null;
    setTraceId(requestTraceId);
    const started = performance.now();
    const abort = new AbortController(); controller.current = abort;
    const timer = setTimeout(() => abort.abort(), 180000);
    try {
      const result = await chatbotApi.send(sessionId, question, abort.signal, requestTraceId);
      clearTimeout(timer);
      if (abort.signal.aborted || token !== version.current) return;
      const duration = Math.round(performance.now() - started);
      setElapsed(duration); setLastResult(result);
      chatbotApi.sessions().then((rows) => { if (token === version.current) { setSessions(rows); setSessionPage(0); } }).catch(() => {});
      let saved = null;
      try {
        const history = await chatbotApi.history(sessionId);
        saved = history.filter((m) => m.sender_type === 'ASSISTANT' && m.message === result.message).at(-1);
      } catch { /* Keep acknowledged responses even if reading history fails. */ }
      if (abort.signal.aborted || token !== version.current) return;
      setMessages((items) => [...items, { id: saved?.id || crypto.randomUUID(), role: 'ASSISTANT', text: result.message,
        response: result, feedbackId: saved?.id, rating: saved?.rating || 0, duration }]);
    } catch (failure) {
      if (token !== version.current) return;
      setError(errorText(failure)); setDraft(question); setNeedsSync(true);
      // Reconcile the optimistic row from the existing history contract before retry.
      // An audit USER may remain, but we never manufacture an ASSISTANT or replay POST.
      try {
        const history = await chatbotApi.history(sessionId);
        if (token !== version.current) return;
        setMessages((items) => restoredMessages(history, items)); setHasOlder(history.length === 100);
        setNeedsSync(abort.signal.aborted);
      } catch (historyFailure) {
        // A returned HTTP error followed by 404 means this session was not created.
        // Abort/network ambiguity and unavailable history still require explicit sync.
        if (token === version.current && historyFailure.status === 404 && failure.status >= 400 && !abort.signal.aborted) {
          setMessages([]); setHasOlder(false); setNeedsSync(false);
        }
      }
    } finally {
      clearTimeout(timer);
      if (token === version.current) { controller.current = null; setBusy(false); }
    }
  }
  function resetPending() {
    version.current += 1;
    controller.current?.abort(); controller.current = null;
    sessionLoad.current = false;
    setBusy(false); setFeedbackBusy(false); setClosing(false); setLoadingSessions(false); setNeedsSync(false); setHasOlder(false);
  }
  function newSession() {
    resetPending();
    const id = crypto.randomUUID();
    try { sessionStorage.setItem(`chatbot-lab.session.v2.${user.userId}`, id); } catch { /* No storage. */ }
    setCurrentSession(null); setClosed(false); setTraceId(null); setSessionId(id); setMessages([]); setLastResult(null); setError(''); setElapsed(null); setRestoring(true);
  }
  function selectSession(id) {
    resetPending();
    try { sessionStorage.setItem(`chatbot-lab.session.v2.${user.userId}`, id); } catch { /* No storage. */ }
    setSessionId(id); setTraceId(null); setMessages([]); setLastResult(null); setElapsed(null); setError(''); setRestoring(true);
    setCurrentSession(sessions.find((s) => s.id === id) || null);
    setClosed(sessions.find((s) => s.id === id)?.is_active === false);
  }
  async function reloadHistory() {
    const token = version.current;
    setRestoring(true); setError('');
    try {
      const rows = await chatbotApi.history(sessionId);
      if (token !== version.current) return;
      setMessages((items) => restoredMessages(rows, items)); setHasOlder(rows.length === 100); setNeedsSync(false);
    } catch (failure) { if (token === version.current) setError(errorText(failure)); }
    finally { if (token === version.current) setRestoring(false); }
  }
  async function olderHistory() {
    if (!messages.length || busy || restoring) return;
    const token = version.current;
    setRestoring(true); setError('');
    try {
      const rows = await chatbotApi.history(sessionId, messages[0].id);
      if (token !== version.current) return;
      setMessages((items) => [...restoredMessages(rows).filter((m) => !items.some((i) => i.id === m.id)), ...items]);
      setHasOlder(rows.length === 100);
    } catch (failure) { if (token === version.current) setError(errorText(failure)); }
    finally { if (token === version.current) setRestoring(false); }
  }
  async function olderSessions() {
    if (sessionLoad.current) return;
    const token = version.current; const nextPage = sessionPage + 1;
    sessionLoad.current = true; setLoadingSessions(true); setError('');
    try {
      const rows = await chatbotApi.sessions(nextPage);
      if (token !== version.current) return;
      setSessions((items) => [...items, ...rows.filter((r) => !items.some((i) => i.id === r.id))]);
      setSessionPage(nextPage);
    } catch (failure) { if (token === version.current) setError(errorText(failure)); }
    finally { if (token === version.current) { sessionLoad.current = false; setLoadingSessions(false); } }
  }
  async function closeSession() {
    const token = version.current;
    setClosing(true); setError('');
    try {
      const result = await chatbotApi.close(sessionId);
      if (token !== version.current) return;
      setClosed(true); setCurrentSession((session) => ({ ...session, ...result }));
      const rows = await chatbotApi.sessions();
      if (token === version.current) { setSessions(rows); setSessionPage(0); }
    } catch (failure) { if (token === version.current) setError(errorText(failure)); }
    finally { if (token === version.current) setClosing(false); }
  }
  async function rate(item, rating) {
    const token = version.current;
    setFeedbackBusy(true); setError('');
    try {
      await chatbotApi.rate(sessionId, item.feedbackId || item.id, rating);
      if (token === version.current) setMessages((items) => items.map((m) => m.id === item.id ? { ...m, rating } : m));
    } catch (failure) { if (token === version.current) setError(errorText(failure)); }
    finally { if (token === version.current) setFeedbackBusy(false); }
  }
  function exportResult() {
    const blob = new Blob([JSON.stringify({ exported_at: new Date().toISOString(), session_id: sessionId, runtime, scenario: selected, messages }, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob); const link = document.createElement('a');
    link.href = url; link.download = `chatbot-lab-${sessionId}.json`; link.click(); URL.revokeObjectURL(url);
  }

  const displayedSessions = currentSession && !sessions.some((s) => s.id === currentSession.id) ? [currentSession, ...sessions] : sessions;

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
        <div className="lab-chat-heading"><div><h2>Cuộc trò chuyện</h2><small>{runtime?.conversation_storage === 'postgres' ? 'Lịch sử lưu trong PostgreSQL' : runtime?.conversation_storage === 'memory' ? 'Lịch sử lưu trong RAM của server' : 'Đang xác minh chế độ lưu lịch sử'}</small></div>
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
        {needsSync && <p>Chưa xác minh lượt gửi vừa rồi. Đọc lại lịch sử trước khi gửi lại.</p>}
        <button type="button" onClick={reloadHistory} disabled={busy || restoring}>Đọc lại lịch sử</button>
        <form onSubmit={send} className="lab-composer"><label htmlFor="chat-question">Câu hỏi của bạn</label>
          <textarea id="chat-question" value={draft} onChange={(event) => setDraft(event.target.value)} maxLength={4000} rows={3} placeholder="Nhập câu hỏi cần kiểm chứng…" disabled={busy || restoring || closed || needsSync} />
          <div><small>{draft.length}/4000 ký tự · Enter xuống dòng</small><button type="submit" disabled={busy || restoring || closed || needsSync || !draft.trim()}>{busy ? 'Đang chờ…' : 'Gửi câu hỏi'}</button></div>
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
        <hr /><label htmlFor="lab-session-history">Lịch sử phiên</label>
        <select id="lab-session-history" value={sessionId} disabled={busy || restoring} onChange={(event) => selectSession(event.target.value)}>
          {!displayedSessions.some((s) => s.id === sessionId) && <option value={sessionId}>Phiên mới</option>}
          {displayedSessions.map((s) => <option key={s.id} value={s.id}>{new Date(s.created_at).toLocaleString('vi-VN')} · {s.is_active ? 'Đang mở' : 'Đã đóng'}</option>)}
        </select>
        {closed && <p>Phiên đã đóng. Bạn có thể đọc lịch sử hoặc tạo phiên mới.</p>}
        <button type="button" onClick={closeSession} disabled={busy || restoring || closed || closing || !displayedSessions.some((s) => s.id === sessionId)}>Đóng phiên</button>
        {sessions.length >= (sessionPage + 1) * 20 && <button type="button" onClick={olderSessions} disabled={busy || restoring || loadingSessions}>Tải thêm phiên cũ</button>}
        {hasOlder && <button type="button" onClick={olderHistory} disabled={busy || restoring}>Tải tin nhắn cũ hơn</button>}
        <h3>Phiên hiện tại</h3><code>{sessionId}</code><p>Thời gian gần nhất: {elapsed == null ? '—' : `${(elapsed / 1000).toFixed(1)} giây`}</p>
        <button type="button" onClick={exportResult} disabled={!messages.length}>Xuất kết quả JSON</button><small>File xuất chứa nội dung chat cá nhân. Chỉ chia sẻ dữ liệu kiểm thử.</small>
        <details><summary>Response JSON gần nhất</summary><pre>{lastResult ? JSON.stringify(lastResult, null, 2) : 'Chưa có response.'}</pre></details>
        <p className="lab-footnote">{runtime?.conversation_storage === 'postgres' ? 'Lịch sử văn bản và tham chiếu sản phẩm được lưu qua restart. Khôi phục tối đa 100 tin gần nhất; cards/nguồn/JSON của lượt gửi chỉ hiển thị trong lần mở trang này.' : runtime?.conversation_storage === 'memory' ? 'Lịch sử đang lưu trong RAM và mất sau restart.' : 'Chưa xác minh chế độ lưu lịch sử.'} Feedback Đúng/Cần sửa chỉ lưu tạm trong RAM; không khôi phục sau restart.</p>
      </aside>
    </div>
    <TracePanel key={sessionId} sessionId={sessionId} traceId={traceId} onSelect={setTraceId} enabled={runtime?.trace_enabled} busy={busy} />
  </main>;
}
