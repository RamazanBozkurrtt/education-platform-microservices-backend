from __future__ import annotations

import re
from typing import Iterable, Optional, TypeVar

from app.schemas.recommendation import CandidateCourse, UserProfile

T = TypeVar("T")


def safe_list(values: Optional[Iterable[T]]) -> list[T]:
    if values is None:
        return []
    return list(values)


def normalize_text(text: Optional[str]) -> str:
    if not text:
        return ""
    lowered = text.strip().lower()
    return re.sub(r"\s+", " ", lowered)


def build_course_text(course: CandidateCourse) -> str:
    parts: list[str] = [
        normalize_text(course.title),
        normalize_text(course.description),
        normalize_text(course.category),
        normalize_text(course.level),
        " ".join(normalize_text(tag) for tag in safe_list(course.tags)),
    ]
    return " ".join(part for part in parts if part).strip()


def build_user_interest_text(profile: UserProfile, query: Optional[str] = None) -> str:
    parts: list[str] = []

    favorite_categories = [normalize_text(value) for value in safe_list(profile.favoriteCategories) if value]
    if favorite_categories:
        parts.append("favorite categories " + " ".join(favorite_categories))

    preferred_levels = [normalize_text(value) for value in safe_list(profile.preferredLevels) if value]
    if preferred_levels:
        parts.append("preferred levels " + " ".join(preferred_levels))

    if profile.dropoutRate is not None and profile.dropoutRate >= 60:
        parts.append("short practical courses")
    elif profile.averageCompletionRate is not None and profile.averageCompletionRate >= 70:
        parts.append("deeper courses")

    if query:
        normalized_query = normalize_text(query)
        if normalized_query:
            parts.append("search query " + normalized_query)

    if not parts:
        return "general learning interests"
    return " ".join(parts)

