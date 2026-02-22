package com.edubase.user.service.abstracts;

import com.edubase.user.dto.response.CustomPageResponse;
import com.edubase.user.dto.response.UserProfileResponse;
import com.edubase.user.entity.UserProfile;

public interface UserService {

    CustomPageResponse<UserProfileResponse> getAll(int pageNumber, int pageSize);

    UserProfile getById(Long id);

    UserProfile update(Long id, UserProfile userProfile);

    void delete(Long id);
}
