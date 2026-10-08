import { useEffect, useState } from 'react';
import { inventoryApi } from '../api/inventoryApi';

export default function VariantSearchPicker({ value, onSelect, categories, brands }) {
  const [open, setOpen] = useState(!value);
  const [keyword, setKeyword] = useState('');
  const [debounced, setDebounced] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [brandId, setBrandId] = useState('');
  const [trackingType, setTrackingType] = useState('');
  const [page, setPage] = useState(0);
  const [requestState, setRequestState] = useState({ key: '', result: null, error: '' });
  const requestKey = JSON.stringify([page, debounced, categoryId, brandId, trackingType]);
  const current = requestState.key === requestKey ? requestState : null;
  const result = current?.result;
  const error = current?.error;
  const loading = open && !current;

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(keyword.trim()), 300);
    return () => clearTimeout(timer);
  }, [keyword]);

  useEffect(() => {
    if (!open) return undefined;
    const controller = new AbortController();
    let active = true;
    inventoryApi.variants({ page, size: 20, keyword: debounced, categoryId, brandId, trackingType }, controller.signal)
      .then((data) => { if (active) setRequestState({ key: requestKey, result: data, error: '' }); })
      .catch((cause) => { if (active && cause.name !== 'AbortError') setRequestState({ key: requestKey, result: null, error: cause.message }); });
    return () => { active = false; controller.abort(); };
  }, [open, page, debounced, categoryId, brandId, trackingType, requestKey]);

  if (!open && value) return <div className="variant-selected-card">
    <div><strong>{value.sku}</strong><span>{value.productName}</span>
      <small>{[value.brandName, value.categoryName, value.color, value.ram, value.storage, value.trackingType].filter(Boolean).join(' · ')}</small>
    </div>
    <button type="button" className="button button-quiet" onClick={() => setOpen(true)}>Đổi SKU</button>
  </div>;

  return <div className="variant-picker">
    <label>Tìm SKU hoặc tên sản phẩm
      <input autoComplete="off" value={keyword} onChange={(event) => { setKeyword(event.target.value); setPage(0); }} placeholder="Ví dụ: DELL-512 hoặc Inspiron" />
    </label>
    <div className="variant-picker-filters">
      <label>Danh mục<select value={categoryId} onChange={(event) => { setCategoryId(event.target.value); setPage(0); }}><option value="">Tất cả</option>{categories.map((item) => <option key={item.categoryId} value={item.categoryId}>{item.categoryName}</option>)}</select></label>
      <label>Thương hiệu<select value={brandId} onChange={(event) => { setBrandId(event.target.value); setPage(0); }}><option value="">Tất cả</option>{brands.map((item) => <option key={item.brandId} value={item.brandId}>{item.brandName}</option>)}</select></label>
      <label>Quản lý thiết bị<select value={trackingType} onChange={(event) => { setTrackingType(event.target.value); setPage(0); }}><option value="">Tất cả</option><option value="NONE">Không</option><option value="SERIAL">Serial</option><option value="IMEI">IMEI</option></select></label>
    </div>
    {loading && <p role="status">Đang tìm SKU…</p>}
    {error && <p role="alert" className="auth-alert">{error}</p>}
    {!loading && !error && result?.content?.length === 0 && <p>Không tìm thấy SKU phù hợp.</p>}
    {!loading && !error && Boolean(result?.content?.length) && <div className="variant-picker-results" aria-label="Kết quả tìm SKU">
      {result.content.map((item) => <button key={item.variantId} type="button" className="variant-picker-result" onClick={() => { onSelect(item); setOpen(false); }}>
        <strong>{item.sku}</strong><span>{item.productName}</span><small>{[item.brandName, item.categoryName, item.color, item.ram, item.storage, item.trackingType].filter(Boolean).join(' · ')}</small>
      </button>)}
    </div>}
    <div className="variant-picker-pages"><button type="button" disabled={page === 0 || loading} onClick={() => setPage(page - 1)}>Trước</button><span>Trang {page + 1}{result?.totalPages ? ` / ${result.totalPages}` : ''}</span><button type="button" disabled={loading || !result || page + 1 >= result.totalPages} onClick={() => setPage(page + 1)}>Sau</button></div>
    {value && <button type="button" className="button button-quiet" onClick={() => setOpen(false)}>Giữ SKU hiện tại</button>}
  </div>;
}
