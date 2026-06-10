package com.edubase.user.configuration.mapper;

import com.edubase.user.dto.request.UserProfileRequest;
import com.edubase.user.dto.response.UserProfileResponse;
import com.edubase.user.entity.UserProfile;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper extends BaseMapper<UserProfile, UserProfileResponse, UserProfileRequest> {
}
