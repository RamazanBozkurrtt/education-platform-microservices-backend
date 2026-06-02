from __future__ import annotations

import logging

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.core.config import get_settings
from app.schemas.recommendation import (
    DashboardRecommendationRequest,
    RecommendationResponse,
    SearchRecommendationRequest,
)
from app.services.embedding_service import EmbeddingService
from app.services.recommendation_service import RecommendationService
from app.services.scoring_service import ScoringService

settings = get_settings()

logging.basicConfig(
    level=getattr(logging, settings.log_level.upper(), logging.INFO),
    format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
)
LOGGER = logging.getLogger(__name__)
for noisy_logger in [
    "httpx",
    "httpcore",
    "huggingface_hub",
    "transformers",
    "sentence_transformers.base.model",
]:
    logging.getLogger(noisy_logger).setLevel(logging.WARNING)

embedding_service = EmbeddingService(settings.embedding_model_name)
scoring_service = ScoringService()
recommendation_service = RecommendationService(embedding_service, scoring_service)

app = FastAPI(
    title=settings.app_name,
    version=settings.app_version,
    description="FastAPI tabanli semantic ve progress-aware recommendation service",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"] if settings.cors_allow_origins == ("*",) else list(settings.cors_allow_origins),
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.on_event("startup")
async def on_startup() -> None:
    LOGGER.info("Recommendation service is ready. fallback_mode=%s", embedding_service.fallback_mode)
    LOGGER.info(
        "Downstream URLs configured: course=%s user=%s auth=%s",
        settings.course_service_url,
        settings.user_service_url,
        settings.auth_service_url,
    )


@app.get("/health", tags=["health"])
async def health() -> dict[str, str]:
    return {"status": "UP"}


@app.post(
    f"{settings.api_prefix}/recommendations/dashboard",
    response_model=RecommendationResponse,
    tags=["recommendations"],
)
async def dashboard_recommendations(request: DashboardRecommendationRequest) -> RecommendationResponse:
    return recommendation_service.recommend_for_dashboard(request)


@app.post(
    f"{settings.api_prefix}/recommendations/search",
    response_model=RecommendationResponse,
    tags=["recommendations"],
)
async def search_recommendations(request: SearchRecommendationRequest) -> RecommendationResponse:
    return recommendation_service.recommend_for_search(request)


app.add_api_route(
    "/recommendations/dashboard",
    dashboard_recommendations,
    methods=["POST"],
    response_model=RecommendationResponse,
    include_in_schema=False,
)

app.add_api_route(
    "/recommendations/search",
    search_recommendations,
    methods=["POST"],
    response_model=RecommendationResponse,
    include_in_schema=False,
)
