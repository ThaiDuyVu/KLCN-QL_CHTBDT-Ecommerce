"""Request-scoped model selection. Embeddings always use the existing local model."""
from contextlib import contextmanager
from contextvars import ContextVar

from fastapi import HTTPException
from app.shared.tracing import event

_provider = ContextVar('llm_provider', default=None)


class SelectableChatModel:
    def __init__(self, settings, ollama, gemini=None):
        self.settings, self.ollama, self.gemini = settings, ollama, gemini

    def available(self, provider):
        if not self.settings.chatbot_ai_enabled:
            return False
        if provider == 'ollama':
            return self.settings.ollama_chat_enabled
        return provider == 'gemini' and self.settings.gemini_enabled and self.gemini is not None

    @property
    def provider(self):
        return _provider.get() or self.settings.llm_provider

    @contextmanager
    def select(self, provider=None):
        chosen = provider or self.settings.llm_provider
        if not self.available(chosen):
            raise HTTPException(503, 'Provider được chọn đang tắt hoặc chưa được cấu hình.')
        token = _provider.set(chosen)
        try:
            event('llm.provider_selected', {'provider': chosen, 'model': self.settings.gemini_model if chosen == 'gemini' else self.settings.ollama_chat_model})
            yield
        finally:
            _provider.reset(token)

    def chat(self, messages, *, schema=None):
        chosen = self.provider
        if not self.available(chosen):
            raise HTTPException(503, 'Provider được chọn đang tắt hoặc chưa được cấu hình.')
        client = self.gemini if chosen == 'gemini' else self.ollama
        return client.chat(messages, schema=schema)

    def embed(self, texts):
        return self.ollama.embed(texts)

    def close(self):
        try:
            if self.gemini is not None:
                self.gemini.close()
        finally:
            self.ollama.close()
