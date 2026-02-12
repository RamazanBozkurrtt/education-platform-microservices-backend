package com.edubase.user.service.concretes;

import com.edubase.common.exceptions.BusinessException;
import com.edubase.common.handling.ErrorCode;
import com.edubase.user.entity.UserProfile;
import com.edubase.user.repository.UserProfileRepository;
import com.edubase.user.service.abstracts.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserProfileRepository userProfileRepository;

    @Override
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public Page<UserProfile> getAll(Pageable pageable) {
        return userProfileRepository.findAll(pageable);
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public UserProfile getById(Long id) {
        return userProfileRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public UserProfile update(Long id, UserProfile userProfile) {
        UserProfile existing = userProfileRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        existing.setEmail(userProfile.getEmail());
        existing.setFirstName(userProfile.getFirstName());
        existing.setLastName(userProfile.getLastName());
        existing.setHeadline(userProfile.getHeadline());
        existing.setBiography(userProfile.getBiography());
        existing.setAvatarUrl(userProfile.getAvatarUrl());
        existing.setSocialLinks(userProfile.getSocialLinks());
        existing.setStatus(userProfile.getStatus());

        return userProfileRepository.save(existing);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(Long id) {
        UserProfile existing = userProfileRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        userProfileRepository.delete(existing);
    }
}
