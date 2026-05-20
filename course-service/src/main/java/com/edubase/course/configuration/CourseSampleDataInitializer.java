package com.edubase.course.configuration;

import com.edubase.course.entity.Category;
import com.edubase.course.entity.CourseLevel;
import com.edubase.course.repository.CategoryRepository;
import com.edubase.course.repository.CourseLevelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CourseSampleDataInitializer implements ApplicationRunner {

    private static final List<String> SAMPLE_CATEGORIES = List.of(
            "Yazilim Gelistirme",
            "Veri Bilimi ve Yapay Zeka",
            "Bulut ve DevOps",
            "Siber Guvenlik",
            "Mobil Uygulama Gelistirme",
            "Web Gelistirme",
            "Oyun Gelistirme",
            "UI/UX Tasarim",
            "Proje ve Urun Yonetimi",
            "Pazarlama ve Buyume"
    );

    private static final List<SampleCourseLevel> SAMPLE_LEVELS = List.of(
            new SampleCourseLevel("Baslangic", 1),
            new SampleCourseLevel("Orta", 2),
            new SampleCourseLevel("Ileri", 3)
    );

    private final CategoryRepository categoryRepository;
    private final CourseLevelRepository courseLevelRepository;

    @Value("${course.sample.enabled:true}")
    private boolean enabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }

        for (String categoryName : SAMPLE_CATEGORIES) {
            if (!categoryRepository.existsByCategoryName(categoryName)) {
                categoryRepository.save(new Category(null, categoryName, null, null));
            }
        }

        for (SampleCourseLevel courseLevel : SAMPLE_LEVELS) {
            if (!courseLevelRepository.existsByLevelName(courseLevel.levelName())) {
                courseLevelRepository.save(new CourseLevel(
                        null,
                        courseLevel.levelName(),
                        courseLevel.displayOrder(),
                        null,
                        null
                ));
            }
        }
    }

    private record SampleCourseLevel(String levelName, int displayOrder) {
    }
}
