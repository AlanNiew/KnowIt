package com.knowit.server.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.knowit.common.ApiResponse;
import com.knowit.common.ErrorCode;
import com.knowit.common.KnowItException;

/**
 * 统一异常处理。
 *
 * <p>规则：业务异常转成对应 HTTP 状态；未知异常一律 500 且<b>不把堆栈返回给客户端</b>—— 堆栈里可能有 SQL、文件路径、内部类名，是信息泄漏面。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(KnowItException.class)
    public ResponseEntity<ApiResponse<Void>> handleKnowIt(KnowItException ex) {
        if (ex.getErrorCode().httpStatus() >= 500) {
            LOG.error("业务异常：code={} msg={}", ex.getErrorCode(), ex.getMessage(), ex);
        } else {
            LOG.warn("业务异常：code={} msg={}", ex.getErrorCode(), ex.getMessage());
        }
        return ResponseEntity.status(ex.getErrorCode().httpStatus())
                .body(ApiResponse.fail(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String detail =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                        .reduce((a, b) -> a + "; " + b)
                        .orElse("参数校验失败");
        return ResponseEntity.badRequest().body(ApiResponse.fail(ErrorCode.BAD_REQUEST, detail));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception ex) {
        LOG.error("未处理异常", ex);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.fail(ErrorCode.INTERNAL, null));
    }
}
