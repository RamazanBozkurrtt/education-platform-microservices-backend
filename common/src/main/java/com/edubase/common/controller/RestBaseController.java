package com.edubase.common.controller;

import com.edubase.common.utils.RestResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class RestBaseController {

    public <T> ResponseEntity<RestResponse<T>> ok(T data) {
        return ResponseEntity.ok(RestResponse.ok(data));
    }

    public <T> ResponseEntity<RestResponse<T>> created(T data) {
        return ResponseEntity.status(HttpStatus.CREATED).body(RestResponse.created(data));
    }

    public <T> ResponseEntity<RestResponse<T>> noContent() {
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(RestResponse.empty());
    }
}
