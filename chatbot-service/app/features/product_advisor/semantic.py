"""Manual pgvector index and a small Ollama embedding adapter."""
from app.shared.tracing import traced, event

import hashlib
from collections.abc import Sequence
from typing import Any
from uuid import UUID

import httpx


class SemanticRankingUnavailable(RuntimeError):
    """The optional vector index could not rank; carries no DSN or driver message."""


def semantic_content(detail: dict[str, Any]) -> str:
    product = detail["product"]
    parts = [product["productName"], product.get("description") or ""]
    parts.extend([detail["category"]["categoryName"], detail["brand"]["brandName"]])
    parts.extend(
        f"{spec['specKey']}: {spec['specValue']}"
        for spec in sorted(detail.get("specifications", []), key=lambda x: (x["specKey"], x["specValue"]))
    )
    parts.extend(
        " ".join(str(variant.get(key) or "") for key in ("sku", "ram", "storage", "color"))
        for variant in sorted(detail.get("variants", []), key=lambda x: x["sku"])
    )
    return "\n".join(part.strip() for part in parts if part.strip())


class OllamaEmbeddingClient:
    def __init__(self, base_url: str, model: str) -> None:
        self._base_url = base_url
        self._model = model

    @property
    def model(self) -> str:
        return self._model

    @traced("product.embedding", ("text",), result=False)
    def embed(self, text: str) -> list[float]:
        response = httpx.post(
            f"{self._base_url.rstrip('/')}/api/embed",
            json={"model": self._model, "input": text},
            timeout=60,
        )
        response.raise_for_status()
        values = response.json()["embeddings"][0]
        if not values:
            raise ValueError("Embedding model returned an empty vector")
        event("embedding.shape", {"model": self.model, "dimensions": len(values)})
        return [float(value) for value in values]


class ProductSemanticIndex:
    def __init__(self, database_url: str) -> None:
        if not database_url:
            raise ValueError("PRODUCT_INDEX_DATABASE_URL is required")
        self._database_url = database_url

    def _connect(self) -> Any:
        import psycopg

        return psycopg.connect(self._database_url, connect_timeout=5)

    def index_signature(self, product_id: UUID) -> tuple[str, str] | None:
        with self._connect() as connection:
            row = connection.execute(
                "SELECT content_hash, embedding_model FROM product_semantic_index WHERE product_id = %s", (product_id,)
            ).fetchone()
        return (row[0], row[1]) if row else None

    def upsert_if_changed(
        self, product_id: UUID, content: str, embedder: OllamaEmbeddingClient
    ) -> bool:
        digest = hashlib.sha256(content.encode("utf-8")).hexdigest()
        if self.index_signature(product_id) == (digest, embedder.model):
            return False
        vector = embedder.embed(content)
        vector_literal = "[" + ",".join(str(number) for number in vector) + "]"
        with self._connect() as connection:
            connection.execute(
                """INSERT INTO product_semantic_index
                   (product_id, semantic_content, content_hash, embedding_model, embedding_vector)
                   VALUES (%s, %s, %s, %s, %s::vector)
                   ON CONFLICT (product_id) DO UPDATE SET
                     semantic_content = EXCLUDED.semantic_content,
                     content_hash = EXCLUDED.content_hash,
                     embedding_model = EXCLUDED.embedding_model,
                     embedding_vector = EXCLUDED.embedding_vector,
                     indexed_at = CURRENT_TIMESTAMP
                   WHERE product_semantic_index.content_hash IS DISTINCT FROM EXCLUDED.content_hash
                      OR product_semantic_index.embedding_model IS DISTINCT FROM EXCLUDED.embedding_model""",
                (product_id, content, digest, embedder.model, vector_literal),
            )
        return True

    @traced("product.vector_ranking", ("product_ids", "model"))
    def scores(self, product_ids: Sequence[UUID], vector: list[float], model: str) -> dict[UUID, float]:
        if not product_ids:
            return {}
        import psycopg

        vector_literal = "[" + ",".join(str(number) for number in vector) + "]"
        try:
            return self._scores(product_ids, vector_literal, len(vector), model)
        except psycopg.Error as exc:
            raise SemanticRankingUnavailable(type(exc).__name__) from None

    def _scores(self, product_ids: Sequence[UUID], vector_literal: str, dimensions: int, model: str) -> dict[UUID, float]:
        with self._connect() as connection:
            sql = """SELECT product_id, 1 - (embedding_vector <=> %s::vector) AS score
                   FROM product_semantic_index
                   WHERE product_id = ANY(%s) AND vector_dims(embedding_vector) = %s
                     AND embedding_model = %s"""
            event("product.sql", {"sql": sql, "parameters": {"product_ids": product_ids, "dimensions": dimensions, "model": model, "vector": "omitted"}})
            rows = connection.execute(sql, (vector_literal, list(product_ids), dimensions, model)).fetchall()
        return {row[0]: float(row[1]) for row in rows if row[1] is not None}
