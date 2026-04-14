package com.edubase.user.grpc;

import com.edubase.contracts.user.v1.UserByAuthIdRequest;
import com.edubase.contracts.user.v1.UserByAuthIdResponse;
import com.edubase.contracts.user.v1.UserQueryServiceGrpc;
import com.edubase.user.entity.UserProfile;
import com.edubase.user.repository.UserProfileRepository;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserQueryGrpcService extends UserQueryServiceGrpc.UserQueryServiceImplBase {

    private final UserProfileRepository userProfileRepository;

    @Override
    public void getUserByAuthId(UserByAuthIdRequest request, StreamObserver<UserByAuthIdResponse> responseObserver) {
        long authUserId = request.getAuthUserId();
        Optional<UserProfile> profile = authUserId > 0
                ? userProfileRepository.findByAuthUserId(authUserId)
                : Optional.empty();

        UserByAuthIdResponse response = UserByAuthIdResponse.newBuilder()
                .setAuthUserId(authUserId)
                .setExists(profile.isPresent())
                .setEmail(profile.map(UserProfile::getEmail).orElse(""))
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
