package com.edubase.course.exception;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;

public class FinalExamNotActiveException extends BusinessException {

    public FinalExamNotActiveException() {
        super(ErrorCode.FINAL_EXAM_NOT_ACTIVE);
    }
}
