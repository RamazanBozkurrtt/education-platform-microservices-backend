package com.edubase.user.service.abstracts;

import com.edubase.user.dto.request.UserProfileRequest;
import com.edubase.user.dto.response.CustomPageResponse;
import com.edubase.user.dto.response.UserProfileResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {

    CustomPageResponse<UserProfileResponse> getAll(int pageNumber, int pageSize);

    UserProfileResponse getById(Long id);

    UserProfileResponse update(Long id, UserProfileRequest userProfileRequest);

    UserProfileResponse getMe(Long authUserId, String authEmail);

    UserProfileResponse updateMe(Long authUserId, String authEmail, UserProfileRequest userProfileRequest);

    UserProfileResponse uploadMyAvatar(Long authUserId, String authEmail, MultipartFile file);

    ResponseEntity<Resource> getPublicAvatar(Long profileId);

    UserProfileResponse create(UserProfileRequest request);
}
