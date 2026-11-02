import os,sys
from common import *
env=dict(os.environ,PYTHONPATH=str(ROOT/'chatbot-service'))
code,_=run(sys.argv[1],sys.argv[2:],env=env,timeout=600)
raise SystemExit(code)
