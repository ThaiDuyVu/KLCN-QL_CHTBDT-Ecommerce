import { apiClient } from '../../api/apiClient';

const base = '/v1/chat';
export const chatbotApi = {
  status: () => apiClient(`${base}/status`),
  send: (sessionId, message, signal) => apiClient(`${base}/sessions/${sessionId}/messages`, {
    method: 'POST', body: { message }, signal,
  }),
  history: (sessionId) => apiClient(`${base}/sessions/${sessionId}/messages`),
  rate: (sessionId, messageId, rating) => apiClient(`${base}/sessions/${sessionId}/messages/${messageId}/feedback`, {
    method: 'POST', body: { rating },
  }),
};
