package com.edubase.course.exception;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;

public class CourseNotFoundException extends BusinessException {

    public CourseNotFoundException() {
        super(ErrorCode.COURSE_NOT_FOUND);
    }
}
