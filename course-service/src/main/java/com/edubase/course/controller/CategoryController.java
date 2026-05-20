package com.edubase.course.controller;

import com.edubase.commonCore.utils.RestResponse;
import com.edubase.course.controller.base.RestBaseController;
import com.edubase.course.dto.response.CategoryResponse;
import com.edubase.course.service.abstracts.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/courses/public/categories", "/api/v1/courses/public/categories"})
public class CategoryController extends RestBaseController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<RestResponse<List<CategoryResponse>>> getPublicCategories() {
        return ok(categoryService.getPublicCategories());
    }
}
