import { useEffect, useState } from 'react';
import { addressApi } from '../api/addressApi';
import AddressForm from './AddressForm';
import '../addresses.css';

export default function AddressBook() {
  const [addresses, setAddresses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busyId, setBusyId] = useState('');
  const [editing, setEditing] = useState(null);
  const [formError, setFormError] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    addressApi.list(controller.signal).then(setAddresses)
      .catch((cause) => { if (cause.name !== 'AbortError') setError(cause.message); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, []);

  async function refresh() { setAddresses(await addressApi.list()); }
  async function save(body) {
    setBusyId('form'); setFormError('');
    try {
      if (editing?.addressId) await addressApi.update(editing.addressId, body);
      else await addressApi.create(body);
      await refresh(); setEditing(null);
    } catch (cause) { setFormError(cause.message); }
    finally { setBusyId(''); }
  }
  async function action(id, operation) {
    if (operation === 'delete' && !window.confirm('Xóa địa chỉ này?')) return;
    setBusyId(id); setError('');
    try {
      if (operation === 'delete') await addressApi.remove(id);
      else await addressApi.makeDefault(id);
      await refresh();
    } catch (cause) { setError(cause.message); }
    finally { setBusyId(''); }
  }

  return <section className="panel address-book" aria-labelledby="address-book-title">
    <div className="address-book-heading"><h2 id="address-book-title">Sổ địa chỉ</h2><button className="button" type="button" onClick={() => { setEditing({}); setFormError(''); }}>+ Thêm địa chỉ</button></div>
    {loading && <p role="status">Đang tải địa chỉ…</p>}
    {error && <p className="auth-alert" role="alert">{error}</p>}
    {!loading && !error && addresses.length === 0 && <p>Chưa có địa chỉ nào. Hãy thêm địa chỉ để đặt hàng.</p>}
    <div className="address-card-list">{addresses.map((address) => <article className="address-card" key={address.addressId}>
      <div><strong>{address.label || 'Địa chỉ'}</strong>{address.isDefault && <span className="address-default">Mặc định</span>}</div>
      <p>{address.recipientName} · {address.recipientPhone}</p><p>{address.fullAddress}</p>
      <div className="address-actions"><button type="button" disabled={Boolean(busyId)} onClick={() => { setEditing(address); setFormError(''); }}>Sửa</button>{!address.isDefault && <button type="button" disabled={Boolean(busyId)} onClick={() => action(address.addressId, 'default')}>Đặt mặc định</button>}<button type="button" disabled={Boolean(busyId)} onClick={() => action(address.addressId, 'delete')}>Xóa</button></div>
    </article>)}</div>
    {editing && <div className="address-edit-panel"><h3>{editing.addressId ? 'Sửa địa chỉ' : 'Thêm địa chỉ'}</h3><AddressForm key={editing.addressId || 'new'} address={editing} submitting={busyId === 'form'} error={formError} onSubmit={save} onCancel={() => setEditing(null)} /></div>}
  </section>;
}
