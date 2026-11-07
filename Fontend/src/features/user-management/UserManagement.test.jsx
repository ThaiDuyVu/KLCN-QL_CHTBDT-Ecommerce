import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router';
import { AuthContext } from '../../auth/AuthContext';
import UsersPanel from './components/UsersPanel';
import RolesPermissionsPanel from './components/RolesPermissionsPanel';
import { userManagementApi as api } from './api/userManagementApi';
import { warehouseApi } from '../warehouses/api/warehouseApi';
vi.mock('../warehouses/api/warehouseApi', () => ({ warehouseApi: { listAll: vi.fn() } }));
vi.mock('./api/userManagementApi', () => ({ userManagementApi: {
  users: vi.fn(), user: vi.fn(), update: vi.fn(), status: vi.fn(), role: vi.fn(),
  permissions: vi.fn(), rolePermissions: vi.fn(), updatePermissions: vi.fn(),
  createEmployee: vi.fn(), employeeByUser: vi.fn(), updateEmployee: vi.fn(),
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
  warehouseApi.listAll.mockResolvedValue([{ warehouseId: 'w1', warehouseName: 'Chi nhánh Quận 1', status: 'ACTIVE' }]);
  api.employeeByUser.mockResolvedValue({ employeeId: 'employee-id', employeeCode: 'NV0001', fullName: 'Staff', position: 'Nhân viên cửa hàng' });
});
it('creates an employee with a job title and never offers the customer role', async () => {
  const withCustomer = [...roles, { roleId: 'customer-id', roleName: 'CUSTOMER' }, { roleId: 'keeper-id', roleName: 'WAREHOUSE_KEEPER', description: 'Thủ kho' }];
  api.createEmployee.mockResolvedValue({ employeeId: 'e2', employeeCode: 'NV0002', fullName: 'Trần Thị Bình', username: 'thukho.q1' });
  mount(<UsersPanel roles={withCustomer} />); await screen.findByText('staff@example.com');
  fireEvent.click(screen.getByRole('button', { name: '+ Thêm nhân viên' }));
  const select = screen.getByLabelText('Chức danh');
  expect([...select.options].map((o) => o.value)).not.toContain('customer-id');
  fireEvent.change(screen.getByLabelText('Họ và tên'), { target: { value: ' Trần Thị Bình ' } });
  fireEvent.change(select, { target: { value: 'keeper-id' } });
  fireEvent.change(screen.getByLabelText('Tên đăng nhập'), { target: { value: 'thukho.q1' } });
  fireEvent.change(screen.getByLabelText('Mật khẩu ban đầu'), { target: { value: 'MatKhau123' } });
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'thukho@dienmay.test' } });
  fireEvent.click(screen.getByRole('button', { name: 'Tạo nhân viên' }));
  await waitFor(() => expect(api.createEmployee).toHaveBeenCalledWith(expect.objectContaining({
    fullName: 'Trần Thị Bình', roleId: 'keeper-id', username: 'thukho.q1', password: 'MatKhau123', email: 'thukho@dienmay.test', employeeCode: null, phone: null, warehouseId: null })));
  expect(await screen.findByText('Đã tạo nhân viên Trần Thị Bình (NV0002) với tài khoản thukho.q1.')).toBeInTheDocument();
  expect(api.users).toHaveBeenCalledTimes(2);
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
