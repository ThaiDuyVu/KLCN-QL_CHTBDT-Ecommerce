// Pure helpers for the customer assistant page; no network or React state here.

// Qwen and backend retrieval can legitimately exceed apiClient's 15s default.
export const CHAT_TIMEOUT_MS = 180000;
export const MAX_MESSAGE_LENGTH = 4000; // SendMessageRequest.message max_length
export const SESSION_PAGE_SIZE = 20; // chatbotApi.sessions page size
export const HISTORY_PAGE_SIZE = 100; // history_page default size

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const storageKey = (userId) => `storefront-chatbot.session.v1.${userId}`;

// Only the selected session ID is kept per browser tab and user; transcripts are reread from the API.
export function storedSessionId(userId) {
  try {
    const value = sessionStorage.getItem(storageKey(userId));
    return UUID.test(value || '') ? value : null;
  } catch {
    return null;
  }
}

export function rememberSessionId(userId, sessionId) {
  try { sessionStorage.setItem(storageKey(userId), sessionId); } catch { /* Storage may be disabled; state stays in memory. */ }
}

export function safeHttpUrl(value) {
  try {
    const url = new URL(value);
    return ['http:', 'https:'].includes(url.protocol) ? url.href : null;
  } catch {
    return null;
  }
}

// Product images are served by the shop backend as root-relative paths or absolute http(s) URLs.
export function safeImageUrl(value) {
  if (typeof value !== 'string' || !value) return null;
  if (value.startsWith('/') && !value.startsWith('//')) return value;
  return safeHttpUrl(value);
}

const dateTime = new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
const clock = new Intl.DateTimeFormat('vi-VN', { hour: '2-digit', minute: '2-digit' });

function valid(value) {
  const date = value ? new Date(value) : null;
  return date && !Number.isNaN(date.getTime()) ? date : null;
}

// Sessions have no stored title; label them by their real creation time only.
export function sessionLabel(session) {
  const created = valid(session?.created_at);
  return created ? `Cuộc trò chuyện · ${dateTime.format(created)}` : 'Cuộc trò chuyện';
}

export function messageTime(value) {
  const date = valid(value);
  return date ? clock.format(date) : '';
}

export function errorFor(failure, { timedOut = false } = {}) {
  if (timedOut) return { kind: 'timeout', text: 'Trợ lý cần nhiều thời gian hơn dự kiến. Câu trả lời có thể vẫn đang được xử lý; hãy đồng bộ lịch sử trước khi gửi lại.' };
  const status = failure?.status;
  if (!status) return { kind: 'network', text: 'Mất kết nối tới máy chủ. Câu hỏi có thể đã được gửi; hãy đồng bộ lịch sử trước khi gửi lại.' };
  if (status === 401) return { kind: 'auth', text: 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại để tiếp tục trò chuyện.' };
  if (status === 403) return { kind: 'forbidden', text: 'Tài khoản của bạn không có quyền truy cập cuộc trò chuyện này.' };
  if (status === 404) return { kind: 'missing', text: 'Cuộc trò chuyện không tồn tại hoặc không còn truy cập được.' };
  if (status === 409) return { kind: 'conflict', text: 'Cuộc trò chuyện đang xử lý một câu hỏi khác hoặc đã kết thúc. Hãy đồng bộ lịch sử rồi thử lại.' };
  if (status === 400 || status === 422) return { kind: 'invalid', text: `Câu hỏi chưa hợp lệ. Vui lòng nhập tối đa ${MAX_MESSAGE_LENGTH} ký tự.` };
  // Upstream detail texts are operator-facing; never tell the customer an answer exists.
  return { kind: 'unavailable', text: 'Trợ lý AI tạm thời chưa khả dụng. Câu hỏi này chưa được trả lời; vui lòng thử lại sau.' };
}

// History is text-only. Reattach cards/sources/comparison only to the same assistant text seen live in this page.
export function mergeHistory(rows, current = []) {
  const rich = current.filter((item) => item.role === 'ASSISTANT' && (item.products?.length || item.sources?.length || item.suggestions?.length || item.comparison));
  return rows.map((row) => {
    const index = row.sender_type === 'ASSISTANT' ? rich.findIndex((item) => item.text === row.message) : -1;
    const extra = index >= 0 ? rich.splice(index, 1)[0] : null;
    return { id: row.id, serverId: row.id, role: row.sender_type, text: row.message, createdAt: row.created_at,
      products: extra?.products || [], sources: extra?.sources || [], suggestions: extra?.suggestions || [],
      comparison: extra?.comparison || null };
  });
}

// After an ambiguous failure, the turn is complete only if history ends with this question and an answer.
export function answeredInHistory(rows, question) {
  const [user, assistant] = rows.slice(-2);
  return rows.length >= 2 && user.sender_type === 'USER' && user.message === question && assistant.sender_type === 'ASSISTANT';
}
