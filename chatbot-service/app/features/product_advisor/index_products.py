"""Run explicitly: python -m app.features.product_advisor.index_products"""

from app.features.product_advisor.backend import BackendProductClient
from app.features.product_advisor.semantic import (
    OllamaEmbeddingClient,
    ProductSemanticIndex,
    semantic_content,
)
from app.shared.config import get_settings
from uuid import UUID


def main() -> None:
    settings = get_settings()
    backend = BackendProductClient(settings.backend_base_url, settings.backend_bearer_token)
    index = ProductSemanticIndex(settings.product_index_database_url)
    embedder = OllamaEmbeddingClient(settings.ollama_base_url, settings.ollama_embedding_model)
    page = 0
    updated = 0
    while True:
        result = backend.catalog_page(page)
        for row in result["content"]:
            if row["status"] != "ACTIVE":
                continue
            product_id = UUID(row["productId"])
            detail = backend.detail(product_id)
            updated += index.upsert_if_changed(product_id, semantic_content(detail), embedder)
        page += 1
        if page >= result["totalPages"]:
            break
    print(f"Indexed {updated} changed products")


if __name__ == "__main__":
    main()
