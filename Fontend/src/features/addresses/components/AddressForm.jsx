export default function AddressForm({ address, onSubmit, onCancel, submitting = false, error = '' }) {
  function submit(event) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    onSubmit(Object.fromEntries(['label', 'recipientName', 'recipientPhone', 'addressLine', 'ward', 'district', 'province']
      .map((name) => [name, String(data.get(name) || '').trim() || null])));
  }

  return <form className="address-form" onSubmit={submit}>
    <div className="address-form-grid">
      <label>Nhãn địa chỉ<input name="label" maxLength={100} defaultValue={address?.label || ''} placeholder="Nhà riêng, công ty…" /></label>
      <label>Tên người nhận<input name="recipientName" required maxLength={255} defaultValue={address?.recipientName || ''} autoComplete="name" /></label>
      <label>Số điện thoại<input name="recipientPhone" type="tel" required maxLength={30} pattern="(0[0-9]{9}|\+84[0-9]{9})" title="Ví dụ: 0901234567 hoặc +84901234567" defaultValue={address?.recipientPhone || ''} autoComplete="tel" /></label>
      <label>Địa chỉ (số nhà, đường)<input name="addressLine" required maxLength={500} defaultValue={address?.addressLine || ''} autoComplete="street-address" /></label>
      <label>Phường/xã<input name="ward" maxLength={150} defaultValue={address?.ward || ''} /></label>
      <label>Quận/huyện<input name="district" maxLength={150} defaultValue={address?.district || ''} /></label>
      <label>Tỉnh/thành<input name="province" maxLength={150} defaultValue={address?.province || ''} /></label>
    </div>
    {error && <p className="auth-alert" role="alert">{error}</p>}
    <div className="address-actions"><button className="button" disabled={submitting}>{submitting ? 'Đang lưu…' : 'Lưu địa chỉ'}</button><button className="button button-quiet" type="button" disabled={submitting} onClick={onCancel}>Hủy</button></div>
  </form>;
}
