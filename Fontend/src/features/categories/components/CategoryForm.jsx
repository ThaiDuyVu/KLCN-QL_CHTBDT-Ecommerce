import { useMemo, useState } from 'react';

function descendantIds(categoryId, categories) {
  if (!categoryId) return new Set();
  const result = new Set([categoryId]);
  let changed = true;
  while (changed) {
    changed = false;
    categories.forEach((category) => {
      if (category.parentId && result.has(category.parentId) && !result.has(category.categoryId)) {
        result.add(category.categoryId);
        changed = true;
      }
    });
  }
  return result;
}

export default function CategoryForm({ categories, initialValue, isSubmitting, serverError, onSubmit, onCancel }) {
  const [form, setForm] = useState(() => ({
    categoryName: initialValue?.categoryName || '',
    description: initialValue?.description || '',
    parentId: initialValue?.parentId || '',
    status: initialValue?.status || 'ACTIVE',
  }));

  const excludedParents = useMemo(
    () => descendantIds(initialValue?.categoryId, categories),
    [categories, initialValue?.categoryId],
  );
  const parentOptions = categories
    .filter((category) => !excludedParents.has(category.categoryId))
    .sort((left, right) => left.categoryName.localeCompare(right.categoryName, 'vi'));

  function change(event) {
    setForm((current) => ({ ...current, [event.target.name]: event.target.value }));
  }

  function submit(event) {
    event.preventDefault();
    onSubmit({
      categoryName: form.categoryName.trim(),
      description: form.description.trim() || null,
      parentId: form.parentId || null,
      status: form.status,
    });
  }

  return (
    <section className="panel category-editor" aria-labelledby="category-editor-title">
      <div className="category-section-heading">
        <div>
          <h2 id="category-editor-title">{initialValue ? 'Sửa danh mục' : 'Tạo danh mục'}</h2>
          <p className="muted">Thông tin cha/con được kiểm tra lại ở backend trước khi lưu.</p>
        </div>
        <button className="button button-quiet" type="button" onClick={onCancel} disabled={isSubmitting}>Đóng</button>
      </div>
      <form className="category-form" onSubmit={submit}>
        <label>Tên danh mục
          <input name="categoryName" value={form.categoryName} onChange={change} maxLength={255} required autoFocus />
        </label>
        <label>Danh mục cha
          <select name="parentId" value={form.parentId} onChange={change}>
            <option value="">Danh mục gốc</option>
            {parentOptions.map((category) => (
              <option key={category.categoryId} value={category.categoryId}>{category.categoryName}</option>
            ))}
          </select>
        </label>
        <label>Trạng thái
          <select name="status" value={form.status} onChange={change}>
            <option value="ACTIVE">Đang hoạt động</option>
            <option value="INACTIVE">Ngừng hoạt động</option>
          </select>
        </label>
        <label className="category-form-description">Mô tả
          <textarea name="description" value={form.description} onChange={change} rows="4" />
        </label>
        {serverError && <p className="auth-alert category-form-error" role="alert">{serverError}</p>}
        <div className="category-form-actions">
          <button className="button" type="submit" disabled={isSubmitting || !form.categoryName.trim()}>
            {isSubmitting ? 'Đang lưu…' : initialValue ? 'Lưu thay đổi' : 'Tạo danh mục'}
          </button>
          <button className="button button-quiet" type="button" onClick={onCancel} disabled={isSubmitting}>Hủy</button>
        </div>
      </form>
    </section>
  );
}
