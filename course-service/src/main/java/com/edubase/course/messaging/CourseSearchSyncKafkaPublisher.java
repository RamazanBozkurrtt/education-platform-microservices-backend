package com.edubase.course.messaging;

import com.edubase.commonCore.events.CourseSearchSyncEvent;
import com.edubase.commonCore.events.CourseSearchSyncEventType;
import com.edubase.course.entity.Course;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CourseSearchSyncKafkaPublisher {

    @Value("${app.kafka.topics.course-search-sync:course.search.sync.v1}")
    private String courseSearchSyncTopic;

    private final KafkaTemplate<String, CourseSearchSyncEvent> kafkaTemplate;

    public void publishUpsert(Course course) {
        publish(course, CourseSearchSyncEventType.UPSERT);
    }

    public void publishDelete(Course course) {
        publish(course, CourseSearchSyncEventType.DELETE);
    }

    private void publish(Course course, CourseSearchSyncEventType eventType) {
        if (course == null || course.getId() == null || course.getId().isBlank()) {
            return;
        }
        Instant occurredAt = Instant.now();
        long eventVersion = resolveEventVersion(course, occurredAt, eventType);

        CourseSearchSyncEvent event = new CourseSearchSyncEvent(
                UUID.randomUUID().toString(),
                eventType,
                eventVersion,
                occurredAt,
                course.getId(),
                course.getTitle(),
                course.getDescription(),
                course.getInstructorId(),
                course.getCategoryId(),
                course.getPrice(),
                course.getStatus() == null ? "" : course.getStatus().name(),
                safeList(course.getTags()),
                safeList(course.getLearningOutcomes())
        );

        kafkaTemplate.send(courseSearchSyncTopic, course.getId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish course search sync event. courseId={} eventType={}",
                                course.getId(), eventType, ex);
                        return;
                    }
                    log.info("Published course search sync event. courseId={} eventType={} topic={}",
                            course.getId(), eventType, courseSearchSyncTopic);
                });
    }

    private long resolveEventVersion(Course course, Instant fallback, CourseSearchSyncEventType eventType) {
        long baseVersion = fallback.toEpochMilli();
        if (course.getUpdatedAt() != null) {
            baseVersion = course.getUpdatedAt().toEpochMilli();
        } else if (course.getCreatedAt() != null) {
            baseVersion = course.getCreatedAt().toEpochMilli();
        }

        if (eventType == CourseSearchSyncEventType.DELETE) {
            return Math.max(baseVersion + 1, fallback.toEpochMilli());
        }
        return baseVersion;
    }

    private List<String> safeList(List<String> source) {
        return source == null ? List.of() : source.stream().filter(value -> value != null && !value.isBlank()).toList();
    }
}
