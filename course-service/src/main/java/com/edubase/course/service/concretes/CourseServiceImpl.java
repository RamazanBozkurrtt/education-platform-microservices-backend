package com.edubase.course.service.concretes;

import com.edubase.course.configuration.mapper.CourseMapper;
import com.edubase.course.configuration.mapper.LessonMapper;
import com.edubase.course.dto.request.CourseCreateRequest;
import com.edubase.course.dto.request.CourseUpdateRequest;
import com.edubase.course.dto.request.LessonCreateRequest;
import com.edubase.course.dto.request.LessonUpdateRequest;
import com.edubase.course.dto.response.CourseResponse;
import com.edubase.course.dto.response.CustomPageResponse;
import com.edubase.course.entity.Course;
import com.edubase.course.entity.CourseStatus;
import com.edubase.course.entity.Lesson;
import com.edubase.course.exception.CourseNotFoundException;
import com.edubase.course.exception.LessonNotFoundException;
import com.edubase.course.exception.PublishValidationException;
import com.edubase.course.repository.CourseRepository;
import com.edubase.course.security.AuthContext;
import com.edubase.course.security.UserRole;
import com.edubase.course.service.abstracts.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

    private final CourseRepository courseRepository;
    private final CourseMapper courseMapper;
    private final LessonMapper lessonMapper;

    @Override
    @PreAuthorize("@courseSecurity.isAdminOrInstructor(#p0)")
    @CacheEvict(cacheNames = {
            "coursesPublicById",
            "coursesPublicPaged"
    }, allEntries = true)
    public CourseResponse createCourse(AuthContext authContext, CourseCreateRequest request) {
        requireAdminOrInstructor(authContext);

        Course course = courseMapper.toEntityFromRequest(request);
        course.setInstructorId(authContext.userId());
        course.setStatus(CourseStatus.DRAFT);
        course.setLessons(new ArrayList<>());

        Course saved = courseRepository.save(course);
        return courseMapper.toResponseFromEntity(saved);
    }

    @Override
    @PreAuthorize("@courseSecurity.canManageCourse(#p0, #p1)")
    public CourseResponse getCourseById(AuthContext authContext, String id) {
        Course course = findCourse(id);
        requireAdminOrInstructor(authContext, course);
        sortLessons(ensureLessons(course));
        return courseMapper.toResponseFromEntity(course);
    }

    @Override
    @Cacheable(cacheNames = "coursesPublicById", key = "#id")
    public CourseResponse getPublicCourseById(String id) {
        Course course = findPublishedCourse(id);
        sortLessons(ensureLessons(course));
        return courseMapper.toResponseFromEntity(course);
    }

    @Override
    @PreAuthorize("@courseSecurity.isAdmin(#p0)")
    public CustomPageResponse<CourseResponse> getCourses(AuthContext authContext, int pageNumber, int pageSize) {
        requireAdmin(authContext);
        PageRequest pageRequest = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Course> page = courseRepository.findAll(pageRequest);
        List<CourseResponse> responses = page.getContent().stream()
                .map(courseMapper::toResponseFromEntity)
                .toList();
        return CustomPageResponse.of(page, responses);
    }

    @Override
    @Cacheable(cacheNames = "coursesPublicPaged", key = "T(String).valueOf(#pageNumber).concat(':').concat(T(String).valueOf(#pageSize))")
    public CustomPageResponse<CourseResponse> getPublicCourses(int pageNumber, int pageSize) {
        PageRequest pageRequest = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Course> page = courseRepository.findAllByStatus(CourseStatus.PUBLISHED, pageRequest);
        List<CourseResponse> responses = page.getContent().stream()
                .map(courseMapper::toResponseFromEntity)
                .toList();
        return CustomPageResponse.of(page, responses);
    }

    @Override
    @PreAuthorize("@courseSecurity.isAdminOrInstructor(#p0)")
    public CustomPageResponse<CourseResponse> getMyCourses(AuthContext authContext, int pageNumber, int pageSize) {
        requireAdminOrInstructor(authContext);
        PageRequest pageRequest = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Course> page = courseRepository.findAllByInstructorId(authContext.userId(), pageRequest);
        List<CourseResponse> responses = page.getContent().stream()
                .map(courseMapper::toResponseFromEntity)
                .toList();
        return CustomPageResponse.of(page, responses);
    }

    @Override
    @PreAuthorize("@courseSecurity.canManageCourse(#p0, #p1)")
    @Caching(evict = {
            @CacheEvict(cacheNames = "coursesPublicById", allEntries = true),
            @CacheEvict(cacheNames = "coursesPublicPaged", allEntries = true)
    })
    public CourseResponse updateCourse(AuthContext authContext, String id, CourseUpdateRequest request) {
        Course course = findCourse(id);
        requireAdminOrInstructor(authContext, course);

        courseMapper.updateCourseFromRequest(request, course);

        Course saved = courseRepository.save(course);
        return courseMapper.toResponseFromEntity(saved);
    }

    @Override
    @PreAuthorize("@courseSecurity.isAdmin(#p0)")
    @Caching(evict = {
            @CacheEvict(cacheNames = "coursesPublicById", allEntries = true),
            @CacheEvict(cacheNames = "coursesPublicPaged", allEntries = true)
    })
    public void deleteCourse(AuthContext authContext, String id) {
        requireAdmin(authContext);

        Course course = findCourse(id);
        courseRepository.delete(course);
    }

    @Override
    @PreAuthorize("@courseSecurity.canManageCourse(#p0, #p1)")
    @Caching(evict = {
            @CacheEvict(cacheNames = "coursesPublicById", allEntries = true),
            @CacheEvict(cacheNames = "coursesPublicPaged", allEntries = true)
    })
    public CourseResponse addLesson(AuthContext authContext, String courseId, LessonCreateRequest request) {
        Course course = findCourse(courseId);
        requireAdminOrInstructor(authContext, course);

        List<Lesson> lessons = ensureLessons(course);
        Lesson lesson = lessonMapper.toEntityFromRequest(request);
        lesson.setId(UUID.randomUUID().toString());
        lessons.add(lesson);
        sortLessons(lessons);

        Course saved = courseRepository.save(course);
        return courseMapper.toResponseFromEntity(saved);
    }

    @Override
    @PreAuthorize("@courseSecurity.canManageCourse(#p0, #p1)")
    @Caching(evict = {
            @CacheEvict(cacheNames = "coursesPublicById", allEntries = true),
            @CacheEvict(cacheNames = "coursesPublicPaged", allEntries = true)
    })
    public CourseResponse updateLesson(AuthContext authContext, String courseId, String lessonId, LessonUpdateRequest request) {
        Course course = findCourse(courseId);
        requireAdminOrInstructor(authContext, course);

        Lesson lesson = findLesson(course, lessonId);
        lessonMapper.updateLessonFromRequest(request, lesson);
        sortLessons(ensureLessons(course));

        Course saved = courseRepository.save(course);
        return courseMapper.toResponseFromEntity(saved);
    }

    @Override
    @PreAuthorize("@courseSecurity.canManageCourse(#p0, #p1)")
    @Caching(evict = {
            @CacheEvict(cacheNames = "coursesPublicById", allEntries = true),
            @CacheEvict(cacheNames = "coursesPublicPaged", allEntries = true)
    })
    public void deleteLesson(AuthContext authContext, String courseId, String lessonId) {
        Course course = findCourse(courseId);
        requireAdminOrInstructor(authContext, course);

        List<Lesson> lessons = ensureLessons(course);
        boolean removed = lessons.removeIf(lesson -> lessonId.equals(lesson.getId()));
        if (!removed) {
            throw new LessonNotFoundException();
        }

        courseRepository.save(course);
    }

    @Override
    @PreAuthorize("@courseSecurity.canManageCourse(#p0, #p1)")
    @Caching(evict = {
            @CacheEvict(cacheNames = "coursesPublicById", allEntries = true),
            @CacheEvict(cacheNames = "coursesPublicPaged", allEntries = true)
    })
    public CourseResponse publishCourse(AuthContext authContext, String id) {
        Course course = findCourse(id);
        requireAdminOrInstructor(authContext, course);

        if (!canPublish(course)) {
            throw new PublishValidationException();
        }

        course.setStatus(CourseStatus.PUBLISHED);

        Course saved = courseRepository.save(course);
        return courseMapper.toResponseFromEntity(saved);
    }

    private Course findCourse(String id) {
        return courseRepository.findById(id).orElseThrow(CourseNotFoundException::new);
    }

    private Course findPublishedCourse(String id) {
        return courseRepository.findByIdAndStatus(id, CourseStatus.PUBLISHED)
                .orElseThrow(CourseNotFoundException::new);
    }

    private Lesson findLesson(Course course, String lessonId) {
        return ensureLessons(course).stream()
                .filter(lesson -> lessonId.equals(lesson.getId()))
                .findFirst()
                .orElseThrow(LessonNotFoundException::new);
    }

    private List<Lesson> ensureLessons(Course course) {
        if (course.getLessons() == null) {
            course.setLessons(new ArrayList<>());
        }
        return course.getLessons();
    }

    private boolean canPublish(Course course) {
        if (!hasText(course.getTitle()) || !hasText(course.getDescription())) {
            return false;
        }
        if (course.getPrice() == null || course.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            return false;
        }
        return course.getLessons() != null && !course.getLessons().isEmpty();
    }

    private void requireAdmin(AuthContext authContext) {
        if (authContext == null || authContext.role() != UserRole.ADMIN) {
            throw new AccessDeniedException("Admin role required");
        }
    }

    private void requireAdminOrInstructor(AuthContext authContext) {
        if (authContext == null || authContext.role() == UserRole.UNKNOWN) {
            throw new AccessDeniedException("Role required");
        }
        if (authContext.role() != UserRole.ADMIN && authContext.role() != UserRole.INSTRUCTOR) {
            throw new AccessDeniedException("Admin or instructor role required");
        }
    }

    private void requireAdminOrInstructor(AuthContext authContext, Course course) {
        requireAdminOrInstructor(authContext);
        if (authContext.role() == UserRole.ADMIN) {
            return;
        }
        if (!authContext.userId().equals(course.getInstructorId())) {
            throw new AccessDeniedException("Instructor cannot access this course");
        }
    }

    private void sortLessons(List<Lesson> lessons) {
        lessons.sort(Comparator
                .comparing(Lesson::getOrderIndex, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(Lesson::getTitle, Comparator.nullsLast(String::compareToIgnoreCase)));
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
