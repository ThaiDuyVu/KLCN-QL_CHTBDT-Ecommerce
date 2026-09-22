import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import PageHeader from '../../../components/ui/PageHeader';
import { warrantyApi } from '../api/warrantyApi';
import { formatDate, ticketStatus, warrantyStatus } from '../warrantyFormat';
import '../warranty.css';

export default function WarrantyDetailPage({ customer = false }) {
  const { warrantyId } = useParams(); const [data, setData] = useState(null); const [loading, setLoading] = useState(true);
  const [error, setError] = useState(''); const [submitting, setSubmitting] = useState(false); const [notice, setNotice] = useState('');
  const load = useCallback((signal) => (customer ? warrantyApi.myWarranty(warrantyId, signal) : warrantyApi.warranty(warrantyId, signal)), [customer, warrantyId]);
  useEffect(() => { const c = new AbortController(); load(c.signal).then(setData).catch(e => { if (e.name !== 'AbortError') setError(e.message); }).finally(() => setLoading(false)); return () => c.abort(); }, [load]);
  async function submit(e) { e.preventDefault(); const issue = String(new FormData(e.currentTarget).get('issueDescription') || '').trim(); if (!issue) return;
    setSubmitting(true); setError(''); setNotice(''); try { await warrantyApi.createTicket(warrantyId, issue); const refreshed = await warrantyApi.myWarranty(warrantyId); setData(refreshed); setNotice('Đã gửi yêu cầu bảo hành.'); e.currentTarget.reset(); }
    catch (nextError) { setError(nextError.message); } finally { setSubmitting(false); } }
  return <><PageHeader title="Chi tiết bảo hành" description={data ? `${data.productName} · ${data.sku}` : 'Thông tin thiết bị và lịch sử yêu cầu'} />
    <Link className="button button-quiet" to={customer ? '/my-warranties' : '/warranties'}>Quay lại</Link>{loading && <p role="status">Đang tải bảo hành…</p>}{error && <p className="auth-alert" role="alert">{error}</p>}{notice && <p role="status">{notice}</p>}
    {data && <div className="warranty-grid"><section className="panel warranty-device"><h2>{data.productName}</h2><p>SKU: <strong>{data.sku}</strong></p><p>Serial: <strong>{data.serialNumber}</strong></p><p>IMEI: {data.imeiNumbers?.join(', ') || 'Không có'}</p><p>Khách hàng: {data.customerName}</p><p>Đơn hàng: {data.orderCode}</p><p>Thời hạn: {formatDate(data.startDate)} – {formatDate(data.endDate)}</p><p className={`warranty-status ${data.status === 'EXPIRED' ? 'expired' : ''}`}>{warrantyStatus[data.status]}</p></section>
      {customer && data.eligible && <form className="panel warranty-form" onSubmit={submit}><label>Mô tả lỗi<textarea className="warranty-note" name="issueDescription" required /></label><button className="button" disabled={submitting}>{submitting ? 'Đang gửi…' : 'Gửi yêu cầu bảo hành'}</button></form>}
      {customer && !data.eligible && <section className="panel"><p>Thiết bị hiện không đủ điều kiện tiếp nhận yêu cầu bảo hành mới.</p></section>}
      <section className="panel"><h2>Lịch sử Warranty Ticket</h2><div className="warranty-history">{data.tickets?.length ? data.tickets.map(ticket => <article className="warranty-ticket" key={ticket.ticketId}><p><strong>{ticket.ticketCode}</strong> · {ticketStatus[ticket.status]}</p><p>{ticket.issueDescription}</p><p className="muted">{formatDate(ticket.createdAt)}{ticket.employeeName ? ` · ${ticket.employeeName}` : ''}</p>{ticket.resolutionNote && <p>Kết quả: {ticket.resolutionNote}</p>}<Link to={customer ? `/my-warranty-tickets/${ticket.ticketId}` : `/warranty-tickets/${ticket.ticketId}`}>Xem ticket</Link></article>) : <p className="muted">Chưa có yêu cầu bảo hành.</p>}</div></section></div>}
  </>;
}
