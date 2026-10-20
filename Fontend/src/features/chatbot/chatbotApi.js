import { apiClient } from '../../api/apiClient';

const base = '/v1/chat';
export const chatbotApi = {
  sessions: (page = 0) => apiClient(`${base}/sessions?page=${page}&size=20`),
  session: (sessionId) => apiClient(`${base}/sessions/${sessionId}`),
  close: (sessionId) => apiClient(`${base}/sessions/${sessionId}/close`, { method: 'POST' }),
  status: () => apiClient(`${base}/status`),
  send: (sessionId, message, signal, traceId) => apiClient(`${base}/sessions/${sessionId}/messages`, {
    method: 'POST', body: { message }, signal, headers: traceId ? { 'X-Chat-Trace-Id': traceId } : undefined,
  }),
  traces: (sessionId) => apiClient(`${base}/sessions/${sessionId}/traces`),
  trace: (sessionId, traceId) => apiClient(`${base}/sessions/${sessionId}/traces/${traceId}`),
  history: (sessionId, before) => apiClient(`${base}/sessions/${sessionId}/messages${before ? `?before=${encodeURIComponent(before)}` : ''}`),
  rate: (sessionId, messageId, rating) => apiClient(`${base}/sessions/${sessionId}/messages/${messageId}/feedback`, {
    method: 'POST', body: { rating },
  }),
};
