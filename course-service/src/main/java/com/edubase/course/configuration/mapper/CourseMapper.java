package com.edubase.course.configuration.mapper;

import com.edubase.course.dto.request.CourseCreateRequest;
import com.edubase.course.dto.request.CourseUpdateRequest;
import com.edubase.course.dto.response.CourseResponse;
import com.edubase.course.entity.Course;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, uses = LessonMapper.class)
public interface CourseMapper extends BaseMapper<Course, CourseResponse, CourseCreateRequest> {

    @Override
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", constant = "DRAFT")
    @Mapping(target = "instructorId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "lessons", ignore = true)
    Course toEntityFromRequest(CourseCreateRequest dto);

    void updateCourseFromRequest(CourseUpdateRequest request, @MappingTarget Course course);
}
