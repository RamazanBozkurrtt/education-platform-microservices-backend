package com.edubase.user.service.concretes;


import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
import com.edubase.user.configuration.mapper.UserMapper;
import com.edubase.user.dto.request.UserProfileRequest;
import com.edubase.user.dto.response.CustomPageResponse;
import com.edubase.user.dto.response.UserProfileResponse;
import com.edubase.user.entity.UserProfile;
import com.edubase.user.repository.UserProfileRepository;
import com.edubase.user.service.abstracts.UserService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserProfileRepository userProfileRepository;
    private final UserMapper userMapper;

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public CustomPageResponse<UserProfileResponse> getAll(int pageNumber, int pageSize) {
        Pageable pageable = PageRequest.of(pageNumber,pageSize, Sort.by("createdAt"));
        Page<UserProfile> userPage = userProfileRepository.findAll(pageable);

        List<UserProfileResponse> responseList = userMapper.toResponseListFromEntityList(userPage.getContent());

        return CustomPageResponse.of(userPage,responseList);
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN')")
    public UserProfileResponse getById(Long id) {
        UserProfile dbUser = userProfileRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return userMapper.toResponseFromEntity(dbUser);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public UserProfileResponse update(Long id, UserProfileRequest userProfileRequest) {
        UserProfile existing = userProfileRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        applyUpdates(existing, userProfileRequest);
        userProfileRepository.save(existing);

        return userMapper.toResponseFromEntity(existing);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public UserProfileResponse getMe(Long authUserId, String authEmail) {
        UserProfile dbUser = resolveOwnProfile(authUserId, authEmail);

        return userMapper.toResponseFromEntity(dbUser);
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public UserProfileResponse updateMe(Long authUserId, String authEmail, UserProfileRequest userProfileRequest) {
        UserProfile existing = resolveOwnProfile(authUserId, authEmail);

        applyUpdates(existing, userProfileRequest);
        userProfileRepository.save(existing);

        return userMapper.toResponseFromEntity(existing);
    }

    private void applyUpdates(UserProfile existing, UserProfileRequest userProfileRequest) {
        existing.setFirstName(userProfileRequest.getFirstName());
        existing.setLastName(userProfileRequest.getLastName());
        existing.setHeadline(userProfileRequest.getHeadline());
        existing.setBiography(userProfileRequest.getBiography());
        existing.setAvatarUrl(userProfileRequest.getAvatarUrl());
        existing.setSocialLinks(userProfileRequest.getSocialLinks());
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public UserProfileResponse create(UserProfileRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        if (userProfileRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
        }
        UserProfile userProfile = userMapper.toEntityFromRequest(request);
        userProfile.setEmail(normalizedEmail);
        userProfileRepository.save(userProfile);
        return userMapper.toResponseFromEntity(userProfile);
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private UserProfile resolveOwnProfile(Long authUserId, String authEmail) {
        if (authUserId != null) {
            return userProfileRepository.findByAuthUserId(authUserId)
                    .orElseGet(() -> resolveLegacyProfileByEmail(authUserId, authEmail));
        }
        return resolveLegacyProfileByEmail(null, authEmail);
    }

    private UserProfile resolveLegacyProfileByEmail(Long authUserId, String authEmail) {
        UserProfile profile = userProfileRepository.findByEmailIgnoreCase(authEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (authUserId != null && profile.getAuthUserId() == null) {
            profile.setAuthUserId(authUserId);
            userProfileRepository.save(profile);
        }

        return profile;
    }

}
