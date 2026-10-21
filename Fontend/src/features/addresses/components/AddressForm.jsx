// Caption and marker share one line; the grid label stacks caption above the input.
function Caption({ children, required = false }) {
  return <span className="address-field-caption">{children}{required
    ? <span className="address-field-required" aria-hidden="true"> *</span>
    : <span className="address-field-hint"> (không bắt buộc)</span>}</span>;
}

export default function AddressForm({ address, onSubmit, onCancel, submitting = false, error = '' }) {
  function submit(event) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    onSubmit(Object.fromEntries(['label', 'recipientName', 'recipientPhone', 'addressLine', 'ward', 'district', 'province']
      .map((name) => [name, String(data.get(name) || '').trim() || null])));
  }

  return <form className="address-form" onSubmit={submit}>
    <fieldset className="address-form-grid" disabled={submitting}>
      <legend className="sr-only">Thông tin địa chỉ nhận hàng</legend>
      <label className="address-field-half"><Caption required>Tên người nhận</Caption><input name="recipientName" required maxLength={255} defaultValue={address?.recipientName || ''} autoComplete="name" placeholder="Họ và tên" /></label>
      <label className="address-field-half"><Caption required>Số điện thoại</Caption><input name="recipientPhone" type="tel" required maxLength={30} pattern="(0[0-9]{9}|\+84[0-9]{9})" title="Ví dụ: 0901234567 hoặc +84901234567" defaultValue={address?.recipientPhone || ''} autoComplete="tel" placeholder="0901234567" /></label>
      <label className="address-field-full"><Caption required>Địa chỉ (số nhà, đường)</Caption><input name="addressLine" required maxLength={500} defaultValue={address?.addressLine || ''} autoComplete="street-address" placeholder="Ví dụ: 12 Lê Lợi" /></label>
      <label className="address-field-third"><Caption>Tỉnh/thành</Caption><input name="province" maxLength={150} defaultValue={address?.province || ''} autoComplete="address-level1" /></label>
      <label className="address-field-third"><Caption>Quận/huyện</Caption><input name="district" maxLength={150} defaultValue={address?.district || ''} autoComplete="address-level2" /></label>
      <label className="address-field-third"><Caption>Phường/xã</Caption><input name="ward" maxLength={150} defaultValue={address?.ward || ''} autoComplete="address-level3" /></label>
      <label className="address-field-full"><Caption>Nhãn địa chỉ</Caption><input name="label" maxLength={100} defaultValue={address?.label || ''} placeholder="Nhà riêng, công ty…" /></label>
    </fieldset>
    {error && <p className="auth-alert" role="alert">{error}</p>}
    <div className="address-form-actions"><button className="button button-quiet" type="button" disabled={submitting} onClick={onCancel}>Hủy</button><button className="button" disabled={submitting}>{submitting ? 'Đang lưu…' : 'Lưu địa chỉ'}</button></div>
  </form>;
}
