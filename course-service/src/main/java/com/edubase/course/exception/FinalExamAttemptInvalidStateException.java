package com.edubase.course.exception;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;

public class FinalExamAttemptInvalidStateException extends BusinessException {

    public FinalExamAttemptInvalidStateException() {
        super(ErrorCode.FINAL_EXAM_ATTEMPT_INVALID_STATE);
    }
}
