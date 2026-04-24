package com.mint.health.controller;

import com.mint.health.common.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ApiResponse<?> handle(Exception e) {
        log.error("请求处理异常", e);
        return ApiResponse.fail(e.getMessage() == null ? "系统异常" : e.getMessage());
    }
}
