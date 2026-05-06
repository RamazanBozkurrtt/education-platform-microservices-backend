package com.edubase.course.service.concretes;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
import com.edubase.course.entity.Course;
import com.edubase.course.entity.CourseStatus;
import com.edubase.course.entity.Lesson;
import com.edubase.course.exception.CourseNotFoundException;
import com.edubase.course.exception.LessonNotFoundException;
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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class CourseMediaServiceImpl implements CourseMediaService {

    private static final long CHUNK_SIZE = 1024 * 1024;
    private static final String VIDEO_EXTENSION = "mp4";
    private static final String VIDEO_CONTENT_TYPE = "video/mp4";
    private static final String DEFAULT_COURSE_IMAGE_FILE = "default-course.png";
    private static final List<String> SUPPORTED_IMAGE_EXTENSIONS = List.of("png", "jpg", "jpeg", "webp", "svg");
    private static final Set<String> SUPPORTED_IMAGE_CONTENT_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/webp",
            "image/svg+xml"
    );

    private final CourseRepository courseRepository;
    private final MinioClient minioClient;

    @Value("${course.media.base-path:videos}")
    private String basePath;

    @Value("${course.media.image-base-path:images/courses}")
    private String imageBasePath;

    @Value("${course.media.minio.bucket:edubase-media}")
    private String mediaBucket;

    @Value("${course.media.minio.auto-create-bucket:true}")
    private boolean autoCreateBucket;

    @Override
    public ResponseEntity<ResourceRegion> getLessonVideo(AuthContext authContext, String courseId, String lessonId, HttpHeaders headers) {
        Course course = resolveCourseForAccess(authContext, courseId);
        Lesson lesson = resolveLesson(course, lessonId);
        String objectKey = buildLessonVideoObjectKey(course.getId(), lesson.getId());
        StoredObject storedObject = getStoredObjectOrThrow(objectKey, LessonNotFoundException::new);

        List<HttpRange> ranges = headers.getRange();
        ResourceRegion region = buildRegion(storedObject.resource(), storedObject.size(), ranges);
        HttpStatus status = ranges.isEmpty() ? HttpStatus.OK : HttpStatus.PARTIAL_CONTENT;
        MediaType mediaType = MediaTypeFactory.getMediaType(storedObject.resource()).orElse(MediaType.APPLICATION_OCTET_STREAM);

        return ResponseEntity.status(status)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .contentType(mediaType)
                .body(region);
    }

    @Override
    public ResponseEntity<Resource> getPublicCourseImage(String courseId) {
        Course course = courseRepository.findByIdAndStatus(courseId, CourseStatus.PUBLISHED)
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
    public void uploadLessonVideo(AuthContext authContext, String courseId, String lessonId, MultipartFile file) {
        Course course = resolveCourseForManagement(authContext, courseId);
        Lesson lesson = resolveLesson(course, lessonId);
        validateVideoFile(file);

        ensureBucketForWrites();
        putObject(buildLessonVideoObjectKey(course.getId(), lesson.getId()), file, VIDEO_CONTENT_TYPE);
    }

    @Override
    public void deleteLessonVideo(AuthContext authContext, String courseId, String lessonId) {
        Course course = resolveCourseForManagement(authContext, courseId);
        Lesson lesson = resolveLesson(course, lessonId);

        ensureBucketForWrites();
        removeObjectIfExists(buildLessonVideoObjectKey(course.getId(), lesson.getId()));
    }

    private Course resolveCourseForAccess(AuthContext authContext, String courseId) {
        if (authContext == null || authContext.role() == null) {
            throw new CourseNotFoundException();
        }

        UserRole role = authContext.role();
        if (role == UserRole.ADMIN) {
            return courseRepository.findById(courseId).orElseThrow(CourseNotFoundException::new);
        }

        if (role == UserRole.INSTRUCTOR) {
            boolean ownsCourse = courseRepository.existsByIdAndInstructorId(courseId, authContext.userId());
            if (!ownsCourse) {
                throw new CourseNotFoundException();
            }
            return courseRepository.findById(courseId).orElseThrow(CourseNotFoundException::new);
        }

        return courseRepository.findByIdAndStatus(courseId, CourseStatus.PUBLISHED)
                .orElseThrow(CourseNotFoundException::new);
    }

    private Course resolveCourseForManagement(AuthContext authContext, String courseId) {
        if (authContext == null || authContext.role() == null) {
            throw new CourseNotFoundException();
        }

        if (authContext.role() == UserRole.ADMIN) {
            return courseRepository.findById(courseId).orElseThrow(CourseNotFoundException::new);
        }

        if (authContext.role() != UserRole.INSTRUCTOR) {
            throw new CourseNotFoundException();
        }

        boolean ownsCourse = courseRepository.existsByIdAndInstructorId(courseId, authContext.userId());
        if (!ownsCourse) {
            throw new CourseNotFoundException();
        }
        return courseRepository.findById(courseId).orElseThrow(CourseNotFoundException::new);
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
        return toStoredObject(objectKey, stat);
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
        return new StoredObject(resource, statObjectResponse.size());
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
        String lessonPath = "courses/%s/lessons/%s.%s".formatted(courseId, lessonId, VIDEO_EXTENSION);
        if (videoBasePath.isBlank()) {
            return lessonPath;
        }
        return "%s/%s".formatted(videoBasePath, lessonPath);
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
        return new BusinessException(ErrorCode.INTERNAL_ERROR);
    }

    private ResourceRegion buildRegion(Resource resource, long contentLength, List<HttpRange> ranges) {
        if (ranges == null || ranges.isEmpty()) {
            long length = Math.min(CHUNK_SIZE, contentLength);
            return new ResourceRegion(resource, 0, length);
        }

        HttpRange range = ranges.get(0);
        long start = range.getRangeStart(contentLength);
        long end = range.getRangeEnd(contentLength);
        long rangeLength = Math.min(CHUNK_SIZE, end - start + 1);
        return new ResourceRegion(resource, start, rangeLength);
    }

    private record StoredObject(Resource resource, long size) {
    }
}
