const paths = {
  search: <><circle cx="10.5" cy="10.5" r="6.5" /><path d="m16 16 4.5 4.5" /></>,
  cart: <><path d="M3 3h2l3 12h11l2-9H6" /><circle cx="9" cy="20" r="1" /><circle cx="18" cy="20" r="1" /></>,
  heart: <path d="M20.5 5.5a5 5 0 0 0-8.5 1 5 5 0 0 0-8.5-1C-1 10 7 16.5 12 21c5-4.5 13-11 8.5-15.5Z" />,
  compare: <><path d="M4 7h15l-3-3M20 17H5l3 3M4 7v5M20 17v-5" /></>,
  user: <><circle cx="12" cy="7" r="4" /><path d="M4 21v-2a8 8 0 0 1 16 0v2" /></>,
  pin: <><path d="M19 10c0 5-7 11-7 11S5 15 5 10a7 7 0 0 1 14 0Z" /><circle cx="12" cy="10" r="2.5" /></>,
  device: <><rect x="3" y="4" width="18" height="13" rx="1.5" /><path d="M8 21h8M12 17v4" /></>,
  arrow: <path d="M4 12h16m-6-6 6 6-6 6" />,
  box: <><path d="m3 7 9-4 9 4v10l-9 4-9-4ZM3 7l9 4 9-4M12 11v10M7 5l9 4" /></>,
};

export default function ShopIcon({ name, className = '' }) {
  return <svg className={`shop-icon ${className}`} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name] || paths.device}</svg>;
}
