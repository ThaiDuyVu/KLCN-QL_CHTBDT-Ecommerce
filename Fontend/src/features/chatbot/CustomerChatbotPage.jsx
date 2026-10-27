import { useEffect, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router';
import { useAuth } from '../../hooks/useAuth';
import { chatbotApi } from './chatbotApi';
import { MessageBubble } from './CustomerChatParts';
import {
  CHAT_TIMEOUT_MS, HISTORY_PAGE_SIZE, MAX_MESSAGE_LENGTH, SESSION_PAGE_SIZE, answeredInHistory, errorFor,
  mergeHistory, rememberSessionId, sessionLabel, storedSessionId,
} from './customerChat';
import './CustomerChatbotPage.css';

// Starter prompts are UI shortcuts; they fill the input and are not backend suggestions.
const STARTERS = ['Tìm laptop dưới 20 triệu.', 'Tư vấn điện thoại RAM 8GB.', 'Tìm sản phẩm phù hợp cho học tập.', 'Đơn hàng gần đây của tôi.', 'Giỏ hàng của tôi.'];

function mergeSessions(current, rows) {
  return [...current, ...rows.filter((row) => !current.some((item) => item.id === row.id))];
}

function ChatWorkspace({ user }) {
  const location = useLocation();
  // Pure initializer (StrictMode may call it twice). IDs generated here and not yet sent cannot
  // exist on the server, so their 404 lookups are skipped.
  const [initial] = useState(() => {
    const stored = storedSessionId(user.userId);
    return stored ? { id: stored, fresh: false } : { id: crypto.randomUUID(), fresh: true };
  });
  const fresh = useRef(initial.fresh ? initial.id : null);
  const [sessionId, setSessionId] = useState(initial.id);
  const [session, setSession] = useState(null); // server record; null until the first message creates it
  const [sessions, setSessions] = useState([]);
  const [sessionPage, setSessionPage] = useState(0);
  const [moreSessions, setMoreSessions] = useState(false);
  const [sessionsError, setSessionsError] = useState(null);
  const [loadingSessions, setLoadingSessions] = useState(true);
  const [messages, setMessages] = useState([]);
  const [hasOlder, setHasOlder] = useState(false);
  const [loading, setLoading] = useState(true);
  const [draft, setDraft] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState('');
  const [needsSync, setNeedsSync] = useState(false);
  const [confirmClose, setConfirmClose] = useState(false);
  const [closing, setClosing] = useState(false);
  const [listOpen, setListOpen] = useState(false);
  const version = useRef(0);
  const inFlight = useRef(null);
  const input = useRef(null);
  const end = useRef(null);
  const closed = session?.is_active === false;

  useEffect(() => { rememberSessionId(user.userId, sessionId); }, [user.userId, sessionId]);

  useEffect(() => {
    let active = true;
    chatbotApi.sessions(0).then((rows) => {
      if (!active) return;
      setSessions(rows); setSessionPage(0); setMoreSessions(rows.length === SESSION_PAGE_SIZE);
    }).catch((failure) => { if (active) setSessionsError(errorFor(failure)); })
      .finally(() => { if (active) setLoadingSessions(false); });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    const token = version.current;
    const current = () => token === version.current;
    if (fresh.current === sessionId) {
      Promise.resolve().then(() => { if (current()) setLoading(false); });
      return () => { version.current += 1; };
    }
    Promise.allSettled([chatbotApi.session(sessionId), chatbotApi.history(sessionId)]).then(([record, history]) => {
      if (!current()) return;
      // 404 is the normal state of a conversation that has not received its first message yet.
      if (record.status === 'fulfilled') setSession(record.value);
      else if (record.reason?.status === 404) setSession(null);
      else setError(errorFor(record.reason));
      if (history.status === 'fulfilled') {
        setMessages(mergeHistory(history.value)); setHasOlder(history.value.length === HISTORY_PAGE_SIZE);
      } else if (history.reason?.status !== 404) setError(errorFor(history.reason));
      setLoading(false);
    });
    // A switched-away request keeps running on the server; only its UI update is discarded.
    return () => { version.current += 1; };
  }, [sessionId]);

  useEffect(() => { end.current?.scrollIntoView?.({ behavior: 'smooth', block: 'nearest' }); }, [messages, busy]);

  function startNew() {
    const id = crypto.randomUUID(); fresh.current = id; switchTo(id);
  }

  function switchTo(id, record = null) {
    version.current += 1; inFlight.current = null;
    setSessionId(id); setSession(record); setMessages([]); setHasOlder(false); setLoading(true);
    setBusy(false); setError(null); setNotice(''); setNeedsSync(false); setConfirmClose(false); setClosing(false); setListOpen(false);
  }

  function refreshSessions() {
    const token = version.current;
    chatbotApi.sessions(0).then((rows) => {
      if (token !== version.current) return;
      setSessions((items) => mergeSessions(rows, items)); setSessionsError(null);
    }).catch(() => { /* The list refresh is cosmetic; the conversation itself already succeeded. */ });
  }

  async function send(text = draft) {
    const question = text.trim();
    if (!question || question.length > MAX_MESSAGE_LENGTH || busy || loading || closed || needsSync || inFlight.current !== null) return;
    const token = version.current; const sid = sessionId; const pendingId = crypto.randomUUID();
    inFlight.current = token;
    if (fresh.current === sid) fresh.current = null; // the POST may create it from here on
    setBusy(true); setError(null); setNotice(''); setDraft('');
    setMessages((items) => [...items, { id: pendingId, role: 'USER', text: question, createdAt: new Date().toISOString(), pending: true }]);
    const abort = new AbortController(); let timedOut = false;
    const timer = setTimeout(() => { timedOut = true; abort.abort(); }, CHAT_TIMEOUT_MS);
    try {
      const result = await chatbotApi.send(sid, question, abort.signal);
      if (token !== version.current) return;
      setMessages((items) => [...items.map((item) => item.id === pendingId ? { ...item, pending: false } : item), {
        id: crypto.randomUUID(), role: 'ASSISTANT', text: result.message, createdAt: new Date().toISOString(),
        products: Array.isArray(result.products) ? result.products : [], sources: Array.isArray(result.sources) ? result.sources : [],
        suggestions: Array.isArray(result.suggested_questions) ? result.suggested_questions : [],
        comparison: result.comparison && Array.isArray(result.comparison.rows) ? result.comparison : null,
      }]);
      if (!session) chatbotApi.session(sid).then((record) => { if (token === version.current) setSession(record); }).catch(() => {});
      refreshSessions();
    } catch (failure) {
      if (token !== version.current) return;
      const ambiguous = timedOut || !failure?.status;
      setError(errorFor(failure, { timedOut })); setDraft(question);
      // The POST may have been processed. Reconcile from history; never replay the request automatically.
      try {
        const rows = await chatbotApi.history(sid);
        if (token !== version.current) return;
        const completed = ambiguous && answeredInHistory(rows, question);
        setMessages((items) => mergeHistory(rows, items)); setHasOlder(rows.length === HISTORY_PAGE_SIZE);
        setNeedsSync(ambiguous && !completed);
        if (completed) { setError(null); setDraft(''); setNotice('Câu trả lời đã được lưu trên máy chủ và hiển thị theo lịch sử bên dưới.'); }
      } catch (historyFailure) {
        if (token !== version.current) return;
        // A returned HTTP error plus 404 history means the conversation was never created.
        if (historyFailure?.status === 404 && !ambiguous) setMessages((items) => items.filter((item) => item.id !== pendingId));
        else setNeedsSync(true);
      }
      if (failure?.status === 409) chatbotApi.session(sid).then((record) => { if (token === version.current) setSession(record); }).catch(() => {});
    } finally {
      clearTimeout(timer);
      if (inFlight.current === token) inFlight.current = null;
      if (token === version.current) setBusy(false);
    }
  }

  async function syncHistory() {
    const token = version.current;
    setLoading(true); setError(null);
    try {
      const [rows, record] = await Promise.all([chatbotApi.history(sessionId), chatbotApi.session(sessionId).catch(() => session)]);
      if (token !== version.current) return;
      setMessages((items) => mergeHistory(rows, items)); setHasOlder(rows.length === HISTORY_PAGE_SIZE); setSession(record); setNeedsSync(false);
    } catch (failure) {
      if (token !== version.current) return;
      if (failure?.status === 404) { setMessages([]); setNeedsSync(false); } else setError(errorFor(failure));
    } finally { if (token === version.current) setLoading(false); }
  }

  async function olderHistory() {
    const cursor = messages.find((item) => item.serverId)?.serverId;
    if (!cursor || loading || busy) return;
    const token = version.current;
    setLoading(true); setError(null);
    try {
      const rows = await chatbotApi.history(sessionId, cursor);
      if (token !== version.current) return;
      setMessages((items) => [...mergeHistory(rows).filter((row) => !items.some((item) => item.id === row.id)), ...items]);
      setHasOlder(rows.length === HISTORY_PAGE_SIZE);
    } catch (failure) { if (token === version.current) setError(errorFor(failure)); }
    finally { if (token === version.current) setLoading(false); }
  }

  async function olderSessions() {
    if (loadingSessions) return;
    const next = sessionPage + 1;
    setLoadingSessions(true);
    try {
      const rows = await chatbotApi.sessions(next);
      setSessions((items) => mergeSessions(items, rows)); setSessionPage(next); setMoreSessions(rows.length === SESSION_PAGE_SIZE); setSessionsError(null);
    } catch (failure) { setSessionsError(errorFor(failure)); }
    finally { setLoadingSessions(false); }
  }

  async function closeSession() {
    const token = version.current;
    setClosing(true); setError(null);
    try {
      const record = await chatbotApi.close(sessionId);
      if (token !== version.current) return;
      setSession(record); setConfirmClose(false);
      setSessions((items) => items.map((item) => item.id === record.id ? record : item));
    } catch (failure) { if (token === version.current) setError(errorFor(failure)); }
    finally { if (token === version.current) setClosing(false); }
  }

  function applySuggestion(question) {
    setDraft(question); input.current?.focus();
  }

  function onKeyDown(event) {
    // Enter sends, Shift+Enter adds a line; IME composition (Vietnamese Telex/VNI) must not submit.
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault(); send();
    }
  }

  const inList = sessions.some((item) => item.id === sessionId);
  const composerLocked = busy || loading || closed || needsSync;
  const tooLong = draft.trim().length > MAX_MESSAGE_LENGTH;

  return <div className="cchat-page">
    <div className="cchat-hero">
      <p className="eyebrow">Trợ lý AI</p>
      <h1>Trợ lý mua sắm Điện Việt</h1>
      <p>Hỗ trợ tìm thiết bị phù hợp, tham khảo sản phẩm và tra cứu thông tin tài khoản của bạn.</p>
    </div>
    <div className="cchat-layout">
      <aside className={`cchat-sidebar${listOpen ? ' is-open' : ''}`} aria-label="Các cuộc trò chuyện">
        <div className="cchat-sidebar-actions">
          <button type="button" className="button" onClick={startNew} disabled={busy}>Cuộc trò chuyện mới</button>
          <button type="button" className="button button-quiet cchat-list-toggle" aria-expanded={listOpen} aria-controls="cchat-session-list" onClick={() => setListOpen((open) => !open)}>
            {listOpen ? 'Ẩn danh sách' : 'Danh sách trò chuyện'}
          </button>
        </div>
        <div id="cchat-session-list" className="cchat-session-list">
          {sessionsError && <p className="cchat-inline-error" role="alert">{sessionsError.text}</p>}
          <ul>
            {!inList && <li><button type="button" className="cchat-session is-active" aria-current="true" disabled>
              <span>Cuộc trò chuyện mới</span><small>{session ? (closed ? 'Đã kết thúc' : 'Đang mở') : 'Chưa gửi tin nhắn'}</small></button></li>}
            {sessions.map((item) => <li key={item.id}>
              <button type="button" className={`cchat-session${item.id === sessionId ? ' is-active' : ''}`} aria-current={item.id === sessionId ? 'true' : undefined}
                disabled={item.id === sessionId} onClick={() => switchTo(item.id, item)}>
                <span>{sessionLabel(item)}</span><small>{item.is_active ? 'Đang mở' : 'Đã kết thúc'}</small>
              </button>
            </li>)}
          </ul>
          {loadingSessions && <p role="status" className="cchat-muted">Đang tải danh sách…</p>}
          {!loadingSessions && !sessions.length && !sessionsError && <p className="cchat-muted">Chưa có cuộc trò chuyện nào được lưu.</p>}
          {moreSessions && <button type="button" className="button button-quiet cchat-more" onClick={olderSessions} disabled={loadingSessions}>Tải thêm cuộc trò chuyện</button>}
        </div>
      </aside>

      <section className="cchat-main" aria-labelledby="cchat-title">
        <div className="cchat-main-heading">
          <div><h2 id="cchat-title">{inList ? sessionLabel(sessions.find((item) => item.id === sessionId)) : 'Cuộc trò chuyện mới'}</h2>
            <small>{closed ? 'Đã kết thúc · chỉ xem lại lịch sử' : session ? 'Đang mở' : 'Cuộc trò chuyện được lưu khi bạn gửi tin nhắn đầu tiên'}</small></div>
          {session && !closed && (confirmClose
            ? <div className="cchat-confirm" role="group" aria-label="Xác nhận kết thúc cuộc trò chuyện">
              <span>Kết thúc cuộc trò chuyện này?</span>
              <button type="button" className="button" onClick={closeSession} disabled={closing || busy}>{closing ? 'Đang kết thúc…' : 'Kết thúc'}</button>
              <button type="button" className="button button-quiet" onClick={() => setConfirmClose(false)} disabled={closing}>Giữ lại</button>
            </div>
            : <button type="button" className="button button-quiet" onClick={() => setConfirmClose(true)} disabled={busy || loading}>Kết thúc cuộc trò chuyện</button>)}
        </div>

        <div className="cchat-transcript" role="log" aria-live="polite" aria-relevant="additions" aria-busy={busy || loading}>
          {hasOlder && <button type="button" className="button button-quiet cchat-more" onClick={olderHistory} disabled={loading || busy}>Xem tin nhắn cũ hơn</button>}
          {loading && !messages.length && <p role="status" className="cchat-muted">Đang tải lịch sử trò chuyện…</p>}
          {!loading && !messages.length && <div className="cchat-empty">
            <h3>Bạn cần tư vấn gì hôm nay?</h3>
            <p>Hỏi về sản phẩm, ngân sách, cấu hình hoặc tra cứu đơn hàng, giỏ hàng của bạn. Trợ lý chỉ tư vấn và tra cứu, không đặt hàng hay thanh toán thay bạn.</p>
            <p className="cchat-muted">Bạn có thể bắt đầu với:</p>
            <div className="cchat-suggestions">{STARTERS.map((question) => <button type="button" key={question} disabled={composerLocked} onClick={() => applySuggestion(question)}>{question}</button>)}</div>
          </div>}
          {messages.map((item) => <MessageBubble key={item.id} item={item} onSuggestion={applySuggestion} suggestionsDisabled={composerLocked} />)}
          {busy && <p role="status" className="cchat-working">Đang trả lời…</p>}
          <div ref={end} />
        </div>

        {notice && <p className="cchat-notice" role="status">{notice}</p>}
        {error && <div className="cchat-error" role="alert"><p>{error.text}</p>
          {error.kind === 'auth' && <Link className="button button-quiet" to="/login" state={{ from: location }}>Đăng nhập lại</Link>}</div>}
        {needsSync && <div className="cchat-sync"><p>Chưa xác minh được câu hỏi vừa gửi. Đồng bộ lịch sử trước khi gửi lại để tránh hỏi trùng.</p>
          <button type="button" className="button" onClick={syncHistory} disabled={loading}>Đồng bộ lịch sử</button></div>}
        {closed && <div className="cchat-closed"><p>Cuộc trò chuyện này đã kết thúc. Bạn vẫn có thể đọc lại lịch sử.</p>
          <button type="button" className="button" onClick={startNew}>Bắt đầu cuộc trò chuyện mới</button></div>}

        <form className="cchat-composer" onSubmit={(event) => { event.preventDefault(); send(); }}>
          <label htmlFor="cchat-input" className="sr-only">Câu hỏi cho Trợ lý AI</label>
          <textarea id="cchat-input" ref={input} value={draft} rows={2} maxLength={MAX_MESSAGE_LENGTH}
            placeholder={closed ? 'Cuộc trò chuyện đã kết thúc' : 'Nhập câu hỏi của bạn…'} aria-describedby="cchat-input-help"
            disabled={closed} onChange={(event) => setDraft(event.target.value)} onKeyDown={onKeyDown} />
          <div className="cchat-composer-row">
            <small id="cchat-input-help">{draft.length}/{MAX_MESSAGE_LENGTH} · Enter để gửi, Shift+Enter để xuống dòng</small>
            <button type="submit" className="button" disabled={composerLocked || !draft.trim() || tooLong}>{busy ? 'Đang trả lời…' : 'Gửi'}</button>
          </div>
        </form>
        <p className="cchat-disclaimer">Giá và tồn kho trong câu trả lời được lấy từ hệ thống tại thời điểm hỏi và có thể khác chi nhánh bạn đang chọn. Vui lòng kiểm tra lại ở trang chi tiết sản phẩm trước khi mua.</p>
      </section>
    </div>
  </div>;
}

export default function CustomerChatbotPage() {
  const { user } = useAuth();
  // Keyed by user: switching accounts discards every in-memory session and transcript.
  return <ChatWorkspace key={user.userId} user={user} />;
}
