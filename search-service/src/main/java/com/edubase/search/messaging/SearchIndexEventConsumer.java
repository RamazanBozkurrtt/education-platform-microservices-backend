package com.edubase.search.messaging;

import com.edubase.commonCore.events.CourseRatingUpdatedEvent;
import com.edubase.commonCore.events.CourseSearchSyncEvent;
import com.edubase.search.service.CourseSearchService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchIndexEventConsumer {

    private final ObjectMapper objectMapper;
    private final CourseSearchService courseSearchService;

    @KafkaListener(topics = "${app.kafka.topics.course-search-sync:course.search.sync.v1}")
    public void onCourseSearchSyncEvent(String payload,
                                        @Header(value = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic) {
        try {
            CourseSearchSyncEvent event = objectMapper.readValue(payload, CourseSearchSyncEvent.class);
            courseSearchService.applyCourseSyncEvent(event);
        } catch (JsonProcessingException ex) {
            log.error("Cannot deserialize course search sync event. topic={} payload={}", topic, payload, ex);
            throw new IllegalArgumentException("Invalid course search sync event payload", ex);
        } catch (Exception ex) {
            log.error("Failed to process course search sync event. topic={} payload={}", topic, payload, ex);
            throw ex;
        }
    }

    @KafkaListener(topics = "${app.kafka.topics.course-rating-updated:course.rating.updated.v1}")
    public void onCourseRatingUpdatedEvent(String payload,
                                           @Header(value = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic) {
        try {
            CourseRatingUpdatedEvent event = objectMapper.readValue(payload, CourseRatingUpdatedEvent.class);
            courseSearchService.applyRatingUpdateEvent(event);
        } catch (JsonProcessingException ex) {
            log.error("Cannot deserialize course rating updated event. topic={} payload={}", topic, payload, ex);
            throw new IllegalArgumentException("Invalid course rating updated payload", ex);
        } catch (Exception ex) {
            log.error("Failed to process course rating updated event. topic={} payload={}", topic, payload, ex);
            throw ex;
        }
    }
}
