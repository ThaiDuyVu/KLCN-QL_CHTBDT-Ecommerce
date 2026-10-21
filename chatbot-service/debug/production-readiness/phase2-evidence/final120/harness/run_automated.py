"""Full automated suites, isolated PG and Java Testcontainers; never change app code."""
import os,sys,xml.etree.ElementTree as ET,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[6];sys.path.insert(0,str(ROOT/'chatbot-service/debug/post-upgrade-validation/harness'))
from common import *
s=load(PRIVATE/'state.json');env=dict(os.environ,CONVERSATION_TEST_DATABASE_URL=s['dsn'],KNOWLEDGE_TEST_DATABASE_URL=s['dsn'])
python=str(ROOT/'chatbot-service/.venv/bin/python')
for name,args in [('python-focused',['tests/test_query_product_v2.py','tests/test_query_integrity.py','tests/test_customer_context_mapping.py','tests/test_customer_context_contracts.py','tests/test_persistent_conversation.py','tests/test_conversation_safety.py','tests/test_pagination_boundaries.py','tests/test_tracing.py','tests/test_final_grounding_safety.py']),('python-full',['tests'])]:
 # Detect an unavailable test module instead of silently discarding it.
 run(name,[python,'-m','pytest',*args,'-q'],cwd=ROOT/'chatbot-service',env=env)
 if name=='python-full' and sys.argv[-1]=='python-only':sys.exit(0)
home=ROOT/'.local/tools/jdk-21.0.12.1+1/Contents/Home';env.update(JAVA_HOME=str(home),DOCKER_HOST='unix:///Users/vu/.docker/run/docker.sock');env['PATH']=str(home/'bin')+':'+str(ROOT/'.local/tools/node-v22.23.3-darwin-arm64/bin')+':'+env['PATH']
names=['AuthMeIntegrationTest','AuthLoginIntegrationTest','AuthLogoutIntegrationTest','AuthRefreshIntegrationTest','ProductPersistenceIntegrationTest','OrderCheckoutIntegrationTest','OrderReadIntegrationTest','OrderWorkflowTest','GoodsReceiptInventoryIntegrationTest','WarehouseControllerTest','WarehouseServiceImplTest']
code,_=run('java-integration-contract',['./mvnw','-q','-Dapi.version=1.44','-Dtest='+','.join(names),'test'],cwd=ROOT/'backend',env=env,timeout=300)
summary=[]
for name in names:
 for p in (ROOT/'backend/target/surefire-reports').glob('TEST-*.'+name+'.xml'):
  a=ET.parse(p).getroot().attrib;summary.append({'test':name,**{k:int(a[k]) for k in ['tests','failures','errors','skipped']}})
dump(OUT/'evidence/java-test-summary.json',{'exit_code':code,'suites':summary,'counts':{k:sum(r[k] for r in summary) for k in ['tests','failures','errors','skipped']}})
for name,argv in [('frontend-full',['npm','test']),('frontend-eslint',['npm','run','lint']),('frontend-build',['npm','run','build'])]:
 code,output=run(name,argv,cwd=ROOT/'Fontend',env=env,timeout=180)
 if name=='frontend-full':
  match=re.search(r'^\s*Tests\s+(.+)$',output,re.M);counts={k:int(m[1]) if (m:=re.search(r'(\d+) '+k,match[1] if match else '')) else 0 for k in ['passed','failed','skipped']}
  log=load(OUT/'evidence/commands.json');log[-1]['counts']=counts;dump(OUT/'evidence/commands.json',log)
