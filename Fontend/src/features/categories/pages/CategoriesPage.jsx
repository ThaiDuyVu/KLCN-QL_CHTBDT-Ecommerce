import { useCallback, useEffect, useMemo, useState } from 'react';
import PageHeader from '../../../components/ui/PageHeader';
import { useAuth } from '../../../hooks/useAuth';
import { ROLES } from '../../../config/projectConfig';
import { categoryApi } from '../api/categoryApi';
import CategoryForm from '../components/CategoryForm';
import CategoryTable from '../components/CategoryTable';
import CategoryTree from '../components/CategoryTree';
import '../categories.css';

const editableRoles = new Set([ROLES.ADMIN, ROLES.MANAGER]);

export default function CategoriesPage() {
  const { user } = useAuth();
  const [categories, setCategories] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState('');
  const [editor, setEditor] = useState(null);
  const [isEditorOpen, setIsEditorOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [formError, setFormError] = useState('');
  const [pendingDelete, setPendingDelete] = useState(null);

  const load = useCallback(async (signal) => {
    try {
      setCategories(await categoryApi.list(signal));
    } catch (requestError) {
      if (requestError.name !== 'AbortError') setError(requestError.message);
    } finally {
      if (!signal?.aborted) setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    categoryApi.list(controller.signal)
      .then(setCategories)
      .catch((requestError) => {
        if (requestError.name !== 'AbortError') setError(requestError.message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsLoading(false);
      });
    return () => controller.abort();
  }, []);

  const categoryNames = useMemo(
    () => new Map(categories.map((category) => [category.categoryId, category.categoryName])),
    [categories],
  );
  const visibleCategories = useMemo(() => categories
    .filter((category) => !status || category.status === status)
    .filter((category) => {
      const term = keyword.trim().toLocaleLowerCase('vi');
      return !term || category.categoryName.toLocaleLowerCase('vi').includes(term)
        || (category.description || '').toLocaleLowerCase('vi').includes(term);
    })
    .sort((left, right) => left.categoryName.localeCompare(right.categoryName, 'vi')),
  [categories, keyword, status]);
  const canManage = editableRoles.has(user?.roleName);

  function openCreate() {
    setEditor(null);
    setFormError('');
    setIsEditorOpen(true);
  }

  function openEdit(category) {
    setEditor(category);
    setFormError('');
    setIsEditorOpen(true);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  async function save(payload) {
    setIsSubmitting(true);
    setFormError('');
    try {
      if (editor) await categoryApi.update(editor.categoryId, payload);
      else await categoryApi.create(payload);
      setIsEditorOpen(false);
      setEditor(null);
      await load();
    } catch (requestError) {
      setFormError(requestError.message);
    } finally {
      setIsSubmitting(false);
    }
  }

  async function remove() {
    if (!pendingDelete) return;
    setIsSubmitting(true);
    setError('');
    try {
      await categoryApi.remove(pendingDelete.categoryId);
      setPendingDelete(null);
      await load();
    } catch (requestError) {
      setError(requestError.message);
      setPendingDelete(null);
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <>
      <PageHeader eyebrow="Product Catalog" title="Danh mục" description="Xem cấu trúc cha/con và quản lý nhóm sản phẩm." />
      {canManage && isEditorOpen && <CategoryForm key={editor?.categoryId || 'new'} categories={categories} initialValue={editor}
        isSubmitting={isSubmitting} serverError={formError} onSubmit={save}
        onCancel={() => { setIsEditorOpen(false); setEditor(null); setFormError(''); }} />}
      {pendingDelete && <section className="panel category-delete-confirm" role="alertdialog" aria-modal="true"
        aria-labelledby="category-delete-title">
        <div><strong id="category-delete-title">Xóa “{pendingDelete.categoryName}”?</strong>
          <p className="muted">Không thể xóa nếu danh mục đang có danh mục con hoặc được sản phẩm tham chiếu.</p></div>
        <div className="category-row-actions">
          <button className="button category-delete-button" type="button" onClick={remove} disabled={isSubmitting}>{isSubmitting ? 'Đang xóa…' : 'Xác nhận xóa'}</button>
          <button className="button button-quiet" type="button" onClick={() => setPendingDelete(null)} disabled={isSubmitting}>Hủy</button>
        </div>
      </section>}
      <section className="panel category-toolbar">
        <div className="category-filters">
          <label>Tìm danh mục<input type="search" value={keyword} onChange={(event) => setKeyword(event.target.value)} placeholder="Tên hoặc mô tả…" /></label>
          <label>Trạng thái<select value={status} onChange={(event) => setStatus(event.target.value)}>
            <option value="">Tất cả</option><option value="ACTIVE">Đang hoạt động</option><option value="INACTIVE">Ngừng hoạt động</option>
          </select></label>
        </div>
        {canManage && <button className="button" type="button" onClick={openCreate}>Tạo danh mục</button>}
      </section>
      {isLoading && <section className="panel category-page-state" role="status">Đang tải danh mục…</section>}
      {!isLoading && error && <section className="panel category-page-state"><p className="auth-alert" role="alert">{error}</p>
        <button className="button button-quiet" type="button" onClick={() => { setError(''); setIsLoading(true); load(); }}>Thử lại</button></section>}
      {!isLoading && !error && categories.length === 0 && <section className="panel empty-state"><h2>Chưa có danh mục</h2><p className="muted">Danh sách hiện chưa có dữ liệu.</p></section>}
      {!isLoading && !error && categories.length > 0 && <div className="category-layout">
        <CategoryTree categories={categories} />
        {visibleCategories.length > 0
          ? <CategoryTable categories={visibleCategories} categoryNames={categoryNames} canManage={canManage} onEdit={openEdit} onDelete={setPendingDelete} />
          : <section className="panel empty-state"><h2>Không tìm thấy danh mục</h2><p className="muted">Hãy thay đổi từ khóa hoặc bộ lọc trạng thái.</p></section>}
      </div>}
    </>
  );
}
