import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import PageHeader from '../../../components/ui/PageHeader';
import { warrantyApi } from '../api/warrantyApi';
import { formatDate, ticketAction, ticketStatus } from '../warrantyFormat';
import '../warranty.css';

export default function WarrantyTicketDetailPage({ customer = false }) {
  const { ticketId } = useParams(); const [data, setData] = useState(null); const [loading, setLoading] = useState(true);
  const [error, setError] = useState(''); const [notice, setNotice] = useState(''); const [submitting, setSubmitting] = useState(false);
  const load = useCallback((signal) => customer ? warrantyApi.myTicket(ticketId, signal) : warrantyApi.ticket(ticketId, signal), [customer, ticketId]);
  useEffect(() => { const c = new AbortController(); load(c.signal).then(setData).catch(e => { if (e.name !== 'AbortError') setError(e.message); }).finally(() => setLoading(false)); return () => c.abort(); }, [load]);
  async function update(e) { e.preventDefault(); const f = new FormData(e.currentTarget); const status = String(f.get('status')); const note = String(f.get('resolutionNote') || '');
    setSubmitting(true); setError(''); setNotice(''); try { const updated = await warrantyApi.updateTicket(ticketId, status, note); setData(updated); setNotice('Đã cập nhật Warranty Ticket.'); }
    catch (nextError) { setError(nextError.message); } finally { setSubmitting(false); } }
  return <><PageHeader title={data?.ticketCode || 'Chi tiết Warranty Ticket'} description={data ? `${data.productName} · ${data.serialNumber}` : 'Thông tin xử lý yêu cầu bảo hành'} /><Link className="button button-quiet" to={customer ? '/my-warranty-tickets' : '/warranty-tickets'}>Quay lại</Link>
    {loading && <p role="status">Đang tải ticket…</p>}{error && <p className="auth-alert" role="alert">{error}</p>}{notice && <p role="status">{notice}</p>}{data && <div className="warranty-grid"><section className="panel warranty-device"><h2>{ticketStatus[data.status]}</h2><p>Thiết bị: {data.productName} · {data.sku}</p><p>Serial: <strong>{data.serialNumber}</strong></p><p>IMEI: {data.imeiNumbers?.join(', ') || 'Không có'}</p><p>Khách hàng: {data.customerName}</p><p>Đơn hàng: {data.orderCode}</p><p>Ngày tạo: {formatDate(data.createdAt)}</p><p>Mô tả: {data.issueDescription}</p><p>Nhân viên xử lý: {data.employeeName || 'Chưa tiếp nhận'}</p>{data.resolutionNote && <p>Kết quả: {data.resolutionNote}</p>}{data.resolvedAt && <p>Hoàn tất: {formatDate(data.resolvedAt)}</p>}</section>
      {!customer && data.allowedStatuses?.length > 0 && <form className="panel warranty-form" onSubmit={update}><label>Thao tác<select name="status" required>{data.allowedStatuses.map(status => <option key={status} value={status}>{ticketAction[status]}</option>)}</select></label><label>Ghi chú xử lý<textarea className="warranty-note" name="resolutionNote" placeholder="Bắt buộc khi hoàn tất hoặc từ chối" /></label><button className="button" disabled={submitting}>{submitting ? 'Đang cập nhật…' : 'Cập nhật ticket'}</button></form>}</div>}
  </>;
}
