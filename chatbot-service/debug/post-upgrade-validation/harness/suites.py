import os,xml.etree.ElementTree as ET
from common import *
state=load(PRIVATE/'state.json');env=dict(os.environ);env.update(CONVERSATION_TEST_DATABASE_URL=state['dsn'],KNOWLEDGE_TEST_DATABASE_URL=state['dsn'])
python=str(ROOT/'chatbot-service/.venv/bin/python')
run('python-focused',[python,'-m','pytest','tests/test_query_product_v2.py','tests/test_persistent_conversation.py','tests/test_final_grounding_safety.py','tests/test_knowledge_services.py','tests/test_tracing.py','-q'],cwd=ROOT/'chatbot-service',env=env)
run('python-full',[python,'-m','pytest','tests','-q'],cwd=ROOT/'chatbot-service',env=env)
env.update(JAVA_HOME=str(ROOT/'.local/tools/jdk-21.0.12.1+1/Contents/Home'),DOCKER_HOST='unix:///Users/vu/.docker/run/docker.sock');env['PATH']=env['JAVA_HOME']+'/bin:'+str(ROOT/'.local/tools/node-v22.23.3-darwin-arm64/bin')+':'+env.get('PATH','')
java_names=['AuthMeIntegrationTest','AuthLoginIntegrationTest','AuthRefreshIntegrationTest','ProductPersistenceIntegrationTest','OrderCheckoutIntegrationTest','OrderWorkflowTest']
code,_=run('java-contract-integration',['./mvnw','-q','-Dapi.version=1.44','-Dtest='+','.join(java_names),'test'],cwd=ROOT/'backend',env=env,timeout=240)
summary=[]
for name in java_names:
 for p in (ROOT/'backend/target/surefire-reports').glob('TEST-*.'+name+'.xml'):
  a=ET.parse(p).getroot().attrib;summary.append({'test':name,**{k:int(a[k]) for k in ['tests','failures','errors','skipped']}})
dump(OUT/'evidence/java-test-summary.json',{'exit_code':code,'tests':summary})
run('frontend-component',['npm','test','--','src/features/chatbot'],cwd=ROOT/'Fontend',env=env)
run('frontend-build',['npm','run','build'],cwd=ROOT/'Fontend',env=env)
run('frontend-lint',['node','node_modules/eslint/bin/eslint.js','src/features/chatbot','--max-warnings','0'],cwd=ROOT/'Fontend',env=env)
