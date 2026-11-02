"""Freeze immutable source and case plan before any final120 execution."""
import hashlib,json,subprocess,sys,shutil
from pathlib import Path
ROOT=Path(__file__).resolve().parents[4]
sys.path.insert(0,str(ROOT/'chatbot-service/debug/post-upgrade-validation/harness'))
from common import OUT,PRIVATE,dump,now,run
old=ROOT/'chatbot-service/debug/post-upgrade-validation/cases.json'
assert not (OUT/'cases.json').exists(),'New dossier only; refuse overwriting frozen plan'
shutil.copyfile(old,OUT/'cases.json');cases=json.loads(old.read_text())
ids={f'{prefix}{i:02}' for prefix,n in [('T',60),('N',48),('B',12)] for i in range(1,n+1)}
assert len(cases)==120 and {c['id'] for c in cases}==ids
sha=hashlib.sha256(old.read_bytes()).hexdigest();dump(OUT/'evidence/plan_sha256.json',{'frozen_at':now(),'cases_sha256':sha,'original_sha256':sha,'exact_byte_copy':True})
paths=subprocess.check_output(['git','ls-files'],cwd=ROOT,text=True).splitlines()
protected={p:hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in paths if p.startswith('chatbot-service/debug/')}
dump(OUT/'evidence/protected_reports_sha256.json',protected)
source={p:hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in paths if p.startswith(('chatbot-service/app/','Fontend/src/','backend/src/'))}
dump(OUT/'source_hashes.json',{'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),'source_version':'final120-help-and-first-post-retry','files':source,'test_files':{str(f.relative_to(ROOT)):hashlib.sha256(f.read_bytes()).hexdigest() for f in (ROOT/'chatbot-service/tests').glob('*.py')}})
for id,cmd in [('git-head',['git','rev-parse','HEAD']),('git-branch',['git','branch','--show-current']),('git-status',['git','status','--short']),('git-history',['git','log','--oneline','-14']),('git-accepted-diff',['git','diff','--stat','8b1753f','HEAD','--','chatbot-service/app','backend/src','Fontend/src/features/chatbot'])]:run(id,cmd)
dump(OUT/'evidence/baseline.json',{'recorded_at':now(),'workspace_name':'QL_CHTBDT_Ecommerce','branch':'dev/thai-duy-vu','actual_head':subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),'connector_head':'f99f713','connector_head_disagrees_with_local_git':True,'initial_tracked_status':'clean','accepted_fix01':'8b1753f','accepted_fix02':'6c7e408','accepted_fix04':'74f4e19','rag':'NOT_AUTHORIZED','storefront_chat':'NOT_AUTHORIZED','production_approval':False})
print('Frozen exactly120 cases and source hashes.')
