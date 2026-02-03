package com.edubase.common.utils;

import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import java.time.LocalDateTime;

@Getter
@Builder
public class RestResponse<T> {

    private final int statusCode;
    private final String status;
    private final String message;
    private final T data;
    private final LocalDateTime timestamp;
    private final boolean success;

    private RestResponse(int statusCode, String status, String message, T data, boolean success) {
        this.statusCode = statusCode;
        this.status = status;
        this.message = message;
        this.data = data;
        this.success = success;
        this.timestamp = LocalDateTime.now();
    }

    // --- Static Factory Methods ---

    public static <T> RestResponse<T> ok(T data) {
        return new RestResponse<>(HttpStatus.OK.value(), HttpStatus.OK.name(), "Success", data, true);
    }

    public static <T> RestResponse<PageResponse<T>> ok(PageResponse<T> pageData) {
        return new RestResponse<>(HttpStatus.OK.value(), HttpStatus.OK.name(), "Pagination Success", pageData, true);
    }

    public static <T> RestResponse<T> created(T data) {
        return new RestResponse<>(HttpStatus.CREATED.value(), HttpStatus.CREATED.name(), "Created successfully", data, true);
    }

    public static <T> RestResponse<T> empty() {
        return new RestResponse<>(HttpStatus.NO_CONTENT.value(), HttpStatus.NO_CONTENT.name(), "No Content", null, true);
    }

    public static <T> RestResponse<T> error(T data, HttpStatus status, String message) {
        return new RestResponse<>(status.value(), status.name(), message, data, false);
    }

    public static <T> RestResponse<T> error(HttpStatus status, String message) {
        return new RestResponse<>(status.value(), status.name(), message, null, false);
    }
}