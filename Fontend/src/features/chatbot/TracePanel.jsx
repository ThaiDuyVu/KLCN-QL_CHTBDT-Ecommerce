import { useEffect, useState } from 'react';
import { chatbotApi } from './chatbotApi';

const labels = {
  'request.accepted': '01 · Câu hỏi nhận vào', 'conversation.context': '02 · Lịch sử đưa vào model',
  'query.understand': '03 · Hiểu truy vấn', 'query.model_attempt': 'Prompt phân loại · từng lần thử',
  'query.validation': 'Kiểm tra QueryPlan', 'query.validation_failed': 'JSON sai schema',
  'query.plan.initial': '04 · QueryPlan do model tạo', 'query.plan.effective': '05 · Bộ lọc sau điều chỉnh',
  'product.search': '06 · Tìm sản phẩm', 'backend.product_http': 'HTTP backend sản phẩm',
  'product.vector_ranking': 'Xếp hạng ngữ nghĩa', 'product.sql': 'SQL xếp hạng',
  'product.cards': 'Chuẩn hóa thẻ sản phẩm', 'customer.context': '07 · Dữ liệu khách hàng',
  'backend.customer_http': 'HTTP backend khách hàng', 'knowledge.retrieve': '08 · Truy xuất tài liệu',
  'knowledge.sql': 'SQL tìm đoạn tài liệu', 'response.grounded_context': '09 · Bằng chứng trước khi trả lời',
  'response.generate': '10 · Tạo câu trả lời', 'response.strategy': 'Nhánh trả lời / prompt cuối',
  'ollama.http': 'Request thật gửi Ollama', 'ollama.result': 'Response thô từ Ollama',
  'ollama.chat': 'Nội dung chat model trả về', 'chat.pipeline': 'Toàn bộ pipeline / response cuối',
};
const statuses = { ok: 'Hoàn tất', warning: 'Cần kiểm tra', error: 'Lỗi', running: 'Đang chạy' };
function Payload({ title, value }) {
  if (value == null) return null;
  return <details className="trace-payload"><summary>{title}</summary><pre>{typeof value === 'string' ? value : JSON.stringify(value, null, 2)}</pre></details>;
}
function promptText(content) {
  try { return JSON.stringify(JSON.parse(content), null, 2); } catch { return content; }
}
function Prompt({ messages }) {
  if (!Array.isArray(messages)) return null;
  return <div className="trace-prompts">{messages.map((message, index) => <details key={index}>
    <summary>Prompt {message.role} · {index + 1}</summary><pre>{promptText(message.content)}</pre>
  </details>)}</div>;
}
export default function TracePanel({ sessionId, traceId, onSelect, enabled, busy }) {
  const [traces, setTraces] = useState([]);
  const [trace, setTrace] = useState(null);
  const [error, setError] = useState('');
  const [query, setQuery] = useState('');
  const [issuesOnly, setIssuesOnly] = useState(false);
  const [refresh, setRefresh] = useState(0);
  useEffect(() => {
    if (!enabled) return;
    let active = true;
    let timer;
    async function load() {
      let running = busy;
      try {
        const items = await chatbotApi.traces(sessionId);
        if (!active) return;
        setTraces(items);
        const id = traceId || items[0]?.trace_id;
        if (id) {
          const data = await chatbotApi.trace(sessionId, id);
          if (!active) return;
          setTrace(data); setError('');
          running ||= data.status === 'running';
        } else { setTrace(null); setError(''); }
      } catch (failure) {
        if (active) { setTrace(null); setError(failure.status === 404 ? 'Trace chưa được tạo hoặc đã hết hạn. Có thể tải lại sau khi gửi câu hỏi.' : 'Không tải được trace. Kiểm tra đăng nhập và dịch vụ.'); }
      } finally {
        if (active && running) timer = setTimeout(load, 1500);
      }
    }
    load();
    return () => { active = false; clearTimeout(timer); };
  }, [sessionId, traceId, enabled, busy, refresh]);
  const current = trace?.trace_id === (traceId || traces[0]?.trace_id) ? trace : null;
  const events = current?.events || [];
  const filtered = events.filter((event) => (!issuesOnly || ['error', 'warning'].includes(event.status)) &&
    `${labels[event.name] || ''} ${event.name} ${JSON.stringify(event.input)} ${JSON.stringify(event.output)}`.toLowerCase().includes(query.toLowerCase()));
  const originalPlan = events.find((event) => event.name === 'query.plan.initial')?.output?.product_search;
  const effectivePlan = events.find((event) => event.name === 'query.plan.effective')?.output?.product_search;
  const changedFields = originalPlan && effectivePlan ? [...new Set([...Object.keys(originalPlan), ...Object.keys(effectivePlan)])].filter((key) => JSON.stringify(originalPlan[key]) !== JSON.stringify(effectivePlan[key])) : [];
  const problems = events.filter((event) => ['error', 'warning'].includes(event.status));
  const firstProblem = problems.find((item) => !problems.some((child) => child.parent_id === item.id)) || problems[0];
  function download() {
    const url = URL.createObjectURL(new Blob([JSON.stringify(current, null, 2)], { type: 'application/json' }));
    const anchor = document.createElement('a'); anchor.href = url; anchor.download = `chat-trace-${current.trace_id}.json`; anchor.click(); URL.revokeObjectURL(url);
  }
  return <section className="lab-panel trace-panel" aria-label="Truy vết xử lý chatbot">
    <div className="trace-heading"><div><span className="lab-eyebrow">PROMPT → QUERY → EVIDENCE → ANSWER</span><h2>Truy vết từng công đoạn</h2>
      <p>Xem đầu vào, biến đổi, dữ liệu thực tế và vị trí fallback của từng lượt chat.</p></div>
      <span className={enabled ? 'lab-good' : 'lab-warn'}>{enabled ? 'Đang bật ghi trace' : 'Chưa bật ghi trace'}</span></div>
    {!enabled ? <p>Bật <code>CHATBOT_TRACE_ENABLED=true</code> với <code>APP_ENV=development</code> trong chatbot-service/.env, khởi động lại chatbot rồi kiểm tra dịch vụ.</p> : <>
      <div className="trace-toolbar"><label>Lượt chat<select aria-label="Chọn trace" value={traceId || traces[0]?.trace_id || ''} onChange={(event) => onSelect(event.target.value)}>
        {!traces.length && <option value="">Chưa có lượt chat</option>}
        {traceId && !traces.some((item) => item.trace_id === traceId) && <option value={traceId}>Đang chờ trace mới…</option>}
        {traces.map((item) => <option key={item.trace_id} value={item.trace_id}>{new Date(item.started_at).toLocaleTimeString('vi-VN')} · {statuses[item.status]} · {item.trace_id.slice(0, 8)}</option>)}
      </select></label>
      <button type="button" onClick={() => setRefresh((n) => n + 1)}>Tải lại trace</button>
      <button type="button" disabled={!current} onClick={download}>Xuất trace JSON</button>
      <label>Tìm trong trace<input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="intent, SKU, prompt, max_price…" /></label>
      <label className="trace-checkbox"><input type="checkbox" checked={issuesOnly} onChange={(event) => setIssuesOnly(event.target.checked)} />Chỉ lỗi / fallback</label></div>
      {error && <p className="lab-warn" role="status">{error}</p>}
      {current && <>
        <div className="trace-summary"><strong>{statuses[current.status]}</strong><span>{current.duration_ms == null ? 'Đang xử lý' : `${(current.duration_ms / 1000).toFixed(2)} giây`}</span><span>{events.length} sự kiện · {problems.length} lỗi/cảnh báo</span><code>{current.trace_id}</code></div>
        {current.dropped_events > 0 && <p role="alert">Trace vượt giới hạn: {current.dropped_events} sự kiện không được lưu.</p>}
        {problems.length > 0 && <p className="lab-warn">Bắt đầu kiểm tra tại: {labels[firstProblem.name] || firstProblem.name}. HTTP 200 vẫn có thể chứa fallback.</p>}
        {changedFields.length > 0 && <div className="trace-plan-diff"><h3>Query đã được điều chỉnh sau khi model trả về</h3><table><thead><tr><th>Trường</th><th>Model tạo</th><th>Dùng để tìm kiếm</th></tr></thead><tbody>{changedFields.map((key) => <tr key={key}><td>{key}</td><td>{JSON.stringify(originalPlan[key]) ?? '—'}</td><td>{JSON.stringify(effectivePlan[key]) ?? '—'}</td></tr>)}</tbody></table></div>}
        <ol className="trace-timeline">{filtered.map((event) => <li className={`trace-event trace-${event.status}`} key={event.id}>
          <details><summary><span className="trace-dot" /><strong>{labels[event.name] || event.name}</strong><span>{statuses[event.status]}</span><small>+{(event.offset_ms / 1000).toFixed(2)}s · {event.duration_ms == null ? 'sự kiện' : `${event.duration_ms.toFixed(1)}ms`}</small></summary>
            <div className="trace-event-body"><code>{event.name}</code>{event.parent_id && <small>Công đoạn cha: {events.find((e) => e.id === event.parent_id)?.name || event.parent_id}</small>}
              <Prompt messages={event.input?.messages || event.output?.messages || event.input?.payload?.messages} />
              <Payload title="Đầu vào / request / query" value={event.input} /><Payload title="Đầu ra / response / lỗi" value={event.output} />
            </div></details>
          <div className="trace-duration" style={{ width: `${Math.max(0.5, Math.min(100, (event.duration_ms || 0) / (current.duration_ms || 1) * 100))}%` }} />
        </li>)}</ol>
        {!filtered.length && <p>Không có sự kiện khớp bộ lọc.</p>}
        <p className="lab-footnote">Prompt hiển thị sau che thông tin nhạy cảm; dữ liệu quá dài có cờ truncated. Trace giữ tối đa 50 lượt trong 1 giờ, mất khi restart. Chỉ các lượt sau khi bật trace mới có dữ liệu. File xuất có thể chứa nội dung chat cá nhân.</p>
      </>}
      {!current && !error && <p>Gửi câu hỏi để xem toàn bộ quá trình xử lý tại đây.</p>}
    </>}
  </section>;
}
