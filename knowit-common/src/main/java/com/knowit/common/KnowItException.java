package com.knowit.common;

/**
 * 项目内所有可预期异常的基类。
 *
 * <p>约定：业务层只抛这个（或其子类），由 knowit-server 的 {@code GlobalExceptionHandler} 统一转成 HTTP 响应。 禁止在业务代码里直接抛
 * {@code RuntimeException} 裸类——否则调用方无法区分 "参数错"和"系统错"。
 */
public class KnowItException extends RuntimeException {

    private final ErrorCode errorCode;

    public KnowItException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public KnowItException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
