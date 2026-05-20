package com.edubase.course.service.concretes;

import com.edubase.course.dto.response.CategoryResponse;
import com.edubase.course.repository.CategoryRepository;
import com.edubase.course.service.abstracts.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    @Cacheable(cacheNames = "courseCategoriesPublic")
    public List<CategoryResponse> getPublicCategories() {
        return categoryRepository.findAllByOrderByCategoryNameAsc().stream()
                .map(category -> CategoryResponse.builder()
                        .id(category.getId())
                        .categoryName(category.getCategoryName())
                        .build())
                .toList();
    }
}
