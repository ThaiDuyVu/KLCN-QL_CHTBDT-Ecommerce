"""Keep unit tests independent of the real AI configuration used by the lab."""
import pytest
from app.shared.config import get_settings


@pytest.fixture(autouse=True)
def isolated_settings(monkeypatch):
    monkeypatch.setenv('CONVERSATION_STORAGE', 'memory')
    monkeypatch.setenv('CONVERSATION_DATABASE_URL', '')
    monkeypatch.setenv('CHATBOT_AI_ENABLED', 'false')
    monkeypatch.setenv('CHATBOT_TRACE_ENABLED', 'false')
    monkeypatch.setenv('CHATBOT_TRACE_FILE_ENABLED', 'false')
    monkeypatch.setenv('PRODUCT_ADVISOR_MODE', 'mock')
    monkeypatch.setenv('KNOWLEDGE_DATABASE_URL', '')
    monkeypatch.setenv('PRODUCT_INDEX_DATABASE_URL', '')
    get_settings.cache_clear()
    yield
    get_settings.cache_clear()
