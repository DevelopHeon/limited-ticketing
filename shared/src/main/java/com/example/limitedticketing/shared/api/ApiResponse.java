package com.example.limitedticketing.shared.api;

public record ApiResponse<T>(String code, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("SUCCESS", data);
    }
}
