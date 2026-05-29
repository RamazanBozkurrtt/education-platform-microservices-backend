package com.edubase.course.exception;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;

public class FinalExamActiveAttemptExistsException extends BusinessException {

    public FinalExamActiveAttemptExistsException() {
        super(ErrorCode.FINAL_EXAM_ACTIVE_ATTEMPT_EXISTS);
    }
}
