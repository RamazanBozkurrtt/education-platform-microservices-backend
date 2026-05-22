package com.edubase.enrollment.service.concretes;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class InternalApiKeyService {

    private final String internalApiKey;

    public InternalApiKeyService(@Value("${app.internal.api-key}") String internalApiKey) {
        this.internalApiKey = internalApiKey;
    }

    public void validate(String providedApiKey) {
        if (providedApiKey == null || !providedApiKey.equals(internalApiKey)) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }
    }
}
