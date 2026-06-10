package com.edubase.enrollment.grpc;

import com.edubase.commonCore.exceptions.BusinessException;
import com.edubase.commonCore.exceptions.ErrorCode;
import com.edubase.contracts.user.v1.UserByAuthIdRequest;
import com.edubase.contracts.user.v1.UserByAuthIdResponse;
import com.edubase.contracts.user.v1.UserQueryServiceGrpc;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserGrpcClient {

    private final UserQueryServiceGrpc.UserQueryServiceBlockingStub userQueryServiceBlockingStub;

    public void assertUserExists(Long authUserId) {
        if (authUserId == null || authUserId <= 0) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }

        try {
            UserByAuthIdResponse response = userQueryServiceBlockingStub
                    .withDeadlineAfter(1500, TimeUnit.MILLISECONDS)
                    .getUserByAuthId(UserByAuthIdRequest.newBuilder().setAuthUserId(authUserId).build());
            if (!response.getExists()) {
                throw new BusinessException(ErrorCode.USER_NOT_FOUND);
            }
        } catch (StatusRuntimeException ex) {
            log.error("gRPC call to user-service failed for authUserId={}", authUserId, ex);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
