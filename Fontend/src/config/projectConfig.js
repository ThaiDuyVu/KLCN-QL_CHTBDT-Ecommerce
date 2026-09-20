export const ROLES = Object.freeze({
  ADMIN: 'ADMIN',
  MANAGER: 'MANAGER',
  STAFF: 'STAFF',
  CUSTOMER: 'CUSTOMER',
});

export const MANAGEMENT_ROLES = [ROLES.ADMIN, ROLES.MANAGER, ROLES.STAFF];

export const projectConfig = Object.freeze({
  appName: 'Điện Việt',
  apiBaseUrl: (import.meta.env.VITE_API_BASE_URL || '/api').trim().replace(/\/+$/, ''),
  csrfCookieName: import.meta.env.VITE_CSRF_COOKIE_NAME || 'XSRF-TOKEN',
  csrfHeaderName: import.meta.env.VITE_CSRF_HEADER_NAME || 'X-XSRF-TOKEN',
});
