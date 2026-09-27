import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import PageHeader from '../../../components/ui/PageHeader';
import SpecificationsEditor from '../components/SpecificationsEditor';
import ProductState from '../components/ProductState';
import useProductRequest from '../hooks/useProductRequest';
import { categoryApi } from '../../categories/api/categoryApi';
import { productApi } from '../api/productApi';
import '../products.css';
import '../product-create.css';

const IMAGE_LIMIT = 10;
const FILE_LIMIT = 5 * 1024 * 1024;

export default function ProductCreatePage() {
  const navigate = useNavigate();
  const [form, setForm] = useState({ productName: '', categoryId: '', brandId: '', description: '', status: 'ACTIVE' });
  const [files, setFiles] = useState([]);
  const [specifications, setSpecifications] = useState([]);
  const [primaryId, setPrimaryId] = useState('');
  const [brandPage, setBrandPage] = useState(0);
  const [selectedBrand, setSelectedBrand] = useState(null);
  const [error, setError] = useState('');
  const [imageError, setImageError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const loadCategories = useCallback((signal) => categoryApi.list(signal), []);
  const categories = useProductRequest('create-product-categories', loadCategories);
  const loadBrands = useCallback((signal) => productApi.brands(brandPage, signal), [brandPage]);
  const brands = useProductRequest(`create-product-brands:${brandPage}`, loadBrands);
  const previews = useMemo(() => files.map((entry) => ({ ...entry, url: URL.createObjectURL(entry.file) })), [files]);
  useEffect(() => () => previews.forEach((entry) => URL.revokeObjectURL(entry.url)), [previews]);
  const dirty = Boolean(form.productName || form.description || form.categoryId || form.brandId || files.length || specifications.length);
  useEffect(() => {
    if (!dirty) return;
    const warn = (event) => { event.preventDefault(); event.returnValue = ''; };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty]);
  const brandOptions = (brands.data?.content || []).filter((brand) => brand.status === 'ACTIVE');
  const activeCategories = (categories.data || []).filter((category) => category.status === 'ACTIVE');
  const primaryIndex = Math.max(0, files.findIndex((entry) => entry.id === primaryId));

  function change(event) {
    const { name, value } = event.target;
    setForm((current) => ({ ...current, [name]: value }));
    if (name === 'brandId') setSelectedBrand(brandOptions.find((brand) => brand.brandId === value) || null);
    setError('');
  }
  function chooseImages(event) {
    const picked = Array.from(event.target.files || []);
    event.target.value = '';
    if (!picked.length) return;
    if (files.length + picked.length > IMAGE_LIMIT) { setImageError('Chỉ được chọn tối đa 10 ảnh.'); return; }
    if (picked.some((file) => !['image/jpeg', 'image/png'].includes(file.type) || !file.size || file.size > FILE_LIMIT)) {
      setImageError('Chọn ảnh JPG/PNG có dữ liệu, mỗi ảnh tối đa 5 MB.'); return;
    }
    const entries = picked.map((file) => ({ id: crypto.randomUUID(), file }));
    setFiles((current) => [...current, ...entries]);
    if (!files.length) setPrimaryId(entries[0].id);
    setImageError('');
  }
  function removeImage(id) {
    const next = files.filter((entry) => entry.id !== id);
    setFiles(next);
    if (primaryId === id) setPrimaryId(next[0]?.id || '');
    setImageError('');
  }
  function cancel() {
    if (!dirty || window.confirm('Bạn muốn bỏ các thông tin chưa lưu?')) navigate('/products');
  }
  async function submit(event) {
    event.preventDefault();
    if (submitting) return;
    if (!form.productName.trim()) { setError('Tên sản phẩm không được để trống.'); return; }
    if (!form.categoryId || !form.brandId) { setError('Vui lòng chọn danh mục và thương hiệu.'); return; }
    if (specifications.some((item) => !item.specKey.trim() || !item.specValue.trim())) { setError('Vui lòng nhập tên và giá trị cho mỗi thông số, hoặc xóa dòng không dùng.'); return; }
    setSubmitting(true); setError('');
    try {
      const product = await productApi.createWithImages({ ...form, productName: form.productName.trim(), description: form.description.trim() || null }, files.map((entry) => entry.file), primaryIndex, specifications.map(({ specKey, specValue }) => ({ specKey: specKey.trim(), specValue: specValue.trim() })));
      navigate(`/products/${product.productId}`, { replace: true, state: { createdProduct: true } });
    } catch (failure) { setError(failure.message || 'Không thể tạo sản phẩm. Vui lòng thử lại.'); }
    finally { setSubmitting(false); }
  }

  return <>
    <Link className="product-back-link" to="/products" onClick={(event) => { if (submitting || (dirty && !window.confirm('Bạn muốn bỏ các thông tin chưa lưu?'))) event.preventDefault(); }}>← Danh sách sản phẩm</Link>
    <PageHeader eyebrow="Quản lý danh mục sản phẩm" title="Tạo sản phẩm" description="Điền thông tin sản phẩm và chọn ảnh từ máy tính của bạn." />
    {categories.isLoading ? <p className="panel" role="status">Đang tải danh mục…</p> : categories.error ? <ProductState error title="Chưa tải được danh mục" message={categories.error.message} retry={categories.retry} /> : <form onSubmit={submit} className="product-create-form" aria-busy={submitting}>
      <div className="product-create-main">
        <section className="panel product-create-section" aria-labelledby="product-information">
          <div className="product-create-section-heading"><span className="product-create-step" aria-hidden="true">01</span><div><h2 id="product-information">Thông tin sản phẩm</h2><p>Tên và phân loại giúp khách hàng tìm đúng thiết bị.</p></div><span className="product-create-required">* Bắt buộc</span></div>
          <fieldset disabled={submitting}>
            <label>Tên sản phẩm <span aria-hidden="true">*</span><input autoFocus name="productName" value={form.productName} onChange={change} required maxLength={255} placeholder="Ví dụ: Samsung Galaxy S25" /></label>
            <div className="product-create-fields">
              <label>Danh mục *<select name="categoryId" value={form.categoryId} onChange={change} required><option value="">Chọn danh mục</option>{activeCategories.map((category) => <option key={category.categoryId} value={category.categoryId}>{category.categoryName}</option>)}</select></label>
              <label>Trạng thái<select name="status" value={form.status} onChange={change}><option value="ACTIVE">Đang hoạt động</option><option value="INACTIVE">Ngừng hoạt động</option></select></label>
            </div>
            {!activeCategories.length && <p role="status" className="auth-alert">Chưa có danh mục đang hoạt động. Hãy bổ sung trong trang Danh mục trước.</p>}
            <label>Thương hiệu *<select name="brandId" value={form.brandId} onChange={change} required disabled={brands.isLoading || Boolean(brands.error)}><option value="">Chọn thương hiệu</option>{selectedBrand && !brandOptions.some((brand) => brand.brandId === selectedBrand.brandId) && <option value={selectedBrand.brandId}>{selectedBrand.brandName}</option>}{brandOptions.map((brand) => <option key={brand.brandId} value={brand.brandId}>{brand.brandName}</option>)}</select></label>
            {brands.isLoading && <p role="status" className="muted">Đang tải thương hiệu…</p>}
            {brands.error && <p className="auth-alert" role="alert">{brands.error.message} <button type="button" className="button button-quiet" onClick={brands.retry}>Thử lại</button></p>}
            {brands.data?.totalPages > 1 && <div className="product-create-brand-pages"><button type="button" className="button button-quiet" disabled={brandPage === 0 || brands.isLoading} onClick={() => setBrandPage((page) => page - 1)}>Trước</button><span>Thương hiệu · Trang {brandPage + 1}/{brands.data.totalPages}</span><button type="button" className="button button-quiet" disabled={brandPage + 1 >= brands.data.totalPages || brands.isLoading} onClick={() => setBrandPage((page) => page + 1)}>Sau</button></div>}
            {brands.data && !brands.data.totalElements && <p className="auth-alert">Chưa có thương hiệu để chọn. Cần tạo thương hiệu trước khi tạo sản phẩm.</p>}
            <label>Mô tả<textarea name="description" value={form.description} onChange={change} rows={6} placeholder="Thông tin và đặc điểm của sản phẩm…" /></label>
          </fieldset>
        </section>
        <section className="panel product-create-section" aria-labelledby="product-images">
          <div className="product-create-section-heading"><span className="product-create-step" aria-hidden="true">02</span><div><h2 id="product-images">Hình ảnh sản phẩm</h2><p>Ảnh rõ nét giúp sản phẩm nổi bật hơn.</p></div><span className="product-create-image-count">{files.length}/10 ảnh</span></div>
          <p className="muted" id="image-help">Chọn một hoặc nhiều ảnh JPG/PNG, tối đa 5 MB/ảnh và 20 megapixel. Ảnh chính hiển thị trên danh sách sản phẩm.</p>
          <label className={`product-file-picker${submitting || files.length >= IMAGE_LIMIT ? ' is-disabled' : ''}`}><span className="product-upload-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6"><path d="M12 16V4m-4 4 4-4 4 4M4 15v4a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-4" /></svg></span><strong>Chọn ảnh từ máy tính</strong><span className="product-file-picker-help">Nhấn để mở thư mục và chọn ảnh sản phẩm</span><span className="product-file-picker-button" aria-hidden="true">+ Thêm hình ảnh</span><input type="file" accept="image/jpeg,image/png" multiple onChange={chooseImages} disabled={submitting || files.length >= IMAGE_LIMIT} aria-describedby="image-help" /></label>
          {imageError && <p className="auth-alert" role="alert">{imageError}</p>}
          {!files.length && <p className="product-image-empty">Ảnh đã chọn sẽ hiển thị tại đây. Bạn có thể thêm ảnh sau.</p>}
          <div className="product-upload-grid">{previews.map((entry, index) => <article key={entry.id} className={index === primaryIndex ? 'product-upload-card is-primary' : 'product-upload-card'}>
            <div className="product-upload-preview"><img src={entry.url} alt={`Xem trước ${entry.file.name}`} />{index === primaryIndex && <span className="product-upload-primary-badge">Ảnh chính</span>}</div>
            <p title={entry.file.name}>{entry.file.name}</p>
            <span className="muted">{(entry.file.size / 1024 / 1024).toFixed(2)} MB</span>
            <label><input type="radio" name="primaryImage" checked={index === primaryIndex} onChange={() => setPrimaryId(entry.id)} disabled={submitting} />Ảnh chính</label>
            <button type="button" className="button button-quiet" disabled={submitting} onClick={() => removeImage(entry.id)} aria-label={`Bỏ ảnh ${entry.file.name}`}>Bỏ ảnh</button>
          </article>)}</div>
        </section>
        <SpecificationsEditor value={specifications} onChange={setSpecifications} disabled={submitting} />
      </div>
      <aside className="panel product-create-summary">
        <div className="product-create-summary-heading"><span className="eyebrow">XEM TRƯỚC</span><h2>Sản phẩm mới</h2></div>
        <div className="product-create-cover">{previews[primaryIndex] ? <img src={previews[primaryIndex].url} alt="Ảnh chính đã chọn" /> : <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.3" aria-hidden="true"><rect x="3" y="3" width="18" height="18" rx="3" /><circle cx="8" cy="8" r="1.5" /><path d="m3 17 5-5 4 4 4-6 5 7" /></svg>}</div>
        <h3 className="product-create-preview-name">{form.productName.trim() || 'Tên sản phẩm của bạn'}</h3>
        <span className={`product-create-status ${form.status === 'ACTIVE' ? 'is-active' : ''}`}>{form.status === 'ACTIVE' ? 'Đang hoạt động' : 'Ngừng hoạt động'}</span>
        <dl className="product-create-review"><div><dt>Danh mục</dt><dd>{activeCategories.find((category) => category.categoryId === form.categoryId)?.categoryName || 'Chưa chọn'}</dd></div><div><dt>Thương hiệu</dt><dd>{selectedBrand?.brandName || 'Chưa chọn'}</dd></div><div><dt>Hình ảnh</dt><dd>{files.length} ảnh đã chọn</dd></div><div><dt>Thông số</dt><dd>{specifications.length} thông số</dd></div></dl>
        <div className="product-create-tip"><strong>Bước tiếp theo</strong><p>Thêm biến thể để thiết lập SKU và giá bán, sau đó nhập hàng để có tồn kho.</p></div>
        {error && <p className="auth-alert" role="alert">{error}</p>}
        <button className="button" type="submit" disabled={submitting || Boolean(brands.error) || !activeCategories.length}>{submitting ? 'Đang tạo và tải ảnh…' : 'Lưu sản phẩm'}</button>
        <button className="button button-quiet" type="button" disabled={submitting} onClick={cancel}>Hủy tạo sản phẩm</button>
      </aside>
    </form>}
  </>;
}
