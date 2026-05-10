package com.edubase.course.configuration;

import com.edubase.course.entity.Category;
import com.edubase.course.repository.CategoryRepository;
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

    private final CategoryRepository categoryRepository;

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
    }
}
