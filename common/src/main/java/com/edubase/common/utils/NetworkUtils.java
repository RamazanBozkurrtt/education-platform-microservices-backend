package com.edubase.common.utils;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Slf4j
public class NetworkUtils {

    private NetworkUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            log.warn("Hostname alınamadı, 'Unknown' kullanılıyor.");
            return "Unknown";
        }
    }

}
