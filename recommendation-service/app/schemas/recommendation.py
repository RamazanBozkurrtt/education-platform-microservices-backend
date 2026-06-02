from __future__ import annotations

from datetime import datetime
from enum import Enum
from typing import Optional

from pydantic import BaseModel, Field, field_validator


class RecommendationContext(str, Enum):
    DASHBOARD = "DASHBOARD"
    SEARCH = "SEARCH"


class UserProfile(BaseModel):
    favoriteCategories: list[str] = Field(default_factory=list)
    averageCompletionRate: Optional[float] = None
    dropoutRate: Optional[float] = None
    preferredDurationSeconds: Optional[int] = None
    completedCourseIds: list[str] = Field(default_factory=list)
    inProgressCourseIds: list[str] = Field(default_factory=list)
    recentlyWatchedCourseIds: list[str] = Field(default_factory=list)
    preferredLevels: list[str] = Field(default_factory=list)

    model_config = {"extra": "ignore"}


class CandidateCourse(BaseModel):
    courseId: Optional[str] = None
    title: Optional[str] = None
    description: Optional[str] = None
    category: Optional[str] = None
    level: Optional[str] = None
    tags: list[str] = Field(default_factory=list)
    durationSeconds: Optional[int] = None
    lessonCount: Optional[int] = None
    rating: Optional[float] = None
    enrollmentCount: Optional[int] = None
    createdAt: Optional[datetime] = None

    model_config = {"extra": "ignore"}


class RecommendationBaseRequest(BaseModel):
    userId: Optional[str] = None
    limit: int = Field(default=10, gt=0, le=100)
    context: RecommendationContext = RecommendationContext.DASHBOARD
    userProfile: UserProfile = Field(default_factory=UserProfile)
    candidateCourses: list[CandidateCourse] = Field(default_factory=list)

    model_config = {"extra": "ignore"}


class DashboardRecommendationRequest(RecommendationBaseRequest):
    context: RecommendationContext = RecommendationContext.DASHBOARD


class SearchRecommendationRequest(RecommendationBaseRequest):
    query: str = Field(..., min_length=1)
    context: RecommendationContext = RecommendationContext.SEARCH

    @field_validator("query")
    @classmethod
    def validate_query_not_blank(cls, value: str) -> str:
        normalized = value.strip()
        if not normalized:
            raise ValueError("query must not be blank")
        return normalized


class RecommendationItem(BaseModel):
    courseId: str
    score: float = Field(ge=0.0, le=1.0)
    reason: str
    badges: list[str] = Field(default_factory=list)

    model_config = {"extra": "ignore"}


class RecommendationResponse(BaseModel):
    recommendations: list[RecommendationItem] = Field(default_factory=list)
    strategy: str = "SEMANTIC_SIMILARITY + PROGRESS_AWARE_RANKING"

    model_config = {"extra": "ignore"}

