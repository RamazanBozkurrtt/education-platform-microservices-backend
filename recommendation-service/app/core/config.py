from __future__ import annotations

import os
from dataclasses import dataclass
from functools import lru_cache


@dataclass(frozen=True)
class Settings:
    app_name: str = os.getenv("APP_NAME", "EduBase Recommendation Service")
    app_version: str = os.getenv("APP_VERSION", "1.0.0")
    log_level: str = os.getenv("LOG_LEVEL", "INFO")
    embedding_model_name: str = os.getenv(
        "EMBEDDING_MODEL_NAME", "sentence-transformers/all-MiniLM-L6-v2"
    )
    strategy_name: str = os.getenv(
        "RECOMMENDATION_STRATEGY", "SEMANTIC_SIMILARITY + PROGRESS_AWARE_RANKING"
    )
    cors_allow_origins: tuple[str, ...] = tuple(
        value.strip()
        for value in os.getenv("CORS_ALLOW_ORIGINS", "*").split(",")
        if value.strip()
    ) or ("*",)
    api_prefix: str = os.getenv("API_PREFIX", "/api/v1")
    course_service_url: str = os.getenv("COURSE_SERVICE_URL", "http://localhost:8080")
    user_service_url: str = os.getenv("USER_SERVICE_URL", "http://localhost:8080")
    auth_service_url: str = os.getenv("AUTH_SERVICE_URL", "http://localhost:8080")


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()
