package com.edubase.course.service.abstracts;

import com.edubase.course.dto.response.CategoryResponse;

import java.util.List;

public interface CategoryService {

    List<CategoryResponse> getPublicCategories();
}
