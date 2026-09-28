from typing import Protocol

from app.shared.contracts.context import GroundedContext


class ResponseGenerationService(Protocol):
    def generate(self, context: GroundedContext) -> str:
        ...
