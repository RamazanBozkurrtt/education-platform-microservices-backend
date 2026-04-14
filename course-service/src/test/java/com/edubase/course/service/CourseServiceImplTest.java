package com.edubase.course.service;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
import com.edubase.course.configuration.mapper.CourseMapper;
import com.edubase.course.configuration.mapper.LessonMapper;
import com.edubase.course.dto.request.CourseCreateRequest;
import com.edubase.course.dto.request.CourseUpdateRequest;
import com.edubase.course.dto.request.LessonCreateRequest;
import com.edubase.course.dto.response.CourseResponse;
import com.edubase.course.entity.Course;
import com.edubase.course.entity.CourseStatus;
import com.edubase.course.entity.Lesson;
import com.edubase.course.grpc.UserGrpcClient;
import com.edubase.course.repository.CourseRepository;
import com.edubase.course.security.AuthContext;
import com.edubase.course.security.UserRole;
import com.edubase.course.service.concretes.CourseServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceImplTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseMapper courseMapper;

    @Mock
    private LessonMapper lessonMapper;

    @Mock
    private UserGrpcClient userGrpcClient;

    @InjectMocks
    private CourseServiceImpl courseService;

    @Test
    void publishCourse_requiresValidCourse() {
        Course course = Course.builder()
                .id("course-1")
                .title(" ")
                .description("Desc")
                .price(BigDecimal.TEN)
                .status(CourseStatus.DRAFT)
                .lessons(new ArrayList<>())
                .build();

        when(courseRepository.findById("course-1")).thenReturn(Optional.of(course));

        AuthContext authContext = new AuthContext("1", UserRole.ADMIN);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> courseService.publishCourse(authContext, "course-1"));

        assertEquals(ErrorCode.COURSE_PUBLISH_INVALID, ex.getErrorCode());
        verify(courseRepository, never()).save(course);
    }

    @Test
    void instructorCannotUpdateOtherInstructorsCourse() {
        Course course = Course.builder()
                .id("course-2")
                .title("Title")
                .description("Desc")
                .price(BigDecimal.ONE)
                .status(CourseStatus.DRAFT)
                .instructorId("99")
                .lessons(new ArrayList<>())
                .build();

        when(courseRepository.findById("course-2")).thenReturn(Optional.of(course));

        AuthContext authContext = new AuthContext("1", UserRole.INSTRUCTOR);
        CourseUpdateRequest request = new CourseUpdateRequest("New", "New desc", BigDecimal.ONE, "cat-1");

        assertThrows(AccessDeniedException.class,
                () -> courseService.updateCourse(authContext, "course-2", request));
        verify(courseRepository, never()).save(course);
    }

    @Test
    void createCourse_shouldSetInstructorAndDraftStatus() {
        CourseCreateRequest request = new CourseCreateRequest("Title", "Desc", BigDecimal.ONE, "cat-1");
        Course mapped = new Course();
        CourseResponse response = CourseResponse.builder().id("course-3").build();

        when(courseMapper.toEntityFromRequest(request)).thenReturn(mapped);
        when(courseRepository.save(mapped)).thenReturn(mapped);
        when(courseMapper.toResponseFromEntity(mapped)).thenReturn(response);

        AuthContext authContext = new AuthContext("42", UserRole.INSTRUCTOR);
        CourseResponse result = courseService.createCourse(authContext, request);

        assertEquals("course-3", result.getId());
        assertEquals("42", mapped.getInstructorId());
        assertEquals(CourseStatus.DRAFT, mapped.getStatus());
        assertNotNull(mapped.getCreatedAt());
        assertNotNull(mapped.getUpdatedAt());
        assertNotNull(mapped.getLessons());
    }

    @Test
    void publishCourse_shouldPublish_whenValid() {
        Course course = Course.builder()
                .id("course-4")
                .title("Title")
                .description("Desc")
                .price(BigDecimal.ONE)
                .status(CourseStatus.DRAFT)
                .instructorId("7")
                .lessons(new ArrayList<>(List.of(Lesson.builder().id("l1").title("L1").orderIndex(1).build())))
                .build();
        CourseResponse response = CourseResponse.builder().status(CourseStatus.PUBLISHED).build();

        when(courseRepository.findById("course-4")).thenReturn(Optional.of(course));
        when(courseRepository.save(course)).thenReturn(course);
        when(courseMapper.toResponseFromEntity(course)).thenReturn(response);

        AuthContext authContext = new AuthContext("7", UserRole.INSTRUCTOR);
        CourseResponse result = courseService.publishCourse(authContext, "course-4");

        assertEquals(CourseStatus.PUBLISHED, course.getStatus());
        assertEquals(CourseStatus.PUBLISHED, result.getStatus());
    }

    @Test
    void addLesson_shouldAssignLessonId() {
        Course course = Course.builder()
                .id("course-5")
                .title("Title")
                .description("Desc")
                .price(BigDecimal.ONE)
                .status(CourseStatus.DRAFT)
                .instructorId("5")
                .lessons(new ArrayList<>())
                .build();
        LessonCreateRequest request = new LessonCreateRequest("Lesson", "https://video", 120, 0);
        Lesson lesson = new Lesson();
        CourseResponse response = CourseResponse.builder().id("course-5").build();

        when(courseRepository.findById("course-5")).thenReturn(Optional.of(course));
        when(lessonMapper.toEntityFromRequest(request)).thenReturn(lesson);
        when(courseRepository.save(course)).thenReturn(course);
        when(courseMapper.toResponseFromEntity(course)).thenReturn(response);

        AuthContext authContext = new AuthContext("5", UserRole.INSTRUCTOR);
        CourseResponse result = courseService.addLesson(authContext, "course-5", request);

        assertEquals("course-5", result.getId());
        assertEquals(1, course.getLessons().size());
        assertNotNull(course.getLessons().get(0).getId());
    }
}
