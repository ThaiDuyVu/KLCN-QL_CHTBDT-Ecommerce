from functools import lru_cache
from typing import Literal

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

    chat_history_window: int = 10
    default_top_k: int = 5

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
