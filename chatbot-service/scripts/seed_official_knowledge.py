"""Load the approved official knowledge set from docs/knowledge into knowledge_documents.

Run from chatbot-service:  .venv/bin/python scripts/seed_official_knowledge.py
Then index vectors:        .venv/bin/python -m app.features.knowledge.ingest

The markdown file is the single source of truth: each "## KT-xx — Title" section becomes one
document (stable id from its source URI, so re-running updates in place). Sections that still
contain placeholders are refused. DEMO documents are unpublished (kept, never deleted) because
chat history may reference their chunks.
"""
import re
import sys
from pathlib import Path
from uuid import NAMESPACE_URL, uuid5

import psycopg

from app.shared.config import get_settings

SOURCE_FILE = Path(__file__).resolve().parents[1] / 'docs' / 'knowledge' / 'BO_TAI_LIEU_KIEN_THUC_CHINH_THUC.md'
AUTHOR_USERNAME = 'seed.manager'
UNPUBLISHED_STATUS = 'ARCHIVED'
PLACEHOLDERS = ('[ĐIỀN', '[XÁC NHẬN', '⚠️')

SECTION = re.compile(r'^## (KT-\d+) — (.+?)\n(.*?)(?=^---$|^## )', re.S | re.M)
META = re.compile(r'\*\*Loại:\*\*\s*(\w+)\s*·\s*\*\*Nguồn:\*\*\s*`([^`]+)`')


def parse(markdown: str) -> list[dict]:
    documents = []
    for code, title, body in SECTION.findall(markdown):
        meta = META.search(body)
        if not meta or '**Nội dung**' not in body:
            raise SystemExit(f'{code}: thiếu dòng Loại/Nguồn hoặc mục **Nội dung**')
        content = body.split('**Nội dung**', 1)[1].strip()
        # Join hard-wrapped lines inside a paragraph; keep blank lines between paragraphs.
        content = '\n\n'.join(' '.join(line.strip() for line in para.splitlines()) for para in re.split(r'\n\s*\n', content))
        if any(p in content for p in PLACEHOLDERS):
            raise SystemExit(f'{code}: nội dung còn chỗ chờ điền/xác nhận — không nạp')
        documents.append({'code': code, 'title': title.strip(), 'document_type': meta.group(1),
                          'source': meta.group(2), 'content': content})
    if not documents:
        raise SystemExit('Không đọc được tài liệu nào từ ' + str(SOURCE_FILE))
    return documents


def main():
    settings = get_settings()
    if not settings.knowledge_database_url:
        raise SystemExit('Cần cấu hình KNOWLEDGE_DATABASE_URL.')
    documents = parse(SOURCE_FILE.read_text(encoding='utf-8'))
    status = settings.knowledge_document_status
    with psycopg.connect(settings.knowledge_database_url) as conn:
        employee = conn.execute(
            'SELECT e.employee_id FROM employees e JOIN users u ON u.user_id = e.user_id WHERE u.username = %s',
            (AUTHOR_USERNAME,)).fetchone()
        if not employee:
            raise SystemExit(f'Thiếu hồ sơ nhân viên {AUTHOR_USERNAME}.')
        for doc in documents:
            conn.execute('''INSERT INTO knowledge_documents (document_id, title, source, document_type, content, status, employee_id)
                VALUES (%s, %s, %s, %s, %s, %s, %s)
                ON CONFLICT (document_id) DO UPDATE SET title = EXCLUDED.title, document_type = EXCLUDED.document_type,
                    content = EXCLUDED.content, status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP''',
                (uuid5(NAMESPACE_URL, doc['source']), doc['title'], doc['source'], doc['document_type'],
                 doc['content'], status, employee[0]))
            print(f"  {doc['code']} {doc['title']} ({len(doc['content'])} ký tự)")
        archived = conn.execute(
            "UPDATE knowledge_documents SET status = %s, updated_at = CURRENT_TIMESTAMP WHERE document_type = 'DEMO' AND status <> %s",
            (UNPUBLISHED_STATUS, UNPUBLISHED_STATUS)).rowcount
    print(f'Đã nạp {len(documents)} tài liệu chính thức; ngừng xuất bản {archived} tài liệu DEMO.')
    print('Bước tiếp theo: .venv/bin/python -m app.features.knowledge.ingest')


if __name__ == '__main__':
    sys.exit(main())
