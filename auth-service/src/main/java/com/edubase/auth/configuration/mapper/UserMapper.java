package com.edubase.auth.configuration.mapper;

import com.edubase.auth.dto.UserRequest;
import com.edubase.auth.dto.UserResponse;
import com.edubase.auth.entity.Role;
import com.edubase.auth.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;


@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper extends BaseMapper<User, UserResponse, UserRequest>{

    @Override
    User toEntityFromRequest(UserRequest dto);

    default List<String> map(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        return roles.stream().map(Role::getName).toList();
    }

    default Set<Role> map(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }
        Set<Role> mapped = new HashSet<>();
        for (String roleName : roles) {
            if (roleName == null || roleName.isBlank()) {
                continue;
            }
            mapped.add(Role.builder().name(roleName.trim()).build());
        }
        return mapped;
    }
}
