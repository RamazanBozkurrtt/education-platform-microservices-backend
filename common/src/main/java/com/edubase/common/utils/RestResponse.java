package com.edubase.common.utils;

import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

@Getter
@Setter
public class RestResponse<T> {

    private int statusCode;
    private String status;
    private String message;
    private T data;
    private LocalDateTime timestamp;
    private boolean success;

    private RestResponse(HttpStatus status, String message,T data,boolean success) {
        this.statusCode = status.value();
        this.status = status.name();
        this.message = message;
        this.data = data;
        this.success = success;
        this.timestamp = LocalDateTime.now();
    }

    public static <T> RestResponse<T> ok(T data) {
        return new RestResponse<>(HttpStatus.OK, "Success", data, true);
    }

    public static <T> RestResponse<T> created(T data) {
        return new RestResponse<>(HttpStatus.CREATED, "Created successfully", data, true);
    }

    public static <T> RestResponse<T> empty() {
        return new RestResponse<>(HttpStatus.NO_CONTENT, "No Content", null, true);
    }

    public static <T> RestResponse<T> error(T data, HttpStatus status, String message) {
        return new RestResponse<>(status, message, data, false);
    }

    public static <T> RestResponse<T> error(HttpStatus status, String message) {
        return new RestResponse<>(status, message, null, false);
    }

}
