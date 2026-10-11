"""Explicit local test data only; does not override existing documents or schema.

Run from chatbot-service: .venv/bin/python scripts/seed_demo_knowledge.py
Requires KNOWLEDGE_DATABASE_URL and the existing synthetic seed.staff employee.
"""
from uuid import NAMESPACE_URL, uuid5

import psycopg

from app.shared.config import get_settings

DOCUMENTS = [
    ('return', 'DEMO — Chính sách đổi trả kiểm thử',
     'Đây là dữ liệu DEMO phục vụ kiểm thử chatbot, không phải chính sách cửa hàng chính thức. '
     'Trong kịch bản DEMO, khách hàng có thể đề nghị đổi trả trong 7 ngày kể từ lúc nhận hàng. '
     'Điều kiện DEMO: có hóa đơn, sản phẩm còn đầy đủ phụ kiện, không bị hư hỏng do người sử dụng. '
     'Chatbot chỉ giải thích, không tự tạo yêu cầu đổi trả. Chính sách áp dụng thực tế phải được cửa hàng xác nhận.'),
    ('payment', 'DEMO — Hướng dẫn kiểm tra thanh toán',
     'Đây là hướng dẫn DEMO kiểm thử. Muốn biết đã thanh toán hay chưa phải đọc trạng thái payment của đơn từ backend. '
     'Không kết luận PAID chỉ vì người dùng nói đã chuyển khoản. Khi chưa có payment, chatbot phải nói chưa đủ dữ liệu. '
     'Chatbot không thực hiện giao dịch, không thu tiền và không yêu cầu mật khẩu hoặc mã OTP.'),
]


def main():
    settings = get_settings()
    if settings.app_env != 'development' or not settings.knowledge_database_url:
        raise SystemExit('Chỉ chạy trong development với KNOWLEDGE_DATABASE_URL đã cấu hình.')
    with psycopg.connect(settings.knowledge_database_url) as conn:
        employee = conn.execute("SELECT e.employee_id FROM employees e JOIN users u ON u.user_id=e.user_id WHERE u.username='seed.staff' AND u.email='seed.staff@example.test'").fetchone()
        if not employee:
            raise SystemExit('Thiếu synthetic employee seed.staff. Không tạo dữ liệu dựa trên nhân viên thật.')
        for key, title, content in DOCUMENTS:
            document_id = uuid5(NAMESPACE_URL, 'chatbot-lab-demo/' + key)
            conn.execute('''INSERT INTO knowledge_documents (document_id,title,source,document_type,content,status,employee_id)
                VALUES (%s,%s,%s,%s,%s,%s,%s) ON CONFLICT (document_id) DO NOTHING''',
                (document_id,title,'demo://chatbot-lab/'+key,'DEMO',content,settings.knowledge_document_status,employee[0]))
    print('Đã chuẩn bị 2 tài liệu DEMO (tài liệu đã tồn tại được giữ nguyên). Chạy ingestion để tạo vector.')


if __name__ == '__main__':
    main()
