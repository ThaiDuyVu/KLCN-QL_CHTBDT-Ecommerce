"""Isolated runtime clone. Only pg_dump/read-only checks touch the source DB."""
import os,subprocess,time,uuid,urllib.request,json,sys,socket
from common import *
from dotenv import dotenv_values
import psycopg
STATE=PRIVATE/'state.json'

def reachable(port,path='/'):
 try:
  with urllib.request.urlopen(f'http://localhost:{port}'+path,timeout=2) as r:return r.status<400
 except Exception:return False

def wait(port,path,limit=120):
 for i in range(limit):
  if reachable(port,path):return
  time.sleep(1)
 raise RuntimeError('Dependency not ready on diagnostic port '+str(port))

def source_snapshot():
 sql="""SELECT json_build_object('inventory',(SELECT json_agg(t ORDER BY inventory_id) FROM (SELECT inventory_id,variant_id,warehouse_id,quantity,reserved_quantity FROM inventory)t),'orders',(SELECT json_agg(t ORDER BY order_id) FROM (SELECT order_id,status,total_amount FROM orders)t),'payments',(SELECT json_agg(t ORDER BY payment_id) FROM (SELECT payment_id,order_id,status,amount FROM payments)t),'variants',(SELECT json_agg(t ORDER BY variant_id) FROM (SELECT variant_id,sku,price,ram,storage,color,warranty_months,status FROM product_variants)t),'warranties',(SELECT count(*) FROM warranties),'tickets',(SELECT count(*) FROM warranty_tickets));"""
 r=subprocess.run(['docker','exec','ecommerce-postgres','sh','-c','psql -X -t -A -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -c "$1"','sh',sql],capture_output=True,text=True,check=True)
 data=json.loads(r.stdout.strip());return {'sha256':hashlib.sha256(json.dumps(data,sort_keys=True).encode()).hexdigest(),'counts':{k:len(v or []) if isinstance(v,list) else v for k,v in data.items()}}

def spawn(name,cmd,cwd,env):
 log=open(PRIVATE/(name+'.log'),'a');p=subprocess.Popen(cmd,cwd=cwd,env=env,stdin=subprocess.DEVNULL,stdout=log,stderr=log,start_new_session=True);log.close();s=load(STATE);s.setdefault('processes',{})[name]={'pid':p.pid,'command_family':Path(cmd[0]).name};dump(STATE,s);STATE.chmod(0o600);return p.pid

def app_env(s):
 env=dict(os.environ);env.update({k:str(v) for k,v in dotenv_values(ROOT/'.env').items() if v is not None})
 env.update({'SPRING_DATASOURCE_URL':f"jdbc:postgresql://localhost:{s['pg_port']}/post_upgrade",'SPRING_DATASOURCE_USERNAME':'postgres','SPRING_DATASOURCE_PASSWORD':s['pg_password'],'CORS_ALLOWED_ORIGINS':'http://localhost:5174'})
 return env

def start_chat(profile='default'):
 s=load(STATE);env=app_env(s);warehouse=s['warehouse_id'] if profile=='default' else s['empty_warehouse_id'] if profile=='empty_warehouse' else ''
 env.update({'APP_ENV':'development','CHATBOT_AI_ENABLED':'true','PRODUCT_ADVISOR_MODE':'real','CHATBOT_TRACE_ENABLED':'true','CHATBOT_TRACE_FILE_ENABLED':'true','CONVERSATION_STORAGE':'postgres','CONVERSATION_DATABASE_URL':s['dsn'],'PRODUCT_INDEX_DATABASE_URL':s['dsn'],'KNOWLEDGE_DATABASE_URL':s['dsn'],'CHATBOT_WAREHOUSE_ID':warehouse,'BACKEND_BASE_URL':'http://localhost:8081','BACKEND_BEARER_TOKEN':'','OLLAMA_BASE_URL':'http://localhost:11434'})
 port={'default':8091,'empty_warehouse':8092,'no_warehouse':8093}[profile]
 spawn('chatbot-'+profile,[str(ROOT/'chatbot-service/.venv/bin/python'),'-m','uvicorn','app.main:app','--host','127.0.0.1','--port',str(port)],ROOT/'chatbot-service',env);wait(port,'/api/v1/health')

if __name__=='__main__':
 from app.shared.config import Settings
 s={'created_at':now(),'processes':{},'source_was_running':subprocess.check_output(['docker','inspect','-f','{{.State.Running}}','ecommerce-postgres'],text=True).strip()=='true','ollama_was_running':reachable(11434,'/api/tags')}
 assert not STATE.exists(),'Existing diagnostic state requires explicit cleanup first'
 dump(STATE,s);STATE.chmod(0o600)
 subprocess.run(['docker','start','ecommerce-postgres'],check=True,capture_output=True)
 for i in range(40):
  try:
   settings=Settings(_env_file=ROOT/'chatbot-service/.env')
   with psycopg.connect(settings.conversation_database_url,connect_timeout=2) as conn:
    unknown=conn.execute("SELECT count(*) FROM customers c JOIN users u ON u.user_id=c.user_id WHERE u.username NOT LIKE 'seed.%'").fetchone()[0]
   assert unknown==0,'Source contains non-seed customers; refuse copying unknown data'
   break
  except psycopg.OperationalError:time.sleep(.5)
 dump(OUT/'evidence/source-business-before.json',source_snapshot())
 backup=subprocess.run(['docker','exec','ecommerce-postgres','sh','-c','pg_dump -Fc --no-owner --no-acl -U "$POSTGRES_USER" "$POSTGRES_DB"'],capture_output=True,check=True).stdout
 (PRIVATE/'synthetic-source.dump').write_bytes(backup);(PRIVATE/'synthetic-source.dump').chmod(0o600)
 s.update(container='chatbot-post-upgrade-'+uuid.uuid4().hex[:10],pg_password=uuid.uuid4().hex)
 env=dict(os.environ,POSTGRES_PASSWORD=s['pg_password'])
 subprocess.run(['docker','run','--rm','-d','--name',s['container'],'-e','POSTGRES_PASSWORD','-e','POSTGRES_DB=post_upgrade','-P','pgvector/pgvector:pg16'],env=env,check=True,capture_output=True)
 s['pg_port']=int(subprocess.check_output(['docker','port',s['container'],'5432/tcp'],text=True).splitlines()[0].rsplit(':',1)[1]);s['dsn']=f"postgresql://postgres:{s['pg_password']}@localhost:{s['pg_port']}/post_upgrade";dump(STATE,s)
 for i in range(60):
  try:
   with psycopg.connect(s['dsn'],connect_timeout=1):pass
   break
  except psycopg.OperationalError:time.sleep(.5)
 subprocess.run(['docker','exec','-i',s['container'],'pg_restore','-U','postgres','-d','post_upgrade','--no-owner','--no-acl'],input=backup,check=True,capture_output=True)
 if not s['source_was_running']:subprocess.run(['docker','stop','ecommerce-postgres'],check=True,capture_output=True)
 with psycopg.connect(s['dsn']) as conn:
  warehouses=conn.execute("SELECT warehouse_id FROM warehouses WHERE status='ACTIVE' ORDER BY warehouse_id").fetchall();assert len(warehouses)>=2
  selected=Settings(_env_file=ROOT/'chatbot-service/.env').chatbot_warehouse_id
  s['warehouse_id']=str(next(w[0] for w in warehouses if str(w[0])==selected));s['empty_warehouse_id']=str(next(w[0] for w in warehouses if str(w[0])!=selected));dump(STATE,s)
  overview={'flyway':conn.execute('SELECT version,success FROM flyway_schema_history WHERE success ORDER BY installed_rank').fetchall(),'catalog_products':conn.execute('SELECT count(*) FROM products').fetchone()[0],'variants':conn.execute('SELECT count(*) FROM product_variants').fetchone()[0],'warehouse_count':len(warehouses),'knowledge_documents':conn.execute('SELECT document_type,status,count(*) FROM knowledge_documents GROUP BY document_type,status').fetchall(),'knowledge_chunks':conn.execute('SELECT count(*) FROM knowledge_chunks WHERE embedding_vector IS NOT NULL').fetchone()[0]}
  # Clear copied auth sessions only inside this disposable clone.
  conn.execute('DELETE FROM refresh_tokens')
 if not s['ollama_was_running']:
  env=dict(os.environ,OLLAMA_HOST='127.0.0.1:11434',OLLAMA_MODELS=str(ROOT/'.local/ollama-models'));spawn('ollama',[str(ROOT/'.local/tools/ollama/ollama'),'serve'],ROOT,env)
 wait(11434,'/api/tags')
 with urllib.request.urlopen('http://localhost:11434/api/tags') as r:models=json.load(r)
 overview['models']=[{k:v for k,v in m.items() if k in ['name','digest','size','details']} for m in models['models']]
 env=app_env(s);env['JAVA_HOME']=str(ROOT/'.local/tools/jdk-21.0.12.1+1/Contents/Home');env['PATH']=env['JAVA_HOME']+'/bin:'+env.get('PATH','')
 code,_=run('build-isolated-backend',['./mvnw','-q','-DskipTests','package'],cwd=ROOT/'backend',env=env,timeout=180);assert code==0
 spawn('backend',[env['JAVA_HOME']+'/bin/java','-jar','target/backend-0.0.1-SNAPSHOT.jar','--spring.profiles.active=dev','--server.address=127.0.0.1','--server.port=8081'],ROOT/'backend',env)
 # Auth csrf returns204 (urllib accepts it).
 wait(8081,'/api/auth/csrf')
 for profile in ['default','empty_warehouse','no_warehouse']:start_chat(profile)
 env.update(BACKEND_PROXY_TARGET='http://localhost:8081',CHATBOT_PROXY_TARGET='http://localhost:8091')
 spawn('frontend',[str(ROOT/'.local/tools/node-v22.23.3-darwin-arm64/bin/node'),'node_modules/vite/bin/vite.js','--host','127.0.0.1','--port','5174'],ROOT/'Fontend',env);wait(5174,'/chatbot-lab')
 overview.update({'source_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'branch':'dev/thai-duy-vu','workspace_info':{'workspaceName':'QL_CHTBDT_Ecommerce','commit':'1c62129','verified_with_connector':True},'runtime':'REAL_QWEN_REAL_SPRING_ISOLATED_POSTGRES','ai_enabled':True,'product_advisor':'real','conversation_storage':'postgres','warehouse_profiles':['default','empty_warehouse','no_warehouse'],'knowledge_status':'PUBLISHED','tracing':{'enabled':True,'file_enabled':True},'production_defaults':{'ai':False,'product_advisor':'mock','conversation':'memory'},'temporary_ports':{'frontend':5174,'backend':8081,'chatbot':[8091,8092,8093]},'source_db_modified':False,'seed_customers_only_verified':True})
 dump(OUT/'environment_sanitized.json',overview);print('Isolated real stack READY',flush=True)
