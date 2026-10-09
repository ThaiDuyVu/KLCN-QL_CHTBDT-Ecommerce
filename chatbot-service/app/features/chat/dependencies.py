from app.features.chat.service import MockChatService
from app.features.knowledge.database import postgres_repository
from app.features.knowledge.service import PgvectorKnowledgeRetrievalService
from uuid import UUID

from app.features.product_advisor.backend import BackendProductClient
from app.features.product_advisor.real_service import RealProductAdvisorService
from app.features.product_advisor.semantic import OllamaEmbeddingClient, ProductSemanticIndex
from app.features.product_advisor.protocol import ProductAdvisorService
from app.features.query_understanding.ollama_service import OllamaQueryUnderstandingService
from app.features.response_generation.ollama_service import OllamaResponseGenerationService
from app.shared.config import Settings
from app.shared.contracts.product import ProductMatch
from app.shared.contracts.query import ProductSearchPlan
from app.shared.contracts.response import ProductCard
from app.shared.ollama.client import OllamaClient


class UnavailableProductAdvisor:
    """Until the product owner injects the real backend adapter, return no evidence."""
    def search(self, plan: ProductSearchPlan) -> list[ProductMatch]:
        return []

    def build_cards(self, matches: list[ProductMatch]) -> list[ProductCard]:
        return []


def build_chat_service(settings: Settings, *, product_advisor: ProductAdvisorService | None = None) -> tuple[MockChatService, OllamaClient | None]:
    if product_advisor is None and settings.product_advisor_mode == "real":
        product_advisor = RealProductAdvisorService(
            backend=BackendProductClient(settings.backend_base_url, settings.backend_bearer_token),
            warehouse_id=UUID(settings.chatbot_warehouse_id) if settings.chatbot_warehouse_id else None,
            index=ProductSemanticIndex(settings.product_index_database_url),
            embedder=OllamaEmbeddingClient(settings.ollama_base_url, settings.ollama_embedding_model),
        )
    if not settings.chatbot_ai_enabled:
        return MockChatService(product_advisor=product_advisor), None
    client = OllamaClient(settings)
    knowledge = None
    if settings.knowledge_database_url:
        repository = postgres_repository(settings.knowledge_database_url)
        knowledge = PgvectorKnowledgeRetrievalService(repository, client, status=settings.knowledge_document_status)
    return MockChatService(
        query_understanding=OllamaQueryUnderstandingService(client, history_window=settings.chat_history_window),
        product_advisor=product_advisor if product_advisor is not None else UnavailableProductAdvisor(),
        response_generation=OllamaResponseGenerationService(client),
        knowledge_retrieval=knowledge,
        knowledge_top_k=settings.default_top_k,
    ), client
