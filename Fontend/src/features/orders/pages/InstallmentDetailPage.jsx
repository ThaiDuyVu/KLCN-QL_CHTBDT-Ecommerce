import { useCallback, useState } from 'react';
import { Link, useParams } from 'react-router';
import PageHeader from '../../../components/ui/PageHeader';
import StatusBadge from '../../../components/ui/StatusBadge';
import useProductRequest from '../../products/hooks/useProductRequest';
import CommerceState from '../components/CommerceState';
import { money } from '../components/orderFormat';
import { installmentApi } from '../api/installmentApi';
import '../installment.css';

const labels = { PENDING: 'Chờ tiếp nhận', PROCESSING: 'Đang xét duyệt', APPROVED: 'Đã duyệt', REJECTED: 'Đã từ chối' };
const actions = { PROCESSING: 'Tiếp nhận', APPROVED: 'Duyệt hồ sơ', REJECTED: 'Từ chối' };

export default function InstallmentDetailPage() {
  const { installmentId } = useParams();
  const load = useCallback((signal) => installmentApi.detail(installmentId, signal), [installmentId]);
  const { data, error, isLoading, retry } = useProductRequest(`installment:${installmentId}`, load);
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState('');
  const [notice, setNotice] = useState('');

  async function changeStatus(status) {
    if (!window.confirm(`${actions[status]} cho đơn ${data.orderCode}?`)) return;
    setBusy(true); setActionError(''); setNotice('');
    try { await installmentApi.status(installmentId, status); retry(); setNotice('Đã cập nhật hồ sơ trả góp.'); }
    catch (requestError) { setActionError(requestError.message); retry(); }
    finally { setBusy(false); }
  }

  return <>
    <PageHeader eyebrow="Vận hành / Trả góp" title={data ? `Hồ sơ ${data.orderCode}` : 'Chi tiết hồ sơ trả góp'}
      description="Kết quả xét duyệt được lưu trong hồ sơ trả góp, tách khỏi trạng thái đơn hàng." />
    <Link className="commerce-back-link" to="/installments">← Danh sách hồ sơ</Link>
    {notice && <p className="commerce-notice" role="status">{notice}</p>}
    {actionError && <p className="auth-alert" role="alert">{actionError}</p>}
    <CommerceState loading={isLoading} error={error} retry={retry} />
    {data && <div className="installment-detail-layout">
      <section className="panel installment-detail-card"><div className="installment-detail-heading"><div><p className="eyebrow">Hồ sơ trả góp</p><h2>{data.orderCode}</h2></div><StatusBadge status={data.status}>{labels[data.status]}</StatusBadge></div>
        <dl className="installment-detail-fields">
          <div><dt>Khách hàng</dt><dd>{data.customerName}</dd></div><div><dt>Đơn vị trả góp</dt><dd>{data.providerName}</dd></div>
          <div><dt>Trạng thái đơn</dt><dd>{data.orderStatus}</dd></div><div><dt>Kỳ hạn</dt><dd>{data.termMonths} tháng</dd></div>
          <div><dt>Tổng tiền</dt><dd>{money(data.totalAmount)}</dd></div><div><dt>Trả trước</dt><dd>{money(data.downPayment)}</dd></div>
          <div><dt>Số tiền còn lại</dt><dd className="installment-amount">{money(data.remainingAmount)}</dd></div>
        </dl>
        <Link className="button button-quiet" to={`/orders/${data.orderId}`}>Xem đơn hàng</Link>
      </section>
      <aside className="panel installment-action-card"><h2>Xử lý hồ sơ</h2><p className="muted">Chỉ MANAGER hoặc ADMIN có quyền thay đổi trạng thái hồ sơ.</p>
        {data.allowedStatuses?.length ? <div className="installment-action-buttons">{data.allowedStatuses.map((status) => <button key={status} className={`button ${status === 'REJECTED' ? 'button-quiet' : ''}`} disabled={busy} onClick={() => changeStatus(status)}>{busy ? 'Đang xử lý…' : actions[status]}</button>)}</div> : <p className="muted">Không có thao tác phù hợp ở trạng thái này.</p>}
      </aside>
    </div>}
  </>;
}
