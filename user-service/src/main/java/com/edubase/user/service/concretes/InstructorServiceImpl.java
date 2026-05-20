package com.edubase.user.service.concretes;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
import com.edubase.user.dto.internal.AuthRoleUpdateResponse;
import com.edubase.user.dto.request.InstructorApplyRequest;
import com.edubase.user.dto.response.InstructorProfileResponse;
import com.edubase.user.entity.InstructorProfile;
import com.edubase.user.repository.InstructorProfileRepository;
import com.edubase.user.repository.UserProfileRepository;
import com.edubase.user.service.abstracts.InstructorService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class InstructorServiceImpl implements InstructorService {

    private final InstructorProfileRepository instructorProfileRepository;
    private final UserProfileRepository userProfileRepository;
    private final AuthRoleClient authRoleClient;

    @Override
    @Transactional
    @PreAuthorize("hasAnyRole('USER','INSTRUCTOR','ADMIN')")
    public InstructorProfileResponse apply(Long authUserId, String authEmail, String bearerToken, InstructorApplyRequest request) {
        Long resolvedAuthUserId = requireAuthUserId(authUserId);
        ensureUserProfileExists(resolvedAuthUserId, authEmail);

        InstructorProfile profile = instructorProfileRepository.findByAuthUserId(resolvedAuthUserId)
                .orElseGet(() -> InstructorProfile.builder().authUserId(resolvedAuthUserId).build());

        applyRequest(profile, request);

        InstructorProfile savedProfile = saveProfileSafely(profile);
        AuthRoleUpdateResponse authRoleUpdateResponse = authRoleClient.grantInstructorRoleForCurrentUser(bearerToken);

        return toResponse(
                savedProfile,
                sortedRoles(authRoleUpdateResponse.roles()),
                authRoleUpdateResponse.accessToken(),
                authRoleUpdateResponse.refreshToken()
        );
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('USER','INSTRUCTOR','ADMIN')")
    public InstructorProfileResponse getMe(Long authUserId, String authEmail, List<String> rolesFromToken) {
        Long resolvedAuthUserId = requireAuthUserId(authUserId);
        ensureUserProfileExists(resolvedAuthUserId, authEmail);

        InstructorProfile profile = instructorProfileRepository.findByAuthUserId(resolvedAuthUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return toResponse(profile, sortedRoles(rolesFromToken), null, null);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyRole('USER','INSTRUCTOR','ADMIN')")
    public InstructorProfileResponse updateMe(Long authUserId, String authEmail, List<String> rolesFromToken, InstructorApplyRequest request) {
        Long resolvedAuthUserId = requireAuthUserId(authUserId);
        ensureUserProfileExists(resolvedAuthUserId, authEmail);

        InstructorProfile profile = instructorProfileRepository.findByAuthUserId(resolvedAuthUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        applyRequest(profile, request);
        InstructorProfile savedProfile = instructorProfileRepository.save(profile);
        return toResponse(savedProfile, sortedRoles(rolesFromToken), null, null);
    }

    private void applyRequest(InstructorProfile profile, InstructorApplyRequest request) {
        profile.setDisplayName(trimToNull(request.displayName()));
        profile.setBiography(trimToNull(request.biography()));
        profile.setExpertise(normalizeExpertise(request.expertise()));
        profile.setWebsiteUrl(trimToNull(request.websiteUrl()));
        profile.setLinkedinUrl(trimToNull(request.linkedinUrl()));
        profile.setGithubUrl(trimToNull(request.githubUrl()));
        profile.setProfileImageUrl(trimToNull(request.profileImageUrl()));
    }

    private InstructorProfile saveProfileSafely(InstructorProfile profile) {
        try {
            return instructorProfileRepository.save(profile);
        } catch (DataIntegrityViolationException ex) {
            return instructorProfileRepository.findByAuthUserId(profile.getAuthUserId())
                    .map(existing -> {
                        existing.setDisplayName(profile.getDisplayName());
                        existing.setBiography(profile.getBiography());
                        existing.setExpertise(profile.getExpertise());
                        existing.setWebsiteUrl(profile.getWebsiteUrl());
                        existing.setLinkedinUrl(profile.getLinkedinUrl());
                        existing.setGithubUrl(profile.getGithubUrl());
                        existing.setProfileImageUrl(profile.getProfileImageUrl());
                        return instructorProfileRepository.save(existing);
                    })
                    .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        }
    }

    private List<String> normalizeExpertise(List<String> expertise) {
        if (expertise == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : expertise) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                normalized.add(trimmed);
            }
        }
        if (normalized.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return new ArrayList<>(normalized);
    }

    private Long requireAuthUserId(Long authUserId) {
        if (authUserId == null || authUserId <= 0) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }
        return authUserId;
    }

    private void ensureUserProfileExists(Long authUserId, String authEmail) {
        if (authUserId == null || authUserId <= 0) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }
        boolean exists = userProfileRepository.findByAuthUserId(authUserId).isPresent()
                || (authEmail != null && !authEmail.isBlank() && userProfileRepository.findByEmailIgnoreCase(authEmail).isPresent());
        if (!exists) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
    }

    private List<String> sortedRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        return roles.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .sorted()
                .toList();
    }

    private InstructorProfileResponse toResponse(InstructorProfile profile, List<String> roles, String accessToken, String refreshToken) {
        return InstructorProfileResponse.builder()
                .id(profile.getId())
                .userId(profile.getAuthUserId())
                .displayName(profile.getDisplayName())
                .biography(profile.getBiography())
                .expertise(profile.getExpertise() == null ? List.of() : List.copyOf(profile.getExpertise()))
                .profileImageUrl(profile.getProfileImageUrl())
                .websiteUrl(profile.getWebsiteUrl())
                .linkedinUrl(profile.getLinkedinUrl())
                .githubUrl(profile.getGithubUrl())
                .roles(roles == null ? List.of() : roles)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
