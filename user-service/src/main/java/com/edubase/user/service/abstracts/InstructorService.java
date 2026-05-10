package com.edubase.user.service.abstracts;

import com.edubase.user.dto.request.InstructorApplyRequest;
import com.edubase.user.dto.response.InstructorProfileResponse;

import java.util.List;

public interface InstructorService {

    InstructorProfileResponse apply(Long authUserId, String authEmail, String bearerToken, InstructorApplyRequest request);

    InstructorProfileResponse getMe(Long authUserId, String authEmail, List<String> rolesFromToken);

    InstructorProfileResponse updateMe(Long authUserId, String authEmail, List<String> rolesFromToken, InstructorApplyRequest request);
}
