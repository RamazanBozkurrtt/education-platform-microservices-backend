package com.edubase.user.service.concretes;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
import com.edubase.commonCore.utils.RestResponse;
import com.edubase.user.dto.internal.AuthRoleUpdateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthRoleClient {

    private final RestClient internalAuthServiceRestClient;

    public AuthRoleUpdateResponse grantInstructorRoleForCurrentUser(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }

        try {
            RestResponse<AuthRoleUpdateResponse> response = internalAuthServiceRestClient.post()
                    .uri("/api/v1/auth/me/roles/instructor")
                    .header(HttpHeaders.AUTHORIZATION, bearerToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            if (response == null || response.getData() == null) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR);
            }
            return response.getData();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403) {
                throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
            }
            log.error("Auth role update failed with status={} body={}", ex.getRawStatusCode(), ex.getResponseBodyAsString(), ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        } catch (RestClientException ex) {
            log.error("Auth role update call failed", ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
