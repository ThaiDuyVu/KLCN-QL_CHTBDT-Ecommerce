export default function StatusBadge({ status, children }) {
  const tone = ['ACTIVE', 'AVAILABLE', 'CONFIRMED', 'DELIVERED', 'COMPLETED', 'PAID'].includes(status)
    ? 'success'
    : ['CANCELLED', 'REJECTED', 'DEFECTIVE', 'LOCKED', 'EXPIRED'].includes(status)
      ? 'danger'
      : ['PENDING', 'DRAFT', 'RECEIVED', 'RESERVED', 'PROCESSING', 'IN_PROGRESS', 'SHIPPED'].includes(status)
        ? 'progress'
        : 'neutral';

  return <span className={`status-pill status-pill--${tone}`}>{children || status || '—'}</span>;
}
