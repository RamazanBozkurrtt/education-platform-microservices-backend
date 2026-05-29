package com.edubase.course.exception;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;

public class FinalExamAlreadyExistsException extends BusinessException {

    public FinalExamAlreadyExistsException() {
        super(ErrorCode.FINAL_EXAM_ALREADY_EXISTS);
    }
}
