from contextlib import asynccontextmanager
import json
import logging

from fastapi import FastAPI

from app.features.chat.dependencies import build_chat_service
from app.features.chat.service import MockChatService
from app.shared.config import get_settings, runtime_warnings, validate_runtime_settings

from app.features.chat.router import router as chat_router


logger = logging.getLogger("chatbot.runtime")


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    validate_runtime_settings(settings)
    warnings = runtime_warnings(settings)
    if warnings:
        logger.warning("chatbot.runtime_configuration %s", json.dumps({
            "app_env": settings.app_env,
            "app_env_explicit": "app_env" in settings.model_fields_set,
            "warnings": warnings,
        }))
    service, client = build_chat_service(settings)
    app.state.chat_service = service
    try:
        yield
    finally:
        if client is not None:
            client.close()


app = FastAPI(
    title="QL CHTBDT Chatbot Service",
    version="0.1.0",
    lifespan=lifespan,
)

app.state.chat_service = MockChatService()
app.include_router(chat_router)


@app.get("/api/v1/health")
def health() -> dict[str, str]:
    return {
        "status": "ok",
        "service": "chatbot-service",
    }
