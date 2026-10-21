from functools import lru_cache
from typing import Literal

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "chatbot-service"
    app_env: str = "development"

    app_host: str = "0.0.0.0"
    app_port: int = 8090

    backend_base_url: str = "http://localhost:8080"

    ollama_base_url: str = "http://localhost:11434"
    ollama_chat_model: str = "qwen3:4b-instruct"
    ollama_embedding_model: str = "qwen3-embedding:0.6b"

    ollama_timeout_seconds: float = Field(default=60, gt=0)
    knowledge_database_url: str | None = None
    knowledge_document_status: str = Field(default="PUBLISHED", min_length=1)
    chatbot_ai_enabled: bool = False
    chatbot_trace_enabled: bool = False
    chatbot_trace_file_enabled: bool = False

    conversation_storage: Literal["memory", "postgres"] = "memory"
    conversation_database_url: str | None = None

    chat_history_window: int = Field(default=10, ge=0)
    default_top_k: int = Field(default=5, ge=1, le=20)

    product_advisor_mode: Literal["mock", "real"] = "mock"
    backend_bearer_token: str = ""
    chatbot_warehouse_id: str = ""
    product_index_database_url: str = ""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )


@lru_cache
def get_settings() -> Settings:
    return Settings()


DEV_LIKE_ENVIRONMENTS = frozenset(('development', 'local', 'test'))


def is_dev_like(app_env: str) -> bool:
    return app_env.lower() in DEV_LIKE_ENVIRONMENTS


def validate_runtime_settings(settings: Settings) -> None:
    """Refuse a non-development start that would serve mock, memory or traced behaviour.

    Called from the application lifespan, not as a model validator, so Settings objects
    built for tests (including app_env='production') stay constructible.
    """
    if is_dev_like(settings.app_env):
        return
    invalid = [name for name, ok in (
        ('CHATBOT_AI_ENABLED', settings.chatbot_ai_enabled),
        ('PRODUCT_ADVISOR_MODE', settings.product_advisor_mode == 'real'),
        ('CONVERSATION_STORAGE', settings.conversation_storage == 'postgres'),
        ('CONVERSATION_DATABASE_URL', bool(settings.conversation_database_url)),
        ('PRODUCT_INDEX_DATABASE_URL', bool(settings.product_index_database_url)),
        ('CHATBOT_TRACE_ENABLED', not settings.chatbot_trace_enabled),
        ('CHATBOT_TRACE_FILE_ENABLED', not settings.chatbot_trace_file_enabled),
    ) if not ok]
    if invalid:
        # Variable names only; values may be connection strings or tokens.
        raise ValueError(f"Invalid chatbot configuration for APP_ENV={settings.app_env!r}: " + ', '.join(invalid))


def runtime_warnings(settings: Settings) -> list[str]:
    """Non-blocking signals for an operator; detection only, a forgotten APP_ENV still starts."""
    return [code for code, active in (
        ('ai_disabled', not settings.chatbot_ai_enabled),
        ('product_advisor_mock', settings.product_advisor_mode == 'mock'),
        ('conversation_memory', settings.conversation_storage == 'memory'),
        ('app_env_implicit', 'app_env' not in settings.model_fields_set),
        ('warehouse_unconfigured', not settings.chatbot_warehouse_id),
    ) if active]
