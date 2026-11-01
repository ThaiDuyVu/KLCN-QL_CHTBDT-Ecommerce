export const ROLES = Object.freeze({
  ADMIN: 'ADMIN',
  MANAGER: 'MANAGER',
  STAFF: 'STAFF',
  SALES_STAFF: 'SALES_STAFF',
  WAREHOUSE_KEEPER: 'WAREHOUSE_KEEPER',
  WARRANTY_TECHNICIAN: 'WARRANTY_TECHNICIAN',
  DELIVERY_STAFF: 'DELIVERY_STAFF',
  CUSTOMER: 'CUSTOMER',
});

// STAFF is the general employee role; the job titles follow the report's actors (mục 1.3.1).
const GENERAL_STAFF = [ROLES.ADMIN, ROLES.MANAGER, ROLES.STAFF];
export const MANAGEMENT_ROLES = [...GENERAL_STAFF, ROLES.SALES_STAFF, ROLES.WAREHOUSE_KEEPER, ROLES.WARRANTY_TECHNICIAN, ROLES.DELIVERY_STAFF];
// Same groups as the backend @RequireAnyAuthority lists.
export const ORDER_ROLES = [...GENERAL_STAFF, ROLES.SALES_STAFF, ROLES.WAREHOUSE_KEEPER, ROLES.DELIVERY_STAFF];
export const CUSTOMER_ROLES = [...GENERAL_STAFF, ROLES.SALES_STAFF, ROLES.WARRANTY_TECHNICIAN];
export const INVENTORY_ROLES = [...GENERAL_STAFF, ROLES.WAREHOUSE_KEEPER, ROLES.SALES_STAFF];
export const GOODS_RECEIPT_ROLES = [...GENERAL_STAFF, ROLES.WAREHOUSE_KEEPER];
export const SERIAL_ROLES = [...GENERAL_STAFF, ROLES.WAREHOUSE_KEEPER, ROLES.SALES_STAFF, ROLES.WARRANTY_TECHNICIAN];
export const WARRANTY_ROLES = [...GENERAL_STAFF, ROLES.WARRANTY_TECHNICIAN];

export const ROLE_LABELS = Object.freeze({
  ADMIN: 'Admin',
  MANAGER: 'Quản lý cửa hàng',
  STAFF: 'Nhân viên cửa hàng',
  SALES_STAFF: 'Nhân viên bán hàng',
  WAREHOUSE_KEEPER: 'Thủ kho',
  WARRANTY_TECHNICIAN: 'Nhân viên kỹ thuật/bảo hành',
  DELIVERY_STAFF: 'Nhân viên giao hàng',
  CUSTOMER: 'Khách hàng',
});

export const projectConfig = Object.freeze({
  appName: 'Điện Việt',
  apiBaseUrl: (import.meta.env.VITE_API_BASE_URL || '/api').trim().replace(/\/+$/, ''),
  csrfCookieName: import.meta.env.VITE_CSRF_COOKIE_NAME || 'XSRF-TOKEN',
  csrfHeaderName: import.meta.env.VITE_CSRF_HEADER_NAME || 'X-XSRF-TOKEN',
});
