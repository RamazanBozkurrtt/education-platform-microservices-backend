package com.edubase.course.exception;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;

public class FinalExamAttemptsExhaustedException extends BusinessException {

    public FinalExamAttemptsExhaustedException() {
        super(ErrorCode.FINAL_EXAM_ATTEMPTS_EXHAUSTED);
    }
}
