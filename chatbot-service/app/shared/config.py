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
