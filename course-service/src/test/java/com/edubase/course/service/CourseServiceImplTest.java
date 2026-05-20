package com.edubase.course.service;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
import com.edubase.commonCore.events.InstructorStatus;
import com.edubase.course.configuration.mapper.CourseMapper;
import com.edubase.course.configuration.mapper.LessonMapper;
import com.edubase.course.dto.request.CourseCreateRequest;
import com.edubase.course.dto.request.CourseUpdateRequest;
import com.edubase.course.dto.request.LessonCreateRequest;
import com.edubase.course.dto.response.CourseResponse;
import com.edubase.course.dto.response.CustomPageResponse;
import com.edubase.course.dto.response.InstructorSummaryResponse;
import com.edubase.course.entity.Category;
import com.edubase.course.entity.Course;
import com.edubase.course.entity.CourseStatus;
import com.edubase.course.entity.Lesson;
import com.edubase.course.messaging.CourseSearchSyncKafkaPublisher;
import com.edubase.course.repository.CategoryRepository;
import com.edubase.course.repository.CourseLevelRepository;
import com.edubase.course.repository.CourseRepository;
import com.edubase.course.security.AuthContext;
import com.edubase.course.security.UserRole;
import com.edubase.course.service.abstracts.CourseMediaService;
import com.edubase.course.service.concretes.CourseServiceImpl;
import com.edubase.course.service.concretes.InstructorProjectionReconciliationService;
import com.edubase.course.service.concretes.InstructorProjectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceImplTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CourseLevelRepository courseLevelRepository;

    @Mock
    private CourseMapper courseMapper;

    @Mock
    private LessonMapper lessonMapper;

    @Mock
    private CourseMediaService courseMediaService;

    @Mock
    private InstructorProjectionService instructorProjectionService;

    @Mock
    private InstructorProjectionReconciliationService reconciliationService;

    @Mock
    private CourseSearchSyncKafkaPublisher courseSearchSyncKafkaPublisher;

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

        when(courseRepository.findByIdAndDeletedAtIsNull("course-1")).thenReturn(Optional.of(course));

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

        when(courseRepository.findByIdAndDeletedAtIsNull("course-2")).thenReturn(Optional.of(course));

        AuthContext authContext = new AuthContext("1", UserRole.INSTRUCTOR);
        CourseUpdateRequest request = new CourseUpdateRequest(
                "New",
                "New desc",
                BigDecimal.ONE,
                "level-1",
                List.of("cat-1"),
                List.of("One", "Two", "Three", "Four"),
                List.of("java")
        );

        assertThrows(AccessDeniedException.class,
                () -> courseService.updateCourse(authContext, "course-2", request));
        verify(courseRepository, never()).save(course);
    }

    @Test
    void createCourse_shouldSetInstructorAndDraftStatus() {
        CourseCreateRequest request = new CourseCreateRequest(
                "Title",
                "Desc",
                BigDecimal.ONE,
                "level-1",
                List.of("cat-1"),
                List.of("One", "Two", "Three", "Four"),
                List.of("backend")
        );
        Course mapped = new Course();
        CourseResponse response = CourseResponse.builder().id("course-3").build();

        when(courseMapper.toEntityFromRequest(request)).thenReturn(mapped);
        when(courseLevelRepository.existsById("level-1")).thenReturn(true);
        when(categoryRepository.findAllById(List.of("cat-1")))
                .thenReturn(List.of(new Category("cat-1", "Category", null, null)));
        when(courseRepository.save(mapped)).thenReturn(mapped);
        when(courseMapper.toResponseFromEntity(mapped)).thenReturn(response);
        when(instructorProjectionService.findSummaryByInstructorId("42"))
                .thenReturn(Optional.of(InstructorSummaryResponse.builder()
                        .instructorId("42")
                        .status(InstructorStatus.ACTIVE)
                        .build()));

        AuthContext authContext = new AuthContext("42", UserRole.INSTRUCTOR);
        CourseResponse result = courseService.createCourse(authContext, request);

        assertEquals("course-3", result.getId());
        assertEquals("42", mapped.getInstructorId());
        assertEquals("level-1", mapped.getLevelId());
        assertEquals(CourseStatus.DRAFT, mapped.getStatus());
        assertNotNull(mapped.getCreatedAt());
        assertNotNull(mapped.getUpdatedAt());
        assertNotNull(mapped.getLessons());
    }

    @Test
    void createCourse_shouldThrowWhenCategoryDoesNotExist() {
        CourseCreateRequest request = new CourseCreateRequest(
                "Title",
                "Desc",
                BigDecimal.ONE,
                "level-1",
                List.of("missing-category"),
                List.of("One", "Two", "Three", "Four"),
                List.of("backend")
        );

        when(courseLevelRepository.existsById("level-1")).thenReturn(true);
        when(categoryRepository.findAllById(List.of("missing-category"))).thenReturn(List.of());
        when(instructorProjectionService.findSummaryByInstructorId("42"))
                .thenReturn(Optional.of(InstructorSummaryResponse.builder()
                        .instructorId("42")
                        .status(InstructorStatus.ACTIVE)
                        .build()));

        AuthContext authContext = new AuthContext("42", UserRole.INSTRUCTOR);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> courseService.createCourse(authContext, request));

        assertEquals(ErrorCode.COURSE_CATEGORY_NOT_FOUND, ex.getErrorCode());
        verify(courseRepository, never()).save(org.mockito.ArgumentMatchers.any(Course.class));
    }

    @Test
    void createCourse_shouldThrowWhenLevelDoesNotExist() {
        CourseCreateRequest request = new CourseCreateRequest(
                "Title",
                "Desc",
                BigDecimal.ONE,
                "missing-level",
                List.of("cat-1"),
                List.of("One", "Two", "Three", "Four"),
                List.of("backend")
        );

        when(courseLevelRepository.existsById("missing-level")).thenReturn(false);
        when(instructorProjectionService.findSummaryByInstructorId("42"))
                .thenReturn(Optional.of(InstructorSummaryResponse.builder()
                        .instructorId("42")
                        .status(InstructorStatus.ACTIVE)
                        .build()));

        AuthContext authContext = new AuthContext("42", UserRole.INSTRUCTOR);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> courseService.createCourse(authContext, request));

        assertEquals(ErrorCode.COURSE_LEVEL_NOT_FOUND, ex.getErrorCode());
        verify(categoryRepository, never()).findAllById(org.mockito.ArgumentMatchers.anyIterable());
        verify(courseRepository, never()).save(org.mockito.ArgumentMatchers.any(Course.class));
    }

    @Test
    void publishCourse_shouldPublish_whenValid() {
        Course course = Course.builder()
                .id("course-4")
                .title("Title")
                .description("Desc")
                .price(BigDecimal.ONE)
                .levelId("level-1")
                .categoryIds(List.of("cat-1"))
                .status(CourseStatus.DRAFT)
                .instructorId("7")
                .lessons(new ArrayList<>(List.of(Lesson.builder().id("l1").title("L1").orderIndex(1).build())))
                .build();
        CourseResponse response = CourseResponse.builder().status(CourseStatus.PUBLISHED).build();

        when(courseRepository.findByIdAndDeletedAtIsNull("course-4")).thenReturn(Optional.of(course));
        when(courseRepository.save(course)).thenReturn(course);
        when(courseMapper.toResponseFromEntity(course)).thenReturn(response);

        AuthContext authContext = new AuthContext("7", UserRole.INSTRUCTOR);
        CourseResponse result = courseService.publishCourse(authContext, "course-4");

        assertEquals(CourseStatus.PUBLISHED, course.getStatus());
        assertEquals(CourseStatus.PUBLISHED, result.getStatus());
    }

    @Test
    void unpublishCourse_shouldMoveToDraft() {
        Course course = Course.builder()
                .id("course-4")
                .title("Title")
                .description("Desc")
                .price(BigDecimal.ONE)
                .status(CourseStatus.PUBLISHED)
                .instructorId("7")
                .lessons(new ArrayList<>(List.of(Lesson.builder().id("l1").title("L1").orderIndex(1).build())))
                .build();
        CourseResponse response = CourseResponse.builder().status(CourseStatus.DRAFT).build();

        when(courseRepository.findByIdAndDeletedAtIsNull("course-4")).thenReturn(Optional.of(course));
        when(courseRepository.save(course)).thenReturn(course);
        when(courseMapper.toResponseFromEntity(course)).thenReturn(response);

        AuthContext authContext = new AuthContext("7", UserRole.INSTRUCTOR);
        CourseResponse result = courseService.unpublishCourse(authContext, "course-4");

        assertEquals(CourseStatus.DRAFT, course.getStatus());
        assertEquals(CourseStatus.DRAFT, result.getStatus());
        verify(courseSearchSyncKafkaPublisher, times(1)).publishUpsert(course);
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
        LessonCreateRequest request = new LessonCreateRequest("Lesson", "Lesson Intro", "https://video", 120, 0, false);
        Lesson lesson = new Lesson();
        CourseResponse response = CourseResponse.builder().id("course-5").build();

        when(courseRepository.findByIdAndDeletedAtIsNull("course-5")).thenReturn(Optional.of(course));
        when(lessonMapper.toEntityFromRequest(request)).thenReturn(lesson);
        when(courseRepository.save(course)).thenReturn(course);
        when(courseMapper.toResponseFromEntity(course)).thenReturn(response);

        AuthContext authContext = new AuthContext("5", UserRole.INSTRUCTOR);
        CourseResponse result = courseService.addLesson(authContext, "course-5", request);

        assertEquals("course-5", result.getId());
        assertEquals(1, course.getLessons().size());
        assertNotNull(course.getLessons().get(0).getId());
    }

    @Test
    void deleteCourse_shouldSoftDeleteCourse() {
        Course course = Course.builder()
                .id("course-6")
                .instructorId("admin-1")
                .lessons(new ArrayList<>())
                .build();

        when(courseRepository.findByIdAndDeletedAtIsNull("course-6")).thenReturn(Optional.of(course));
        when(courseRepository.save(course)).thenReturn(course);

        AuthContext authContext = new AuthContext("admin-1", UserRole.ADMIN);
        courseService.deleteCourse(authContext, "course-6");

        verify(courseSearchSyncKafkaPublisher, times(1)).publishDelete(course);
        verify(courseRepository, times(1)).save(course);
        assertNotNull(course.getDeletedAt());
    }

    @Test
    void deleteLesson_shouldCleanupLessonVideoBeforeRemovingLesson() {
        Lesson lesson = Lesson.builder().id("lesson-6").build();
        Course course = Course.builder()
                .id("course-7")
                .instructorId("inst-7")
                .lessons(new ArrayList<>(List.of(lesson)))
                .build();

        when(courseRepository.findByIdAndDeletedAtIsNull("course-7")).thenReturn(Optional.of(course));
        when(courseRepository.save(course)).thenReturn(course);

        AuthContext authContext = new AuthContext("inst-7", UserRole.INSTRUCTOR);
        courseService.deleteLesson(authContext, "course-7", "lesson-6");

        verify(courseMediaService, times(1)).deleteLessonVideoAsset("course-7", "lesson-6");
        verify(courseRepository, times(1)).save(course);
        verify(courseSearchSyncKafkaPublisher, times(1)).publishUpsert(course);
    }

    @Test
    void getPublicCourses_shouldHandleMissingLevelId() {
        Course course = Course.builder()
                .id("course-8")
                .status(CourseStatus.PUBLISHED)
                .instructorId("inst-8")
                .categoryIds(List.of("cat-1"))
                .lessons(new ArrayList<>())
                .build();
        CourseResponse mapped = CourseResponse.builder()
                .id("course-8")
                .instructorId("inst-8")
                .categoryIds(List.of("cat-1"))
                .build();

        when(courseRepository.findAllByStatusAndDeletedAtIsNull(
                org.mockito.ArgumentMatchers.eq(CourseStatus.PUBLISHED),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageImpl<>(List.of(course)));
        when(courseMapper.toResponseFromEntity(course)).thenReturn(mapped);
        when(categoryRepository.findAllById(List.of("cat-1")))
                .thenReturn(List.of(new Category("cat-1", "Category", null, null)));
        when(instructorProjectionService.findSummariesByInstructorIds(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(java.util.Map.of());

        CustomPageResponse<CourseResponse> result = courseService.getPublicCourses(0, 50);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("course-8", result.getContent().get(0).getId());
        assertNull(result.getContent().get(0).getLevel());
    }
}
