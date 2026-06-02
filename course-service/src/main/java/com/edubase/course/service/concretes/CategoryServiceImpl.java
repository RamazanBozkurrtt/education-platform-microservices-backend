package com.edubase.course.service.concretes;

import com.edubase.course.configuration.CourseCacheNames;
import com.edubase.course.dto.response.CategoryResponse;
import com.edubase.course.repository.CategoryRepository;
import com.edubase.course.service.abstracts.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    @Cacheable(cacheNames = CourseCacheNames.COURSE_CATEGORIES_PUBLIC, key = "'v2'")
    public List<CategoryResponse> getPublicCategories() {
        return categoryRepository.findAllByOrderByCategoryNameAsc().stream()
                .map(category -> CategoryResponse.builder()
                        .id(category.getId())
                        .categoryName(category.getCategoryName())
                        .build())
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
