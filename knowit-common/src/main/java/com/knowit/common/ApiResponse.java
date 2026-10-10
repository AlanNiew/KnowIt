package com.knowit.common;

import java.util.List;

/**
 * 统一响应包装。
 *
 * <p>所有 REST 接口必须返回它，不许裸返回实体。 失败时 {@code data} 为 {@code null}，前端只判 {@code success}； 机器可读的错误原因在
 * {@code errorCode}，给人看的在 {@code message}。
 */
public record ApiResponse<T>(boolean success, T data, String errorCode, String message) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(true, null, null, null);
    }

    public static <T> ApiResponse<T> fail(ErrorCode code, String message) {
        return new ApiResponse<>(
                false, null, code.name(), message != null ? message : code.defaultMessage());
    }

    /** 分页数据的成功响应。把 total 提到外层，避免每个接口各写一套。 */
    public static <T> ApiResponse<PageResult<T>> page(
            List<T> items, long total, int page, int size) {
        return ok(new PageResult<>(items, total, page, size));
    }
}
