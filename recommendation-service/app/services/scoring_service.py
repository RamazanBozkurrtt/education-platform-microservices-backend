from __future__ import annotations

import math
from typing import Optional

import numpy as np

from app.schemas.recommendation import (
    CandidateCourse,
    RecommendationContext,
    RecommendationItem,
    RecommendationResponse,
    UserProfile,
)
from app.utils.text_utils import normalize_text, safe_list


class ScoringService:
    DEFAULT_DURATION_SECONDS = 7200

    def score_and_rank(
        self,
        *,
        context: RecommendationContext,
        limit: int,
        user_profile: UserProfile,
        candidate_courses: list[CandidateCourse],
        semantic_scores: dict[str, float],
        query: Optional[str] = None,
    ) -> RecommendationResponse:
        completed_ids = {
            course_id.strip()
            for course_id in safe_list(user_profile.completedCourseIds)
            if isinstance(course_id, str) and course_id.strip()
        }

        favorite_categories_ordered = [
            normalize_text(value)
            for value in safe_list(user_profile.favoriteCategories)
            if isinstance(value, str) and value.strip()
        ]
        favorite_categories = set(favorite_categories_ordered)
        preferred_levels = {
            normalize_text(value)
            for value in safe_list(user_profile.preferredLevels)
            if isinstance(value, str) and value.strip()
        }

        preferred_duration = (
            user_profile.preferredDurationSeconds
            if (user_profile.preferredDurationSeconds or 0) > 0
            else self.DEFAULT_DURATION_SECONDS
        )
        dropout_rate = float(user_profile.dropoutRate or 0.0)

        valid_courses: list[CandidateCourse] = []
        for course in candidate_courses:
            course_id = (course.courseId or "").strip()
            if not course_id or course_id in completed_ids:
                continue
            valid_courses.append(course)

        if not valid_courses:
            return RecommendationResponse(recommendations=[])

        max_enrollment = max((max(course.enrollmentCount or 0, 0) for course in valid_courses), default=1)
        if max_enrollment <= 0:
            max_enrollment = 1

        weights = (
            {
                "semantic": 0.55,
                "category": 0.15,
                "duration": 0.10,
                "level": 0.10,
                "popularity": 0.10,
            }
            if context == RecommendationContext.SEARCH
            else {
                "semantic": 0.40,
                "category": 0.25,
                "duration": 0.15,
                "level": 0.10,
                "popularity": 0.10,
            }
        )

        recommendations: list[RecommendationItem] = []
        for course in valid_courses:
            course_id = (course.courseId or "").strip()
            semantic_score = float(np.clip(semantic_scores.get(course_id, 0.0), 0.0, 1.0))
            category_score = self._category_score(course, favorite_categories)
            duration_score = self._duration_score(
                duration_seconds=course.durationSeconds,
                preferred_duration_seconds=preferred_duration,
                dropout_rate=dropout_rate,
            )
            level_score = self._level_score(course, preferred_levels)
            popularity_score = self._popularity_score(
                rating=course.rating,
                enrollment_count=course.enrollmentCount,
                max_enrollment=max_enrollment,
            )

            final_score = (
                weights["semantic"] * semantic_score
                + weights["category"] * category_score
                + weights["duration"] * duration_score
                + weights["level"] * level_score
                + weights["popularity"] * popularity_score
            )
            final_score = float(np.clip(final_score, 0.0, 1.0))

            reason = self._build_reason(
                context=context,
                query=query,
                course=course,
                semantic_score=semantic_score,
                category_score=category_score,
                duration_score=duration_score,
                level_score=level_score,
                popularity_score=popularity_score,
                dropout_rate=dropout_rate,
                preferred_duration=preferred_duration,
                favorite_categories=favorite_categories,
                favorite_categories_ordered=favorite_categories_ordered,
            )

            badges = self._build_badges(
                course=course,
                semantic_score=semantic_score,
                category_score=category_score,
                popularity_score=popularity_score,
                dropout_rate=dropout_rate,
                preferred_duration=preferred_duration,
            )

            recommendations.append(
                RecommendationItem(
                    courseId=course_id,
                    score=round(final_score, 4),
                    reason=reason,
                    badges=badges,
                )
            )

        recommendations.sort(key=lambda item: item.score, reverse=True)
        limited = recommendations[: max(limit, 1)]
        return RecommendationResponse(recommendations=limited)

    def _category_score(self, course: CandidateCourse, favorite_categories: set[str]) -> float:
        if not favorite_categories:
            return 0.5

        course_category = normalize_text(course.category)
        tag_set = {normalize_text(tag) for tag in safe_list(course.tags) if isinstance(tag, str)}

        if course_category and course_category in favorite_categories:
            return 1.0
        if tag_set.intersection(favorite_categories):
            return 0.8
        return 0.2

    def _level_score(self, course: CandidateCourse, preferred_levels: set[str]) -> float:
        if not preferred_levels:
            return 0.5
        course_level = normalize_text(course.level)
        if course_level and course_level in preferred_levels:
            return 1.0
        return 0.25

    def _duration_score(
        self,
        *,
        duration_seconds: Optional[int],
        preferred_duration_seconds: int,
        dropout_rate: float,
    ) -> float:
        if duration_seconds is None or duration_seconds <= 0:
            return 0.5

        base = 1.0 - min(abs(duration_seconds - preferred_duration_seconds) / preferred_duration_seconds, 1.0)
        score = float(np.clip(base, 0.0, 1.0))

        if duration_seconds <= preferred_duration_seconds:
            score += 0.1

        if dropout_rate >= 60:
            if duration_seconds <= preferred_duration_seconds:
                score += 0.15
            if duration_seconds <= 3600:
                score += 0.1
            if duration_seconds > preferred_duration_seconds * 1.5:
                score -= 0.25

        return float(np.clip(score, 0.0, 1.0))

    def _popularity_score(
        self, *, rating: Optional[float], enrollment_count: Optional[int], max_enrollment: int
    ) -> float:
        rating_norm = float(np.clip((rating if rating is not None else 3.8) / 5.0, 0.0, 1.0))
        safe_enrollment = max(enrollment_count or 0, 0)
        enrollment_norm = math.log1p(safe_enrollment) / math.log1p(max_enrollment) if max_enrollment > 0 else 0.0
        return float(np.clip((0.6 * rating_norm) + (0.4 * enrollment_norm), 0.0, 1.0))

    def _build_reason(
        self,
        *,
        context: RecommendationContext,
        query: Optional[str],
        course: CandidateCourse,
        semantic_score: float,
        category_score: float,
        duration_score: float,
        level_score: float,
        popularity_score: float,
        dropout_rate: float,
        preferred_duration: int,
        favorite_categories: set[str],
        favorite_categories_ordered: list[str],
    ) -> str:
        course_category = normalize_text(course.category)
        course_level = normalize_text(course.level)
        duration_seconds = course.durationSeconds or 0

        if context == RecommendationContext.SEARCH and query and semantic_score >= 0.55:
            return "Aramana en yakın içeriklerden biri olduğu için önerildi."

        if dropout_rate >= 60 and duration_seconds > 0 and duration_seconds <= preferred_duration:
            return "Uzun kursları yarıda bırakma eğilimin olduğu için daha kısa bir alternatif önerildi."

        if category_score >= 0.8 and favorite_categories:
            reason_categories = favorite_categories_ordered[:2] or list(favorite_categories)[:2]
            joined = " ve ".join(self._pretty_label(value) for value in reason_categories)
            return f"{joined} kategorilerinde ilerlediğin için önerildi."

        if level_score >= 0.9 and course_level:
            return "Seviyene uygun olduğu için önerildi."

        if semantic_score >= 0.6:
            return "Son ilgi alanlarına semantik olarak benzediği için önerildi."

        if popularity_score >= 0.72:
            if course_level == "beginner":
                return "Popüler ve başlangıç için uygun olduğu için önerildi."
            return "Popüler olduğu ve öğrenme hedeflerine uyduğu için önerildi."

        if duration_score >= 0.7 and course_category:
            return f"{course_category} odaklı ve öğrenme ritmine uygun olduğu için önerildi."

        return "İlgi alanlarına ve öğrenme davranışına uygun olduğu için önerildi."

    def _pretty_label(self, value: str) -> str:
        if not value:
            return value
        return " ".join(part.capitalize() for part in value.split())

    def _build_badges(
        self,
        *,
        course: CandidateCourse,
        semantic_score: float,
        category_score: float,
        popularity_score: float,
        dropout_rate: float,
        preferred_duration: int,
    ) -> list[str]:
        badges: list[str] = []

        if category_score >= 0.8:
            badges.append("Category Match")
        if semantic_score >= 0.55:
            badges.append("Semantic Match")
        if course.durationSeconds is not None and (
            course.durationSeconds <= 5400
            or (dropout_rate >= 60 and course.durationSeconds <= preferred_duration)
        ):
            badges.append("Short Course")
        if normalize_text(course.level) == "beginner":
            badges.append("Beginner Friendly")
        if popularity_score >= 0.72:
            badges.append("Popular")

        if course.category and course.category.strip():
            badges.append(course.category.strip())
        if course.level and course.level.strip():
            badges.append(course.level.strip())
        badges.append("Recommended")

        deduped: list[str] = []
        for badge in badges:
            if badge and badge not in deduped:
                deduped.append(badge)
            if len(deduped) >= 5:
                break
        return deduped
