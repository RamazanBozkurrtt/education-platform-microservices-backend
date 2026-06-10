package com.edubase.course.service.concretes;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
import com.drew.imaging.mp4.Mp4MetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.mp4.Mp4Directory;
import com.edubase.course.dto.response.MediaDurationBackfillResponse;
import com.edubase.course.dto.response.VideoPlaybackUrlResponse;
import com.edubase.course.entity.Course;
import com.edubase.course.entity.CourseStatus;
import com.edubase.course.entity.Lesson;
import com.edubase.course.exception.CourseNotFoundException;
import com.edubase.course.exception.LessonNotFoundException;
import com.edubase.course.messaging.CourseSearchSyncKafkaPublisher;
import com.edubase.course.repository.CourseRepository;
import com.edubase.course.security.AuthContext;
import com.edubase.course.security.UserRole;
import com.edubase.course.service.abstracts.CourseMediaService;
import com.edubase.course.service.abstracts.VideoDurationService;
import com.edubase.course.storage.MinioObjectResource;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.access.AccessDeniedException;

@Service
@Slf4j
@RequiredArgsConstructor
public class CourseMediaServiceImpl implements CourseMediaService {

    private static final String VIDEO_EXTENSION = "mp4";
    private static final String VIDEO_CONTENT_TYPE = "video/mp4";
    private static final String DEFAULT_COURSE_IMAGE_FILE = "default-course.png";
    private static final String COURSE_VIDEO_OBJECT_KEY_TEMPLATE = "courses/%s/lessons/%s.%s";
    private static final String LESSON_VIDEO_PUBLIC_PATH_TEMPLATE = "/api/v1/courses/%s/lessons/%s/video/stream";
    private static final String LESSON_VIDEO_PUBLIC_SIGNED_PATH_TEMPLATE = "/api/v1/courses/public/%s/lessons/%s/video/stream";
    private static final long MIN_PLAYBACK_URL_TTL_SECONDS = 60L;
    private static final int MAX_REASONABLE_VIDEO_DURATION_SECONDS = 24 * 60 * 60;
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final Pattern DURATION_UNITS_PATTERN = Pattern.compile(
            "(\\d+(?:[\\.,]\\d+)?)\\s*(milliseconds?|msecs?|msec|ms|hours?|hrs?|hr|h|minutes?|mins?|min|m(?!s)|seconds?|secs?|sec|s)"
    );
    private static final List<String> SUPPORTED_IMAGE_EXTENSIONS = List.of("png", "jpg", "jpeg", "webp", "svg");
    private static final Set<String> SUPPORTED_IMAGE_CONTENT_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/webp",
            "image/svg+xml"
    );

    private final CourseRepository courseRepository;
    private final MinioClient minioClient;
    private final CourseSearchSyncKafkaPublisher courseSearchSyncKafkaPublisher;
    private final VideoDurationService videoDurationService;

    @Value("${course.media.base-path:videos}")
    private String basePath;

    @Value("${course.media.image-base-path:images/courses}")
    private String imageBasePath;

    @Value("${course.media.minio.bucket:edubase-media}")
    private String mediaBucket;

    @Value("${course.media.minio.auto-create-bucket:true}")
    private boolean autoCreateBucket;

    @Value("${course.media.playback-signing-key:${jwt.secret}}")
    private String playbackSigningKey;

    @Value("${course.media.playback-url-ttl-seconds:900}")
    private long playbackUrlTtlSeconds;

    @Override
    public ResponseEntity<Resource> getLessonVideo(AuthContext authContext, String courseId, String lessonId, HttpHeaders headers) {
        Course course = resolveCourseForAccess(authContext, courseId);
        Lesson lesson = resolveLesson(course, lessonId);
        return streamLessonVideo(course.getId(), lesson.getId(), headers);
    }

    @Override
    public ResponseEntity<Resource> getPublicLessonVideoBySignature(String courseId, String lessonId, String userId, long expiresAt, String signature, HttpHeaders headers) {
        validatePlaybackSignature(courseId, lessonId, userId, expiresAt, signature);

        Course course = courseRepository.findByIdAndDeletedAtIsNull(courseId).orElseThrow(CourseNotFoundException::new);
        Lesson lesson = resolveLesson(course, lessonId);
        return streamLessonVideo(course.getId(), lesson.getId(), headers);
    }

    @Override
    public VideoPlaybackUrlResponse createLessonVideoPlaybackUrl(AuthContext authContext, String courseId, String lessonId) {
        Course course = resolveCourseForAccess(authContext, courseId);
        Lesson lesson = resolveLesson(course, lessonId);
        long expiresAt = Instant.now().getEpochSecond() + effectivePlaybackUrlTtlSeconds();
        String signature = signPlaybackUrl(course.getId(), lesson.getId(), authContext.userId(), expiresAt);

        return VideoPlaybackUrlResponse.builder()
                .url(buildSignedPlaybackUrl(course.getId(), lesson.getId(), authContext.userId(), expiresAt, signature))
                .expiresAt(expiresAt)
                .build();
    }

    @Override
    public ResponseEntity<Resource> getPublicCourseImage(String courseId) {
        Course course = courseRepository.findByIdAndStatusAndDeletedAtIsNull(courseId, CourseStatus.PUBLISHED)
                .orElseThrow(CourseNotFoundException::new);
        return buildImageResponse(course);
    }

    @Override
    public ResponseEntity<Resource> getCourseImage(AuthContext authContext, String courseId) {
        Course course = resolveCourseForAccess(authContext, courseId);
        return buildImageResponse(course);
    }

    @Override
    public void uploadCourseImage(AuthContext authContext, String courseId, MultipartFile file) {
        Course course = resolveCourseForManagement(authContext, courseId);
        validateImageFile(file);
        String imageExtension = resolveImageExtension(file);

        ensureBucketForWrites();
        for (String extension : SUPPORTED_IMAGE_EXTENSIONS) {
            if (!extension.equals(imageExtension)) {
                removeObjectIfExists(buildCourseImageObjectKey(course.getId(), extension));
            }
        }
        putObject(buildCourseImageObjectKey(course.getId(), imageExtension), file, normalizedImageContentType(file, imageExtension));
    }

    @Override
    public void deleteCourseImage(AuthContext authContext, String courseId) {
        Course course = resolveCourseForManagement(authContext, courseId);
        ensureBucketForWrites();
        for (String extension : SUPPORTED_IMAGE_EXTENSIONS) {
            removeObjectIfExists(buildCourseImageObjectKey(course.getId(), extension));
        }
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = "coursesPublicById", allEntries = true),
            @CacheEvict(cacheNames = "coursesPublicPaged", allEntries = true)
    })
    public void uploadLessonVideo(AuthContext authContext, String courseId, String lessonId, MultipartFile file) {
        Course course = resolveCourseForManagement(authContext, courseId);
        Lesson lesson = resolveLesson(course, lessonId);
        validateVideoFile(file);
        Integer durationSeconds = resolveVideoDurationSeconds(file);

        ensureBucketForWrites();
        putObject(buildLessonVideoObjectKey(course.getId(), lesson.getId()), file, VIDEO_CONTENT_TYPE);
        lesson.setVideoUrl(buildLessonVideoPublicPath(course.getId(), lesson.getId()));
        lesson.setVideoUpdatedAt(Instant.now());
        if (durationSeconds != null && durationSeconds > 0) {
            lesson.setDuration(durationSeconds);
            log.info("VIDEO_DURATION_COMPUTED | courseId={} lessonId={} durationSeconds={}",
                    course.getId(), lesson.getId(), durationSeconds);
        } else {
            log.warn("VIDEO_DURATION_UNAVAILABLE | courseId={} lessonId={} existingDurationSeconds={}",
                    course.getId(), lesson.getId(), lesson.getDuration());
        }
        course.setUpdatedAt(Instant.now());
        Course saved = courseRepository.save(course);
        courseSearchSyncKafkaPublisher.publishUpsert(saved);
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = "coursesPublicById", allEntries = true),
            @CacheEvict(cacheNames = "coursesPublicPaged", allEntries = true)
    })
    public void deleteLessonVideo(AuthContext authContext, String courseId, String lessonId) {
        Course course = resolveCourseForManagement(authContext, courseId);
        Lesson lesson = resolveLesson(course, lessonId);

        deleteLessonVideoAsset(course.getId(), lesson.getId());
        lesson.setVideoUrl(null);
        lesson.setVideoUpdatedAt(null);
        lesson.setDuration(null);
        course.setUpdatedAt(Instant.now());
        Course saved = courseRepository.save(course);
        courseSearchSyncKafkaPublisher.publishUpsert(saved);
    }

    @Override
    public void deleteCourseMediaAssets(Course course) {
        if (course == null || course.getId() == null || course.getId().isBlank()) {
            return;
        }
        for (String extension : SUPPORTED_IMAGE_EXTENSIONS) {
            removeObjectIfExists(buildCourseImageObjectKey(course.getId(), extension));
        }
        List<Lesson> lessons = course.getLessons();
        if (lessons == null || lessons.isEmpty()) {
            return;
        }
        for (Lesson lesson : lessons) {
            if (lesson != null && lesson.getId() != null && !lesson.getId().isBlank()) {
                removeObjectIfExists(buildLessonVideoObjectKey(course.getId(), lesson.getId()));
            }
        }
    }

    @Override
    public void deleteLessonVideoAsset(String courseId, String lessonId) {
        if (courseId == null || courseId.isBlank() || lessonId == null || lessonId.isBlank()) {
            return;
        }
        removeObjectIfExists(buildLessonVideoObjectKey(courseId, lessonId));
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = "coursesPublicById", allEntries = true),
            @CacheEvict(cacheNames = "coursesPublicPaged", allEntries = true)
    })
    public MediaDurationBackfillResponse backfillLessonDurations(AuthContext authContext) {
        requireAdmin(authContext);
        BackfillCounters counters = new BackfillCounters();

        List<Course> courses = courseRepository.findAll();
        for (Course course : courses) {
            if (course == null || course.getDeletedAt() != null || !hasText(course.getId())) {
                continue;
            }

            List<Lesson> lessons = course.getLessons();
            if (lessons == null || lessons.isEmpty()) {
                continue;
            }

            boolean courseChanged = false;
            for (Lesson lesson : lessons) {
                if (lesson == null || !hasText(lesson.getId()) || !hasText(lesson.getVideoUrl())) {
                    counters.skippedCount++;
                    continue;
                }

                counters.scannedCount++;
                Integer existing = lesson.getDuration();
                if (isReasonableVideoDuration(existing) && existing > 1) {
                    counters.skippedCount++;
                    continue;
                }

                Integer recalculatedDuration = resolveDurationFromStoredVideo(course.getId(), lesson.getId());
                if (recalculatedDuration != null && recalculatedDuration > 0) {
                    if (!Objects.equals(existing, recalculatedDuration)) {
                        lesson.setDuration(recalculatedDuration);
                        courseChanged = true;
                        counters.updatedCount++;
                        log.info("VIDEO_DURATION_BACKFILL_UPDATED | courseId={} lessonId={} durationSeconds={}",
                                course.getId(), lesson.getId(), recalculatedDuration);
                    }
                    continue;
                }

                counters.failedCount++;
                if (!Objects.equals(existing, 0)) {
                    lesson.setDuration(0);
                    courseChanged = true;
                    counters.updatedCount++;
                }
                log.warn("VIDEO_DURATION_BACKFILL_FAILED | courseId={} lessonId={}", course.getId(), lesson.getId());
            }

            if (courseChanged) {
                course.setUpdatedAt(Instant.now());
                Course saved = courseRepository.save(course);
                courseSearchSyncKafkaPublisher.publishUpsert(saved);
            }
        }

        log.info("VIDEO_DURATION_BACKFILL_SUMMARY | scannedCount={} updatedCount={} failedCount={} skippedCount={}",
                counters.scannedCount, counters.updatedCount, counters.failedCount, counters.skippedCount);

        return MediaDurationBackfillResponse.builder()
                .scannedCount(counters.scannedCount)
                .updatedCount(counters.updatedCount)
                .failedCount(counters.failedCount)
                .skippedCount(counters.skippedCount)
                .build();
    }

    public Integer resolveVideoDurationSeconds(MultipartFile file) {
        Path tempFile = null;
        try {
            tempFile = copyToTempVideoFile(file);
            Integer ffprobeDuration = resolveDurationWithFfprobe(tempFile, file.getOriginalFilename());
            if (isReasonableVideoDuration(ffprobeDuration)) {
                return ffprobeDuration;
            }
            if (ffprobeDuration != null && ffprobeDuration > 0) {
                log.warn("VIDEO_DURATION_UNREASONABLE_FFPROBE_VALUE | durationSeconds={} file={}",
                        ffprobeDuration, file.getOriginalFilename());
            }

            Integer movieHeaderDuration = resolveDurationFromMp4MovieHeader(tempFile);
            if (isReasonableVideoDuration(movieHeaderDuration)) {
                return movieHeaderDuration;
            }
            if (movieHeaderDuration != null && movieHeaderDuration > 0) {
                log.warn("VIDEO_DURATION_UNREASONABLE_MVHD_VALUE | durationSeconds={} file={}",
                        movieHeaderDuration, file.getOriginalFilename());
            }
        } finally {
            deleteTempFileQuietly(tempFile);
        }

        try (var inputStream = file.getInputStream()) {
            Metadata metadata = Mp4MetadataReader.readMetadata(inputStream);
            Integer durationSeconds = extractDurationSeconds(metadata);
            if (isReasonableVideoDuration(durationSeconds) && durationSeconds > 1) {
                return durationSeconds;
            }
            if (durationSeconds != null && durationSeconds == 1) {
                log.warn("VIDEO_DURATION_SUSPICIOUS_ONE_SECOND_FALLBACK | file={}", file.getOriginalFilename());
            }
            if (durationSeconds != null && durationSeconds > MAX_REASONABLE_VIDEO_DURATION_SECONDS) {
                log.warn("VIDEO_DURATION_UNREASONABLE_METADATA_VALUE | durationSeconds={} file={}",
                        durationSeconds, file.getOriginalFilename());
            }
            return null;
        } catch (Exception ex) {
            log.warn("VIDEO_DURATION_PARSE_ERROR | msg={}", ex.getMessage());
            return null;
        }
    }

    private Path copyToTempVideoFile(MultipartFile file) {
        try {
            Path tempFile = Files.createTempFile("course-video-duration-", ".mp4");
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return tempFile;
        } catch (Exception ex) {
            log.warn("VIDEO_DURATION_TEMP_FILE_ERROR | file={} msg={}", file.getOriginalFilename(), ex.getMessage());
            return null;
        }
    }

    private Integer resolveDurationWithFfprobe(Path tempFile, String originalFilename) {
        if (tempFile == null) {
            return null;
        }
        try {
            return videoDurationService.extractDurationSeconds(tempFile)
                    .stream()
                    .boxed()
                    .findFirst()
                    .orElse(null);
        } catch (Exception ex) {
            log.warn("VIDEO_DURATION_FFPROBE_PIPELINE_ERROR | file={} msg={}", originalFilename, ex.getMessage());
            return null;
        }
    }

    private Integer resolveDurationFromStoredVideo(String courseId, String lessonId) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("course-video-backfill-", ".mp4");
            try (InputStream inputStream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(mediaBucket)
                            .object(buildLessonVideoObjectKey(courseId, lessonId))
                            .build())) {
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return videoDurationService.extractDurationSeconds(tempFile)
                    .stream()
                    .boxed()
                    .findFirst()
                    .orElse(null);
        } catch (ErrorResponseException ex) {
            if (!isObjectMissing(ex)) {
                log.warn("VIDEO_DURATION_BACKFILL_OBJECT_ERROR | courseId={} lessonId={} msg={}",
                        courseId, lessonId, ex.getMessage());
            }
            return null;
        } catch (Exception ex) {
            log.warn("VIDEO_DURATION_BACKFILL_ERROR | courseId={} lessonId={} msg={}",
                    courseId, lessonId, ex.getMessage());
            return null;
        } finally {
            deleteTempFileQuietly(tempFile);
        }
    }

    private Course resolveCourseForAccess(AuthContext authContext, String courseId) {
        if (authContext == null || authContext.role() == null) {
            throw new CourseNotFoundException();
        }

        UserRole role = authContext.role();
        if (role == UserRole.ADMIN) {
            return courseRepository.findByIdAndDeletedAtIsNull(courseId).orElseThrow(CourseNotFoundException::new);
        }

        if (role == UserRole.INSTRUCTOR) {
            boolean ownsCourse = courseRepository.existsByIdAndInstructorIdAndDeletedAtIsNull(courseId, authContext.userId());
            if (ownsCourse) {
                return courseRepository.findByIdAndDeletedAtIsNull(courseId).orElseThrow(CourseNotFoundException::new);
            }
            // Instructors may also consume published courses as learners.
            return courseRepository.findByIdAndStatusAndDeletedAtIsNull(courseId, CourseStatus.PUBLISHED)
                    .orElseThrow(CourseNotFoundException::new);
        }

        return courseRepository.findByIdAndStatusAndDeletedAtIsNull(courseId, CourseStatus.PUBLISHED)
                .orElseThrow(CourseNotFoundException::new);
    }

    private Course resolveCourseForManagement(AuthContext authContext, String courseId) {
        if (authContext == null || authContext.role() == null) {
            throw new CourseNotFoundException();
        }

        if (authContext.role() == UserRole.ADMIN) {
            return courseRepository.findByIdAndDeletedAtIsNull(courseId).orElseThrow(CourseNotFoundException::new);
        }

        if (authContext.role() != UserRole.INSTRUCTOR) {
            throw new CourseNotFoundException();
        }

        boolean ownsCourse = courseRepository.existsByIdAndInstructorIdAndDeletedAtIsNull(courseId, authContext.userId());
        if (!ownsCourse) {
            throw new CourseNotFoundException();
        }
        return courseRepository.findByIdAndDeletedAtIsNull(courseId).orElseThrow(CourseNotFoundException::new);
    }

    private Lesson resolveLesson(Course course, String lessonId) {
        if (course.getLessons() == null || course.getLessons().isEmpty()) {
            throw new LessonNotFoundException();
        }

        return course.getLessons().stream()
                .filter(item -> lessonId.equals(item.getId()))
                .findFirst()
                .orElseThrow(LessonNotFoundException::new);
    }

    private ResponseEntity<Resource> buildImageResponse(Course course) {
        Resource resource = resolveCourseImageResource(course.getId());
        MediaType mediaType = MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(mediaType).body(resource);
    }

    private Resource resolveCourseImageResource(String courseId) {
        for (String extension : SUPPORTED_IMAGE_EXTENSIONS) {
            StoredObject candidate = findStoredObject(buildCourseImageObjectKey(courseId, extension));
            if (candidate != null) {
                return candidate.resource();
            }
        }

        StoredObject fallbackObject = findStoredObject(buildDefaultCourseImageObjectKey());
        if (fallbackObject != null) {
            return fallbackObject.resource();
        }

        String classpathBasePath = normalizePathPrefix(imageBasePath);
        String defaultImagePath = classpathBasePath.isBlank()
                ? DEFAULT_COURSE_IMAGE_FILE
                : "%s/%s".formatted(classpathBasePath, DEFAULT_COURSE_IMAGE_FILE);
        Resource fallback = new ClassPathResource(defaultImagePath);
        if (fallback.exists() && fallback.isReadable()) {
            return fallback;
        }
        throw new CourseNotFoundException();
    }

    private StoredObject getStoredObjectOrThrow(String objectKey, Supplier<? extends RuntimeException> notFoundException) {
        StatObjectResponse stat = statObjectOrNull(objectKey);
        if (stat == null) {
            throw notFoundException.get();
        }
        Resource resource = new MinioObjectResource(minioClient, mediaBucket, objectKey, stat.size());
        return new StoredObject(resource, stat.size(), objectKey);
    }

    private StoredObject findStoredObject(String objectKey) {
        StatObjectResponse stat = statObjectOrNull(objectKey);
        if (stat == null) {
            return null;
        }
        return toStoredObject(objectKey, stat);
    }

    private StoredObject toStoredObject(String objectKey, StatObjectResponse statObjectResponse) {
        Resource resource = new MinioObjectResource(minioClient, mediaBucket, objectKey, statObjectResponse.size());
        return new StoredObject(resource, statObjectResponse.size(), objectKey);
    }

    private StatObjectResponse statObjectOrNull(String objectKey) {
        try {
            return minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(mediaBucket)
                            .object(objectKey)
                            .build()
            );
        } catch (ErrorResponseException ex) {
            if (isObjectMissing(ex)) {
                return null;
            }
            throw storageException(ex);
        } catch (Exception ex) {
            throw storageException(ex);
        }
    }

    private void ensureBucketForWrites() {
        if (!autoCreateBucket) {
            return;
        }

        try {
            boolean bucketExists = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(mediaBucket)
                            .build()
            );
            if (!bucketExists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(mediaBucket)
                                .build()
                );
            }
        } catch (IOException ex) {
            throw storageException(ex);
        } catch (Exception ex) {
            throw storageException(ex);
        }
    }

    private void putObject(String objectKey, MultipartFile file, String contentType) {
        try (var inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(mediaBucket)
                            .object(objectKey)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(contentType)
                            .build()
            );
        } catch (Exception ex) {
            throw storageException(ex);
        }
    }

    private void removeObjectIfExists(String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(mediaBucket)
                            .object(objectKey)
                            .build()
            );
        } catch (ErrorResponseException ex) {
            if (isObjectMissing(ex)) {
                return;
            }
            throw storageException(ex);
        } catch (Exception ex) {
            throw storageException(ex);
        }
    }

    private String buildLessonVideoObjectKey(String courseId, String lessonId) {
        String videoBasePath = normalizePathPrefix(basePath);
        String lessonPath = COURSE_VIDEO_OBJECT_KEY_TEMPLATE.formatted(courseId, lessonId, VIDEO_EXTENSION);
        if (videoBasePath.isBlank()) {
            return lessonPath;
        }
        return "%s/%s".formatted(videoBasePath, lessonPath);
    }

    private String buildLessonVideoPublicPath(String courseId, String lessonId) {
        return LESSON_VIDEO_PUBLIC_PATH_TEMPLATE.formatted(courseId, lessonId);
    }

    private String buildCourseImageObjectKey(String courseId, String extension) {
        String imagePath = normalizePathPrefix(imageBasePath);
        String filename = "%s.%s".formatted(courseId, extension);
        if (imagePath.isBlank()) {
            return filename;
        }
        return "%s/%s".formatted(imagePath, filename);
    }

    private String buildDefaultCourseImageObjectKey() {
        String imagePath = normalizePathPrefix(imageBasePath);
        if (imagePath.isBlank()) {
            return DEFAULT_COURSE_IMAGE_FILE;
        }
        return "%s/%s".formatted(imagePath, DEFAULT_COURSE_IMAGE_FILE);
    }

    private void requireAdmin(AuthContext authContext) {
        if (authContext == null || authContext.role() != UserRole.ADMIN) {
            throw new AccessDeniedException("Admin role required");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String normalizePathPrefix(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = value.replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw validationException();
        }

        String extension = resolveImageExtension(file);
        if (extension == null) {
            throw validationException();
        }

        String contentType = normalizedContentType(file.getContentType());
        if (!contentType.isBlank() && !SUPPORTED_IMAGE_CONTENT_TYPES.contains(contentType)) {
            throw validationException();
        }
    }

    private String resolveImageExtension(MultipartFile file) {
        String extensionFromName = extensionOf(file.getOriginalFilename());
        if (SUPPORTED_IMAGE_EXTENSIONS.contains(extensionFromName)) {
            return extensionFromName;
        }

        String contentType = normalizedContentType(file.getContentType());
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            case "image/svg+xml" -> "svg";
            default -> null;
        };
    }

    private String normalizedImageContentType(MultipartFile file, String extension) {
        String contentType = normalizedContentType(file.getContentType());
        if (SUPPORTED_IMAGE_CONTENT_TYPES.contains(contentType)) {
            return contentType;
        }
        return switch (extension) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            default -> MediaType.APPLICATION_OCTET_STREAM_VALUE;
        };
    }

    private void validateVideoFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw validationException();
        }

        String contentType = normalizedContentType(file.getContentType());
        String extension = extensionOf(file.getOriginalFilename());
        if (!VIDEO_CONTENT_TYPE.equals(contentType) && !VIDEO_EXTENSION.equals(extension)) {
            throw validationException();
        }

        if (!hasMp4Signature(file)) {
            throw validationException();
        }
    }

    private boolean hasMp4Signature(MultipartFile file) {
        try (var inputStream = file.getInputStream()) {
            byte[] header = inputStream.readNBytes(12);
            if (header.length < 8) {
                return false;
            }
            return header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p';
        } catch (IOException ex) {
            return false;
        }
    }

    private Integer extractDurationSeconds(Metadata metadata) {
        Mp4Directory mp4Directory = metadata.getFirstDirectoryOfType(Mp4Directory.class);
        Integer best = null;
        if (mp4Directory != null) {
            String durationDescription = mp4Directory.getDescription(Mp4Directory.TAG_DURATION);
            if (durationDescription != null) {
                Integer parsedDuration = parseDurationDescription(durationDescription);
                if (parsedDuration != null && parsedDuration > 0) {
                    best = parsedDuration;
                }
            }
            Long durationSeconds = mp4Directory.getLongObject(Mp4Directory.TAG_DURATION_SECONDS);
            if (durationSeconds != null && durationSeconds > 0 && durationSeconds <= Integer.MAX_VALUE) {
                int asInt = durationSeconds.intValue();
                if (best == null || asInt > best) {
                    best = asInt;
                }
            }
        }

        Integer discovered = extractBestDurationFromAllMetadataTags(metadata);
        if (discovered != null && discovered > 0 && (best == null || discovered > best)) {
            best = discovered;
        }

        return best;
    }

    private Integer resolveDurationFromMp4MovieHeader(Path videoPath) {
        if (videoPath == null || !Files.exists(videoPath)) {
            return null;
        }
        try (RandomAccessFile file = new RandomAccessFile(videoPath.toFile(), "r")) {
            return findMovieHeaderDuration(file, 0L, file.length(), 0);
        } catch (Exception ex) {
            log.warn("VIDEO_DURATION_MVHD_PARSE_ERROR | file={} msg={}", videoPath.getFileName(), ex.getMessage());
            return null;
        }
    }

    private Integer findMovieHeaderDuration(RandomAccessFile file, long start, long end, int depth) throws IOException {
        if (depth > 3 || start < 0 || end <= start) {
            return null;
        }

        long position = start;
        while (position + 8L <= end) {
            file.seek(position);
            long atomSize = readUnsignedInt(file);
            String atomType = readAtomType(file);
            long headerSize = 8L;

            if (atomSize == 1L) {
                if (position + 16L > end) {
                    return null;
                }
                atomSize = file.readLong();
                headerSize = 16L;
            } else if (atomSize == 0L) {
                atomSize = end - position;
            }

            if (atomSize < headerSize || position + atomSize > end) {
                return null;
            }

            long contentStart = position + headerSize;
            long contentEnd = position + atomSize;
            if ("mvhd".equals(atomType)) {
                return readMovieHeaderDurationSeconds(file, contentStart, contentEnd);
            }
            if ("moov".equals(atomType)) {
                Integer duration = findMovieHeaderDuration(file, contentStart, contentEnd, depth + 1);
                if (duration != null) {
                    return duration;
                }
            }

            position += atomSize;
        }

        return null;
    }

    private Integer readMovieHeaderDurationSeconds(RandomAccessFile file, long contentStart, long contentEnd) throws IOException {
        if (contentStart + 20L > contentEnd) {
            return null;
        }

        file.seek(contentStart);
        int version = file.readUnsignedByte();
        file.skipBytes(3);

        long timescale;
        double durationUnits;
        if (version == 1) {
            if (contentStart + 32L > contentEnd) {
                return null;
            }
            file.skipBytes(16);
            timescale = readUnsignedInt(file);
            durationUnits = unsignedLongToDouble(file.readLong());
        } else {
            file.skipBytes(8);
            timescale = readUnsignedInt(file);
            durationUnits = readUnsignedInt(file);
        }

        if (timescale <= 0L || durationUnits <= 0d) {
            return null;
        }
        double seconds = durationUnits / timescale;
        if (!Double.isFinite(seconds) || seconds <= 0d || seconds > Integer.MAX_VALUE) {
            return null;
        }
        return (int) Math.ceil(seconds);
    }

    private long readUnsignedInt(RandomAccessFile file) throws IOException {
        return Integer.toUnsignedLong(file.readInt());
    }

    private String readAtomType(RandomAccessFile file) throws IOException {
        byte[] type = new byte[4];
        file.readFully(type);
        return new String(type, StandardCharsets.ISO_8859_1);
    }

    private double unsignedLongToDouble(long value) {
        if (value >= 0L) {
            return (double) value;
        }
        return (double) (value & Long.MAX_VALUE) + Math.pow(2.0d, 63.0d);
    }

    private Integer parseDurationDescription(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);

        // ISO-8601 duration support (e.g. PT3M11.5S)
        if (normalized.startsWith("p")) {
            try {
                long seconds = Duration.parse(normalized.toUpperCase(Locale.ROOT)).getSeconds();
                if (seconds > 0 && seconds <= Integer.MAX_VALUE) {
                    return (int) seconds;
                }
            } catch (Exception ignore) {
                // Fall through to permissive parsing below.
            }
        }

        // Human-readable unit support (e.g. "3 min 11 sec", "191.2 seconds")
        Integer unitDuration = parseDurationWithUnits(normalized);
        if (unitDuration != null && unitDuration > 0) {
            return unitDuration;
        }

        // HH:MM:SS and MM:SS support with optional fractional seconds on final segment.
        Integer colonDuration = parseColonSeparatedDuration(normalized);
        if (colonDuration != null && colonDuration > 0) {
            return colonDuration;
        }

        // Plain numeric seconds support (e.g. "191", "191.2")
        String numericToken = stripTrailingSecondUnits(normalized);
        try {
            double numericSeconds = parseFlexibleDouble(numericToken);
            if (numericSeconds > 0 && numericSeconds <= Integer.MAX_VALUE) {
                return (int) Math.round(numericSeconds);
            }
        } catch (NumberFormatException ignore) {
            return null;
        }

        return null;
    }

    private Integer parseDurationWithUnits(String normalized) {
        Matcher matcher = DURATION_UNITS_PATTERN.matcher(normalized);
        double totalSeconds = 0d;
        boolean foundAny = false;
        while (matcher.find()) {
            double valuePart = parseFlexibleDouble(matcher.group(1));
            String unit = matcher.group(2).toLowerCase(Locale.ROOT);
            if (valuePart < 0) {
                return null;
            }
            foundAny = true;
            if (isMillisecondUnit(unit)) {
                totalSeconds += valuePart / 1000d;
            } else if (unit.startsWith("h")) {
                totalSeconds += valuePart * 3600d;
            } else if (isMinuteUnit(unit)) {
                totalSeconds += valuePart * 60d;
            } else if (isSecondUnit(unit)) {
                totalSeconds += valuePart;
            } else {
                return null;
            }
        }

        if (!foundAny || totalSeconds <= 0d || totalSeconds > Integer.MAX_VALUE) {
            return null;
        }
        return (int) Math.round(totalSeconds);
    }

    private Integer parseColonSeparatedDuration(String normalized) {
        String[] parts = normalized.split(":");
        if (parts.length < 2 || parts.length > 3) {
            return null;
        }

        try {
            double seconds = 0d;
            for (int i = 0; i < parts.length; i++) {
                String trimmedPart = parts[i].trim();
                double valuePart = (i == parts.length - 1)
                        ? parseFlexibleDouble(trimmedPart)
                        : Long.parseLong(trimmedPart);
                if (valuePart < 0) {
                    return null;
                }
                seconds = (seconds * 60) + valuePart;
            }
            if (seconds > 0 && seconds <= Integer.MAX_VALUE) {
                return (int) Math.round(seconds);
            }
            return null;
        } catch (NumberFormatException ignore) {
            return null;
        }
    }

    private String stripTrailingSecondUnits(String normalized) {
        if (normalized.endsWith(" seconds")) {
            return normalized.substring(0, normalized.length() - " seconds".length()).trim();
        }
        if (normalized.endsWith(" second")) {
            return normalized.substring(0, normalized.length() - " second".length()).trim();
        }
        if (normalized.endsWith(" secs")) {
            return normalized.substring(0, normalized.length() - " secs".length()).trim();
        }
        if (normalized.endsWith(" sec")) {
            return normalized.substring(0, normalized.length() - " sec".length()).trim();
        }
        if (normalized.endsWith(" s")) {
            return normalized.substring(0, normalized.length() - " s".length()).trim();
        }
        return normalized;
    }

    private double parseFlexibleDouble(String value) {
        String normalized = value.trim();
        if (normalized.indexOf(',') >= 0 && normalized.indexOf('.') < 0) {
            normalized = normalized.replace(',', '.');
        }
        return Double.parseDouble(normalized);
    }

    private boolean isReasonableVideoDuration(Integer durationSeconds) {
        return durationSeconds != null && durationSeconds > 0 && durationSeconds <= MAX_REASONABLE_VIDEO_DURATION_SECONDS;
    }

    private boolean isMillisecondUnit(String unit) {
        return "ms".equals(unit)
                || "msec".equals(unit)
                || "msecs".equals(unit)
                || unit.startsWith("millisecond");
    }

    private boolean isMinuteUnit(String unit) {
        return "m".equals(unit)
                || "min".equals(unit)
                || "mins".equals(unit)
                || unit.startsWith("minute");
    }

    private boolean isSecondUnit(String unit) {
        return "s".equals(unit)
                || "sec".equals(unit)
                || "secs".equals(unit)
                || unit.startsWith("second");
    }

    private Integer extractBestDurationFromAllMetadataTags(Metadata metadata) {
        if (metadata == null) {
            return null;
        }
        Integer best = null;
        for (Directory directory : metadata.getDirectories()) {
            for (var tag : directory.getTags()) {
                String tagName = tag.getTagName();
                if (tagName == null || !tagName.toLowerCase(Locale.ROOT).contains("duration")) {
                    continue;
                }
                Integer candidate = parseDurationDescription(tag.getDescription());
                if (candidate != null && candidate > 0 && (best == null || candidate > best)) {
                    best = candidate;
                }
            }
        }
        return best;
    }

    private String extensionOf(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizedContentType(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        int separator = normalized.indexOf(';');
        if (separator >= 0) {
            return normalized.substring(0, separator).trim();
        }
        return normalized;
    }

    private boolean isObjectMissing(ErrorResponseException ex) {
        if (ex.errorResponse() == null || ex.errorResponse().code() == null) {
            return false;
        }

        String errorCode = ex.errorResponse().code();
        return "NoSuchKey".equals(errorCode)
                || "NoSuchObject".equals(errorCode)
                || "NoSuchBucket".equals(errorCode);
    }

    private BusinessException validationException() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR);
    }

    private BusinessException storageException(Exception ex) {
        return new BusinessException(ErrorCode.COURSE_MEDIA_STORAGE_ERROR, ex);
    }

    private ResponseEntity<Resource> streamLessonVideo(String courseId, String lessonId, HttpHeaders headers) {
        String objectKey = buildLessonVideoObjectKey(courseId, lessonId);
        StoredObject storedObject = getStoredObjectOrThrow(objectKey, LessonNotFoundException::new);
        long objectSize = storedObject.size();
        MediaType mediaType = MediaTypeFactory.getMediaType(storedObject.resource()).orElse(MediaType.APPLICATION_OCTET_STREAM);

        Optional<HttpRange> firstRange;
        try {
            firstRange = firstRange(headers);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */%d".formatted(objectSize))
                    .build();
        }

        if (firstRange.isEmpty()) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .contentType(mediaType)
                    .contentLength(objectSize)
                    .body(storedObject.resource());
        }

        HttpRange range = firstRange.get();
        long start;
        long end;
        try {
            start = range.getRangeStart(objectSize);
            end = range.getRangeEnd(objectSize);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */%d".formatted(objectSize))
                    .build();
        }

        long length = (end - start) + 1;
        Resource rangedResource = new MinioObjectResource(minioClient, mediaBucket, objectKey, length, start, length);

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, "bytes %d-%d/%d".formatted(start, end, objectSize))
                .contentLength(length)
                .contentType(mediaType)
                .body(rangedResource);
    }

    private Optional<HttpRange> firstRange(HttpHeaders headers) {
        if (headers == null) {
            return Optional.empty();
        }
        List<HttpRange> ranges = headers.getRange();
        if (ranges == null || ranges.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ranges.get(0));
    }

    private void validatePlaybackSignature(String courseId, String lessonId, String userId, long expiresAt, String signature) {
        long now = Instant.now().getEpochSecond();
        if (expiresAt <= now || userId == null || userId.isBlank() || signature == null || signature.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }

        String expected = signPlaybackUrl(courseId, lessonId, userId, expiresAt);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }
    }

    private String signPlaybackUrl(String courseId, String lessonId, String userId, long expiresAt) {
        String payload = "%s|%s|%s|%d".formatted(courseId, lessonId, userId, expiresAt);
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec key = new SecretKeySpec(playbackSigningKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(key);
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            throw storageException(ex);
        }
    }

    private String buildSignedPlaybackUrl(String courseId, String lessonId, String userId, long expiresAt, String signature) {
        String path = LESSON_VIDEO_PUBLIC_SIGNED_PATH_TEMPLATE.formatted(courseId, lessonId);
        return "%s?uid=%s&exp=%d&sig=%s".formatted(path, encodeQueryParam(userId), expiresAt, encodeQueryParam(signature));
    }

    private String encodeQueryParam(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private long effectivePlaybackUrlTtlSeconds() {
        if (playbackUrlTtlSeconds < MIN_PLAYBACK_URL_TTL_SECONDS) {
            return MIN_PLAYBACK_URL_TTL_SECONDS;
        }
        return playbackUrlTtlSeconds;
    }

    private void deleteTempFileQuietly(Path tempFile) {
        if (tempFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException ex) {
            log.debug("VIDEO_DURATION_TEMPFILE_DELETE_ERROR | file={} msg={}", tempFile.getFileName(), ex.getMessage());
        }
    }

    private static final class BackfillCounters {
        private long scannedCount;
        private long updatedCount;
        private long failedCount;
        private long skippedCount;
    }

    private record StoredObject(Resource resource, long size, String objectKey) {
    }
}
