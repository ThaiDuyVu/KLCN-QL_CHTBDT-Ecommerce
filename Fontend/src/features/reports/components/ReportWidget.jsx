import { useCallback } from 'react';
import useProductRequest from '../../products/hooks/useProductRequest';
import { reportApi } from '../api/reportApi';

export default function ReportWidget({ resource, filters, title, description, children, className = '' }) {
  const filterKey = JSON.stringify(filters);
  const load = useCallback((signal) => reportApi.get(resource, JSON.parse(filterKey), signal), [resource, filterKey]);
  const result = useProductRequest(`report:${resource}:${filterKey}`, load);
  return <section className={`panel report-widget ${className}`} aria-label={title} aria-busy={result.isLoading}>
    <header className="report-widget-heading"><h2>{title}</h2>{description && <p>{description}</p>}</header>
    {result.isLoading ? <div className="report-loading" role="status">Đang tải {title.toLowerCase()}…</div>
      : result.error ? <div className="auth-alert" role="alert"><p>{result.error.message}</p><button className="button button-quiet" onClick={result.retry}>Thử lại</button></div>
        : children(result.data)}
  </section>;
}
