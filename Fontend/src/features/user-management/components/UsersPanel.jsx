import { useCallback, useState } from 'react';
import { useSearchParams } from 'react-router';
import { useAuth } from '../../../hooks/useAuth';
import { userManagementApi as api } from '../api/userManagementApi';
import useManagementRequest from '../hooks/useManagementRequest';
import RequestState from './RequestState';
import UserEditor from './UserEditor';
import StatusBadge from '../../../components/ui/StatusBadge';

const labels = { ACTIVE: 'Đang hoạt động', INACTIVE: 'Ngừng hoạt động', LOCKED: 'Đã khóa' };
const size = 20;
export default function UsersPanel({ roles }) {
  const { user, refreshSession, invalidateSession } = useAuth();
  const [params, setParams] = useSearchParams();
  const number = Number(params.get('page') || 1);
  const page = Number.isSafeInteger(number) && number > 0 && (number - 1) * size <= 2147483647 ? number - 1 : 0;
  const keyword = params.get('keyword') || '';
  const status = Object.hasOwn(labels, params.get('status')) ? params.get('status') : '';
  const roleId = roles.some((role) => role.roleId === params.get('roleId')) ? params.get('roleId') : '';
  const key = JSON.stringify([page, keyword, status, roleId]);
  const load = useCallback((signal) => api.users({ page, size, keyword, status, roleId }, signal), [page, keyword, status, roleId]);
  const { data, error, loading, reload } = useManagementRequest(key, load);
  const [editor, setEditor] = useState(null);
  const [busyId, setBusyId] = useState(null);
  const [editingBusy, setEditingBusy] = useState(false);
  const submitting = editingBusy || Boolean(busyId);
  const [actionError, setActionError] = useState('');
  const [notice, setNotice] = useState('');
  function search(event) {
    event.preventDefault(); const form = new FormData(event.currentTarget); const next = new URLSearchParams();
    for (const name of ['keyword', 'status', 'roleId']) if (form.get(name)?.trim()) next.set(name, form.get(name).trim());
    setParams(next); setEditor(null); setNotice(''); setActionError('');
  }
  function changePage(value) { const next = new URLSearchParams(params); next.set('page', value + 1); setParams(next); setEditor(null); }
  function saved(updated) { setEditor(updated); setNotice('Đã cập nhật người dùng.'); reload(); }
  async function changeStatus(target, nextStatus) {
    if (!window.confirm(`${labels[nextStatus]} tài khoản ${target.username}?${target.userId === user?.userId && nextStatus !== 'ACTIVE' ? ' Bạn sẽ phải đăng nhập lại khi tài khoản được kích hoạt/mở khóa.' : ''}`)) return;
    setBusyId(target.userId); setActionError(''); setNotice('');
    try {
      const updated = await api.status(target.userId, nextStatus);
      if (editor?.userId === updated.userId) setEditor(updated);
      reload(); setNotice(`Đã cập nhật trạng thái ${target.username}.`);
      if (target.userId === user?.userId) await refreshSession();
    } catch (e) { if (e.status === 401) invalidateSession(); setActionError(e.message); }
    finally { setBusyId(null); }
  }
  return <>
    <form className="panel management-filters" onSubmit={search} key={JSON.stringify([keyword, status, roleId])} role="search">
      <label>Tìm người dùng<input disabled={submitting} name="keyword" type="search" placeholder="Username, email, tên hiển thị" defaultValue={keyword} /></label>
      <label>Trạng thái<select disabled={submitting} name="status" defaultValue={status}><option value="">Tất cả</option>{Object.entries(labels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
      <label>Role<select disabled={submitting} name="roleId" defaultValue={roleId}><option value="">Tất cả</option>{roles.map((role) => <option key={role.roleId} value={role.roleId}>{role.roleName}</option>)}</select></label>
      <button className="button" disabled={submitting}>Tìm kiếm</button>
      <button type="button" className="button button-quiet" disabled={submitting} onClick={() => { setParams({}); setEditor(null); }}>Xóa bộ lọc</button>
    </form>
    {actionError && <p className="auth-alert" role="alert">{actionError}</p>}
    {notice && <p role="status">{notice}</p>}
    {editor && <UserEditor key={`${editor.userId}:${editor.email}:${editor.roleId}:${editor.status}`} target={editor} roles={roles} onSaved={saved} disabled={submitting} onSubmitting={setEditingBusy} onClose={() => setEditor(null)} />}
    <RequestState loading={loading} error={error} retry={reload} />
    {!loading && !error && (data?.content?.length ? <>
      <p className="muted" role="status">{data.totalElements} người dùng · Trang {data.page + 1}/{data.totalPages}</p>
      <div className="panel management-table-scroll" tabIndex={0} role="region" aria-label="Danh sách người dùng">
        <table className="management-table"><thead><tr>{['Username', 'Tên hiển thị', 'Email', 'Điện thoại', 'Role', 'Trạng thái', 'Ngày tạo', 'Thao tác'].map((name) => <th scope="col" key={name}>{name}</th>)}</tr></thead>
          <tbody>{data.content.map((target) => <tr key={target.userId}>
            <th scope="row">{target.username}</th><td>{target.displayName || '—'}</td><td>{target.email}</td><td>{target.phone || '—'}</td>
            <td>{target.roleName || 'Chưa gán'}</td><td><StatusBadge status={target.status}>{labels[target.status] || target.status}</StatusBadge></td>
            <td>{target.createdAt ? new Date(target.createdAt).toLocaleString('vi-VN') : '—'}</td>
            <td><div className="management-actions">
              <button className="button button-quiet" disabled={submitting} onClick={() => { setEditor(target); setNotice(''); }}>Sửa / Role</button>
              {target.status !== 'ACTIVE' && <button className="button button-quiet" disabled={submitting} onClick={() => changeStatus(target, 'ACTIVE')}>{target.status === 'LOCKED' ? 'Mở khóa' : 'Kích hoạt'}</button>}
              {target.status === 'ACTIVE' && <button className="button button-quiet" disabled={submitting} onClick={() => changeStatus(target, 'INACTIVE')}>Ngừng hoạt động</button>}
              {target.status !== 'LOCKED' && <button className="button button-quiet" disabled={submitting} onClick={() => changeStatus(target, 'LOCKED')}>Khóa</button>}
              {busyId === target.userId && <span role="status">Đang cập nhật…</span>}
            </div></td>
          </tr>)}</tbody></table>
      </div>
      <nav className="management-pagination" aria-label="Phân trang người dùng">
        <button className="button button-quiet" disabled={submitting || data.page === 0} onClick={() => changePage(data.page - 1)}>Trang trước</button>
        <span>Trang {data.page + 1}/{data.totalPages}</span><button className="button button-quiet" disabled={submitting || data.page + 1 >= data.totalPages} onClick={() => changePage(data.page + 1)}>Trang sau</button>
      </nav>
    </> : <section className="panel management-state"><h2>Không có người dùng phù hợp</h2><p>Thử thay đổi từ khóa hoặc bộ lọc.</p>{page > 0 && <button className="button button-quiet" onClick={() => changePage(0)}>Về trang đầu</button>}</section>)}
  </>;
}
