package com.edubase.user.service.concretes;

import com.edubase.commonCore.events.InstructorStatus;
import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
import com.edubase.user.dto.internal.InstructorSummaryResponse;
import com.edubase.user.entity.UserProfile;
import com.edubase.user.entity.UserStatus;
import com.edubase.user.repository.UserProfileRepository;
import com.edubase.user.service.abstracts.InstructorInternalQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InstructorInternalQueryServiceImpl implements InstructorInternalQueryService {

    private final UserProfileRepository userProfileRepository;

    public InstructorSummaryResponse getByInstructorId(String instructorId) {
        Long authUserId = parseInstructorId(instructorId);
        UserProfile profile = userProfileRepository.findByAuthUserId(authUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return toSummary(profile);
    }

    public List<InstructorSummaryResponse> getByInstructorIds(Collection<String> instructorIds) {
        List<Long> authUserIds = instructorIds.stream()
                .map(this::parseInstructorId)
                .distinct()
                .toList();

        return userProfileRepository.findAllByAuthUserIdIn(authUserIds).stream()
                .filter(profile -> profile.getAuthUserId() != null)
                .sorted(Comparator.comparing(UserProfile::getAuthUserId))
                .map(this::toSummary)
                .collect(Collectors.toList());
    }

    private Long parseInstructorId(String instructorId) {
        if (instructorId == null || instructorId.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        try {
            return Long.parseLong(instructorId.trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private InstructorSummaryResponse toSummary(UserProfile profile) {
        return InstructorSummaryResponse.builder()
                .instructorId(String.valueOf(profile.getAuthUserId()))
                .fullName(fullNameOf(profile))
                .email(profile.getEmail())
                .profileImageUrl(profile.getAvatarUrl())
                .headline(profile.getHeadline())
                .status(toInstructorStatus(profile.getStatus()))
                .build();
    }

    private String fullNameOf(UserProfile profile) {
        String firstName = profile.getFirstName() == null ? "" : profile.getFirstName().trim();
        String lastName = profile.getLastName() == null ? "" : profile.getLastName().trim();
        String fullName = (firstName + " " + lastName).trim();
        if (!fullName.isEmpty()) {
            return fullName;
        }
        return profile.getEmail();
    }

    private InstructorStatus toInstructorStatus(UserStatus status) {
        if (status == UserStatus.DEACTIVATED || status == UserStatus.BANNED) {
            return InstructorStatus.DEACTIVATED;
        }
        return InstructorStatus.ACTIVE;
    }
}
