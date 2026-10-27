"""Durable owner-scoped conversation, using existing V1 columns only."""
from contextlib import contextmanager
from hashlib import sha256
from uuid import UUID, uuid4

from fastapi import HTTPException
from pydantic import ValidationError

from app.features.conversation.models import ChatMessage, ChatSession, SenderType, StoredAssistantMessage
from app.shared.tracing import event
from app.features.conversation.context import completed_messages


FEEDBACK = '(SELECT f.rating FROM message_feedback f WHERE f.history_id=h.history_id)'


class PostgresConversationRepository:
    storage = 'postgres'

    def __init__(self, database_url):
        if not database_url:
            raise ValueError('CONVERSATION_DATABASE_URL is required for postgres storage')
        self.database_url = database_url

    @contextmanager
    def connect(self):
        import psycopg
        try:
            with psycopg.connect(self.database_url, connect_timeout=5) as connection:
                yield connection
        except psycopg.Error as exc:
            raise HTTPException(503, 'Chưa thể đọc hoặc lưu lịch sử hội thoại trong database.') from exc

    @staticmethod
    def session(row):
        if row[4] not in {'ACTIVE', 'CLOSED'}:
            raise HTTPException(502, 'Trạng thái session không được hỗ trợ.')
        return ChatSession(id=row[0], customer_id=row[1], created_at=row[2], ended_at=row[3], is_active=row[4]=='ACTIVE')

    def owned(self, conn, session_id, customer_id, *, lock=False):
        row = conn.execute('SELECT session_id,customer_id,started_at,ended_at,status FROM chat_sessions WHERE session_id=%s' +
                           (' FOR UPDATE' if lock else ''), (session_id,)).fetchone()
        if row is None: raise HTTPException(404, 'Session not found')
        if row[1] != customer_id: raise HTTPException(403, 'Session does not belong to this customer')
        return self.session(row)

    @contextmanager
    def request_lock(self, session_id, customer_id):
        """Session-level advisory lease; no long transaction around model/network IO."""
        key = int.from_bytes(sha256(session_id.bytes).digest()[:8], 'big', signed=True)
        with self.connect() as conn:
            conn.autocommit = True
            try:
                self.owned(conn, session_id, customer_id)
            except HTTPException as exc:
                if exc.status_code != 404: raise
            acquired = conn.execute('SELECT pg_try_advisory_lock(%s)', (key,)).fetchone()[0]
            if not acquired:
                raise HTTPException(409, 'Session đang xử lý một câu hỏi. Vui lòng chờ rồi thử lại.')
            try:
                yield
            finally:
                conn.execute('SELECT pg_advisory_unlock(%s)', (key,))

    def create_session(self, customer_id, session_id=None):
        sid = session_id or uuid4()
        with self.connect() as conn:
            conn.execute("INSERT INTO chat_sessions(session_id,customer_id,status) VALUES(%s,%s,'ACTIVE') ON CONFLICT(session_id) DO NOTHING", (sid, customer_id))
            result = self.owned(conn, sid, customer_id)
        event('conversation.session_persisted', {'session_id': sid, 'storage': self.storage})
        return result

    def get_session(self, session_id, customer_id):
        with self.connect() as conn: return self.owned(conn, session_id, customer_id)

    def list_sessions(self, customer_id, page=0, size=20):
        with self.connect() as conn:
            rows = conn.execute('SELECT session_id,customer_id,started_at,ended_at,status FROM chat_sessions WHERE customer_id=%s ORDER BY started_at DESC,session_id DESC LIMIT %s OFFSET %s',
                                (customer_id,size,page*size)).fetchall()
        return [self.session(row) for row in rows]

    def get_active_sessions(self, customer_id):
        with self.connect() as conn:
            rows = conn.execute("SELECT session_id,customer_id,started_at,ended_at,status FROM chat_sessions WHERE customer_id=%s AND status='ACTIVE' ORDER BY started_at DESC,session_id DESC LIMIT 100",
                                (customer_id,)).fetchall()
        return [self.session(row) for row in rows]

    def end_session(self, session_id, customer_id):
        with self.connect() as conn:
            self.owned(conn,session_id,customer_id,lock=True)
            conn.execute("UPDATE chat_sessions SET status='CLOSED',ended_at=COALESCE(ended_at,clock_timestamp()) WHERE session_id=%s AND customer_id=%s",(session_id,customer_id))
            return self.owned(conn,session_id,customer_id)

    @staticmethod
    def decode(role, text):
        if role == SenderType.ASSISTANT:
            try:
                stored = StoredAssistantMessage.model_validate_json(text)
                return stored.text, stored.snapshot
            except (ValidationError, ValueError):
                pass  # Existing plain text remains readable; USER never supplies snapshots.
        return text, None

    @staticmethod
    def stored_rating(value):
        # message_feedback lưu thang 1–5; API dùng -1 / 0 / 1.
        if value is None or value == 3: return 0
        return 1 if value >= 4 else -1

    def message(self, row):
        role=SenderType(row[2]);text,_=self.decode(role,row[3])
        rating=self.stored_rating(row[5]) if len(row) > 5 else 0
        return ChatMessage(id=row[0],session_id=row[1],sender_type=role,message=text,rating=rating,created_at=row[4])

    def add_message(self, session_id, sender_type, message, customer_id=None):
        if customer_id is None: raise HTTPException(401, 'Authenticated customer required')
        with self.connect() as conn:
            session=self.owned(conn,session_id,customer_id,lock=True)
            if not session.is_active: raise HTTPException(409,'Session is closed')
            row=conn.execute('INSERT INTO chat_history(history_id,session_id,customer_id,sender_type,message,created_at) VALUES(%s,%s,%s,%s,%s,clock_timestamp()) RETURNING history_id,session_id,sender_type,message,created_at',
                             (uuid4(),session_id,customer_id,SenderType(sender_type).value,message)).fetchone()
        event('conversation.message_persisted', {'history_id':row[0], 'sender_type':row[2], 'storage':self.storage})
        return self.message(row)

    def save_assistant(self,session_id,customer_id,text,snapshot=None):
        payload=StoredAssistantMessage(text=text,snapshot=snapshot).model_dump_json()
        if len(payload.encode()) > 128000:
            raise HTTPException(503,'Snapshot hội thoại vượt giới hạn lưu trữ.')
        return self.add_message(session_id,SenderType.ASSISTANT,payload,customer_id)

    def get_recent_messages(self,session_id,window=None):
        limit=10 if window is None else window
        if limit <= 0: return []
        with self.connect() as conn:
            rows=conn.execute('SELECT h.history_id,h.session_id,h.sender_type,h.message,h.created_at,'+FEEDBACK+' FROM chat_history h JOIN chat_sessions s ON s.session_id=h.session_id AND s.customer_id=h.customer_id WHERE h.session_id=%s ORDER BY h.created_at DESC,h.history_id DESC LIMIT %s',(session_id,min(limit,100))).fetchall()
        return [self.message(r) for r in reversed(rows)]

    def get_context_messages(self, session_id, customer_id, window=10):
        # Read a bounded audit tail; a failed USER is still available via history_page.
        with self.connect() as conn:
            self.owned(conn, session_id, customer_id)
            rows = conn.execute('SELECT h.history_id,h.session_id,h.sender_type,h.message,h.created_at,'+FEEDBACK+' FROM chat_history h WHERE h.session_id=%s AND h.customer_id=%s ORDER BY h.created_at DESC,h.history_id DESC LIMIT 100', (session_id, customer_id)).fetchall()
        history = [self.message(row) for row in reversed(rows)]
        result = completed_messages(history, window)
        event('conversation.context_selection', {'audit_rows_scanned': len(history),
              'context_messages': len(result), 'policy': 'completed_adjacent_turns', 'audit_preserved': True})
        return result

    def get_message(self,session_id,message_id):
        with self.connect() as conn:
            row=conn.execute('SELECT h.history_id,h.session_id,h.sender_type,h.message,h.created_at,'+FEEDBACK+' FROM chat_history h WHERE h.session_id=%s AND h.history_id=%s',(session_id,message_id)).fetchone()
        return self.message(row) if row else None

    def get_snapshot(self,session_id,customer_id):
        with self.connect() as conn:
            self.owned(conn,session_id,customer_id)
            rows=conn.execute("SELECT h.message FROM chat_history h JOIN chat_sessions s ON s.session_id=h.session_id AND s.customer_id=h.customer_id WHERE h.session_id=%s AND h.sender_type='ASSISTANT' ORDER BY h.created_at DESC,h.history_id DESC LIMIT 100",(session_id,)).fetchall()
        for row in rows:
            _,snapshot=self.decode(SenderType.ASSISTANT,row[0])
            if snapshot and snapshot.cards: return snapshot
        return None

    def last_product_cards(self,session_id,customer_id):
        snapshot=self.get_snapshot(session_id,customer_id)
        return snapshot.cards if snapshot else []

    def remember_product_cards(self,*args):
        pass  # Committed atomically with the ASSISTANT envelope in save_assistant.

    def set_rating(self,session_id,message_id,customer_id,rating):
        with self.connect() as conn:
            self.owned(conn,session_id,customer_id)
            found=conn.execute('SELECT 1 FROM chat_history WHERE session_id=%s AND history_id=%s AND customer_id=%s',
                               (session_id,message_id,customer_id)).fetchone()
            if found is None: raise HTTPException(404,'Message not found')
            if rating == 0:
                conn.execute('DELETE FROM message_feedback WHERE history_id=%s AND customer_id=%s',(message_id,customer_id))
            else:
                conn.execute('INSERT INTO message_feedback(history_id,customer_id,rating) VALUES(%s,%s,%s) '
                             'ON CONFLICT (history_id) DO UPDATE SET rating=EXCLUDED.rating,created_at=clock_timestamp()',
                             (message_id,customer_id,5 if rating > 0 else 1))
        event('conversation.feedback_persisted', {'history_id':message_id, 'rating':rating, 'storage':self.storage})
        return self.get_message(session_id,message_id)


    def history_page(self, session_id, customer_id, before=None, size=100):
        with self.connect() as conn:
            self.owned(conn,session_id,customer_id)
            args=[session_id,customer_id]
            condition=''
            if before is not None:
                cursor=conn.execute('SELECT created_at,history_id FROM chat_history WHERE session_id=%s AND customer_id=%s AND history_id=%s',
                                    (session_id,customer_id,before)).fetchone()
                if cursor is None: raise HTTPException(404,'History cursor not found')
                condition=' AND (h.created_at,h.history_id)<(%s,%s)'
                args.extend(cursor)
            args.append(size)
            rows=conn.execute('SELECT h.history_id,h.session_id,h.sender_type,h.message,h.created_at,'+FEEDBACK+' FROM chat_history h WHERE h.session_id=%s AND h.customer_id=%s'+condition+' ORDER BY h.created_at DESC,h.history_id DESC LIMIT %s',args).fetchall()
        return [self.message(r) for r in reversed(rows)]
