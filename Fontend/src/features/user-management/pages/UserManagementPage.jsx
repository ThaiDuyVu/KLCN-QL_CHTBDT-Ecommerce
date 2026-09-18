import { useState } from 'react';
import PageHeader from '../../../components/ui/PageHeader';
import { userManagementApi as api } from '../api/userManagementApi';
import useManagementRequest from '../hooks/useManagementRequest';
import RequestState from '../components/RequestState';
import UsersPanel from '../components/UsersPanel';
import RolesPermissionsPanel from '../components/RolesPermissionsPanel';
import '../user-management.css';
export default function UserManagementPage() {
  const [section, setSection] = useState('users');
  const { data: roles, error, loading, reload } = useManagementRequest('roles', api.roles);
  return <>
    <PageHeader title="Quản lý người dùng" description="Quản lý thông tin, trạng thái tài khoản, role và permission." />
    <div className="management-tabs" aria-label="Khu vực quản trị">
      <button className={`button ${section === 'users' ? '' : 'button-quiet'}`} aria-pressed={section === 'users'} onClick={() => setSection('users')}>Người dùng</button>
      <button className={`button ${section === 'roles' ? '' : 'button-quiet'}`} aria-pressed={section === 'roles'} onClick={() => setSection('roles')}>Roles & Permissions</button>
    </div>
    <RequestState loading={loading} error={error} retry={reload} />
    {!loading && !error && (roles?.length ? section === 'users' ? <UsersPanel roles={roles} /> : <RolesPermissionsPanel roles={roles} /> : <p>Chưa có role trong hệ thống.</p>)}
  </>;
}
