export default function CustomerStatus({ value, label }) {
  if (!value) return <span className="status-pill status-pill--neutral">Khách vãng lai</span>;
  const tone = ['ACTIVE', 'DELIVERED', 'APPROVED'].includes(value) ? 'success' : ['CANCELLED', 'LOCKED', 'REJECTED', 'EXPIRED', 'VOIDED'].includes(value) ? 'danger' : ['INACTIVE'].includes(value) ? 'neutral' : 'progress';
  return <span className={`status-pill status-pill--${tone}`}>{label || value}</span>;
}
