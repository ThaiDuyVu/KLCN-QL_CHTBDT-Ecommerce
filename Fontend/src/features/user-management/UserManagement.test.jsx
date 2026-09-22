import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router';
import { AuthContext } from '../../auth/AuthContext';
import UsersPanel from './components/UsersPanel';
import RolesPermissionsPanel from './components/RolesPermissionsPanel';
import { userManagementApi as api } from './api/userManagementApi';
vi.mock('./api/userManagementApi', () => ({ userManagementApi: {
  users: vi.fn(), user: vi.fn(), update: vi.fn(), status: vi.fn(), role: vi.fn(),
  permissions: vi.fn(), rolePermissions: vi.fn(), updatePermissions: vi.fn(),
} }));
const roles = [{ roleId: 'staff-id', roleName: 'STAFF' }, { roleId: 'manager-id', roleName: 'MANAGER' }, { roleId: 'admin-id', roleName: 'ADMIN' }];
const target = { userId: 'user-id', username: 'staff', email: 'staff@example.com', displayName: 'Staff', phone: '0900', status: 'ACTIVE', roleId: 'staff-id', roleName: 'STAFF' };
function mount(child) { return render(<MemoryRouter><AuthContext.Provider value={{ user: { userId: 'admin-user', roleName: 'ADMIN' }, refreshSession: vi.fn(), invalidateSession: vi.fn() }}>{child}</AuthContext.Provider></MemoryRouter>); }
afterEach(() => { cleanup(); vi.restoreAllMocks(); });
beforeEach(() => {
  vi.resetAllMocks(); vi.spyOn(window, 'confirm').mockReturnValue(true);
  api.users.mockResolvedValue({ content: [target], page: 0, size: 20, totalElements: 1, totalPages: 1 });
  api.permissions.mockResolvedValue([{ permissionId: 'view-id', permissionName: 'USER_VIEW' }]);
  api.rolePermissions.mockResolvedValue([]);
});
it('searches with keyword, status and role filters', async () => {
  mount(<UsersPanel roles={roles} />); await screen.findByText('staff@example.com');
  fireEvent.change(screen.getByLabelText('Tìm người dùng'), { target: { value: '  staff  ' } });
  fireEvent.change(screen.getByLabelText('Trạng thái'), { target: { value: 'LOCKED' } });
  fireEvent.change(screen.getByLabelText('Role'), { target: { value: 'staff-id' } });
  fireEvent.click(screen.getByRole('button', { name: 'Tìm kiếm' }));
  await waitFor(() => expect(api.users).toHaveBeenLastCalledWith({ page: 0, size: 20, keyword: 'staff', status: 'LOCKED', roleId: 'staff-id' }, expect.anything()));
});
it('displays duplicate email errors without reporting success', async () => {
  api.update.mockRejectedValue(new Error('Email đã được sử dụng'));
  mount(<UsersPanel roles={roles} />); fireEvent.click(await screen.findByRole('button', { name: 'Sửa / Role' }));
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'other@example.com' } });
  fireEvent.click(screen.getByRole('button', { name: 'Lưu thông tin' }));
  expect(await screen.findByRole('alert')).toHaveTextContent('Email đã được sử dụng');
  expect(screen.queryByText('Đã cập nhật người dùng.')).not.toBeInTheDocument();
});
it('locks users with confirmation and reloads the list', async () => {
  api.status.mockResolvedValue({ ...target, status: 'LOCKED' });
  mount(<UsersPanel roles={roles} />); fireEvent.click(await screen.findByRole('button', { name: 'Khóa' }));
  await waitFor(() => expect(api.status).toHaveBeenCalledWith('user-id', 'LOCKED'));
  expect(window.confirm).toHaveBeenCalled();
  await waitFor(() => expect(api.users).toHaveBeenCalledTimes(2));
});
it('assigns API role ids and displays the updated role', async () => {
  const updated = { ...target, roleId: 'manager-id', roleName: 'MANAGER' };
  api.user.mockResolvedValue(updated); api.role.mockResolvedValue(null);
  mount(<UsersPanel roles={roles} />); fireEvent.click(await screen.findByRole('button', { name: 'Sửa / Role' }));
  fireEvent.change(screen.getAllByLabelText('Role')[1], { target: { value: 'manager-id' } });
  api.users.mockResolvedValue({ content: [updated], page: 0, size: 20, totalElements: 1, totalPages: 1 });
  fireEvent.click(screen.getByRole('button', { name: 'Đổi role' }));
  await waitFor(() => expect(api.role).toHaveBeenCalledWith('user-id', 'manager-id'));
  expect(await screen.findByRole('cell', { name: 'MANAGER' })).toBeInTheDocument();
});
it('makes ADMIN permission editing read-only', async () => {
  mount(<RolesPermissionsPanel roles={[roles[2]]} />);
  expect(await screen.findByRole('checkbox')).toBeDisabled();
  expect(screen.queryByRole('button', { name: 'Lưu permission' })).not.toBeInTheDocument();
  expect(api.updatePermissions).not.toHaveBeenCalled();
});
it('retains permission save confirmation after reload', async () => {
  api.updatePermissions.mockResolvedValue(null);
  mount(<RolesPermissionsPanel roles={[roles[0]]} />);
  fireEvent.click(await screen.findByRole('checkbox'));
  fireEvent.click(screen.getByRole('button', { name: 'Lưu permission' }));
  await waitFor(() => expect(api.updatePermissions).toHaveBeenCalledWith('staff-id', ['view-id']));
  expect(await screen.findByText('Đã lưu permission.')).toBeInTheDocument();
});
