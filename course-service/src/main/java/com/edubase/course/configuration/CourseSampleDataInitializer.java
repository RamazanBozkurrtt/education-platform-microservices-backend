package com.edubase.course.configuration;

import com.edubase.course.entity.Course;
import com.edubase.course.entity.CourseStatus;
import com.edubase.course.entity.Lesson;
import com.edubase.course.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CourseSampleDataInitializer implements ApplicationRunner {

    private static final String SAMPLE_COURSE_ID = "1";
    private static final String SAMPLE_LESSON_ID = "1";

    private final CourseRepository courseRepository;

    @Value("${course.sample.enabled:true}")
    private boolean enabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled || courseRepository.existsById(SAMPLE_COURSE_ID)) {
            return;
        }

        Instant now = Instant.now();
        Lesson lesson = Lesson.builder()
                .id(SAMPLE_LESSON_ID)
                .title("Ornek Ders 1")
                .videoUrl("/courses/" + SAMPLE_COURSE_ID + "/lessons/" + SAMPLE_LESSON_ID + "/video")
                .duration(120)
                .orderIndex(1)
                .build();

        Course course = Course.builder()
                .id(SAMPLE_COURSE_ID)
                .title("Ornek Kurs")
                .description("Gateway uzerinden video servis etmek icin ornek kurs.")
                .price(BigDecimal.ZERO)
                .status(CourseStatus.PUBLISHED)
                .instructorId("sample")
                .createdAt(now)
                .updatedAt(now)
                .lessons(List.of(lesson))
                .build();

        courseRepository.save(course);
    }
}
