from common import *
from runtime import Runner,CASES
runner=Runner();rows=[]
try:
 for id in ['T07','T15','T30','T31','T51']:
  base=next(c for c in CASES if c['id']==id)
  for i in range(1,4):
   case=dict(base,id=f'R-{id}-{i}',session_class='fresh_owned_customer')
   row,_=runner.send(case,setup=True);row['repeated_case']=id;rows.append(row)
   original=OUT/f'evidence/setups/{case["id"]}.json';target=OUT/f'evidence/repeats/{case["id"]}.json';target.parent.mkdir(exist_ok=True);original.replace(target)
finally:runner.close()
dump(OUT/'evidence/repeats/results.json',rows);print('15 real repeated prompts executed separately')
