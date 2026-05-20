package com.edubase.course.service.concretes;

import com.edubase.course.dto.response.CourseLevelResponse;
import com.edubase.course.repository.CourseLevelRepository;
import com.edubase.course.service.abstracts.CourseLevelService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseLevelServiceImpl implements CourseLevelService {

    private final CourseLevelRepository courseLevelRepository;

    @Override
    @Cacheable(cacheNames = "courseLevelsPublic")
    public List<CourseLevelResponse> getPublicCourseLevels() {
        return courseLevelRepository.findAllByOrderByDisplayOrderAscLevelNameAsc().stream()
                .map(level -> CourseLevelResponse.builder()
                        .id(level.getId())
                        .levelName(level.getLevelName())
                        .build())
                .toList();
    }
}
