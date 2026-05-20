package com.edubase.course.service.abstracts;

import com.edubase.course.dto.response.CourseLevelResponse;

import java.util.List;

public interface CourseLevelService {

    List<CourseLevelResponse> getPublicCourseLevels();
}
