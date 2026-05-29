package com.edubase.course.exception;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;

public class FinalExamNotReadyException extends BusinessException {

    public FinalExamNotReadyException() {
        super(ErrorCode.FINAL_EXAM_NOT_READY);
    }
}
