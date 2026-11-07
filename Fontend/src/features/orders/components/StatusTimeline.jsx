import { date, statusLabels } from './orderFormat';

const actorTypes = { STAFF: 'Nhân viên', CUSTOMER: 'Khách hàng', SYSTEM: 'Hệ thống' };

/** Who moved the order to each status, when, and why (order_status_history, V11). */
export default function StatusTimeline({ history }) {
  if (!history?.length) return null;
  return <section className="panel commerce-section" aria-label="Lịch sử trạng thái"><h3>Lịch sử trạng thái</h3>
    <ol className="status-timeline">{history.map((change, index) => <li key={`${change.changedAt}-${index}`}>
      <strong>{change.fromStatus ? `${statusLabels[change.fromStatus]} → ` : ''}{statusLabels[change.toStatus]}</strong>
      <span className="muted"> · {date(change.changedAt)} · {actorTypes[change.actorType]}{change.actorName ? `: ${change.actorName}` : ''}</span>
      {change.note && <p>{change.note}</p>}
    </li>)}</ol>
  </section>;
}
