package com.edubase.course.repository;

import com.edubase.course.entity.Category;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CategoryRepository extends MongoRepository<Category, String> {
    boolean existsByCategoryName(String categoryName);

    List<Category> findAllByOrderByCategoryNameAsc();
}
