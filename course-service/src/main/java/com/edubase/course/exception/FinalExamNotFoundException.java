package com.edubase.course.exception;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;

public class FinalExamNotFoundException extends BusinessException {

    public FinalExamNotFoundException() {
        super(ErrorCode.FINAL_EXAM_NOT_FOUND);
    }
}
