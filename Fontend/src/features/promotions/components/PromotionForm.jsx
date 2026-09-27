import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { promotionApi } from '../api/promotionApi';
import ProductPicker from './ProductPicker';
import { useAuth } from '../../../hooks/useAuth';
import { money, date } from '../../orders/components/orderFormat';

function localDate(value) {
  if (!value) return '';
  const instant = new Date(value);
  return new Date(instant.getTime() - instant.getTimezoneOffset() * 60000).toISOString().slice(0, 19);
}

export default function PromotionForm({ promotion }) {
  const navigate = useNavigate();
  const { invalidateSession } = useAuth();
  const [products, setProducts] = useState(promotion?.products || []);
  const [fields, setFields] = useState({
    promotionName: promotion?.promotionName || '', description: promotion?.description || '',
    discountType: promotion?.discountType || 'PERCENTAGE', discountValue: promotion?.discountValue ?? '',
    startDate: localDate(promotion?.startDate), endDate: localDate(promotion?.endDate), status: promotion?.status || 'ACTIVE',
  });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const percentage = fields.discountType === 'PERCENTAGE';
  function change(event) {
    setFields((current) => ({ ...current, [event.target.name]: event.target.value }));
    setError('');
  }
  async function submit(event) {
    event.preventDefault();
    if (busy) return;
    setError('');
    if (!fields.promotionName.trim()) { setError('Vui lòng nhập tên khuyến mãi.'); return; }
    const startDate = new Date(fields.startDate); const endDate = new Date(fields.endDate);
    if (endDate < startDate) { setError('Ngày kết thúc phải từ ngày bắt đầu trở đi.'); return; }
    setBusy(true);
    try {
      const saved = await promotionApi.save(promotion?.promotionId, {
        promotionName: fields.promotionName.trim(), description: fields.description.trim() || null,
        discountType: fields.discountType, discountValue: Number(fields.discountValue),
        startDate: startDate.toISOString(), endDate: endDate.toISOString(), status: fields.status,
        productIds: products.map((item) => item.productId),
      });
      navigate(`/promotions/${saved.promotionId}`, { state: { saved: true } });
    } catch (requestError) {
      if (requestError.status === 401) invalidateSession();
      setError(requestError.message);
    } finally { setBusy(false); }
  }
  return <form className="promotion-editor" onSubmit={submit} aria-busy={busy}>
    <fieldset disabled={busy} className="promotion-editor-layout">
      <legend className="sr-only">Thông tin khuyến mãi</legend>
      <div className="promotion-editor-main">
        <section className="panel promotion-form" aria-labelledby="promotion-information">
          <div className="promotion-section-heading"><span className="promotion-step" aria-hidden="true">01</span><div><h2 id="promotion-information">Thiết lập chương trình</h2><p>Mức giảm và thời gian áp dụng cho sản phẩm.</p></div></div>
          <label>Tên khuyến mãi *<input autoFocus name="promotionName" required maxLength={255} value={fields.promotionName} onChange={change} placeholder="Ví dụ: Ưu đãi thiết bị cuối tuần" /></label>
          <div className="promotion-form-grid">
            <label>Loại giảm giá<select name="discountType" value={fields.discountType} onChange={change}><option value="PERCENTAGE">Giảm theo phần trăm</option><option value="FIXED_AMOUNT">Giảm số tiền cố định</option></select></label>
            <label>{percentage ? 'Mức giảm (%) *' : 'Mức giảm mỗi sản phẩm (₫) *'}<input name="discountValue" type="number" required min="0" max={percentage ? 100 : 9999999999999.99} step="0.01" value={fields.discountValue} onChange={change} placeholder={percentage ? 'Ví dụ: 10' : 'Ví dụ: 500000'} /></label>
            <label>Bắt đầu *<input name="startDate" type="datetime-local" required step="1" value={fields.startDate} onChange={change} /></label>
            <label>Kết thúc *<input name="endDate" type="datetime-local" required step="1" value={fields.endDate} onChange={change} /></label>
          </div>
          <p className="promotion-field-hint">Thời gian hiển thị theo múi giờ trên thiết bị của bạn.</p>
          <label>Trạng thái<select name="status" value={fields.status} onChange={change}><option value="ACTIVE">Bật chương trình</option><option value="INACTIVE">Tắt chương trình</option></select></label>
          <label>Mô tả<textarea name="description" rows="4" value={fields.description} onChange={change} placeholder="Ghi chú về chương trình khuyến mãi…" /></label>
        </section>
        <ProductPicker value={products} onChange={setProducts} disabled={busy} />
      </div>
      <aside className="panel promotion-editor-summary">
        <span className="eyebrow">TÓM TẮT CHƯƠNG TRÌNH</span>
        <div className="promotion-discount-preview"><span>{percentage ? 'Giảm theo phần trăm' : 'Giảm trên mỗi sản phẩm'}</span><strong>{fields.discountValue === '' ? '—' : percentage ? `${fields.discountValue}%` : money(Number(fields.discountValue))}</strong></div>
        <h2>{fields.promotionName.trim() || 'Khuyến mãi mới'}</h2>
        <span className={`promotion-summary-status ${fields.status === 'ACTIVE' ? 'is-active' : ''}`}>{fields.status === 'ACTIVE' ? 'Bật chương trình' : 'Tắt chương trình'}</span>
        <dl className="promotion-summary-fields"><div><dt>Sản phẩm</dt><dd>{products.length} đã chọn</dd></div><div><dt>Bắt đầu</dt><dd>{fields.startDate ? date(new Date(fields.startDate).toISOString()) : 'Chưa chọn'}</dd></div><div><dt>Kết thúc</dt><dd>{fields.endDate ? date(new Date(fields.endDate).toISOString()) : 'Chưa chọn'}</dd></div></dl>
        <div className="promotion-rule-note"><strong>Lưu ý khi áp dụng</strong><p>Chương trình bật không được trùng thời gian trên cùng sản phẩm. Mọi biến thể đều được hưởng ưu đãi, giá sau giảm tối thiểu là 0.</p></div>
        {error && <p className="auth-alert" role="alert">{error}</p>}
        <button className="button" type="submit" disabled={busy}>{busy ? 'Đang lưu…' : promotion ? 'Lưu thay đổi' : 'Tạo khuyến mãi'}</button>
        <Link className="button button-quiet" to={promotion ? `/promotions/${promotion.promotionId}` : '/promotions'} aria-disabled={busy} onClick={(event) => { if (busy) event.preventDefault(); }}>Hủy</Link>
      </aside>
    </fieldset>
  </form>;
}
