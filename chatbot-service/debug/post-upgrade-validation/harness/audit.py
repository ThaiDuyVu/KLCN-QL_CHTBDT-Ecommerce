import ast,hashlib,json,os,subprocess
from pathlib import Path
from common import *
assert subprocess.check_output(['git','rev-parse','--short','HEAD'],cwd=ROOT,text=True).strip()=='1c62129'
dump(OUT/'evidence/original_reports_sha256.json',preserve_hashes())
commands=[('git-branches',['git','branch','-vv']),('git-head',['git','rev-parse','HEAD']),('git-status',['git','status','--short']),('git-history',['git','log','--graph','--oneline','438227c..HEAD']),('git-source-diff',['git','diff','--name-status','438227c','HEAD','--','chatbot-service/app','chatbot-service/tests','backend/src','Fontend/src/features/chatbot']),('git-historical-stat',['git','diff','--stat','438227c','HEAD']),('git-merge-tree',['git','diff','--exit-code','1660315','HEAD']),('git-migrations',['git','diff','438227c','HEAD','--','backend/src/main/resources/db/migration'])]
for id,cmd in commands:run(id,cmd)
files=[]
paths=list((ROOT/'chatbot-service/app').rglob('*.py'))+list((ROOT/'chatbot-service/tests').glob('*.py'))
for p in paths:
 source=p.read_text();tree=ast.parse(source);files.append({'file':str(p.relative_to(ROOT)),'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'lines':len(source.splitlines()),'definitions':[{'name':n.name,'line':n.lineno} for n in ast.walk(tree) if isinstance(n,(ast.ClassDef,ast.FunctionDef,ast.AsyncFunctionDef))]})
dump(OUT/'evidence/python_code_inventory.json',files)
versions=[('python-version',[str(ROOT/'chatbot-service/.venv/bin/python'),'--version']),('pytest-version',[str(ROOT/'chatbot-service/.venv/bin/python'),'-m','pytest','--version']),('node-version',[str(ROOT/'.local/tools/node-v22.23.3-darwin-arm64/bin/node'),'--version']),('java-version',[str(ROOT/'.local/tools/jdk-21.0.12.1+1/Contents/Home/bin/java'),'-version']),('docker-version',['docker','version','--format','{{.Server.Version}}'])]
for id,cmd in versions:run(id,cmd,timeout=15)
print('Git audit and source inventory saved')
