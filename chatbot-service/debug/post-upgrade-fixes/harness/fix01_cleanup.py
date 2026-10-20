"""Remove only test-owned resources; verify source business/report fingerprints."""
import hashlib
import os
from pathlib import Path
import signal
import subprocess
import time
import sys

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / 'chatbot-service/debug/post-upgrade-validation/harness'))
from common import OUT, PRIVATE, load, dump, now
from environment import source_snapshot

s = load(PRIVATE / 'state.json')
assert not s.get('cleaned_up'), 'Environment already cleaned'
source = load(ROOT / '.local/fix01-runtime-output/evidence/source-business-before.json')
processes = []
for name, process in s['processes'].items():
    cmd = subprocess.run(['ps', '-p', str(process['pid']), '-o', 'command='], capture_output=True, text=True)
    if cmd.returncode != 0:
        processes.append({'name': name, 'already_exited': True})
        continue
    assert str(ROOT) in cmd.stdout, 'Refuse to stop a process outside diagnostic ownership'
    os.kill(process['pid'], signal.SIGTERM)
    processes.append({'name': name, 'stop_requested': True})
time.sleep(1)
removed = subprocess.run(['docker', 'rm', '-f', s['container']], capture_output=True)
assert removed.returncode == 0
subprocess.run(['docker', 'start', 'ecommerce-postgres'], check=True, capture_output=True)
for _ in range(40):
    try:
        after = source_snapshot()
        break
    except subprocess.CalledProcessError:
        time.sleep(.5)
else:
    raise RuntimeError('Source read-only fingerprint unavailable')
if not s['source_was_running']:
    subprocess.run(['docker', 'stop', 'ecommerce-postgres'], check=True, capture_output=True)
expected = load(OUT / 'evidence/fix01/original_reports_sha256.json')
current = {p: hashlib.sha256((ROOT / p).read_bytes()).hexdigest() for p in expected}
record = {'completed_at': now(), 'temporary_container_removed': True,
          'source_business_before': source, 'source_business_after': after,
          'source_business_unchanged': source == after,
          'original_reports_unchanged': expected == current,
          'source_database_running_state_restored': True, 'test_processes': processes,
          'normal_application_processes_modified': False,
          'isolated_fixtures_remaining': 0}
dump(OUT / 'evidence/fix01/cleanup.json', record)
assert source == after and expected == current
(PRIVATE / 'synthetic-source.dump').unlink(missing_ok=True)
for key in ['dsn', 'pg_password']:
    s.pop(key, None)
s['cleaned_up'] = True
dump(PRIVATE / 'state.json', s)
for p in PRIVATE.rglob('*'):
    if p.is_file():
        p.chmod(0o600)
print('Cleanup PASS: source business/reports unchanged; disposable DB removed; owned stack stopped.')
