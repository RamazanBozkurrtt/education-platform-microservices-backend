package com.edubase.user.service.concretes;

import com.edubase.commonCore.events.InstructorEventType;
import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
import com.edubase.user.dto.internal.InstructorSummaryResponse;
import com.edubase.user.entity.UserProfile;
import com.edubase.user.entity.UserStatus;
import com.edubase.user.messaging.InstructorLifecycleDomainEvent;
import com.edubase.user.repository.UserProfileRepository;
import com.edubase.user.service.abstracts.InstructorInternalCommandService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InstructorInternalCommandServiceImpl implements InstructorInternalCommandService {

    private final UserProfileRepository userProfileRepository;
    private final InstructorInternalQueryServiceImpl instructorInternalQueryServiceImpl;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public InstructorSummaryResponse changeStatus(String instructorId, UserStatus targetStatus) {
        Long authUserId = parseInstructorId(instructorId);
        UserProfile profile = userProfileRepository.findByAuthUserId(authUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        profile.setStatus(targetStatus);
        userProfileRepository.save(profile);

        InstructorEventType eventType = (targetStatus == UserStatus.DEACTIVATED || targetStatus == UserStatus.BANNED)
                ? InstructorEventType.INSTRUCTOR_DEACTIVATED
                : InstructorEventType.INSTRUCTOR_UPDATED;
        applicationEventPublisher.publishEvent(new InstructorLifecycleDomainEvent(eventType, profile));

        return instructorInternalQueryServiceImpl.getByInstructorId(instructorId);
    }

    private Long parseInstructorId(String instructorId) {
        try {
            return Long.parseLong(instructorId.trim());
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }
}
