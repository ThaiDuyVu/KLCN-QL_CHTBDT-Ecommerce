"""Run with: python -m app.features.knowledge.ingest [--force]"""
import argparse

from app.features.knowledge.database import postgres_repository
from app.features.knowledge.service import KnowledgeIndexingService
from app.shared.config import get_settings
from app.shared.ollama.client import OllamaClient


def main() -> None:
    parser = argparse.ArgumentParser(description="Index eligible knowledge documents")
    parser.add_argument("--force", action="store_true", help="Re-embed all chunks after a model change")
    args = parser.parse_args()
    settings = get_settings()
    if not settings.knowledge_database_url:
        parser.error("KNOWLEDGE_DATABASE_URL is required")
    repository = postgres_repository(settings.knowledge_database_url)
    client = OllamaClient(settings)
    try:
        count = KnowledgeIndexingService(repository, client, status=settings.knowledge_document_status).ingest(force=args.force)
        print(f"Indexed {count} documents")
    finally:
        client.close()


if __name__ == "__main__":
    main()
