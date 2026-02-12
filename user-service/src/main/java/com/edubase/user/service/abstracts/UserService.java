package com.edubase.user.service.abstracts;

import com.edubase.user.entity.UserProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {

    Page<UserProfile> getAll(Pageable pageable);

    UserProfile getById(Long id);

    UserProfile update(Long id, UserProfile userProfile);

    void delete(Long id);
}
