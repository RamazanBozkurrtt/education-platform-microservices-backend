package com.edubase.payment.exception;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;

public class PaymentAlreadyCompletedException extends BusinessException {

    public PaymentAlreadyCompletedException() {
        super(ErrorCode.PAYMENT_ALREADY_COMPLETED);
    }
}
