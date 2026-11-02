import hashlib,json,os,re,subprocess,time
from datetime import datetime,timezone
from pathlib import Path
ROOT=Path(__file__).resolve().parents[4]
OUT=Path(os.environ.get('CHATBOT_DIAGNOSTIC_OUTPUT',str(ROOT/'chatbot-service/debug/post-upgrade-validation')));OUT.mkdir(parents=True,exist_ok=True);(OUT/'evidence').mkdir(exist_ok=True)
PRIVATE=Path(os.environ.get('CHATBOT_DIAGNOSTIC_PRIVATE',str(ROOT/'.local/post-upgrade-validation')));PRIVATE.mkdir(parents=True,exist_ok=True);PRIVATE.chmod(0o700)

def now():return datetime.now(timezone.utc).isoformat()
def scrub(text):
 text=str(text).replace(str(ROOT),'workspace:/')
 text=re.sub(r'(?i)(?:jdbc:)?postgres(?:ql)?://[^\s\x22\x27]+','[REDACTED_DATABASE_URL]',text)
 text=re.sub(r'eyJ[A-Za-z0-9_-]+\.eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+','[REDACTED_JWT]',text)
 text=re.sub(r'(?i)(authorization|cookie|password|jwt_secret)\s*[:=]\s*[^\n]+',r'\1: [REDACTED]',text)
 return text

def dump(path,data):
 path=Path(path);path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data,ensure_ascii=False,indent=2,default=str)+'\n')
def load(path):return json.loads(Path(path).read_text())
def run(id,cmd,*,cwd=ROOT,env=None,timeout=180,public=True):
 start=now();t=time.monotonic()
 try:
  p=subprocess.run(cmd,cwd=cwd,env=env,capture_output=True,text=True,timeout=timeout);code=p.returncode;output=p.stdout+p.stderr
 except subprocess.TimeoutExpired as exc:
  code=124;output='COMMAND_TIMEOUT\n'+str(exc.stdout or '')
 (PRIVATE/(id+'.log')).write_text(output)
 # Java logs may include complete HTTP/SQL DTOs: expose test summaries, not raw logs.
 if any('mvn' in str(x) for x in cmd):
  safe='Maven execution '+('SUCCESS' if code==0 else 'FAILED')+'; inspect sanitized Surefire summary in environment/results.\n'
 else:safe=scrub(output)
 target=OUT/'evidence/commands'/f'{id}.txt';target.parent.mkdir(exist_ok=True);target.write_text(safe)
 counts={k:sum(int(n) for n in re.findall(r'(\d+) '+k,output)) for k in ['passed','failed','skipped']}
 if 'frontend-component' in id:
  matches=re.search(r'Tests\s+(\d+) passed',output)
  if matches:counts['passed']=int(matches[1])
 record={'id':id,'command':scrub(' '.join(map(str,cmd))),'cwd':str(Path(cwd).relative_to(ROOT)) if Path(cwd)!=ROOT else '.','started_at':start,'duration_seconds':round(time.monotonic()-t,3),'exit_code':code,'counts':counts,'sanitized_output':str(target.relative_to(OUT))}
 log=OUT/'evidence/commands.json';items=load(log) if log.exists() else [];items.append(record);dump(log,items)
 print(id,'exit',code,counts,flush=True)
 return code,output

def preserve_hashes():
 paths=list((ROOT/'chatbot-service/debug/eval60').rglob('*'))+list((ROOT/'chatbot-service/debug/chatbot-upgrade').rglob('*'))+[ROOT/'chatbot-service/debug/CHATBOT_RUNTIME_DEBUG_REPORT.md']
 return {str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in paths if p.is_file()}
