package com.edubase.search.repository;

import com.edubase.search.entity.CourseSearchDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface CourseSearchRepository extends ElasticsearchRepository<CourseSearchDocument, String> {
}
