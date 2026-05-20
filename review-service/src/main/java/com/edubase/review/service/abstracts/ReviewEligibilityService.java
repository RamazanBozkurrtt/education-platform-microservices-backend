package com.edubase.review.service.abstracts;

public interface ReviewEligibilityService {

    void assertCanReview(Long userId, String courseId);
}
