package com.edubase.course.service.concretes;

import com.edubase.course.entity.Course;
import com.edubase.course.entity.CourseStatus;
import com.edubase.course.entity.Lesson;
import com.edubase.course.exception.CourseNotFoundException;
import com.edubase.course.exception.LessonNotFoundException;
import com.edubase.course.repository.CourseRepository;
import com.edubase.course.security.AuthContext;
import com.edubase.course.security.UserRole;
import com.edubase.course.service.abstracts.CourseMediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseMediaServiceImpl implements CourseMediaService {

    private static final long CHUNK_SIZE = 1024 * 1024;

    private final CourseRepository courseRepository;

    @Value("${course.media.base-path:videos}")
    private String basePath;

    @Override
    public ResponseEntity<ResourceRegion> getLessonVideo(AuthContext authContext, String courseId, String lessonId, HttpHeaders headers) {
        Course course = resolveCourseForAccess(authContext, courseId);

        if (course.getLessons() == null || course.getLessons().isEmpty()) {
            throw new LessonNotFoundException();
        }

        Lesson lesson = course.getLessons().stream()
                .filter(item -> lessonId.equals(item.getId()))
                .findFirst()
                .orElseThrow(LessonNotFoundException::new);

        Path videoPath = Paths.get(basePath, "courses", course.getId(), "lessons", lesson.getId() + ".mp4");
        Resource resource = toResource(videoPath);
        long contentLength = contentLength(resource);

        List<HttpRange> ranges = headers.getRange();
        ResourceRegion region = buildRegion(resource, contentLength, ranges);
        HttpStatus status = ranges.isEmpty() ? HttpStatus.OK : HttpStatus.PARTIAL_CONTENT;
        MediaType mediaType = MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);

        return ResponseEntity.status(status).contentType(mediaType).body(region);
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

    private Resource toResource(Path videoPath) {
        try {
            Resource resource = new UrlResource(videoPath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new LessonNotFoundException();
            }
            return resource;
        } catch (IOException ex) {
            throw new LessonNotFoundException();
        }
    }

    private long contentLength(Resource resource) {
        try {
            return resource.contentLength();
        } catch (IOException ex) {
            throw new LessonNotFoundException();
        }
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
}
