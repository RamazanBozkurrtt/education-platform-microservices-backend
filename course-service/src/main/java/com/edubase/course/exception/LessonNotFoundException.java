package com.edubase.course.exception;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;

public class LessonNotFoundException extends BusinessException {

    public LessonNotFoundException() {
        super(ErrorCode.COURSE_LESSON_NOT_FOUND);
    }
}
