const SUGGESTIONS = ['CPU', 'RAM', 'Màn hình', 'Pin'];

export default function SpecificationsEditor({ value, onChange, disabled }) {
  function add(specKey = '') { onChange([...value, { id: crypto.randomUUID(), specKey, specValue: '' }]); }
  function update(id, field, text) { onChange(value.map((item) => item.id === id ? { ...item, [field]: text } : item)); }
  return <section className="panel product-create-section" aria-labelledby="product-specifications">
    <div className="product-create-section-heading"><span className="product-create-step" aria-hidden="true">03</span><div><h2 id="product-specifications">Thông số kỹ thuật</h2><p>Thêm các thông tin phù hợp với sản phẩm, không bắt buộc theo mẫu cố định.</p></div><span className="product-create-image-count">{value.length} thông số</span></div>
    <div className="product-spec-suggestions"><span>Thêm nhanh</span>{SUGGESTIONS.map((name) => <button type="button" key={name} disabled={disabled} onClick={() => add(name)}>+ {name}</button>)}</div>
    {!value.length && <p className="product-spec-empty">Chưa có thông số. Chọn gợi ý bên trên hoặc thêm một thông số riêng.</p>}
    <div className="product-spec-rows">{value.map((item, index) => <div className="product-spec-row" key={item.id}>
      <span className="product-spec-index" aria-hidden="true">{String(index + 1).padStart(2, '0')}</span>
      <label>Tên thông số<input value={item.specKey} required maxLength={100} disabled={disabled} onChange={(event) => update(item.id, 'specKey', event.target.value)} placeholder="Ví dụ: RAM" /></label>
      <label>Giá trị<input value={item.specValue} required maxLength={1000} disabled={disabled} onChange={(event) => update(item.id, 'specValue', event.target.value)} placeholder="Ví dụ: 16 GB DDR5" /></label>
      <button type="button" className="product-spec-remove" disabled={disabled} onClick={() => onChange(value.filter((entry) => entry.id !== item.id))} aria-label={`Xóa thông số ${item.specKey || index + 1}`} title="Xóa thông số">×</button>
    </div>)}</div>
    <button className="button button-quiet product-spec-add" type="button" disabled={disabled} onClick={() => add()}>+ Thêm thông số</button>
  </section>;
}
