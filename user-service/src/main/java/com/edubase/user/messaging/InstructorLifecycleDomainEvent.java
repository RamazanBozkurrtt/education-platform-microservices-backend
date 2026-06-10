package com.edubase.user.messaging;

import com.edubase.commonCore.events.InstructorEventType;
import com.edubase.user.entity.UserProfile;

public record InstructorLifecycleDomainEvent(
        InstructorEventType eventType,
        UserProfile profile
) {
}
