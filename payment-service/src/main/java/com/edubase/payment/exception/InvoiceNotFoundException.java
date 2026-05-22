package com.edubase.payment.exception;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;

public class InvoiceNotFoundException extends BusinessException {

    public InvoiceNotFoundException() {
        super(ErrorCode.INVOICE_NOT_FOUND);
    }
}
