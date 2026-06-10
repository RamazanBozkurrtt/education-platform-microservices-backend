package com.edubase.course.exception;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;

public class CourseCategoryNotFoundException extends BusinessException {

    public CourseCategoryNotFoundException() {
        super(ErrorCode.COURSE_CATEGORY_NOT_FOUND);
    }
}
