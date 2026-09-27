import ShopIcon from './ShopIcon';

export default function ComingSoonButton({ children, icon, className = '' }) {
  return <button type="button" className={`shop-coming-soon ${className}`} disabled title={`${children} · Sắp có`} aria-label={`${children} (sắp có)`}>
    {icon && <ShopIcon name={icon} />}<span>{children}</span><small>Sắp có</small>
  </button>;
}
