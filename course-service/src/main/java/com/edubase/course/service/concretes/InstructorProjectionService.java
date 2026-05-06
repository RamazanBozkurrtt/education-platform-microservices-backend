package com.edubase.course.service.concretes;

import com.edubase.commonCore.events.InstructorLifecycleEvent;
import com.edubase.commonCore.events.InstructorStatus;
import com.edubase.course.dto.response.InstructorSummaryResponse;
import com.edubase.course.entity.InstructorProjection;
import com.edubase.course.entity.ProcessedKafkaEvent;
import com.edubase.course.repository.InstructorProjectionRepository;
import com.edubase.course.repository.ProcessedKafkaEventRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InstructorProjectionService {

    private final InstructorProjectionRepository instructorProjectionRepository;
    private final ProcessedKafkaEventRepository processedKafkaEventRepository;

    public Optional<InstructorSummaryResponse> findSummaryByInstructorId(String instructorId) {
        return instructorProjectionRepository.findById(instructorId).map(this::toSummary);
    }

    public Map<String, InstructorSummaryResponse> findSummariesByInstructorIds(Collection<String> instructorIds) {
        if (instructorIds == null || instructorIds.isEmpty()) {
            return Map.of();
        }
        return instructorProjectionRepository.findAllById(instructorIds).stream()
                .map(this::toSummary)
                .collect(Collectors.toMap(InstructorSummaryResponse::instructorId, Function.identity()));
    }

    @Transactional
    public void applyKafkaEvent(InstructorLifecycleEvent event, String topic) {
        if (event == null || event.eventId() == null || event.eventId().isBlank() || event.instructorId() == null) {
            return;
        }
        if (processedKafkaEventRepository.existsById(event.eventId())) {
            return;
        }

        InstructorProjection current = instructorProjectionRepository.findById(event.instructorId()).orElse(null);
        if (isOutOfOrder(current, event)) {
            markAsProcessed(event.eventId(), topic);
            return;
        }

        InstructorProjection merged = merge(current, event);
        instructorProjectionRepository.save(merged);
        markAsProcessed(event.eventId(), topic);
    }

    @Transactional
    public void upsertFromSummary(InstructorSummaryResponse summary) {
        if (summary == null || summary.instructorId() == null || summary.instructorId().isBlank()) {
            return;
        }
        InstructorProjection current = instructorProjectionRepository.findById(summary.instructorId()).orElse(null);
        Instant now = Instant.now();

        InstructorProjection merged = InstructorProjection.builder()
                .instructorId(summary.instructorId())
                .fullName(summary.fullName() == null ? defaultFullName(summary.email()) : summary.fullName())
                .email(summary.email())
                .profileImageUrl(summary.profileImageUrl())
                .headline(summary.headline())
                .status(summary.status() == null ? InstructorStatus.ACTIVE : summary.status())
                .sourceUpdatedAt(now)
                .lastEventId(current == null ? "reconcile" : current.getLastEventId())
                .lastEventVersion(current == null ? now.toEpochMilli() : current.getLastEventVersion() + 1)
                .projectionUpdatedAt(now)
                .build();
        instructorProjectionRepository.save(merged);
    }

    private boolean isOutOfOrder(InstructorProjection current, InstructorLifecycleEvent event) {
        if (current == null || current.getLastEventVersion() == null || event.eventVersion() == null) {
            return false;
        }
        return event.eventVersion() < current.getLastEventVersion();
    }

    private InstructorProjection merge(InstructorProjection current, InstructorLifecycleEvent event) {
        Instant sourceUpdatedAt = event.occurredAt() == null ? Instant.now() : event.occurredAt();
        long nextVersion = event.eventVersion() == null ? sourceUpdatedAt.toEpochMilli() : event.eventVersion();

        return InstructorProjection.builder()
                .instructorId(event.instructorId())
                .fullName(event.fullName() == null ? defaultFullName(event.email()) : event.fullName())
                .email(event.email())
                .profileImageUrl(event.profileImageUrl())
                .headline(event.headline())
                .status(event.status() == null ? InstructorStatus.ACTIVE : event.status())
                .sourceUpdatedAt(sourceUpdatedAt)
                .lastEventId(event.eventId())
                .lastEventVersion(nextVersion)
                .projectionUpdatedAt(Instant.now())
                .build();
    }

    private void markAsProcessed(String eventId, String topic) {
        processedKafkaEventRepository.save(ProcessedKafkaEvent.builder()
                .eventId(eventId)
                .topic(topic == null ? "unknown" : topic)
                .processedAt(Instant.now())
                .build());
    }

    private InstructorSummaryResponse toSummary(InstructorProjection projection) {
        return InstructorSummaryResponse.builder()
                .instructorId(projection.getInstructorId())
                .fullName(projection.getFullName())
                .email(projection.getEmail())
                .profileImageUrl(projection.getProfileImageUrl())
                .headline(projection.getHeadline())
                .status(projection.getStatus())
                .build();
    }

    private String defaultFullName(String email) {
        return email == null ? "Unknown Instructor" : email;
    }
}
