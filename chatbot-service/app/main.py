from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.features.chat.dependencies import build_chat_service
from app.features.chat.service import MockChatService
from app.shared.config import get_settings

from app.features.chat.router import router as chat_router


@asynccontextmanager
async def lifespan(app: FastAPI):
    service, client = build_chat_service(get_settings())
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
