package com.edubase.course.exception;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;

public class FinalExamCourseNotCompletedException extends BusinessException {

    public FinalExamCourseNotCompletedException() {
        super(ErrorCode.FINAL_EXAM_COURSE_NOT_COMPLETED);
    }
}
