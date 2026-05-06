package com.edubase.course.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "processed_kafka_event")
public class ProcessedKafkaEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false, length = 64)
    private String eventId;

    @Column(name = "topic", nullable = false, length = 200)
    private String topic;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
