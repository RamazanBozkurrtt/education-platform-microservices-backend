package com.edubase.enrollment.configuration.grpc;

import com.edubase.contracts.course.v1.CourseQueryServiceGrpc;
import com.edubase.contracts.user.v1.UserQueryServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcClientConfig {

    @Bean(destroyMethod = "shutdownNow")
    @Qualifier("userServiceGrpcChannel")
    public ManagedChannel userServiceGrpcChannel(
            @Value("${grpc.client.user.host:localhost}") String host,
            @Value("${grpc.client.user.port:9091}") int port) {
        return ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
    }

    @Bean(destroyMethod = "shutdownNow")
    @Qualifier("courseServiceGrpcChannel")
    public ManagedChannel courseServiceGrpcChannel(
            @Value("${grpc.client.course.host:localhost}") String host,
            @Value("${grpc.client.course.port:9092}") int port) {
        return ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
    }

    @Bean
    public UserQueryServiceGrpc.UserQueryServiceBlockingStub userQueryServiceBlockingStub(
            @Qualifier("userServiceGrpcChannel") ManagedChannel channel) {
        return UserQueryServiceGrpc.newBlockingStub(channel);
    }

    @Bean
    public CourseQueryServiceGrpc.CourseQueryServiceBlockingStub courseQueryServiceBlockingStub(
            @Qualifier("courseServiceGrpcChannel") ManagedChannel channel) {
        return CourseQueryServiceGrpc.newBlockingStub(channel);
    }
}
