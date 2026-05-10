package com.edubase.review.service.concretes;

import com.edubase.review.service.abstracts.ReviewEligibilityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DefaultReviewEligibilityService implements ReviewEligibilityService {

    @Override
    public void assertCanReview(Long userId, String courseId) {
        // TODO: integrate with enrollment-service and enforce enrollment check here.
        log.debug("Enrollment check skipped for userId={} courseId={}", userId, courseId);
    }
}
