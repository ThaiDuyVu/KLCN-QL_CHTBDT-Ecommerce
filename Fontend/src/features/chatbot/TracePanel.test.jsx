import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, expect, it, vi } from 'vitest';
import TracePanel from './TracePanel';
import { chatbotApi } from './chatbotApi';
vi.mock('./chatbotApi', () => ({ chatbotApi: { traces: vi.fn(), trace: vi.fn() } }));
beforeEach(() => {
  chatbotApi.traces.mockResolvedValue([{ trace_id: 'trace-1', started_at: '2026-10-11T00:00:00Z', status: 'warning' }]);
  chatbotApi.trace.mockResolvedValue({ trace_id: 'trace-1', status: 'warning', duration_ms: 100, events: [
    { id: '1', name: 'query.model_attempt', status: 'ok', offset_ms: 0, duration_ms: 50, input: { messages: [{ role: 'system', content: 'Only valid JSON <script>alert(1)</script>' }] }, output: { raw_output: 'invalid' } },
    { id: '2', name: 'query.validation_failed', status: 'warning', offset_ms: 50, output: { reason: 'schema' } },
  ] });
});
it('shows literal prompts and filters to failed validation', async () => {
  const user = userEvent.setup();
  render(<TracePanel sessionId="s" traceId="trace-1" onSelect={vi.fn()} enabled busy={false} />);
  await screen.findByText('JSON sai schema');
  await user.click(screen.getByText('Prompt phân loại · từng lần thử'));
  await user.click(screen.getByText('Prompt system · 1'));
  expect(screen.getByText('Only valid JSON <script>alert(1)</script>')).toBeInTheDocument();
  expect(document.querySelector('script')).toBeNull();
  await user.click(screen.getByLabelText('Chỉ lỗi / fallback'));
  expect(screen.queryByText('Prompt phân loại · từng lần thử')).not.toBeInTheDocument();
  expect(screen.getByText('JSON sai schema')).toBeInTheDocument();
});
it('does not request traces while disabled', () => {
  chatbotApi.traces.mockClear();
  render(<TracePanel sessionId="s" onSelect={vi.fn()} enabled={false} />);
  expect(screen.getByText('Chưa bật ghi trace')).toBeInTheDocument();
  expect(chatbotApi.traces).not.toHaveBeenCalled();
});
it('reports expired trace without showing the previous result', async () => {
  chatbotApi.trace.mockRejectedValue({ status: 404 });
  render(<TracePanel sessionId="s" traceId="expired" onSelect={vi.fn()} enabled />);
  await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('Trace chưa được tạo hoặc đã hết hạn'));
});
