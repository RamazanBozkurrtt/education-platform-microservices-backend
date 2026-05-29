package com.edubase.course.service.concretes;

import com.edubase.commonCore.events.InstructorStatus;
import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
import com.edubase.course.configuration.mapper.CourseMapper;
import com.edubase.course.configuration.mapper.LessonMapper;
import com.edubase.course.dto.request.CourseCreateRequest;
import com.edubase.course.dto.request.CourseUpdateRequest;
import com.edubase.course.dto.request.LessonCreateRequest;
import com.edubase.course.dto.request.LessonUpdateRequest;
import com.edubase.course.dto.response.CategoryResponse;
import com.edubase.course.dto.response.CourseLevelResponse;
import com.edubase.course.dto.response.CourseResponse;
import com.edubase.course.dto.response.CustomPageResponse;
import com.edubase.course.dto.response.InstructorSummaryResponse;
import com.edubase.course.dto.response.LessonResponse;
import com.edubase.course.entity.Category;
import com.edubase.course.entity.Course;
import com.edubase.course.entity.CourseLevel;
import com.edubase.course.entity.CourseStatus;
import com.edubase.course.entity.Lesson;
import com.edubase.course.exception.CourseCategoryNotFoundException;
import com.edubase.course.exception.CourseLevelNotFoundException;
import com.edubase.course.exception.CourseNotFoundException;
import com.edubase.course.exception.LessonNotFoundException;
import com.edubase.course.exception.PublishValidationException;
import com.edubase.course.messaging.CourseSearchSyncKafkaPublisher;
import com.edubase.course.repository.CategoryRepository;
import com.edubase.course.repository.CourseLevelRepository;
import com.edubase.course.repository.CourseRepository;
import com.edubase.course.security.AuthContext;
import com.edubase.course.security.UserRole;
import com.edubase.course.service.abstracts.CourseMediaService;
import com.edubase.course.service.abstracts.CourseService;
import com.edubase.course.service.abstracts.finalexam.FinalExamPublishValidationService;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

    private static final String PUBLIC_COURSE_IMAGE_PATH_TEMPLATE = "/courses/public/%s/image";

    private final CategoryRepository categoryRepository;
    private final CourseLevelRepository courseLevelRepository;
    private final CourseRepository courseRepository;
    private final CourseMapper courseMapper;
    private final LessonMapper lessonMapper;
    private final CourseMediaService courseMediaService;
    private final InstructorProjectionService instructorProjectionService;
    private final InstructorProjectionReconciliationService reconciliationService;
    private final CourseSearchSyncKafkaPublisher courseSearchSyncKafkaPublisher;
    private final FinalExamPublishValidationService finalExamPublishValidationService;

    @Override
    @PreAuthorize("@courseSecurity.isAdminOrInstructor(#p0)")
    @CacheEvict(cacheNames = {
            "coursesPublicById",
            "coursesPublicPaged"
    }, allEntries = true)
    public CourseResponse createCourse(AuthContext authContext, CourseCreateRequest request) {
        requireAdminOrInstructor(authContext);
        if (authContext.role() == UserRole.INSTRUCTOR) {
            validateInstructorForCourseCreation(authContext.userId());
        }
        String normalizedLevelId = normalizeAndValidateLevelId(request.getLevelId());
        List<String> normalizedCategoryIds = normalizeAndValidateCategoryIds(request.getCategoryIds());

        Course course = courseMapper.toEntityFromRequest(request);
        course.setLevelId(normalizedLevelId);
        course.setCategoryIds(normalizedCategoryIds);
        course.setCategoryId(resolvePrimaryCategoryId(normalizedCategoryIds));
        course.setInstructorId(authContext.userId());
        course.setStatus(CourseStatus.DRAFT);
        course.setLessons(new ArrayList<>());
        Instant now = Instant.now();
        if (course.getCreatedAt() == null) {
            course.setCreatedAt(now);
        }
        course.setUpdatedAt(now);

        Course saved = courseRepository.save(course);
        courseSearchSyncKafkaPublisher.publishUpsert(saved);
        return enrichCourseResponse(courseMapper.toResponseFromEntity(saved));
    }

    @Override
    @PreAuthorize("@courseSecurity.canManageCourse(#p0, #p1)")
    public CourseResponse getCourseById(AuthContext authContext, String id) {
        Course course = findCourse(id);
        requireAdminOrInstructor(authContext, course);
        sortLessons(ensureLessons(course));
        return enrichCourseResponse(courseMapper.toResponseFromEntity(course));
    }

    @Override
    @Cacheable(cacheNames = "coursesPublicById", key = "#id")
    public CourseResponse getPublicCourseById(String id) {
        Course course = findPublishedCourse(id);
        sortLessons(ensureLessons(course));
        return enrichCourseResponse(courseMapper.toResponseFromEntity(course));
    }

    @Override
    @PreAuthorize("@courseSecurity.isAdmin(#p0)")
    public CustomPageResponse<CourseResponse> getCourses(AuthContext authContext, int pageNumber, int pageSize) {
        requireAdmin(authContext);
        PageRequest pageRequest = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Course> page = courseRepository.findAllByDeletedAtIsNull(pageRequest);
        List<CourseResponse> responses = enrichCourseResponses(page.getContent().stream()
                .map(courseMapper::toResponseFromEntity)
                .toList());
        return CustomPageResponse.of(page, responses);
    }

    @Override
    @Cacheable(cacheNames = "coursesPublicPaged", key = "T(String).valueOf(#pageNumber).concat(':').concat(T(String).valueOf(#pageSize))")
    public CustomPageResponse<CourseResponse> getPublicCourses(int pageNumber, int pageSize) {
        PageRequest pageRequest = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Course> page = courseRepository.findAllByStatusAndDeletedAtIsNull(CourseStatus.PUBLISHED, pageRequest);
        List<CourseResponse> responses = enrichCourseResponses(page.getContent().stream()
                .map(courseMapper::toResponseFromEntity)
                .toList());
        return CustomPageResponse.of(page, responses);
    }

    @Override
    @PreAuthorize("@courseSecurity.isAuthenticatedUser(#p0)")
    public CustomPageResponse<CourseResponse> getMyCourses(AuthContext authContext, int pageNumber, int pageSize) {
        requireAuthenticatedRole(authContext);
        PageRequest pageRequest = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Course> page = courseRepository.findAllByInstructorIdAndDeletedAtIsNull(authContext.userId(), pageRequest);
        List<CourseResponse> responses = enrichCourseResponses(page.getContent().stream()
                .map(courseMapper::toResponseFromEntity)
                .toList());
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
        String normalizedLevelId = normalizeAndValidateLevelId(request.getLevelId());
        List<String> normalizedCategoryIds = normalizeAndValidateCategoryIds(request.getCategoryIds());

        courseMapper.updateCourseFromRequest(request, course);
        course.setLevelId(normalizedLevelId);
        course.setCategoryIds(normalizedCategoryIds);
        course.setCategoryId(resolvePrimaryCategoryId(normalizedCategoryIds));

        Course saved = courseRepository.save(course);
        courseSearchSyncKafkaPublisher.publishUpsert(saved);
        return enrichCourseResponse(courseMapper.toResponseFromEntity(saved));
    }

    @Override
    @PreAuthorize("@courseSecurity.canManageCourse(#p0, #p1)")
    @Caching(evict = {
            @CacheEvict(cacheNames = "coursesPublicById", allEntries = true),
            @CacheEvict(cacheNames = "coursesPublicPaged", allEntries = true)
    })
    public void deleteCourse(AuthContext authContext, String id) {
        Course course = findCourse(id);
        requireAdminOrInstructor(authContext, course);
        Instant now = Instant.now();
        course.setDeletedAt(now);
        course.setUpdatedAt(now);
        Course saved = courseRepository.save(course);
        courseSearchSyncKafkaPublisher.publishDelete(saved);
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
        courseSearchSyncKafkaPublisher.publishUpsert(saved);
        return enrichCourseResponse(courseMapper.toResponseFromEntity(saved));
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
        courseSearchSyncKafkaPublisher.publishUpsert(saved);
        return enrichCourseResponse(courseMapper.toResponseFromEntity(saved));
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
        Lesson lesson = findLesson(course, lessonId);
        courseMediaService.deleteLessonVideoAsset(course.getId(), lesson.getId());

        List<Lesson> lessons = ensureLessons(course);
        boolean removed = lessons.removeIf(item -> lessonId.equals(item.getId()));
        if (!removed) {
            throw new LessonNotFoundException();
        }

        courseRepository.save(course);
        courseSearchSyncKafkaPublisher.publishUpsert(course);
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
        finalExamPublishValidationService.validateForPublishIfPresent(course.getId());

        course.setStatus(CourseStatus.PUBLISHED);

        Course saved = courseRepository.save(course);
        courseSearchSyncKafkaPublisher.publishUpsert(saved);
        return enrichCourseResponse(courseMapper.toResponseFromEntity(saved));
    }

    @Override
    @PreAuthorize("@courseSecurity.canManageCourse(#p0, #p1)")
    @Caching(evict = {
            @CacheEvict(cacheNames = "coursesPublicById", allEntries = true),
            @CacheEvict(cacheNames = "coursesPublicPaged", allEntries = true)
    })
    public CourseResponse unpublishCourse(AuthContext authContext, String id) {
        Course course = findCourse(id);
        requireAdminOrInstructor(authContext, course);
        course.setStatus(CourseStatus.DRAFT);

        Course saved = courseRepository.save(course);
        courseSearchSyncKafkaPublisher.publishUpsert(saved);
        return enrichCourseResponse(courseMapper.toResponseFromEntity(saved));
    }

    private CourseResponse enrichCourseResponse(CourseResponse response) {
        if (response == null) {
            return response;
        }
        response.setDuration(calculateTotalDurationSeconds(response.getLessons()));
        response.setImageUrl(buildPublicCourseImageUrl(response.getId()));
        List<String> normalizedCategoryIds = normalizeCategoryIds(response.getCategoryIds(), response.getCategoryId());
        response.setCategoryIds(normalizedCategoryIds);
        Map<String, CategoryResponse> categoriesById = findCategoriesByIds(new HashSet<>(normalizedCategoryIds));
        List<CategoryResponse> categories = normalizedCategoryIds.stream()
                .map(categoriesById::get)
                .filter(item -> item != null)
                .toList();
        response.setCategories(categories);
        response.setCategoryId(resolvePrimaryCategoryId(normalizedCategoryIds));
        response.setCategory(resolvePrimaryCategory(categories));
        response.setLevel(resolveLevel(response.getLevelId()));
        if (response.getInstructorId() == null || response.getInstructorId().isBlank()) {
            return response;
        }
        InstructorSummaryResponse instructor = instructorProjectionService.findSummaryByInstructorId(response.getInstructorId())
                .orElseGet(() -> buildMissingInstructor(response.getInstructorId()));
        response.setInstructor(instructor);
        return response;
    }

    private List<CourseResponse> enrichCourseResponses(List<CourseResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return responses;
        }
        HashSet<String> categoryIds = responses.stream()
                .flatMap(response -> normalizeCategoryIds(response.getCategoryIds(), response.getCategoryId()).stream())
                .collect(Collectors.toCollection(HashSet::new));
        Map<String, CategoryResponse> categoriesById = findCategoriesByIds(categoryIds);

        HashSet<String> levelIds = responses.stream()
                .map(CourseResponse::getLevelId)
                .filter(this::hasText)
                .map(String::trim)
                .collect(Collectors.toCollection(HashSet::new));
        Map<String, CourseLevelResponse> levelsById = findLevelsByIds(levelIds);

        HashSet<String> instructorIds = responses.stream()
                .map(CourseResponse::getInstructorId)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.toCollection(HashSet::new));
        Map<String, InstructorSummaryResponse> summariesById =
                instructorProjectionService.findSummariesByInstructorIds(instructorIds);

        for (CourseResponse response : responses) {
            response.setDuration(calculateTotalDurationSeconds(response.getLessons()));
            response.setImageUrl(buildPublicCourseImageUrl(response.getId()));
            List<String> normalizedCategoryIds = normalizeCategoryIds(response.getCategoryIds(), response.getCategoryId());
            response.setCategoryIds(normalizedCategoryIds);
            List<CategoryResponse> categories = normalizedCategoryIds.stream()
                    .map(categoriesById::get)
                    .filter(item -> item != null)
                    .toList();
            response.setCategories(categories);
            response.setCategoryId(resolvePrimaryCategoryId(normalizedCategoryIds));
            response.setCategory(resolvePrimaryCategory(categories));
            String normalizedLevelId = normalizeLevelId(response.getLevelId());
            response.setLevel(normalizedLevelId == null ? null : levelsById.get(normalizedLevelId));
            if (response.getInstructorId() == null || response.getInstructorId().isBlank()) {
                continue;
            }
            InstructorSummaryResponse summary = summariesById.get(response.getInstructorId());
            response.setInstructor(summary != null ? summary : buildMissingInstructor(response.getInstructorId()));
        }
        return responses;
    }

    private InstructorSummaryResponse buildMissingInstructor(String instructorId) {
        return InstructorSummaryResponse.builder()
                .instructorId(instructorId)
                .fullName("Unknown Instructor")
                .email(null)
                .profileImageUrl(null)
                .headline(null)
                .status(InstructorStatus.UNKNOWN)
                .build();
    }

    private void validateInstructorForCourseCreation(String instructorId) {
        Optional<InstructorSummaryResponse> projection = instructorProjectionService.findSummaryByInstructorId(instructorId);
        if (projection.isPresent()) {
            if (projection.get().status() == InstructorStatus.DEACTIVATED) {
                throw new BusinessException(ErrorCode.AUTH_LOCKED_OR_INACTIVE);
            }
            return;
        }

        InstructorSummaryResponse reconciled = reconciliationService.reconcileOne(instructorId);
        if (reconciled == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (reconciled.status() == InstructorStatus.DEACTIVATED) {
            throw new BusinessException(ErrorCode.AUTH_LOCKED_OR_INACTIVE);
        }
    }

    private Course findCourse(String id) {
        return courseRepository.findByIdAndDeletedAtIsNull(id).orElseThrow(CourseNotFoundException::new);
    }

    private Course findPublishedCourse(String id) {
        return courseRepository.findByIdAndStatusAndDeletedAtIsNull(id, CourseStatus.PUBLISHED)
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
        if (!hasText(course.getLevelId())) {
            return false;
        }
        if (normalizeCategoryIds(course.getCategoryIds(), legacyCategoryId(course)).isEmpty()) {
            return false;
        }
        return course.getLessons() != null && !course.getLessons().isEmpty();
    }

    private List<String> normalizeAndValidateCategoryIds(List<String> categoryIds) {
        List<String> normalizedCategoryIds = normalizeCategoryIds(categoryIds, null);
        if (normalizedCategoryIds.isEmpty()) {
            throw new CourseCategoryNotFoundException();
        }
        Set<String> existingCategoryIds = categoryRepository.findAllById(normalizedCategoryIds).stream()
                .map(Category::getId)
                .collect(Collectors.toSet());
        if (existingCategoryIds.size() != normalizedCategoryIds.size()) {
            throw new CourseCategoryNotFoundException();
        }
        return normalizedCategoryIds;
    }

    private String normalizeAndValidateLevelId(String levelId) {
        String normalizedLevelId = normalizeLevelId(levelId);
        if (normalizedLevelId == null || !courseLevelRepository.existsById(normalizedLevelId)) {
            throw new CourseLevelNotFoundException();
        }
        return normalizedLevelId;
    }

    private String normalizeLevelId(String levelId) {
        if (!hasText(levelId)) {
            return null;
        }
        return levelId.trim();
    }

    private Map<String, CategoryResponse> findCategoriesByIds(Set<String> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return Map.of();
        }
        List<String> normalizedCategoryIds = categoryIds.stream()
                .filter(this::hasText)
                .map(String::trim)
                .sorted()
                .toList();
        return categoryRepository.findAllById(normalizedCategoryIds).stream()
                .collect(Collectors.toMap(Category::getId, this::toCategoryResponse));
    }

    private Map<String, CourseLevelResponse> findLevelsByIds(Set<String> levelIds) {
        if (levelIds == null || levelIds.isEmpty()) {
            return Map.of();
        }
        return courseLevelRepository.findAllById(levelIds).stream()
                .collect(Collectors.toMap(CourseLevel::getId, this::toCourseLevelResponse));
    }

    private CourseLevelResponse resolveLevel(String levelId) {
        String normalizedLevelId = normalizeLevelId(levelId);
        if (normalizedLevelId == null) {
            return null;
        }
        return courseLevelRepository.findById(normalizedLevelId)
                .map(this::toCourseLevelResponse)
                .orElse(null);
    }

    private CategoryResponse toCategoryResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .categoryName(category.getCategoryName())
                .build();
    }

    private CourseLevelResponse toCourseLevelResponse(CourseLevel courseLevel) {
        return CourseLevelResponse.builder()
                .id(courseLevel.getId())
                .levelName(courseLevel.getLevelName())
                .build();
    }

    @SuppressWarnings("deprecation")
    private List<String> normalizeCategoryIds(List<String> categoryIds, String fallbackCategoryId) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (categoryIds != null) {
            categoryIds.stream()
                    .filter(this::hasText)
                    .map(String::trim)
                    .forEach(normalized::add);
        }
        if (normalized.isEmpty() && hasText(fallbackCategoryId)) {
            normalized.add(fallbackCategoryId.trim());
        }
        return List.copyOf(normalized);
    }

    private String resolvePrimaryCategoryId(List<String> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return null;
        }
        return categoryIds.get(0);
    }

    @SuppressWarnings("deprecation")
    private String legacyCategoryId(Course course) {
        if (course == null) {
            return null;
        }
        return course.getCategoryId();
    }

    private CategoryResponse resolvePrimaryCategory(List<CategoryResponse> categories) {
        if (categories == null || categories.isEmpty()) {
            return null;
        }
        return categories.get(0);
    }

    private void requireAdmin(AuthContext authContext) {
        if (authContext == null || authContext.role() != UserRole.ADMIN) {
            throw new AccessDeniedException("Admin role required");
        }
    }

    private void requireAuthenticatedRole(AuthContext authContext) {
        if (authContext == null || authContext.role() == UserRole.UNKNOWN) {
            throw new AccessDeniedException("Role required");
        }
        if (authContext.userId() == null || authContext.userId().isBlank()) {
            throw new AccessDeniedException("Authenticated user required");
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

    private Integer calculateTotalDurationSeconds(List<LessonResponse> lessons) {
        if (lessons == null || lessons.isEmpty()) {
            return 0;
        }
        long total = lessons.stream()
                .map(LessonResponse::getDuration)
                .filter(duration -> duration != null && duration > 0)
                .mapToLong(Integer::longValue)
                .sum();
        if (total <= 0L) {
            return 0;
        }
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    private String buildPublicCourseImageUrl(String courseId) {
        if (courseId == null || courseId.isBlank()) {
            return null;
        }
        return PUBLIC_COURSE_IMAGE_PATH_TEMPLATE.formatted(courseId);
    }
}
