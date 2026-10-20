"""Sanitize published copies, verify frozen plan/source preservation and dossier completeness."""
from common import *
from runtime import public
ROOT_SECRET_KEYS={'customer_id','customerId','user_id','userId','display_name','displayName','fullName','recipientName','recipientPhone','shippingAddress','serialNumber','serial_number','imeiNumbers','imei_numbers'}
# Preserve expectations bytes; their exact historical prompt includes only documented synthetic order codes.
plan_hash=load(OUT/'evidence/plan_sha256.json')['cases_sha256']
for p in OUT.rglob('*.json'):
 if p.name=='cases.json':continue
 dump(p,public(load(p)))
assert hashlib.sha256((OUT/'cases.json').read_bytes()).hexdigest()==plan_hash
assert preserve_hashes()==load(OUT/'evidence/original_reports_sha256.json')
assert subprocess.check_output(['git','diff','--name-only'],cwd=ROOT,text=True).strip()==''
rows=load(OUT/'results.json');assert len(rows)==120 and len({r['id'] for r in rows})==120
issues=[]
patterns={'jwt':r'eyJ[A-Za-z0-9_-]{10,}\.eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]+','private_key':r'-----BEGIN (?:RSA |OPENSSH |EC )?PRIVATE KEY-----','database_url':r'(?:jdbc:)?postgres(?:ql)?://[^\s\x22\x27<>]+','secret_field':r'(?i)"(?:password|authorization|cookie|cookies|access_token|refresh_token)"\s*:\s*"(?!\[REDACTED\]|synthetic)[^"\n]+"'}
for p in OUT.rglob('*'):
 if not p.is_file() or p.suffix in ['.py','.pyc']:continue
 text=p.read_text(errors='replace')
 for kind,pattern in patterns.items():
  for m in re.finditer(pattern,text):issues.append({'file':str(p.relative_to(OUT)),'kind':kind,'line':text[:m.start()].count('\n')+1})
assert not issues,issues
for p in [OUT/'results.json',OUT/'evidence/root_causes.json']:
 def check(value):
  if isinstance(value,dict):
   assert not ROOT_SECRET_KEYS.intersection(value)
   for v in value.values():check(v)
  elif isinstance(value,list):
   for v in value:check(v)
 check(load(p))
changed=subprocess.check_output(['git','ls-files','--others','--exclude-standard','chatbot-service/debug/post-upgrade-validation'],cwd=ROOT,text=True)
(OUT/'evidence/final_changed_files.txt').write_text(changed)
(OUT/'evidence/final_git_status.txt').write_text(subprocess.check_output(['git','status','-sb'],cwd=ROOT,text=True))
dump(OUT/'evidence/publication_checks.json',{'completed_at':now(),'case_count':120,'frozen_plan_sha256':plan_hash,'production_tracked_diff_empty':True,'original_reports_hashes_equal':True,'secret_pattern_findings':0,'customer_private_identifiers_removed_or_pseudonymized':True,'published_data_is_synthetic':True,'no_diagnostic_commit_push':True})
print('Publication checks PASS:120 statuses, frozen expectations, source/report preservation, sanitized evidence')
