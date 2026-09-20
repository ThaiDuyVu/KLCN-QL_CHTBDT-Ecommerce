import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, createApiClient } from './apiClient';

const config = {
  apiBaseUrl: 'https://example.test/api',
  csrfCookieName: 'XSRF-TOKEN',
  csrfHeaderName: 'X-XSRF-TOKEN',
};

afterEach(() => {
  vi.unstubAllGlobals();
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/';
});

describe('Shared API transport', () => {
  it('blocks requests before fetch when no base URL is configured', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    await expect(createApiClient({ ...config, apiBaseUrl: '' })('/products')).rejects.toBeInstanceOf(ApiError);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('sends credentials and CSRF header for a JSON mutation', async () => {
    document.cookie = 'XSRF-TOKEN=demo%20csrf; path=/';
    const fetchMock = vi.fn().mockResolvedValue(new Response('{"id":1}', {
      status: 201, headers: { 'Content-Type': 'application/json' },
    }));
    vi.stubGlobal('fetch', fetchMock);
    await expect(createApiClient(config)('/products', { method: 'POST', body: { name: 'Demo' } }))
      .resolves.toEqual({ id: 1 });
    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe('https://example.test/api/products');
    expect(options.credentials).toBe('include');
    expect(options.headers.get('X-XSRF-TOKEN')).toBe('demo csrf');
    expect(options.headers.get('Content-Type')).toBe('application/json');
    expect(options.body).toBe('{"name":"Demo"}');
  });

  it('returns null for a no-content response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })));
    await expect(createApiClient(config)('/products', { method: 'DELETE' })).resolves.toBeNull();
  });

  it('retains HTTP status and structured error data', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{"message":"Không được phép"}', {
      status: 403, headers: { 'Content-Type': 'application/json' },
    })));
    await expect(createApiClient(config)('/products')).rejects.toMatchObject({
      name: 'ApiError', status: 403, message: 'Không được phép', data: { message: 'Không được phép' },
    });
  });

  it('normalizes network errors', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    await expect(createApiClient(config)('/products')).rejects.toMatchObject({ name: 'ApiError', status: 0 });
  });

  it('preserves cancellation for callers', async () => {
    const error = new DOMException('Cancelled', 'AbortError');
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(error));
    await expect(createApiClient(config)('/products')).rejects.toBe(error);
  });

  it.each(['https://other.test/products', '//other.test/products'])('rejects external path %s', async (path) => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    await expect(createApiClient(config)(path)).rejects.toBeInstanceOf(ApiError);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
