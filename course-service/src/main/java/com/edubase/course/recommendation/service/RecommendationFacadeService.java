package com.edubase.course.recommendation.service;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
import com.edubase.course.recommendation.client.RecommendationServiceClient;
import com.edubase.course.recommendation.dto.request.CandidateCourseRequest;
import com.edubase.course.recommendation.dto.request.RecommendationContext;
import com.edubase.course.recommendation.dto.request.RecommendationDashboardRequest;
import com.edubase.course.recommendation.dto.request.RecommendationSearchRequest;
import com.edubase.course.recommendation.dto.request.RecommendationUserProfileRequest;
import com.edubase.course.recommendation.dto.response.CourseRecommendationResponse;
import com.edubase.course.recommendation.dto.response.RecommendationExplainProfileResponse;
import com.edubase.course.recommendation.dto.response.RecommendationExplainResponse;
import com.edubase.course.recommendation.dto.response.RecommendationListResponse;
import com.edubase.course.recommendation.dto.response.RecommendationServiceItemResponse;
import com.edubase.course.recommendation.dto.response.RecommendationServiceResponse;
import com.edubase.course.recommendation.model.CandidateCourseData;
import com.edubase.course.recommendation.model.UserRecommendationProfile;
import com.edubase.course.security.AuthContext;
import com.edubase.course.security.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationFacadeService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 30;
    private static final String DEFAULT_STRATEGY = "SEMANTIC_SIMILARITY + PROGRESS_AWARE_RANKING";

    private final RecommendationServiceClient recommendationServiceClient;
    private final RecommendationProfileService recommendationProfileService;
    private final CandidateCourseService candidateCourseService;
    private final FallbackRecommendationService fallbackRecommendationService;
    private final RecommendationLogService recommendationLogService;

    public RecommendationListResponse getDashboardRecommendations(AuthContext authContext, Integer limit) {
        AuthContext context = requireAllowedUser(authContext);
        int normalizedLimit = normalizeLimit(limit);
        UserRecommendationProfile profile = recommendationProfileService.buildProfile(context.userId());
        List<CandidateCourseData> candidates = candidateCourseService.buildCandidates(profile, null);

        if (candidates.isEmpty()) {
            return RecommendationListResponse.builder()
                    .recommendations(List.of())
                    .strategy("NO_CANDIDATES")
                    .build();
        }

        RecommendationDashboardRequest request = RecommendationDashboardRequest.builder()
                .userId(context.userId())
                .limit(normalizedLimit)
                .context(RecommendationContext.DASHBOARD)
                .userProfile(toRequestProfile(profile))
                .candidateCourses(toRequestCandidates(candidates))
                .build();

        RecommendationListResponse response = resolveRecommendations(
                context.userId(),
                RecommendationContext.DASHBOARD,
                normalizedLimit,
                candidates,
                () -> recommendationServiceClient.getDashboardRecommendations(request));

        recommendationLogService.logRecommendations(context.userId(), RecommendationContext.DASHBOARD.name(),
                response.getStrategy(), response.getRecommendations());
        return response;
    }

    public RecommendationListResponse getSearchRecommendations(AuthContext authContext, String query, Integer limit) {
        AuthContext context = requireAllowedUser(authContext);
        if (!hasText(query)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        int normalizedLimit = normalizeLimit(limit);
        UserRecommendationProfile profile = recommendationProfileService.buildProfile(context.userId());
        List<CandidateCourseData> candidates = candidateCourseService.buildCandidates(profile, query);

        if (candidates.isEmpty()) {
            return RecommendationListResponse.builder()
                    .recommendations(List.of())
                    .strategy("NO_CANDIDATES")
                    .build();
        }

        RecommendationSearchRequest request = RecommendationSearchRequest.builder()
                .userId(context.userId())
                .query(query.trim())
                .limit(normalizedLimit)
                .context(RecommendationContext.SEARCH)
                .userProfile(toRequestProfile(profile))
                .candidateCourses(toRequestCandidates(candidates))
                .build();

        RecommendationListResponse response = resolveRecommendations(
                context.userId(),
                RecommendationContext.SEARCH,
                normalizedLimit,
                candidates,
                () -> recommendationServiceClient.getSearchRecommendations(request));

        recommendationLogService.logRecommendations(context.userId(), RecommendationContext.SEARCH.name(),
                response.getStrategy(), response.getRecommendations());
        return response;
    }

    public RecommendationExplainResponse explainCurrentUser(AuthContext authContext) {
        AuthContext context = requireAllowedUser(authContext);
        UserRecommendationProfile profile = recommendationProfileService.buildProfile(context.userId());

        RecommendationExplainProfileResponse explainProfile = RecommendationExplainProfileResponse.builder()
                .favoriteCategories(safeList(profile.getFavoriteCategories()))
                .averageCompletionRate(safeDouble(profile.getAverageCompletionRate()))
                .dropoutRate(safeDouble(profile.getDropoutRate()))
                .preferredDurationSeconds(safeLong(profile.getPreferredDurationSeconds()))
                .dropoutRisk(recommendationProfileService.resolveDropoutRisk(safeDouble(profile.getDropoutRate())))
                .preferredDurationLabel(recommendationProfileService.resolvePreferredDurationLabel(safeLong(profile.getPreferredDurationSeconds())))
                .completedCourseCount(safeList(profile.getCompletedCourseIds()).size())
                .inProgressCourseCount(safeList(profile.getInProgressCourseIds()).size())
                .build();

        return RecommendationExplainResponse.builder()
                .userProfile(explainProfile)
                .recommendationStrategy(DEFAULT_STRATEGY)
                .explanation("Sistem kullanicinin izledigi kategorileri, kurs tamamlama oranlarini, yarida birakma davranisini ve kurs surelerini analiz ederek oneri uretir.")
                .build();
    }

    private RecommendationListResponse resolveRecommendations(
            String userId,
            RecommendationContext context,
            int limit,
            List<CandidateCourseData> candidates,
            RecommendationSupplier recommendationSupplier) {
        Map<String, CandidateCourseData> candidatesById = new HashMap<>();
        for (CandidateCourseData candidate : candidates) {
            candidatesById.put(candidate.getCourseId(), candidate);
        }

        try {
            RecommendationServiceResponse serviceResponse = recommendationSupplier.get();
            if (serviceResponse == null || safeList(serviceResponse.getRecommendations()).isEmpty()) {
                return fallbackWithLog(userId, context, candidates, limit, "empty response");
            }

            List<CourseRecommendationResponse> enriched = new ArrayList<>();
            for (RecommendationServiceItemResponse item : safeList(serviceResponse.getRecommendations())) {
                if (item == null || !hasText(item.getCourseId())) {
                    continue;
                }
                CandidateCourseData course = candidatesById.get(item.getCourseId().trim());
                if (course == null) {
                    continue;
                }
                enriched.add(CourseRecommendationResponse.builder()
                        .courseId(course.getCourseId())
                        .title(course.getTitle())
                        .description(course.getDescription())
                        .category(course.getCategory())
                        .level(course.getLevel())
                        .durationSeconds(course.getDurationSeconds())
                        .lessonCount(course.getLessonCount())
                        .rating(course.getRating())
                        .studentsCount(safeLong(course.getEnrollmentCount()))
                        .thumbnailUrl(course.getThumbnailUrl())
                        .score(item.getScore() == null ? 0.0d : item.getScore())
                        .reason(hasText(item.getReason()) ? item.getReason().trim() : defaultReason(context))
                        .badges(normalizeBadges(item.getBadges(), course))
                        .build());
            }

            if (enriched.isEmpty()) {
                return fallbackWithLog(userId, context, candidates, limit, "response items could not be enriched");
            }

            List<CourseRecommendationResponse> limited = enriched.stream().limit(limit).toList();
            String strategy = hasText(serviceResponse.getStrategy()) ? serviceResponse.getStrategy() : DEFAULT_STRATEGY;
            log.info("Received recommendations from recommendation-service. userId={} context={} itemCount={} strategy={}",
                    userId, context, limited.size(), strategy);
            return RecommendationListResponse.builder()
                    .recommendations(limited)
                    .strategy(strategy)
                    .build();
        } catch (RestClientException ex) {
            return fallbackWithLog(userId, context, candidates, limit, "failed response", ex);
        } catch (Exception ex) {
            return fallbackWithLog(userId, context, candidates, limit, "failed response", ex);
        }
    }

    private RecommendationListResponse fallbackWithLog(
            String userId,
            RecommendationContext context,
            List<CandidateCourseData> candidates,
            int limit,
            String reason) {
        log.warn("Using fallback recommendations because recommendation-service {}. userId={} context={}",
                reason, userId, context);
        return fallbackRecommendationService.buildFallbackRecommendations(candidates, limit);
    }

    private RecommendationListResponse fallbackWithLog(
            String userId,
            RecommendationContext context,
            List<CandidateCourseData> candidates,
            int limit,
            String reason,
            Exception ex) {
        log.warn("Using fallback recommendations because recommendation-service {}. userId={} context={}",
                reason, userId, context, ex);
        return fallbackRecommendationService.buildFallbackRecommendations(candidates, limit);
    }

    private RecommendationUserProfileRequest toRequestProfile(UserRecommendationProfile profile) {
        return RecommendationUserProfileRequest.builder()
                .favoriteCategories(safeList(profile.getFavoriteCategories()))
                .averageCompletionRate(safeDouble(profile.getAverageCompletionRate()))
                .dropoutRate(safeDouble(profile.getDropoutRate()))
                .preferredDurationSeconds(safeLong(profile.getPreferredDurationSeconds()))
                .completedCourseIds(safeList(profile.getCompletedCourseIds()))
                .inProgressCourseIds(safeList(profile.getInProgressCourseIds()))
                .recentlyWatchedCourseIds(safeList(profile.getRecentlyWatchedCourseIds()))
                .preferredLevels(safeList(profile.getPreferredLevels()))
                .build();
    }

    private List<CandidateCourseRequest> toRequestCandidates(List<CandidateCourseData> candidates) {
        return candidates.stream()
                .map(candidate -> CandidateCourseRequest.builder()
                        .courseId(candidate.getCourseId())
                        .title(candidate.getTitle())
                        .description(candidate.getDescription())
                        .category(candidate.getCategory())
                        .level(candidate.getLevel())
                        .tags(safeList(candidate.getTags()))
                        .durationSeconds(candidate.getDurationSeconds())
                        .lessonCount(candidate.getLessonCount())
                        .rating(candidate.getRating())
                        .enrollmentCount(candidate.getEnrollmentCount())
                        .createdAt(candidate.getCreatedAt())
                        .build())
                .toList();
    }

    private List<String> normalizeBadges(List<String> badges, CandidateCourseData candidate) {
        List<String> normalized = new ArrayList<>();
        for (String badge : safeList(badges)) {
            if (hasText(badge)) {
                normalized.add(badge.trim());
            }
        }
        if (normalized.isEmpty()) {
            if (hasText(candidate.getCategory())) {
                normalized.add(candidate.getCategory());
            }
            normalized.add("Recommended");
        }
        return normalized.stream().distinct().limit(4).toList();
    }

    private String defaultReason(RecommendationContext context) {
        if (context == RecommendationContext.SEARCH) {
            return "Aradigin konuya benzer oldugu icin onerildi.";
        }
        return "Ilgi alanlarina ve ilerleme davranisina gore onerildi.";
    }

    private AuthContext requireAllowedUser(AuthContext authContext) {
        if (authContext == null || !hasText(authContext.userId()) || authContext.role() == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }
        UserRole role = authContext.role();
        if (role == UserRole.ADMIN || role == UserRole.STUDENT || role == UserRole.USER || role == UserRole.INSTRUCTOR) {
            return new AuthContext(authContext.userId().trim(), role);
        }
        throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private double safeDouble(Double value) {
        return value == null ? 0.0d : value;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    @FunctionalInterface
    private interface RecommendationSupplier {
        RecommendationServiceResponse get();
    }
}
