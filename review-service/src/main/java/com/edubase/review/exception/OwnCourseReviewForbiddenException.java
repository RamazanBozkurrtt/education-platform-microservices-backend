package com.edubase.review.exception;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;

public class OwnCourseReviewForbiddenException extends BusinessException {

    public OwnCourseReviewForbiddenException() {
        super(ErrorCode.REVIEW_OWN_COURSE_FORBIDDEN);
    }
}
