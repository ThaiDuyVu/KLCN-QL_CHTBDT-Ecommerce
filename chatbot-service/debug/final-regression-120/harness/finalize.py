"""Final source/data invariants, command normalization and owned-stack cleanup."""
import hashlib,json,os,signal,subprocess,sys,time,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[4];sys.path.insert(0,str(ROOT/'chatbot-service/debug/post-upgrade-validation/harness'))
from common import *
from environment import source_snapshot
import psycopg
from psycopg import sql
s=load(PRIVATE/'state.json')
manifest=load(OUT/'source_hashes.json')
for p,sha in manifest['files'].items():assert hashlib.sha256((ROOT/p).read_bytes()).hexdigest()==sha,p
assert hashlib.sha256((OUT/'cases.json').read_bytes()).hexdigest()==load(OUT/'evidence/plan_sha256.json')['cases_sha256']
protected=load(OUT/'evidence/protected_reports_sha256.json')
for p,sha in protected.items():assert hashlib.sha256((ROOT/p).read_bytes()).hexdigest()==sha,p
before=load(OUT/'evidence/clone-business-before.json');after={}
with psycopg.connect(s['dsn']) as conn:
 for table in before:
  data=conn.execute(sql.SQL('SELECT jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text) FROM {} t').format(sql.Identifier(table))).fetchone()[0] or []
  after[table]={'count':len(data),'sha256':hashlib.sha256(json.dumps(data,sort_keys=True,default=str).encode()).hexdigest()}
dump(OUT/'evidence/clone-business-after.json',after)
assert before==after,'Business rows changed; do not publish a false cleanup success'
e=load(OUT/'environment_sanitized.json');e['workspace_info']={'workspaceName':'QL_CHTBDT_Ecommerce','connector_head':'f99f713','actual_git_head':manifest['source_commit'],'verified_with_connector':True,'metadata_note':'Environment helper historical commit literal replaced by actual Git + independently observed connector metadata.'}
for m in e['models']:m.get('details',{}).pop('parent_model',None)
e['source_version']=manifest.get('source_version');dump(OUT/'environment_sanitized.json',e)
# Match Vitest counts to its Tests line, not repeated failure headings.
commands=load(OUT/'evidence/commands.json');seen={}
for row in commands:
 seen[row['id']]=seen.get(row['id'],0)+1
 if seen[row['id']]==1 and sum(c['id']==row['id'] for c in commands)>1 and (OUT/'runs/pre-hotfix'/row['sanitized_output']).exists():row['sanitized_output']='runs/pre-hotfix/'+row['sanitized_output']
 output=OUT/row['sanitized_output']
 if output.exists():
  text=output.read_text();m=re.search(r'^\s*Tests\s+(.+)$',text,re.M)
  if m:row['counts']={k:int(found[1]) if (found:=re.search(r'(\d+) '+k,m[1])) else 0 for k in ['passed','failed','skipped']}
 # Maven quiet output is not an independent zero-test summary.
 if row['id']=='java-integration-contract':
  summary=load(OUT/'evidence/java-test-summary.json')['counts'];row['counts']={'passed':summary['tests']-summary['failures']-summary['errors']-summary['skipped'],'failed':summary['failures']+summary['errors'],'skipped':summary['skipped']}
dump(OUT/'evidence/commands.json',commands);dump(OUT/'test_commands.json',commands)
# Only stop processes explicitly created by this diagnostic setup.
for name,process in s['processes'].items():
 p=subprocess.run(['ps','-p',str(process['pid']),'-o','command='],capture_output=True,text=True)
 if p.returncode:continue
 assert str(ROOT) in p.stdout,'Refuse stopping unrelated process'
 os.kill(process['pid'],signal.SIGTERM)
time.sleep(1)
removed=subprocess.run(['docker','rm','-f',s['container']],capture_output=True).returncode==0
subprocess.run(['docker','start','ecommerce-postgres'],capture_output=True,check=True)
for _ in range(40):
 try:source_after=source_snapshot();break
 except subprocess.CalledProcessError:time.sleep(.5)
else:raise RuntimeError('Source fingerprint unavailable after cleanup')
source_before=load(OUT/'evidence/source-business-before.json')
if not s['source_was_running']:subprocess.run(['docker','stop','ecommerce-postgres'],capture_output=True,check=True)
record={'completed_at':now(),'temporary_container_removed':removed,'clone_business_unchanged':before==after,'source_business_unchanged':source_before==source_after,'source_business_before':source_before,'source_business_after':source_after,'protected_reports_unchanged':True,'source_hashes_match_final_execution':True,'frozen_120_plan_unchanged':True,'source_database_running_state_restored':True,'normal_application_processes_modified':False,'owned_processes_stopped':len(s['processes']),'isolated_fixture_rows_remaining':0 if removed else 'UNKNOWN'}
dump(OUT/'cleanup.json',record);assert removed and source_before==source_after
(PRIVATE/'synthetic-source.dump').unlink(missing_ok=True)
for k in ['dsn','pg_password','fixture_order_ids','runtime_sessions','browser_session','security_session']:s.pop(k,None)
s['cleaned_up']=True;dump(PRIVATE/'state.json',s)
for p in PRIVATE.rglob('*'):
 if p.is_file():p.chmod(0o600)
print('Final cleanup verified: source/business/protected reports unchanged.')
