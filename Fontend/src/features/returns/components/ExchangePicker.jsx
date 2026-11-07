import { useState } from 'react';
import { money } from '../../orders/components/orderFormat';
import { returnApi } from '../api/returnApi';

/** Products the customer wants instead of the returned goods. Prices are indicative; the store prices the replacement order. */
export default function ExchangePicker({ value, onChange, disabled }) {
  const [hits, setHits] = useState(null);
  const [keyword, setKeyword] = useState('');
  const [error, setError] = useState('');
  async function search() {
    if (!keyword.trim()) return;
    setError('');
    try { setHits((await returnApi.searchVariants(keyword.trim())).content || []); } catch (e) { setError(e.message); }
  }
  const add = (v) => onChange(value.some((x) => x.variantId === v.variantId) ? value
    : [...value, { variantId: v.variantId, sku: v.sku, productName: v.productName, price: Number(v.effectivePrice ?? v.price ?? 0), quantity: 1 }]);
  return <fieldset className="exchange-picker" disabled={disabled}><legend>Sản phẩm muốn đổi sang</legend>
    <div className="pos-inline"><input value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder="Tên hoặc SKU sản phẩm" aria-label="Tìm sản phẩm muốn đổi"
      onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); search(); } }} /><button type="button" className="button button-quiet" onClick={search}>Tìm</button></div>
    {error && <p role="alert" className="auth-alert">{error}</p>}
    {hits && (hits.length ? <ul className="return-hits">{hits.map((v) => <li key={v.variantId}><button type="button" className="button button-quiet" onClick={() => add(v)}>+ {v.productName} · {v.sku} · {money(v.effectivePrice ?? v.price)}</button></li>)}</ul> : <p className="muted">Không tìm thấy sản phẩm.</p>)}
    {value.map((line) => <div className="return-pick" key={line.variantId}><span>{line.productName} · {line.sku} · {money(line.price)}</span>
      <input type="number" min="1" max="20" value={line.quantity} aria-label={`Số lượng đổi ${line.sku}`}
        onChange={(e) => onChange(value.map((x) => (x.variantId === line.variantId ? { ...x, quantity: Math.max(1, Number(e.target.value) || 1) } : x)))} />
      <button type="button" className="button button-quiet" onClick={() => onChange(value.filter((x) => x.variantId !== line.variantId))}>Bỏ</button></div>)}
  </fieldset>;
}
