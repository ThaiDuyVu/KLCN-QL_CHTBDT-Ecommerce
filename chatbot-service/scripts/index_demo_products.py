"""Index existing local catalog using the documented synthetic customer account.

Explicit development operation. Never stores a bearer token in .env.
"""
import httpx
from app.shared.config import get_settings
from app.features.product_advisor.backend import backend_cookies
from app.features.product_advisor.index_products import main as index_products


def main():
    settings = get_settings()
    if settings.app_env != 'development':
        raise SystemExit('Chỉ dùng cho môi trường development có tài khoản seed.customer1.')
    with httpx.Client(base_url=settings.backend_base_url, timeout=10) as client:
        client.get('/api/auth/csrf').raise_for_status()
        csrf = client.cookies.get('XSRF-TOKEN')
        client.post('/api/auth/login', json={'username':'seed.customer1','password':'123'},
                    headers={'X-XSRF-TOKEN':csrf}).raise_for_status()
        token = backend_cookies.set(dict(client.cookies))
        try:
            index_products()
        finally:
            backend_cookies.reset(token)


if __name__ == '__main__':
    main()
