import { useCallback, useState } from 'react';
import PageHeader from '../../components/ui/PageHeader';
import useProductRequest from '../products/hooks/useProductRequest';
import CommerceState from '../orders/components/CommerceState';
import { date } from '../orders/components/orderFormat';
import { backupApi } from './backupApi';
import '../orders/commerce.css';

const size = (bytes) => bytes >= 1048576 ? `${(bytes / 1048576).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`;

/** ADMIN: full database backup (pg_dump) and restore (pg_restore). A restore first saves the current data automatically. */
export default function BackupsPage() {
  const load = useCallback((signal) => backupApi.list(signal), []);
  const { data, error, isLoading, retry } = useProductRequest('backups', load);
  const [busy, setBusy] = useState('');
  const [notice, setNotice] = useState('');
  const [actionError, setActionError] = useState('');
  async function run(kind, action, message) {
    setBusy(kind); setNotice(''); setActionError('');
    try { const result = await action(); setNotice(message(result)); retry(); }
    catch (e) { setActionError(e.message); } finally { setBusy(''); }
  }
  function restore(name) {
    const typed = window.prompt(`KHÔI PHỤC sẽ thay toàn bộ dữ liệu hiện tại bằng bản ${name}. Dữ liệu hiện tại sẽ được tự sao lưu trước.\nNhập lại đúng tên bản sao lưu để xác nhận:`, '');
    if (typed === null) return;
    if (typed.trim() !== name) { setActionError('Tên xác nhận không khớp, chưa khôi phục.'); return; }
    run(`restore:${name}`, () => backupApi.restore(name, typed.trim()),
      (result) => `Đã khôi phục từ ${result.restored}. Dữ liệu trước khi khôi phục được lưu ở ${result.safetyBackup.name}. Nên đăng nhập lại.`);
  }
  function remove(name) {
    if (!window.confirm(`Xóa bản sao lưu ${name}?`)) return;
    run(`delete:${name}`, () => backupApi.remove(name), () => `Đã xóa ${name}.`);
  }
  return <><PageHeader eyebrow="Quản trị hệ thống" title="Sao lưu & khôi phục dữ liệu" description="Sao lưu toàn bộ cơ sở dữ liệu và khôi phục khi cần. Chỉ Admin được thao tác." />
    <div className="order-detail-actions"><button className="button" disabled={Boolean(busy)} onClick={() => run('create', () => backupApi.create(), (r) => `Đã sao lưu: ${r.name} (${size(r.sizeBytes)}).`)}>
      {busy === 'create' ? 'Đang sao lưu…' : 'Sao lưu ngay'}</button></div>
    {notice && <p role="status" className="commerce-notice">{notice}</p>}
    {actionError && <p role="alert" className="auth-alert">{actionError}</p>}
    <CommerceState loading={isLoading} error={error} retry={retry} />
    {!isLoading && !error && (data?.length ? <section className="panel commerce-section"><div className="commerce-scroll"><table className="product-table">
      <thead><tr><th>Bản sao lưu</th><th>Thời điểm</th><th>Dung lượng</th><th>Loại</th><th>Thao tác</th></tr></thead>
      <tbody>{data.map((row) => <tr key={row.name}><th scope="row">{row.name}</th><td>{date(row.createdAt)}</td><td>{size(row.sizeBytes)}</td>
        <td>{row.automatic ? 'Tự động trước khi khôi phục' : 'Thủ công'}</td>
        <td><div className="order-detail-actions"><a className="button button-quiet" href={backupApi.downloadUrl(row.name)} download>Tải về</a>
          <button className="button button-quiet" disabled={Boolean(busy)} onClick={() => restore(row.name)}>{busy === `restore:${row.name}` ? 'Đang khôi phục…' : 'Khôi phục'}</button>
          <button className="button button-quiet" disabled={Boolean(busy)} onClick={() => remove(row.name)}>Xóa</button></div></td></tr>)}</tbody></table></div></section>
      : <section className="panel commerce-section"><p>Chưa có bản sao lưu nào.</p></section>)}
  </>;
}
