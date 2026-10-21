"""Sequential remaining cases and independent UI/fault/repeat evidence."""
import os,sys,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[6];sys.path.insert(0,str(ROOT/'chatbot-service/debug/post-upgrade-validation/harness'))
from common import *
python=str(ROOT/'chatbot-service/.venv/bin/python')
for _ in range(720):
 log=load(OUT/'evidence/commands.json')
 record=next((r for r in log if r['id']=='runtime-final-source-all-prompts'),None)
 if record:assert record['exit_code']==0;break
 time.sleep(1)
else:raise RuntimeError('Final runtime command did not complete')
for id,path,extra in [('boundary-final-12',OUT/'harness/run_boundaries.py',{}),('browser-final-history-restart',ROOT/'chatbot-service/debug/post-upgrade-validation/harness/browser.py',{}),('evaluate-final-source',OUT/'harness/evaluate_current.py',{}),('ui-final-real-browser',OUT/'harness/ui_browser.py',{}),('fix04-negative-controls',ROOT/'chatbot-service/debug/post-upgrade-fixes/harness/fix04_faults.py',{'CHATBOT_DIAGNOSTIC_OUTPUT':str(OUT/'evidence/fix04-controls')}),('repeated-high-risk',ROOT/'chatbot-service/debug/post-upgrade-validation/harness/repeats.py',{})]:
 code,_=run(id,[python,str(path)],env=dict(os.environ,**extra),timeout=360);print(id,code,flush=True)
