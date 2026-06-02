from __future__ import annotations

import logging

import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

from app.utils.text_utils import normalize_text

LOGGER = logging.getLogger(__name__)


class EmbeddingService:
    def __init__(self, model_name: str) -> None:
        self._model_name = model_name
        self._model = None
        self._fallback_mode = True
        self._load_model()

    @property
    def fallback_mode(self) -> bool:
        return self._fallback_mode

    def compute_similarity(self, user_text: str, course_texts: list[str]) -> list[float]:
        if not course_texts:
            return []

        normalized_user_text = normalize_text(user_text)
        normalized_course_texts = [normalize_text(text) for text in course_texts]

        if self._model is not None:
            try:
                embeddings = self._model.encode(
                    [normalized_user_text, *normalized_course_texts],
                    convert_to_numpy=True,
                    normalize_embeddings=True,
                )
                user_embedding = embeddings[0].reshape(1, -1)
                course_embeddings = embeddings[1:]
                scores = cosine_similarity(user_embedding, course_embeddings)[0]
                return [float(np.clip(score, 0.0, 1.0)) for score in scores]
            except Exception:
                LOGGER.exception("Semantic embedding failed at runtime, switching to lexical fallback.")
                self._fallback_mode = True

        return self._lexical_similarity(normalized_user_text, normalized_course_texts)

    def _load_model(self) -> None:
        try:
            from sentence_transformers import SentenceTransformer

            self._model = SentenceTransformer(self._model_name)
            self._fallback_mode = False
            LOGGER.info("Embedding model loaded: %s", self._model_name)
        except Exception:
            self._model = None
            self._fallback_mode = True
            LOGGER.exception(
                "Could not load embedding model (%s). Lexical fallback mode enabled.",
                self._model_name,
            )

    def _lexical_similarity(self, user_text: str, course_texts: list[str]) -> list[float]:
        if not course_texts:
            return []

        try:
            documents = [user_text, *course_texts]
            if all(not doc for doc in documents):
                return [0.0] * len(course_texts)

            vectorizer = TfidfVectorizer(ngram_range=(1, 2))
            matrix = vectorizer.fit_transform(documents)
            scores = cosine_similarity(matrix[0:1], matrix[1:]).flatten()
            return [float(np.clip(score, 0.0, 1.0)) for score in scores]
        except Exception:
            LOGGER.exception("TF-IDF lexical fallback failed; using token overlap fallback.")
            return self._token_overlap_similarity(user_text, course_texts)

    def _token_overlap_similarity(self, user_text: str, course_texts: list[str]) -> list[float]:
        user_tokens = set(user_text.split())
        if not user_tokens:
            return [0.0] * len(course_texts)

        scores: list[float] = []
        for text in course_texts:
            tokens = set(text.split())
            if not tokens:
                scores.append(0.0)
                continue
            intersection = len(user_tokens.intersection(tokens))
            union = len(user_tokens.union(tokens))
            score = (intersection / union) if union else 0.0
            scores.append(float(np.clip(score, 0.0, 1.0)))
        return scores

