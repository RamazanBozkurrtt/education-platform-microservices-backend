package com.edubase.course.configuration.grpc;

import com.edubase.contracts.user.v1.UserQueryServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcClientConfig {

    @Bean(destroyMethod = "shutdownNow")
    public ManagedChannel userServiceGrpcChannel(
            @Value("${grpc.client.user.host:localhost}") String host,
            @Value("${grpc.client.user.port:9091}") int port) {
        return ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
    }

    @Bean
    public UserQueryServiceGrpc.UserQueryServiceBlockingStub userQueryServiceBlockingStub(ManagedChannel userServiceGrpcChannel) {
        return UserQueryServiceGrpc.newBlockingStub(userServiceGrpcChannel);
    }
}
