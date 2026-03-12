package com.edubase.course.exception;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;

public class PublishValidationException extends BusinessException {

    public PublishValidationException() {
        super(ErrorCode.COURSE_PUBLISH_INVALID);
    }
}
