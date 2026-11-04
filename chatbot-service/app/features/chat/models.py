from pydantic import BaseModel, ConfigDict, Field
from typing import Literal


class SendMessageRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    message: str = Field(min_length=1, max_length=4000)
    provider: Literal['ollama', 'gemini'] | None = None


class FeedbackRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    rating: int = Field(default=0, ge=-1, le=1)
