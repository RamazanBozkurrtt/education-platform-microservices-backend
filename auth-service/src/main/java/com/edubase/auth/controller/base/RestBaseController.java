package com.edubase.auth.controller.base;

import com.edubase.common.utils.PageResponse;
import com.edubase.common.utils.RestResponse;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class RestBaseController {

    public <T> ResponseEntity<RestResponse<T>> ok(T data) {
        return ResponseEntity.ok(RestResponse.ok(data));
    }

    public <T> ResponseEntity<RestResponse<PageResponse<T>>> ok(Page<T> page) {
        return ResponseEntity.ok(RestResponse.ok(PageResponse.of(page)));
    }

    public <T> ResponseEntity<RestResponse<T>> created(T data) {
        return ResponseEntity.status(HttpStatus.CREATED).body(RestResponse.created(data));
    }

    public <T> ResponseEntity<RestResponse<T>> noContent() {
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(RestResponse.empty());
    }
}
