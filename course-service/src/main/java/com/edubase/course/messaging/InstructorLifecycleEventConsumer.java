package com.edubase.course.messaging;

import com.edubase.commonCore.events.InstructorLifecycleEvent;
import com.edubase.course.service.concretes.InstructorProjectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InstructorLifecycleEventConsumer {

    private final InstructorProjectionService instructorProjectionService;

    @KafkaListener(topics = "${app.kafka.topics.instructor-lifecycle:instructor.lifecycle.v1}")
    public void onEvent(InstructorLifecycleEvent event,
                        @Header(value = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic) {
        try {
            instructorProjectionService.applyKafkaEvent(event, topic);
        } catch (Exception ex) {
            log.error("Failed to process instructor lifecycle event. eventId={} instructorId={}",
                    event == null ? null : event.eventId(),
                    event == null ? null : event.instructorId(),
                    ex);
            throw ex;
        }
    }
}
