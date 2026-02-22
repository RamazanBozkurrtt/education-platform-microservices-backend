package com.edubase.user.configuration.mapper;

import com.edubase.user.dto.request.UserProfileRequest;
import com.edubase.user.dto.response.UserProfileResponse;
import com.edubase.user.entity.UserProfile;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper extends BaseMapper<UserProfile, UserProfileResponse, UserProfileRequest> {
}
