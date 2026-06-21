package com.stockagent.common;

import lombok.Data;

/**
 * 统一API响应包装
 */
@Data
public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;
    private boolean success;

    private ApiResponse() {}

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.setCode(200);
        r.setSuccess(true);
        r.setMessage("success");
        r.setData(data);
        return r;
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.setCode(200);
        r.setSuccess(true);
        r.setMessage(message);
        r.setData(data);
        return r;
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.setCode(code);
        r.setSuccess(false);
        r.setMessage(message);
        return r;
    }

    public static <T> ApiResponse<T> fail(String message) {
        return fail(500, message);
    }
}