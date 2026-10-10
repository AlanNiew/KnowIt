package com.knowit.common;

/**
 * 错误码。HTTP 语义与机器可读码分离。
 *
 * <p>命名规则：{@code <域>_<具体问题>}，全大写下划线。 新增错误码只许追加，不许改已有码的含义——前端会按码做文案映射。
 */
public enum ErrorCode {

    // ---- 通用 ----
    BAD_REQUEST(400, "请求参数不合法"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "资源状态冲突"),
    INTERNAL(500, "服务内部错误，请查看日志"),

    // ---- 文档 / 摄取 ----
    DOCUMENT_NOT_FOUND(404, "文档不存在"),
    DOCUMENT_TOO_LARGE(413, "文件超过大小上限"),
    DOCUMENT_TYPE_UNSUPPORTED(415, "不支持的文件类型"),
    INGESTION_FAILED(500, "文档解析或索引进失败"),

    // ---- 检索 / 生成 ----
    KNOWLEDGE_BASE_NOT_FOUND(404, "知识库不存在"),
    NO_ACCESSIBLE_SCOPE(403, "没有可访问的知识库"),
    INSUFFICIENT_EVIDENCE(422, "知识库中没有足够依据回答该问题"),

    // ---- 推理后端 ----
    INFERENCE_UNAVAILABLE(503, "本地推理服务不可用");

    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(int httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
