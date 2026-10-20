"""Run review follow-up tests with a disposable schema-only PostgreSQL clone."""
import os
from pathlib import Path
import subprocess
import sys
import time
import uuid
import psycopg

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / 'chatbot-service/debug/post-upgrade-validation/harness'))
from common import OUT, dump, run, now

source = 'ecommerce-postgres'
was_running = subprocess.check_output(['docker', 'inspect', '-f', '{{.State.Running}}', source], text=True).strip() == 'true'
name = 'chatbot-fix01-followup-' + uuid.uuid4().hex[:10]
created = False
try:
    subprocess.run(['docker', 'start', source], check=True, capture_output=True)
    for _ in range(40):
        ready = subprocess.run(['docker', 'exec', source, 'sh', '-c', 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"'], capture_output=True)
        if ready.returncode == 0:
            break
        time.sleep(.5)
    schema = subprocess.run(['docker', 'exec', source, 'sh', '-c', 'pg_dump -Fc --schema-only --no-owner --no-acl -U "$POSTGRES_USER" "$POSTGRES_DB"'], check=True, capture_output=True).stdout
    env = dict(os.environ, POSTGRES_PASSWORD=uuid.uuid4().hex)
    subprocess.run(['docker', 'run', '--rm', '-d', '--name', name, '-e', 'POSTGRES_PASSWORD', '-e', 'POSTGRES_DB=followup', '-P', 'pgvector/pgvector:pg16'], env=env, check=True, capture_output=True)
    created = True
    port = int(subprocess.check_output(['docker', 'port', name, '5432/tcp'], text=True).splitlines()[0].rsplit(':', 1)[1])
    dsn = f"postgresql://postgres:{env['POSTGRES_PASSWORD']}@localhost:{port}/followup"
    for _ in range(60):
        try:
            with psycopg.connect(dsn, connect_timeout=1):
                pass
            break
        except psycopg.OperationalError:
            time.sleep(.5)
    subprocess.run(['docker', 'exec', '-i', name, 'pg_restore', '-U', 'postgres', '-d', 'followup', '--no-owner', '--no-acl'], input=schema, check=True, capture_output=True)
    env = dict(os.environ, CONVERSATION_TEST_DATABASE_URL=dsn, KNOWLEDGE_TEST_DATABASE_URL=dsn)
    python = str(ROOT / 'chatbot-service/.venv/bin/python')
    commands = [
        ('followup-focused', [python, '-m', 'pytest', 'tests/test_query_integrity.py', 'tests/test_query_product_v2.py', 'tests/test_query_contracts.py', 'tests/test_real_product_advisor.py', 'tests/test_ai_chat_flow.py', '-q']),
        ('followup-full', [python, '-m', 'pytest', 'tests', '-q']),
    ]
    for id, command in commands:
        code, _ = run(id, command, cwd=ROOT / 'chatbot-service', env=env)
        assert code == 0
finally:
    removed = subprocess.run(['docker', 'rm', '-f', name], capture_output=True).returncode == 0 if created else True
    if not was_running:
        subprocess.run(['docker', 'stop', source], check=True, capture_output=True)
    dump(OUT / 'cleanup.json', {'completed_at': now(), 'temporary_database_removed': removed, 'source_running_state_restored': True, 'source_access': 'read-only pg_dump --schema-only', 'business_rows_copied': 0, 'application_services_started': False})
