package com.edubase.course.configuration.mapper;

import com.edubase.course.dto.request.LessonCreateRequest;
import com.edubase.course.dto.request.LessonUpdateRequest;
import com.edubase.course.dto.response.LessonResponse;
import com.edubase.course.entity.Lesson;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface LessonMapper extends BaseMapper<Lesson, LessonResponse, LessonCreateRequest> {

    @Override
    @Mapping(target = "id", ignore = true)
    Lesson toEntityFromRequest(LessonCreateRequest dto);

    void updateLessonFromRequest(LessonUpdateRequest request, @MappingTarget Lesson lesson);
}
