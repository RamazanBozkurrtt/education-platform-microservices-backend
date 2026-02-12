package com.edubase.auth.configuration.mapper;

import com.edubase.auth.dto.UserRequest;
import com.edubase.auth.dto.UserResponse;
import com.edubase.auth.entity.User;
import org.mapstruct.Mapper;


@Mapper(componentModel = "spring")
public interface UserMapper extends BaseMapper<User, UserResponse, UserRequest>{
}
