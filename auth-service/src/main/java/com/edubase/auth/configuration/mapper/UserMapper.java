package com.edubase.auth.configuration.mapper;

import com.edubase.auth.dto.UserRequest;
import com.edubase.auth.dto.UserResponse;
import com.edubase.auth.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;


@Mapper(componentModel = "spring")
public interface UserMapper extends BaseMapper<User, UserResponse, UserRequest>{

    @Override
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    User toEntityFromRequest(UserRequest dto);
}
