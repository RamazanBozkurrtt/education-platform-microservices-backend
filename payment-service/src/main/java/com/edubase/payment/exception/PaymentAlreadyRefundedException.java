package com.edubase.payment.exception;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;

public class PaymentAlreadyRefundedException extends BusinessException {

    public PaymentAlreadyRefundedException() {
        super(ErrorCode.PAYMENT_ALREADY_REFUNDED);
    }
}
