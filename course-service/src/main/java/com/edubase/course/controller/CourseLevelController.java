package com.edubase.course.controller;

import com.edubase.commonCore.utils.RestResponse;
import com.edubase.course.controller.base.RestBaseController;
import com.edubase.course.dto.response.CourseLevelResponse;
import com.edubase.course.service.abstracts.CourseLevelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/courses/public/levels", "/api/v1/courses/public/levels"})
public class CourseLevelController extends RestBaseController {

    private final CourseLevelService courseLevelService;

    @GetMapping
    public ResponseEntity<RestResponse<List<CourseLevelResponse>>> getPublicCourseLevels() {
        return ok(courseLevelService.getPublicCourseLevels());
    }
}
