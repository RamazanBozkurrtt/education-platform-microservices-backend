package com.edubase.auth.configuration.mapper;

import com.edubase.auth.dto.UserRequest;
import com.edubase.auth.dto.UserResponse;
import com.edubase.auth.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;


@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper extends BaseMapper<User, UserResponse, UserRequest>{

    @Override
    User toEntityFromRequest(UserRequest dto);
}
