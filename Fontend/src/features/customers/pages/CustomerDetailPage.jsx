import { useCallback, useState } from 'react';
import { Link, useLocation, useParams, useSearchParams } from 'react-router';
import PageHeader from '../../../components/ui/PageHeader';
import { useAuth } from '../../../hooks/useAuth';
import { ROLES } from '../../../config/projectConfig';
import useProductRequest from '../../products/hooks/useProductRequest';
import { customerApi } from '../api/customerApi';
import CustomerProfile from '../components/CustomerProfile';
import CustomerHistory from '../components/CustomerHistory';
import CustomerForm from '../components/CustomerForm';
import { CUSTOMER_EDITOR_ROLES, pageNumber } from '../components/customerFormat';
import '../customers.css';

export default function CustomerDetailPage() {
  const { customerId } = useParams();
  const { user } = useAuth();
  const location = useLocation();
  const [params, setParams] = useSearchParams();
  const canViewInstallments = [ROLES.ADMIN, ROLES.MANAGER].includes(user?.roleName);
  const tabs = [{ key: 'orders', label: 'Đơn hàng' }, { key: 'warranties', label: 'Bảo hành' }, ...(canViewInstallments ? [{ key: 'installments', label: 'Trả góp' }] : [])];
  const resource = tabs.some((tab) => tab.key === params.get('tab')) ? params.get('tab') : 'orders';
  const page = pageNumber(params.get(`${resource}Page`), 10);
  const load = useCallback((signal) => customerApi.detail(customerId, signal), [customerId]);
  const result = useProductRequest(`customer:${customerId}`, load);
  const [editing, setEditing] = useState(false);
  const [notice, setNotice] = useState(location.state?.created ? 'Đã tạo khách hàng.' : '');
  const canEdit = CUSTOMER_EDITOR_ROLES.includes(user?.roleName);
  function chooseTab(key) {
    const next = new URLSearchParams(params);
    next.set('tab', key);
    setParams(next);
  }
  function changePage(nextPage) {
    const next = new URLSearchParams(params);
    next.set(`${resource}Page`, String(nextPage));
    setParams(next);
  }
  return <div className="customers-page">
    <Link className="customer-back-link" to={`/customers${location.state?.listSearch || ''}`}>← Danh sách khách hàng</Link>
    <PageHeader eyebrow="Vận hành / Khách hàng" title="Chi tiết khách hàng" description="Hồ sơ và lịch sử nghiệp vụ; quản lý tài khoản tiếp tục nằm trong User Management." />
    {result.isLoading ? <p className="panel customer-state" role="status">Đang tải hồ sơ khách hàng…</p> : result.error ? <div className="panel customer-results"><div className="auth-alert" role="alert"><p>{result.error.message}</p><button className="button button-quiet" onClick={result.retry}>Thử lại</button></div></div> : <>
      {notice && <p className="commerce-notice" role="status">{notice}</p>}
      {editing ? <CustomerForm customer={result.data} onClose={() => setEditing(false)} onSaved={() => { setEditing(false); setNotice('Đã cập nhật thông tin khách hàng.'); result.retry(); }} />
        : <CustomerProfile customer={result.data} canManageUsers={user?.roleName === ROLES.ADMIN} onEdit={canEdit ? () => { setEditing(true); setNotice(''); } : undefined} />}
      <nav className="customer-history-tabs" aria-label="Lịch sử khách hàng">{tabs.map((tab) => <button type="button" key={tab.key} onClick={() => chooseTab(tab.key)} className={resource === tab.key ? 'is-active' : ''} aria-current={resource === tab.key ? 'page' : undefined}>{tab.label}</button>)}</nav>
      <CustomerHistory key={`${customerId}:${resource}`} customerId={customerId} resource={resource} page={page} onPageChange={changePage} />
    </>}
  </div>;
}
