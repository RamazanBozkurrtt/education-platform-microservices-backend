package com.edubase.course.grpc;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class GrpcServerRunner {

    @Value("${grpc.server.port:9092}")
    private int grpcPort;

    private final CourseQueryGrpcService courseQueryGrpcService;
    private Server server;

    @PostConstruct
    void start() {
        try {
            server = NettyServerBuilder.forPort(grpcPort)
                    .addService(courseQueryGrpcService)
                    .build()
                    .start();
            log.info("Course gRPC server started on port {}", grpcPort);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to start course gRPC server", ex);
        }
    }

    @PreDestroy
    void stop() {
        if (server == null) {
            return;
        }
        server.shutdown();
        try {
            if (!server.awaitTermination(5, TimeUnit.SECONDS)) {
                server.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            server.shutdownNow();
        }
    }
}
