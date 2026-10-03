export default function CustomerStatus({ value, label }) {
  const tone = ['ACTIVE', 'DELIVERED', 'APPROVED'].includes(value) ? 'success' : ['CANCELLED', 'LOCKED', 'REJECTED', 'EXPIRED'].includes(value) ? 'danger' : ['INACTIVE'].includes(value) ? 'neutral' : 'progress';
  return <span className={`status-pill status-pill--${tone}`}>{label || value}</span>;
}
