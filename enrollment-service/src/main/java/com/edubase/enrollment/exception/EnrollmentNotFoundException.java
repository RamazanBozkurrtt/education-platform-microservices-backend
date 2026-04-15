package com.edubase.enrollment.exception;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;

public class EnrollmentNotFoundException extends BusinessException {

    public EnrollmentNotFoundException() {
        super(ErrorCode.ENROLLMENT_NOT_FOUND);
    }
}
