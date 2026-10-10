package com.knowit.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("统一响应包装")
class ApiResponseTest {

    @Test
    @DisplayName("成功响应不带 errorCode")
    void okCarriesDataWithoutErrorCode() {
        ApiResponse<String> response = ApiResponse.ok("hello");

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo("hello");
        assertThat(response.errorCode()).isNull();
    }

    @Test
    @DisplayName("失败响应带上机器可读错误码")
    void failCarriesErrorCode() {
        ApiResponse<Void> response = ApiResponse.fail(ErrorCode.NOT_FOUND, null);

        assertThat(response.success()).isFalse();
        assertThat(response.errorCode()).isEqualTo("NOT_FOUND");
        // message 为空时回落到错误码的默认文案，前端不必维护两份映射
        assertThat(response.message()).isEqualTo("资源不存在");
    }

    @Test
    @DisplayName("自定义 message 覆盖默认文案")
    void failMessageOverridesDefault() {
        ApiResponse<Void> response = ApiResponse.fail(ErrorCode.NOT_FOUND, "知识库 42 不存在");

        assertThat(response.message()).isEqualTo("知识库 42 不存在");
    }
}
