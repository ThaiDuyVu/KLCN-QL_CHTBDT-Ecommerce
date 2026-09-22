import { projectConfig } from '../config/projectConfig';

export class ApiError extends Error {
  constructor(message, { status = 0, data = null, cause } = {}) {
    super(message, { cause });
    this.name = 'ApiError';
    this.status = status;
    this.data = data;
  }
}

function readCookie(name) {
  const prefix = `${encodeURIComponent(name)}=`;
  const cookie = document.cookie.split('; ').find((entry) => entry.startsWith(prefix));
  if (!cookie) return null;
  try {
    return decodeURIComponent(cookie.slice(prefix.length));
  } catch {
    return null;
  }
}

export function createApiClient(config = projectConfig) {
  let csrfRequest = null;

  async function ensureCsrfToken() {
    if (!readCookie(config.csrfCookieName)) {
      // Share the request to avoid competing CSRF cookies.
      csrfRequest ??= request('/auth/csrf').finally(() => { csrfRequest = null; });
      await csrfRequest;
    }
    const token = readCookie(config.csrfCookieName);
    if (!token) throw new ApiError('Không nhận được cookie CSRF. Kiểm tra cấu hình cookie và địa chỉ backend.');
    return token;
  }

  async function request(path, { method = 'GET', body, headers, signal } = {}) {
    if (!config.apiBaseUrl) throw new ApiError('Chưa cấu hình địa chỉ API.');
    if (!path.startsWith('/') || path.startsWith('//')) {
      throw new ApiError('API path phải là đường dẫn nội bộ bắt đầu bằng /.');
    }

    const requestHeaders = new Headers(headers);
    requestHeaders.set('Accept', 'application/json');
    const verb = method.toUpperCase();
    if (!['GET', 'HEAD', 'OPTIONS'].includes(verb)) {
      requestHeaders.set(config.csrfHeaderName, await ensureCsrfToken());
    }
    if (body !== undefined) requestHeaders.set('Content-Type', 'application/json');

    try {
      const response = await fetch(`${config.apiBaseUrl}${path}`, {
        method: verb,
        credentials: 'include',
        headers: requestHeaders,
        body: body === undefined ? undefined : JSON.stringify(body),
        signal: signal ?? AbortSignal.timeout(15000),
      });
      const text = await response.text();
      let data = text || null;
      if (text && response.headers.get('content-type')?.includes('json')) {
        try {
          data = JSON.parse(text);
        } catch {
          // Several legacy handlers return plain text while content negotiation keeps
          // application/json. Preserve that server message for failed requests.
          if (response.ok) throw new ApiError('API trả về JSON không hợp lệ.', { status: response.status });
          data = text;
        }
      }
      if (!response.ok) {
        throw new ApiError(
          typeof data === 'string' && !data.trim().startsWith('<') ? data
            : typeof data?.message === 'string' ? data.message : `Yêu cầu API thất bại (${response.status}).`,
          { status: response.status, data },
        );
      }
      return data;
    } catch (error) {
      if (error instanceof ApiError || error.name === 'AbortError') throw error;
      throw new ApiError('Không thể kết nối máy chủ. Vui lòng thử lại.', { cause: error });
    }
  }

  return request;
}

export const apiClient = createApiClient();
