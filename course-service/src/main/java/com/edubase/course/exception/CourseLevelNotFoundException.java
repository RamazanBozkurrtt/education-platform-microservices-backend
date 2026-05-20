package com.edubase.course.exception;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;

public class CourseLevelNotFoundException extends BusinessException {

    public CourseLevelNotFoundException() {
        super(ErrorCode.COURSE_LEVEL_NOT_FOUND);
    }
}
