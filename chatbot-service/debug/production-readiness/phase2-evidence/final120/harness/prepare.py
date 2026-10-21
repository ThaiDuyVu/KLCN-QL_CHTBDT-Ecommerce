"""Reproduce isolated setup, approved fixtures and before-business fingerprints."""
import hashlib,json,os,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[6];sys.path.insert(0,str(ROOT/'chatbot-service/debug/post-upgrade-validation/harness'))
from common import *
assert not (PRIVATE/'state.json').exists(),'Use a fresh private directory; never reuse cleaned or live diagnostic state'
code,_=run('environment-setup',[str(ROOT/'chatbot-service/.venv/bin/python'),str(ROOT/'chatbot-service/debug/post-upgrade-validation/harness/environment.py')],env=os.environ.copy(),timeout=240)
assert code==0
from fixtures import prepare
prepare()
import psycopg
from psycopg import sql
state=load(PRIVATE/'state.json');tables=['orders','payments','order_items','installment_payments','inventory','warranties','warranty_tickets','carts','cart_items','products','product_variants','serial_numbers'];result={}
with psycopg.connect(state['dsn']) as conn:
 for table in tables:
  data=conn.execute(sql.SQL('SELECT jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text) FROM {} t').format(sql.Identifier(table))).fetchone()[0] or []
  result[table]={'count':len(data),'sha256':hashlib.sha256(json.dumps(data,sort_keys=True,default=str).encode()).hexdigest()}
dump(OUT/'evidence/clone-business-before.json',result)
print('Disposable runtime, approved fixtures and business fingerprints prepared.')
