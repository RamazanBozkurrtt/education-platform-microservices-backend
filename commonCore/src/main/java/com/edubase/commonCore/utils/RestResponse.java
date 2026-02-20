package com.edubase.commonCore.utils;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestResponse<T> {

    private boolean success;
    private int status;          // HTTP status code (200, 400, 401...)
    private String message;      // user-friendly message
    private T data;              // payload (success)
    private Object errors;       // payload (error)
    private long timestamp;      // epoch millis

    public static <T> RestResponse<T> ok(T data) {
        return RestResponse.<T>builder()
                .success(true)
                .status(200)
                .message("OK")
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static <T> RestResponse<T> created(T data) {
        return RestResponse.<T>builder()
                .success(true)
                .status(201)
                .message("Created")
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static <T> RestResponse<T> empty(int status, String message) {
        return RestResponse.<T>builder()
                .success(true)
                .status(status)
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static <T> RestResponse<T> empty() {
        return RestResponse.<T>builder()
                .success(true)
                .status(204)
                .message("No Content")
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static <T> RestResponse<T> error(int status, String message) {
        return RestResponse.<T>builder()
                .success(false)
                .status(status)
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static <T> RestResponse<T> error(Object errors, int status, String message) {
        return RestResponse.<T>builder()
                .success(false)
                .status(status)
                .message(message)
                .errors(errors)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
