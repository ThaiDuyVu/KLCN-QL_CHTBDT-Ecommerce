"""Clean the Fix04 disposable stack, preserving source business data and accepted reports."""
import hashlib
import os
from pathlib import Path
import signal
import subprocess
import sys
import time

ROOT=Path(__file__).resolve().parents[4]
sys.path.insert(0,str(ROOT/'chatbot-service/debug/post-upgrade-validation/harness'))
from common import OUT,PRIVATE,load,dump,now
from environment import source_snapshot

s=load(PRIVATE/'state.json');assert not s.get('cleaned_up')
for name,process in s['processes'].items():
 command=subprocess.run(['ps','-p',str(process['pid']),'-o','command='],capture_output=True,text=True)
 if command.returncode!=0:continue
 assert str(ROOT) in command.stdout,'Refuse to stop a process outside diagnostic ownership'
 os.kill(process['pid'],signal.SIGTERM)
time.sleep(1)
removed=subprocess.run(['docker','rm','-f',s['container']],capture_output=True).returncode==0
assert removed
subprocess.run(['docker','start','ecommerce-postgres'],check=True,capture_output=True)
for _ in range(40):
 try:after=source_snapshot();break
 except subprocess.CalledProcessError:time.sleep(.5)
else:raise RuntimeError('Cannot verify source business fingerprint')
before=load(ROOT/'.local/fix04-setup-output/evidence/source-business-before.json')
if not s['source_was_running']:subprocess.run(['docker','stop','ecommerce-postgres'],check=True,capture_output=True)
hashes=load(OUT/'protected_reports_sha256.json');same=all(hashlib.sha256((ROOT/p).read_bytes()).hexdigest()==sha for p,sha in hashes.items())
record={'completed_at':now(),'temporary_container_removed':removed,'source_business_before':before,'source_business_after':after,'source_business_unchanged':before==after,'protected_reports_unchanged':same,'source_database_running_state_restored':True,'normal_application_processes_modified':False,'isolated_business_fixture_rows_remaining':0,'test_owned_processes_stopped':len(s['processes'])}
dump(OUT/'cleanup.json',record)
assert before==after and same
(PRIVATE/'synthetic-source.dump').unlink(missing_ok=True)
for key in ['dsn','pg_password','fixture_order_ids']:s.pop(key,None)
s['cleaned_up']=True;dump(PRIVATE/'state.json',s)
for p in PRIVATE.rglob('*'):
 if p.is_file():p.chmod(0o600)
print('Cleanup verified: business source and accepted reports unchanged; disposable stack removed.')
