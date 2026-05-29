package com.edubase.course.service;

import com.edubase.course.entity.Course;
import com.edubase.course.entity.CourseStatus;
import com.edubase.course.entity.Lesson;
import com.edubase.course.messaging.CourseSearchSyncKafkaPublisher;
import com.edubase.course.repository.CourseRepository;
import com.edubase.course.security.AuthContext;
import com.edubase.course.security.UserRole;
import com.edubase.course.service.concretes.CourseMediaServiceImpl;
import com.drew.metadata.Metadata;
import com.drew.metadata.mp4.Mp4Directory;
import io.minio.MinioClient;
import io.minio.StatObjectResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseMediaServiceImplTest {

    private static final byte[] VALID_MP4_HEADER = new byte[]{
            0, 0, 0, 24, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'
    };

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private MinioClient minioClient;

    @Mock
    private CourseSearchSyncKafkaPublisher courseSearchSyncKafkaPublisher;

    @InjectMocks
    private CourseMediaServiceImpl courseMediaService;

    @BeforeEach
    void setUp() {
        courseMediaService = spy(courseMediaService);
        ReflectionTestUtils.setField(courseMediaService, "mediaBucket", "edubase-media");
        ReflectionTestUtils.setField(courseMediaService, "basePath", "videos");
        ReflectionTestUtils.setField(courseMediaService, "autoCreateBucket", false);
    }

    @Test
    void uploadLessonVideo_updatesLessonVideoUrl() {
        Lesson lesson = Lesson.builder().id("lesson-1").build();
        Course course = Course.builder()
                .id("course-1")
                .instructorId("instructor-1")
                .lessons(List.of(lesson))
                .build();
        AuthContext authContext = new AuthContext("instructor-1", UserRole.INSTRUCTOR);
        MockMultipartFile file = new MockMultipartFile("file", "lesson.mp4", "video/mp4", VALID_MP4_HEADER);

        when(courseRepository.existsByIdAndInstructorIdAndDeletedAtIsNull("course-1", "instructor-1")).thenReturn(true);
        when(courseRepository.findByIdAndDeletedAtIsNull("course-1")).thenReturn(Optional.of(course));
        when(courseRepository.save(course)).thenReturn(course);
        doReturn(95).when(courseMediaService).resolveVideoDurationSeconds(file);

        courseMediaService.uploadLessonVideo(authContext, "course-1", "lesson-1", file);

        assertEquals("/courses/course-1/lessons/lesson-1/video", lesson.getVideoUrl());
        assertEquals(95, lesson.getDuration());
        assertNotNull(lesson.getVideoUpdatedAt());
        verify(courseRepository).save(course);
        verify(courseSearchSyncKafkaPublisher).publishUpsert(course);
    }

    @Test
    void uploadLessonVideo_whenDurationCannotBeParsed_keepsExistingDuration() {
        Lesson lesson = Lesson.builder().id("lesson-1").duration(120).build();
        Course course = Course.builder()
                .id("course-1")
                .instructorId("instructor-1")
                .lessons(List.of(lesson))
                .build();
        AuthContext authContext = new AuthContext("instructor-1", UserRole.INSTRUCTOR);
        MockMultipartFile file = new MockMultipartFile("file", "lesson.mp4", "video/mp4", VALID_MP4_HEADER);

        when(courseRepository.existsByIdAndInstructorIdAndDeletedAtIsNull("course-1", "instructor-1")).thenReturn(true);
        when(courseRepository.findByIdAndDeletedAtIsNull("course-1")).thenReturn(Optional.of(course));
        when(courseRepository.save(course)).thenReturn(course);
        doReturn(null).when(courseMediaService).resolveVideoDurationSeconds(file);

        courseMediaService.uploadLessonVideo(authContext, "course-1", "lesson-1", file);

        assertEquals(120, lesson.getDuration());
        assertNotNull(lesson.getVideoUpdatedAt());
        verify(courseRepository).save(course);
        verify(courseSearchSyncKafkaPublisher).publishUpsert(course);
    }

    @Test
    void deleteLessonVideo_clearsLessonVideoUrl() {
        Lesson lesson = Lesson.builder().id("lesson-1").videoUrl("/courses/course-1/lessons/lesson-1/video").build();
        Course course = Course.builder()
                .id("course-1")
                .instructorId("instructor-1")
                .lessons(List.of(lesson))
                .build();
        AuthContext authContext = new AuthContext("instructor-1", UserRole.INSTRUCTOR);

        when(courseRepository.existsByIdAndInstructorIdAndDeletedAtIsNull("course-1", "instructor-1")).thenReturn(true);
        when(courseRepository.findByIdAndDeletedAtIsNull("course-1")).thenReturn(Optional.of(course));
        when(courseRepository.save(course)).thenReturn(course);

        courseMediaService.deleteLessonVideo(authContext, "course-1", "lesson-1");

        assertNull(lesson.getVideoUrl());
        assertNull(lesson.getVideoUpdatedAt());
        assertNull(lesson.getDuration());
        verify(courseRepository).save(course);
        verify(courseSearchSyncKafkaPublisher).publishUpsert(course);
    }

    @Test
    void getLessonVideo_withoutRange_returnsWholeObject() throws Exception {
        Lesson lesson = Lesson.builder().id("lesson-1").build();
        Course course = Course.builder()
                .id("course-1")
                .instructorId("instructor-1")
                .lessons(List.of(lesson))
                .build();
        AuthContext authContext = new AuthContext("instructor-1", UserRole.INSTRUCTOR);
        HttpHeaders headers = new HttpHeaders();
        StatObjectResponse stat = mock(StatObjectResponse.class);

        when(courseRepository.existsByIdAndInstructorIdAndDeletedAtIsNull("course-1", "instructor-1")).thenReturn(true);
        when(courseRepository.findByIdAndDeletedAtIsNull("course-1")).thenReturn(Optional.of(course));
        when(minioClient.statObject(any())).thenReturn(stat);
        when(stat.size()).thenReturn(10_000L);

        ResponseEntity<Resource> response = courseMediaService.getLessonVideo(authContext, "course-1", "lesson-1", headers);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("bytes", response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES));
        assertEquals(10_000L, response.getHeaders().getContentLength());
        assertNotNull(response.getBody());
    }

    @Test
    void getLessonVideo_withRange_returns206AndCorrectContentRange() throws Exception {
        Lesson lesson = Lesson.builder().id("lesson-1").build();
        Course course = Course.builder()
                .id("course-1")
                .instructorId("instructor-1")
                .lessons(List.of(lesson))
                .build();
        AuthContext authContext = new AuthContext("instructor-1", UserRole.INSTRUCTOR);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RANGE, "bytes=0-1023");
        StatObjectResponse stat = mock(StatObjectResponse.class);

        when(courseRepository.existsByIdAndInstructorIdAndDeletedAtIsNull("course-1", "instructor-1")).thenReturn(true);
        when(courseRepository.findByIdAndDeletedAtIsNull("course-1")).thenReturn(Optional.of(course));
        when(minioClient.statObject(any())).thenReturn(stat);
        when(stat.size()).thenReturn(5_000L);

        ResponseEntity<Resource> response = courseMediaService.getLessonVideo(authContext, "course-1", "lesson-1", headers);

        assertEquals(HttpStatus.PARTIAL_CONTENT, response.getStatusCode());
        assertEquals("bytes", response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES));
        assertEquals("bytes 0-1023/5000", response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE));
        assertEquals(1024L, response.getHeaders().getContentLength());
        assertNotNull(response.getBody());
    }

    @Test
    void getLessonVideo_whenInstructorIsNotOwner_allowsPublishedCourseAsLearner() throws Exception {
        Lesson lesson = Lesson.builder().id("lesson-1").build();
        Course course = Course.builder()
                .id("course-1")
                .instructorId("course-owner")
                .status(CourseStatus.PUBLISHED)
                .lessons(List.of(lesson))
                .build();
        AuthContext authContext = new AuthContext("other-instructor", UserRole.INSTRUCTOR);
        HttpHeaders headers = new HttpHeaders();
        StatObjectResponse stat = mock(StatObjectResponse.class);

        when(courseRepository.existsByIdAndInstructorIdAndDeletedAtIsNull("course-1", "other-instructor")).thenReturn(false);
        when(courseRepository.findByIdAndStatusAndDeletedAtIsNull("course-1", CourseStatus.PUBLISHED))
                .thenReturn(Optional.of(course));
        when(minioClient.statObject(any())).thenReturn(stat);
        when(stat.size()).thenReturn(10_000L);

        ResponseEntity<Resource> response = courseMediaService.getLessonVideo(authContext, "course-1", "lesson-1", headers);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("bytes", response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES));
        assertEquals(10_000L, response.getHeaders().getContentLength());
        assertNotNull(response.getBody());
    }

    @Test
    void parseDurationDescription_shouldParseMinuteSecondFormatWithFraction() {
        Integer parsed = ReflectionTestUtils.invokeMethod(courseMediaService, "parseDurationDescription", "00:03:11.200");
        assertEquals(191, parsed);
    }

    @Test
    void parseDurationDescription_shouldParseHumanReadableUnits() {
        Integer parsed = ReflectionTestUtils.invokeMethod(courseMediaService, "parseDurationDescription", "3 min 11 sec");
        assertEquals(191, parsed);
    }

    @Test
    void extractDurationSeconds_shouldPreferRicherDurationOverOneSecondFallback() {
        Metadata metadata = new Metadata();
        Mp4Directory directory = new Mp4Directory();
        directory.setString(Mp4Directory.TAG_DURATION, "00:03:11.000");
        directory.setLong(Mp4Directory.TAG_DURATION_SECONDS, 1L);
        metadata.addDirectory(directory);

        Integer parsed = ReflectionTestUtils.invokeMethod(courseMediaService, "extractDurationSeconds", metadata);
        assertEquals(191, parsed);
    }
}
