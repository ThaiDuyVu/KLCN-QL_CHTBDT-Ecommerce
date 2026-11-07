import { useEffect, useState } from 'react';
import { projectConfig } from '../../config/projectConfig';
import './print.css';

/**
 * Shared A4 frame for the store's business forms (outline CN04 "kết xuất biểu mẫu").
 * Hidden on screen; only this element is printed. Save as PDF through the browser print dialog.
 */
export default function PrintForm({ title, number, date, branch, children, signatures = [], note }) {
  return <article className="print-form" aria-hidden="true">
    <header className="print-form-header">
      <div><strong>{projectConfig.appName}</strong>{branch && <p>{branch}</p>}</div>
      <div className="print-form-meta">{number && <p>Số: <strong>{number}</strong></p>}{date && <p>Ngày: {date}</p>}</div>
    </header>
    <h1 className="print-form-title">{title}</h1>
    {children}
    {note && <p className="print-form-note">{note}</p>}
    {signatures.length > 0 && <footer className="print-form-signatures">{signatures.map((label) => <div key={label}><strong>{label}</strong><span>(Ký, ghi rõ họ tên)</span></div>)}</footer>}
  </article>;
}

export function PrintFields({ rows }) {
  return <dl className="print-form-fields">{rows.filter(([, value]) => value !== undefined).map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value || '—'}</dd></div>)}</dl>;
}

/** Renders its form (children) only while the print dialog is open, so the page DOM stays free of duplicates. */
export function PrintButton({ label = 'In phiếu', children }) {
  const [printing, setPrinting] = useState(false);
  useEffect(() => {
    if (!printing) return undefined;
    const done = () => setPrinting(false);
    window.addEventListener('afterprint', done, { once: true });
    window.print(); // blocks until the dialog closes in desktop browsers; afterprint covers the others
    done();
    return () => window.removeEventListener('afterprint', done);
  }, [printing]);
  return <><button type="button" className="button button-quiet" onClick={() => setPrinting(true)}>{label}</button>{printing && children}</>;
}
