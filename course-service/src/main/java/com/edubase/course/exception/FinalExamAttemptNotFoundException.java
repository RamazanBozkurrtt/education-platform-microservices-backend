package com.edubase.course.exception;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;

public class FinalExamAttemptNotFoundException extends BusinessException {

    public FinalExamAttemptNotFoundException() {
        super(ErrorCode.FINAL_EXAM_ATTEMPT_NOT_FOUND);
    }
}
