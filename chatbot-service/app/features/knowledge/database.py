from contextlib import contextmanager

from app.features.knowledge.repository import KnowledgeRepositoryError, PostgresKnowledgeRepository


def postgres_repository(database_url: str) -> PostgresKnowledgeRepository:
    # Lazy import: the mock pipeline does not require a database driver/server.
    import psycopg

    @contextmanager
    def connect():
        try:
            with psycopg.connect(database_url, connect_timeout=5) as connection:
                yield connection
        except psycopg.Error as exc:
            raise KnowledgeRepositoryError("Knowledge database operation failed") from exc

    return PostgresKnowledgeRepository(connect)
