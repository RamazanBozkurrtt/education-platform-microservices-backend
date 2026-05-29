package com.edubase.course.service.concretes;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
import com.drew.imaging.mp4.Mp4MetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.mp4.Mp4Directory;
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
import com.edubase.course.storage.MinioObjectResource;
import io.minio.BucketExistsArgs;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
@Slf4j
@RequiredArgsConstructor
public class CourseMediaServiceImpl implements CourseMediaService {

    private static final String VIDEO_EXTENSION = "mp4";
    private static final String VIDEO_CONTENT_TYPE = "video/mp4";
    private static final String DEFAULT_COURSE_IMAGE_FILE = "default-course.png";
    private static final String COURSE_VIDEO_OBJECT_KEY_TEMPLATE = "courses/%s/lessons/%s.%s";
    private static final String LESSON_VIDEO_PUBLIC_PATH_TEMPLATE = "/courses/%s/lessons/%s/video";
    private static final String LESSON_VIDEO_PUBLIC_SIGNED_PATH_TEMPLATE = "/courses/public/%s/lessons/%s/video";
    private static final long MIN_PLAYBACK_URL_TTL_SECONDS = 60L;
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final Pattern DURATION_UNITS_PATTERN = Pattern.compile(
            "(\\d+(?:[\\.,]\\d+)?)\\s*(hours?|hrs?|hr|h|minutes?|mins?|min|m|seconds?|secs?|sec|s|milliseconds?|msecs?|msec|ms)"
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

    @Value("${app.gateway-url:http://localhost:8090}")
    private String gatewayBaseUrl;

    @Override
    public ResponseEntity<Resource> getLessonVideo(AuthContext authContext, String courseId, String lessonId, HttpHeaders headers) {
        Course course = resolveCourseForAccess(authContext, courseId);
        Lesson lesson = resolveLesson(course, lessonId);
        return streamLessonVideo(course.getId(), lesson.getId(), headers);
    }

    @Override
    public ResponseEntity<Resource> getPublicLessonVideoBySignature(String courseId, String lessonId, long expiresAt, String signature, HttpHeaders headers) {
        validatePlaybackSignature(courseId, lessonId, expiresAt, signature);

        Course course = courseRepository.findByIdAndDeletedAtIsNull(courseId).orElseThrow(CourseNotFoundException::new);
        Lesson lesson = resolveLesson(course, lessonId);
        return streamLessonVideo(course.getId(), lesson.getId(), headers);
    }

    @Override
    public VideoPlaybackUrlResponse createLessonVideoPlaybackUrl(AuthContext authContext, String courseId, String lessonId) {
        Course course = resolveCourseForAccess(authContext, courseId);
        Lesson lesson = resolveLesson(course, lessonId);
        long expiresAt = Instant.now().getEpochSecond() + effectivePlaybackUrlTtlSeconds();
        String signature = signPlaybackUrl(course.getId(), lesson.getId(), expiresAt);

        return VideoPlaybackUrlResponse.builder()
                .url(buildSignedPlaybackUrl(course.getId(), lesson.getId(), expiresAt, signature))
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

    public Integer resolveVideoDurationSeconds(MultipartFile file) {
        try (var inputStream = file.getInputStream()) {
            Metadata metadata = Mp4MetadataReader.readMetadata(inputStream);
            Integer durationSeconds = extractDurationSeconds(metadata);
            if (durationSeconds != null && durationSeconds > 0) {
                return durationSeconds;
            }
            return null;
        } catch (Exception ex) {
            log.warn("VIDEO_DURATION_PARSE_ERROR | msg={}", ex.getMessage());
            return null;
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
            String unit = matcher.group(2);
            if (valuePart < 0) {
                return null;
            }
            foundAny = true;
            if (unit.startsWith("h")) {
                totalSeconds += valuePart * 3600d;
            } else if (unit.startsWith("m") && !"ms".equals(unit) && !"msec".equals(unit) && !"msecs".equals(unit) && !unit.startsWith("millisecond")) {
                totalSeconds += valuePart * 60d;
            } else if (unit.startsWith("s") || unit.startsWith("sec")) {
                totalSeconds += valuePart;
            } else {
                totalSeconds += valuePart / 1000d;
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

        Optional<HttpRange> firstRange = firstRange(headers);
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

    private void validatePlaybackSignature(String courseId, String lessonId, long expiresAt, String signature) {
        long now = Instant.now().getEpochSecond();
        if (expiresAt <= now || signature == null || signature.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }

        String expected = signPlaybackUrl(courseId, lessonId, expiresAt);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }
    }

    private String signPlaybackUrl(String courseId, String lessonId, long expiresAt) {
        String payload = "%s|%s|%d".formatted(courseId, lessonId, expiresAt);
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

    private String buildSignedPlaybackUrl(String courseId, String lessonId, long expiresAt, String signature) {
        String normalizedGatewayBaseUrl = normalizeGatewayBaseUrl(gatewayBaseUrl);
        String path = LESSON_VIDEO_PUBLIC_SIGNED_PATH_TEMPLATE.formatted(courseId, lessonId);
        return "%s%s?exp=%d&sig=%s".formatted(normalizedGatewayBaseUrl, path, expiresAt, signature);
    }

    private long effectivePlaybackUrlTtlSeconds() {
        if (playbackUrlTtlSeconds < MIN_PLAYBACK_URL_TTL_SECONDS) {
            return MIN_PLAYBACK_URL_TTL_SECONDS;
        }
        return playbackUrlTtlSeconds;
    }

    private String normalizeGatewayBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8090";
        }
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private record StoredObject(Resource resource, long size, String objectKey) {
    }
}
