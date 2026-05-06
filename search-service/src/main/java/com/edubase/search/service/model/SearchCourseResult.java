package com.edubase.search.service.model;

import java.util.List;

public record SearchCourseResult(
        List<SearchCourseHit> hits,
        long totalElements,
        int pageNumber,
        int pageSize
) {
}
