package com.edubase.enrollment.exception;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;

public class EnrollmentAlreadyExistsException extends BusinessException {

    public EnrollmentAlreadyExistsException() {
        super(ErrorCode.ENROLLMENT_ALREADY_EXISTS);
    }
}
