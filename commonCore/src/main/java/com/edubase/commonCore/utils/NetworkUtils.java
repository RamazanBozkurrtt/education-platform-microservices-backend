package com.edubase.commonCore.utils;

import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

@Slf4j
public class NetworkUtils {

    private static final String UNKNOWN = "Unknown";

    private NetworkUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            log.warn("Hostname alınamadı, '{}' kullanılıyor. Hata: {}", UNKNOWN, e.getMessage());
            return UNKNOWN;
        }
    }

    public static String getHostAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            log.error("IP Adresi alınamadı! Mikroservis iletişimi aksayabilir.");
            return InetAddress.getLoopbackAddress().getHostAddress();
        }
    }

    /**
     * Consul gibi servis kayıt araçları için benzersiz bir Instance ID üretir.
     * Format: {service-name}:{ip}:{port}:{random-uuid}
     */
    public static String generateInstanceId(String serviceName, int port) {
        if (serviceName == null || serviceName.isEmpty()) {
            serviceName = "default-service";
        }

        String ip = getHostAddress();
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        // Örn: course-service:192.168.1.15:8081:a1b2c3d4
        return String.format("%s:%s:%d:%s", serviceName, ip, port, uniqueSuffix);
    }
}
