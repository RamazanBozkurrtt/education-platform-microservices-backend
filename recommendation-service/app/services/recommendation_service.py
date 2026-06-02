from __future__ import annotations

import logging

from app.core.config import get_settings
from app.schemas.recommendation import (
    DashboardRecommendationRequest,
    RecommendationResponse,
    SearchRecommendationRequest,
)
from app.services.embedding_service import EmbeddingService
from app.services.scoring_service import ScoringService
from app.utils.text_utils import build_course_text, build_user_interest_text

LOGGER = logging.getLogger(__name__)


class RecommendationService:
    def __init__(self, embedding_service: EmbeddingService, scoring_service: ScoringService) -> None:
        self.embedding_service = embedding_service
        self.scoring_service = scoring_service
        self.strategy_name = get_settings().strategy_name

    def recommend_for_dashboard(self, request: DashboardRecommendationRequest) -> RecommendationResponse:
        return self._recommend(
            context=request.context,
            limit=request.limit,
            user_profile=request.userProfile,
            candidate_courses=request.candidateCourses,
            query=None,
        )

    def recommend_for_search(self, request: SearchRecommendationRequest) -> RecommendationResponse:
        return self._recommend(
            context=request.context,
            limit=request.limit,
            user_profile=request.userProfile,
            candidate_courses=request.candidateCourses,
            query=request.query,
        )

    def _recommend(self, *, context, limit, user_profile, candidate_courses, query):
        if not candidate_courses:
            return RecommendationResponse(recommendations=[], strategy=self.strategy_name)

        user_interest_text = build_user_interest_text(user_profile, query=query)

        course_ids: list[str] = []
        course_texts: list[str] = []
        for course in candidate_courses:
            course_id = (course.courseId or "").strip()
            if not course_id:
                continue
            course_ids.append(course_id)
            course_texts.append(build_course_text(course))

        if not course_ids:
            return RecommendationResponse(recommendations=[], strategy=self.strategy_name)

        semantic_scores = self.embedding_service.compute_similarity(user_interest_text, course_texts)
        semantic_score_map = {
            course_ids[index]: semantic_scores[index] if index < len(semantic_scores) else 0.0
            for index in range(len(course_ids))
        }

        if self.embedding_service.fallback_mode:
            LOGGER.info("Recommendation service is running in lexical fallback mode.")

        response = self.scoring_service.score_and_rank(
            context=context,
            limit=limit,
            user_profile=user_profile,
            candidate_courses=candidate_courses,
            semantic_scores=semantic_score_map,
            query=query,
        )
        response.strategy = self.strategy_name
        return response

