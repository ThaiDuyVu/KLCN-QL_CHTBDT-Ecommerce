"""Opt-in development traces. ContextVars propagate through asyncio.to_thread."""
from collections import OrderedDict
from contextlib import contextmanager
from contextvars import ContextVar
from copy import deepcopy
from datetime import datetime, timezone
from functools import wraps
import inspect
import json
import logging
from logging.handlers import RotatingFileHandler
from pathlib import Path
import re
import traceback
from threading import RLock
from time import monotonic
from uuid import uuid4

from fastapi import HTTPException
from pydantic import BaseModel

_current = ContextVar('chat_trace', default=None)
_parent = ContextVar('chat_trace_parent', default=None)
SENSITIVE = {'password', 'authorization', 'cookie', 'cookies', 'setcookie', 'token', 'accesstoken',
             'refreshtoken', 'secret', 'jwtsecret', 'databaseurl', 'dsn', 'email', 'phone',
             'phonenumber', 'address', 'shippingaddress', 'recipientphone', 'recipientaddress'}


def sanitize(value):
    if isinstance(value, BaseModel):
        value = value.model_dump(mode='json')
    if isinstance(value, dict):
        return {str(k): '[REDACTED]' if re.sub(r'[^a-z]', '', str(k).lower()) in SENSITIVE else sanitize(v)
                for k, v in value.items()}
    if isinstance(value, (list, tuple, set)):
        return [sanitize(v) for v in value]
    if isinstance(value, str):
        # JSON embedded in model messages must receive the same redaction as DTOs.
        try:
            parsed = json.loads(value)
            if isinstance(parsed, (dict, list)):
                cleaned = sanitize(parsed)
                if cleaned != parsed:
                    return json.dumps(cleaned, ensure_ascii=False)
        except (ValueError, TypeError):
            pass
        value = re.sub(r'(?i)(Bearer\s+)[\w.\-]+', r'\1[REDACTED]', value)
        value = re.sub(r'(?i)(postgres(?:ql)?://)[^\s]+', r'\1[REDACTED]', value)
        return value
    if value is None or isinstance(value, (int, float, bool)):
        return value
    return str(value)


def bounded(value):
    cleaned = sanitize(value)
    raw = json.dumps(cleaned, ensure_ascii=False, default=str)
    if len(raw) > 64000:
        return {'truncated': True, 'original_characters': len(raw), 'preview': raw[:64000]}
    return cleaned


def error_data(exc):
    result = {'type': type(exc).__name__, 'frames': [
        {'file': str(Path(frame.filename).name), 'function': frame.name, 'line': frame.lineno}
        for frame in traceback.extract_tb(exc.__traceback__)[-8:]
    ]}
    if isinstance(exc, HTTPException):
        result['http_status'] = exc.status_code
    # Never stringify arbitrary exceptions: transport/DB errors may contain credentials.
    if hasattr(exc, 'errors'):
        result['validation'] = exc.errors(include_input=False, include_context=False, include_url=False)
    return result


class Trace:
    def __init__(self, trace_id, session_id, owner):
        self.started = monotonic()
        self.lock = RLock()
        self.size = 0
        self.data = dict(trace_id=str(trace_id), session_id=str(session_id), owner=str(owner),
                         started_at=datetime.now(timezone.utc).isoformat(), status='running',
                         duration_ms=None, events=[], dropped_events=0,
                         capture_policy='Sanitized payloads; 64k characters per payload, 1MB per trace, 256 events. Credentials and structured contact fields are redacted. Free-text may contain personal data.')

    def add(self, name, status, inputs=None, output=None, parent_id=None):
        with self.lock:
            event = dict(id=str(uuid4()), parent_id=parent_id, name=name, status=status,
                         offset_ms=round((monotonic()-self.started)*1000, 2), duration_ms=None,
                         input=bounded(inputs), output=bounded(output))
            size = len(json.dumps(event, ensure_ascii=False))
            if len(self.data['events']) >= 256 or self.size + size > 1000000:
                self.data['dropped_events'] += 1
                return None
            self.size += size
            self.data['events'].append(event)
            return event

    def finish_event(self, event, started, status, output):
        if event is None:
            return
        with self.lock:
            cleaned = bounded(output)
            size = len(json.dumps(cleaned, ensure_ascii=False))
            if self.size + size > 1000000:
                cleaned = {'truncated': True, 'reason': 'trace_size_limit'}
            else:
                self.size += size
            event.update(status=status, duration_ms=round((monotonic()-started)*1000, 2), output=cleaned)

    def snapshot(self):
        with self.lock:
            data = deepcopy(self.data)
            data.pop('owner', None)
            return data


class TraceStore:
    def __init__(self, capacity=50, ttl=3600):
        self.capacity, self.ttl = capacity, ttl
        self.items = OrderedDict()
        self.lock = RLock()

    def _expire(self):
        for key in list(self.items):
            if monotonic() - self.items[key].started > self.ttl:
                del self.items[key]

    def create(self, trace_id, session_id, owner):
        with self.lock:
            self._expire()
            key = str(trace_id)
            if key in self.items:
                raise HTTPException(409, 'Trace ID đã được sử dụng. Hãy tạo ID mới.')
            trace = Trace(trace_id, session_id, owner)
            self.items[key] = trace
            while len(self.items) > self.capacity:
                self.items.popitem(last=False)
            return trace

    def get(self, trace_id, session_id, owner):
        with self.lock:
            self._expire()
            t = self.items.get(str(trace_id))
            if not t or t.data['owner'] != str(owner) or t.data['session_id'] != str(session_id):
                raise HTTPException(404, 'Không tìm thấy trace hoặc trace đã hết hạn.')
            return t.snapshot()

    def list(self, session_id, owner):
        with self.lock:
            self._expire()
            return [{k: v for k, v in t.snapshot().items() if k != 'events'}
                    for t in reversed(self.items.values())
                    if t.data['owner'] == str(owner) and t.data['session_id'] == str(session_id)]


store = TraceStore()
_file_lock = RLock()
_file_handler = None


def enabled(settings):
    return settings.chatbot_trace_enabled and settings.app_env.lower() in {'development', 'local', 'test'}


def persist(trace):
    from app.shared.config import get_settings
    global _file_handler
    if not get_settings().chatbot_trace_file_enabled:
        return
    try:
        with _file_lock:
            if _file_handler is None:
                path = Path(__file__).resolve().parents[4] / '.local' / 'logs' / 'chatbot-traces.jsonl'
                path.parent.mkdir(parents=True, exist_ok=True)
                _file_handler = RotatingFileHandler(path, maxBytes=5_000_000, backupCount=2, encoding='utf-8')
                _file_handler.setFormatter(logging.Formatter('%(message)s'))
            payload = json.dumps(trace.snapshot(), ensure_ascii=False)
            _file_handler.emit(logging.LogRecord('chatbot.trace', logging.INFO, '', 0, payload, (), None))
    except Exception:
        logging.getLogger(__name__).warning('Could not persist chatbot trace; in-memory trace remains available')


@contextmanager
def capture(trace):
    token = _current.set(trace)
    parent = _parent.set(None)
    try:
        yield
    except BaseException as exc:
        with trace.lock:
            trace.data['status'] = 'error'
            trace.data['error'] = error_data(exc)
        raise
    else:
        with trace.lock:
            trace.data['status'] = 'warning' if any(e['status'] in {'warning', 'error'} for e in trace.data['events']) else 'ok'
    finally:
        with trace.lock:
            trace.data['duration_ms'] = round((monotonic()-trace.started)*1000, 2)
        _parent.reset(parent)
        _current.reset(token)
        persist(trace)


def event(name, output=None, *, status='ok', inputs=None):
    trace = _current.get()
    if trace is not None:
        trace.add(name, status, inputs, output, _parent.get())


@contextmanager
def span(name, inputs=None):
    trace = _current.get()
    if trace is None:
        yield lambda output: None
        return
    started = monotonic()
    item = trace.add(name, 'running', inputs, parent_id=_parent.get())
    token = _parent.set(item['id'] if item else _parent.get())
    output = None
    def save(value):
        nonlocal output
        output = value
    try:
        yield save
    except BaseException as exc:
        trace.finish_event(item, started, 'error', error_data(exc))
        raise
    else:
        trace.finish_event(item, started, 'ok', output)
    finally:
        _parent.reset(token)


def traced(name, fields=(), *, result=True):
    """Only allowlisted arguments are captured; never self, cookies, transports or settings."""
    def decorate(fn):
        signature = inspect.signature(fn)
        def inputs(args, kwargs):
            bound = signature.bind(*args, **kwargs)
            return {k: v for k, v in bound.arguments.items() if k in fields}
        if inspect.iscoroutinefunction(fn):
            @wraps(fn)
            async def wrapper(*args, **kwargs):
                if _current.get() is None:
                    return await fn(*args, **kwargs)
                with span(name, inputs(args, kwargs)) as done:
                    value = await fn(*args, **kwargs)
                    done(value if result else {'completed': True})
                    return value
        else:
            @wraps(fn)
            def wrapper(*args, **kwargs):
                if _current.get() is None:
                    return fn(*args, **kwargs)
                with span(name, inputs(args, kwargs)) as done:
                    value = fn(*args, **kwargs)
                    done(value if result else {'completed': True})
                    return value
        return wrapper
    return decorate
