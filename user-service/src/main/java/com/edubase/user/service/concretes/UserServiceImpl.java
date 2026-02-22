package com.edubase.user.service.concretes;


import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
import com.edubase.user.configuration.mapper.UserMapper;
import com.edubase.user.dto.response.CustomPageResponse;
import com.edubase.user.dto.response.UserProfileResponse;
import com.edubase.user.entity.UserProfile;
import com.edubase.user.repository.UserProfileRepository;
import com.edubase.user.service.abstracts.UserService;
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
        Pageable pageable = PageRequest.of(pageNumber,pageSize, Sort.by("createdData"));
        Page<UserProfile> userPage = userProfileRepository.findAll(pageable);

        List<UserProfileResponse> responseList = userMapper.toResponseListFromEntityList(userPage.getContent());

        return CustomPageResponse.of(userPage,responseList);
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public UserProfile getById(Long id) {
        return userProfileRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    @Override
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
