import { useCallback, useState } from 'react';
import { Link } from 'react-router';
import PageHeader from '../../../components/ui/PageHeader';
import StatusBadge from '../../../components/ui/StatusBadge';
import useProductRequest from '../../products/hooks/useProductRequest';
import CommerceState from '../components/CommerceState';
import { installmentApi } from '../api/installmentApi';
import '../installment.css';

export default function InstallmentProvidersPage() {
  const load = useCallback((signal) => installmentApi.providers(signal), []);
  const { data, error, isLoading, retry } = useProductRequest('installment-providers', load);
  const [editing, setEditing] = useState(undefined);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState('');
  const [notice, setNotice] = useState('');

  async function save(event) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setSaving(true); setFormError(''); setNotice('');
    try {
      await installmentApi.saveProvider(editing?.providerId, {
        providerName: String(form.get('providerName')).trim(), providerCode: String(form.get('providerCode')).trim(),
        contactPhone: String(form.get('contactPhone')).trim() || null,
        contactEmail: String(form.get('contactEmail')).trim() || null,
        status: form.get('status'),
      });
      setEditing(undefined); setNotice('Đã lưu đơn vị trả góp.'); retry();
    } catch (requestError) { setFormError(requestError.message); }
    finally { setSaving(false); }
  }

  return <>
    <PageHeader eyebrow="Vận hành / Trả góp" title="Đơn vị trả góp" description="Quản lý các đơn vị nội bộ được chọn ở bước checkout." />
    <div className="installment-page-actions"><Link className="button button-quiet" to="/installments">← Hồ sơ trả góp</Link><button className="button" onClick={() => { setEditing(null); setFormError(''); }}>Thêm đơn vị</button></div>
    {notice && <p className="commerce-notice" role="status">{notice}</p>}
    {editing !== undefined && <form key={editing?.providerId || 'new'} className="panel installment-provider-form" onSubmit={save}>
      <div className="data-section-heading"><h2>{editing ? `Sửa ${editing.providerName}` : 'Thêm đơn vị trả góp'}</h2></div>
      <div className="installment-provider-grid">
        <label>Tên đơn vị<input name="providerName" required maxLength={255} defaultValue={editing?.providerName || ''} /></label>
        <label>Mã đơn vị<input name="providerCode" required maxLength={100} defaultValue={editing?.providerCode || ''} /></label>
        <label>Điện thoại liên hệ<input name="contactPhone" maxLength={30} defaultValue={editing?.contactPhone || ''} /></label>
        <label>Email liên hệ<input name="contactEmail" type="email" maxLength={255} defaultValue={editing?.contactEmail || ''} /></label>
        <label>Trạng thái<select name="status" defaultValue={editing?.status || 'ACTIVE'}><option value="ACTIVE">Đang hoạt động</option><option value="INACTIVE">Ngừng hoạt động</option></select></label>
      </div>
      {formError && <p className="auth-alert" role="alert">{formError}</p>}
      <div className="installment-page-actions"><button className="button" disabled={saving}>{saving ? 'Đang lưu…' : 'Lưu đơn vị'}</button><button type="button" className="button button-quiet" disabled={saving} onClick={() => setEditing(undefined)}>Đóng</button></div>
    </form>}
    <CommerceState loading={isLoading} error={error} retry={retry} />
    {!isLoading && !error && (data?.length ? <section className="panel installment-table-wrap"><div className="data-section-heading"><div><h2>Danh sách đơn vị</h2><p>{data.length} đơn vị trả góp</p></div></div>
      <table className="product-table"><thead><tr><th>Mã</th><th>Tên đơn vị</th><th>Điện thoại</th><th>Email</th><th>Trạng thái</th><th></th></tr></thead><tbody>{data.map((row) => <tr key={row.providerId}><th scope="row">{row.providerCode}</th><td>{row.providerName}</td><td>{row.contactPhone || '—'}</td><td>{row.contactEmail || '—'}</td><td><StatusBadge status={row.status}>{row.status === 'ACTIVE' ? 'Đang hoạt động' : 'Ngừng hoạt động'}</StatusBadge></td><td><button className="button button-quiet" onClick={() => { setEditing(row); setFormError(''); }}>Sửa</button></td></tr>)}</tbody></table>
    </section> : <section className="panel empty-state"><h2>Chưa có đơn vị trả góp</h2><p>Thêm một đơn vị ACTIVE để khách hàng có thể chọn khi checkout.</p></section>)}
  </>;
}
