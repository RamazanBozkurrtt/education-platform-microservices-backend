package com.edubase.user.service.abstracts;

import com.edubase.user.dto.request.UserProfileRequest;
import com.edubase.user.dto.response.CustomPageResponse;
import com.edubase.user.dto.response.UserProfileResponse;

public interface UserService {

    CustomPageResponse<UserProfileResponse> getAll(int pageNumber, int pageSize);

    UserProfileResponse getById(Long id);

    UserProfileResponse update(Long id, UserProfileRequest userProfileRequest);

    UserProfileResponse getMe(Long authUserId, String authEmail);

    UserProfileResponse updateMe(Long authUserId, String authEmail, UserProfileRequest userProfileRequest);

    UserProfileResponse create(UserProfileRequest request);
}
