#!/usr/bin/env python3
"""Expose only VNPAY's GET IPN endpoint through a development HTTPS tunnel."""
import argparse
import shutil
import subprocess
import threading
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--backend-port', type=int, default=8080)
    parser.add_argument('--proxy-port', type=int, default=18080)
    args = parser.parse_args()
    if not shutil.which('cloudflared'):
        parser.error('cloudflared is required: brew install cloudflared')
    upstream = f'http://127.0.0.1:{args.backend_port}'

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *unused):
            pass  # Do not log callback query strings or signatures.

        def do_GET(self):
            if urlsplit(self.path).path != '/api/payments/vnpay/ipn':
                self.send_error(404)
                return
            if len(self.path) > 8192:
                self.send_error(414)
                return
            try:
                request = urllib.request.Request(upstream + self.path, headers={'Accept': 'application/json'})
                with urllib.request.urlopen(request, timeout=20) as response:
                    body = response.read()
                    self.send_response(response.status)
                    self.send_header('Content-Type', 'application/json')
                    self.send_header('Content-Length', str(len(body)))
                    self.send_header('Cache-Control', 'no-store')
                    self.end_headers()
                    self.wfile.write(body)
            except (urllib.error.URLError, TimeoutError):
                self.send_error(502, 'Backend unavailable')

    server = ThreadingHTTPServer(('127.0.0.1', args.proxy_port), Handler)
    server.daemon_threads = True
    threading.Thread(target=server.serve_forever, daemon=True).start()
    process = subprocess.Popen([
        'cloudflared', 'tunnel', '--no-autoupdate', '--url',
        f'http://127.0.0.1:{args.proxy_port}',
    ])
    try:
        process.wait()
    except KeyboardInterrupt:
        process.terminate()
    finally:
        if process.poll() is None:
            process.terminate()
        process.wait()
        server.shutdown()
        server.server_close()


if __name__ == '__main__':
    main()
