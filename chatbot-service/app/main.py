from fastapi import FastAPI

from app.features.chat.router import router as chat_router


app = FastAPI(
    title="QL CHTBDT Chatbot Service",
    version="0.1.0",
)

app.include_router(chat_router)


@app.get("/api/v1/health")
def health() -> dict[str, str]:
    return {
        "status": "ok",
        "service": "chatbot-service",
    }