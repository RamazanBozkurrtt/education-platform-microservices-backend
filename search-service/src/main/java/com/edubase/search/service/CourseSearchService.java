package com.edubase.search.service;

import com.edubase.commonCore.events.CourseRatingUpdatedEvent;
import com.edubase.commonCore.events.CourseSearchSyncEvent;
import com.edubase.commonCore.events.CourseSearchSyncEventType;
import com.edubase.search.entity.CourseSearchDocument;
import com.edubase.search.repository.CourseSearchRepository;
import com.edubase.search.service.model.SearchCourseHit;
import com.edubase.search.service.model.SearchCourseResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CourseSearchService {

    private final CourseSearchRepository courseSearchRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    public void applyCourseSyncEvent(CourseSearchSyncEvent event) {
        if (event == null || isBlank(event.courseId())) {
            return;
        }
        CourseSearchDocument current = courseSearchRepository.findById(event.courseId()).orElse(null);
        long incomingVersion = event.eventVersion() == null
                ? fallbackVersion(event.occurredAt())
                : event.eventVersion();
        if (current != null && current.getCourseEventVersion() != null && incomingVersion < current.getCourseEventVersion()) {
            return;
        }

        CourseSearchDocument target = current == null
                ? CourseSearchDocument.builder().id(event.courseId()).build()
                : current;

        target.setTitle(normalize(event.title()));
        target.setDescription(normalize(event.description()));
        target.setInstructorId(normalize(event.instructorId()));
        target.setCategoryId(normalize(event.categoryId()));
        target.setPrice(event.price() == null ? null : event.price().doubleValue());
        target.setStatus(normalize(event.status()));
        target.setTags(safeList(event.tags()));
        target.setLearningOutcomes(safeList(event.learningOutcomes()));
        target.setSearchable(resolveSearchable(event));
        target.setCourseEventVersion(incomingVersion);
        target.setUpdatedAt(event.occurredAt() == null ? Instant.now() : event.occurredAt());

        if (target.getAverageRating() == null) {
            target.setAverageRating(0.0d);
        }
        if (target.getRatingCount() == null) {
            target.setRatingCount(0L);
        }

        courseSearchRepository.save(target);
    }

    public void applyRatingUpdateEvent(CourseRatingUpdatedEvent event) {
        if (event == null || isBlank(event.courseId())) {
            return;
        }
        long incomingVersion = event.eventVersion() == null
                ? fallbackVersion(event.occurredAt())
                : event.eventVersion();

        CourseSearchDocument target = courseSearchRepository.findById(event.courseId())
                .orElseGet(() -> CourseSearchDocument.builder()
                        .id(event.courseId())
                        .searchable(false)
                        .build());

        if (target.getRatingEventVersion() != null && incomingVersion < target.getRatingEventVersion()) {
            return;
        }

        target.setAverageRating(event.averageRating() == null ? 0.0d : event.averageRating());
        target.setRatingCount(event.ratingCount() == null ? 0L : event.ratingCount());
        target.setRatingEventVersion(incomingVersion);
        target.setUpdatedAt(event.occurredAt() == null ? Instant.now() : event.occurredAt());
        if (target.getSearchable() == null) {
            target.setSearchable(false);
        }

        courseSearchRepository.save(target);
    }

    public SearchCourseResult searchCourses(String query,
                                            int pageNumber,
                                            int pageSize,
                                            String categoryId,
                                            String instructorId,
                                            Double minPrice,
                                            Double maxPrice,
                                            Double minRating) {
        int safePageNumber = Math.max(pageNumber, 0);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);

        Criteria criteria = new Criteria("searchable").is(true);
        if (!isBlank(query)) {
            Criteria textCriteria = new Criteria("title").matches(query)
                    .or("description").matches(query)
                    .or("tags").matches(query)
                    .or("learningOutcomes").matches(query);
            criteria = criteria.and(textCriteria);
        }
        if (!isBlank(categoryId)) {
            criteria = criteria.and("categoryId").is(categoryId.trim());
        }
        if (!isBlank(instructorId)) {
            criteria = criteria.and("instructorId").is(instructorId.trim());
        }
        if (minPrice != null) {
            criteria = criteria.and("price").greaterThanEqual(minPrice);
        }
        if (maxPrice != null) {
            criteria = criteria.and("price").lessThanEqual(maxPrice);
        }
        if (minRating != null) {
            criteria = criteria.and("averageRating").greaterThanEqual(minRating);
        }

        CriteriaQuery searchQuery = new CriteriaQuery(criteria, PageRequest.of(safePageNumber, safePageSize));
        if (isBlank(query)) {
            searchQuery.addSort(Sort.by(Sort.Direction.DESC, "updatedAt"));
        }

        SearchHits<CourseSearchDocument> hits = elasticsearchOperations.search(searchQuery, CourseSearchDocument.class);
        List<SearchCourseHit> mappedHits = hits.getSearchHits().stream()
                .map(this::mapHit)
                .toList();
        return new SearchCourseResult(mappedHits, hits.getTotalHits(), safePageNumber, safePageSize);
    }

    private SearchCourseHit mapHit(SearchHit<CourseSearchDocument> hit) {
        CourseSearchDocument content = hit.getContent();
        return new SearchCourseHit(
                content.getId(),
                content.getTitle(),
                content.getDescription(),
                content.getInstructorId(),
                content.getCategoryId(),
                content.getPrice(),
                content.getStatus(),
                safeList(content.getTags()),
                safeList(content.getLearningOutcomes()),
                content.getAverageRating() == null ? 0.0d : content.getAverageRating(),
                content.getRatingCount() == null ? 0L : content.getRatingCount(),
                hit.getScore()
        );
    }

    private long fallbackVersion(Instant occurredAt) {
        return Optional.ofNullable(occurredAt).orElseGet(Instant::now).toEpochMilli();
    }

    private boolean resolveSearchable(CourseSearchSyncEvent event) {
        if (event.eventType() == CourseSearchSyncEventType.DELETE) {
            return false;
        }
        return "PUBLISHED".equalsIgnoreCase(normalize(event.status()));
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<String> safeList(List<String> source) {
        if (source == null) {
            return List.of();
        }
        return source.stream()
                .filter(item -> item != null && !item.isBlank())
                .toList();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
