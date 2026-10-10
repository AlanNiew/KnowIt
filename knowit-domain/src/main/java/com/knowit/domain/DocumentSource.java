package com.knowit.domain;

/**
 * Ingestion 入参：一份待处理的文档及其全部元数据。
 *
 * @param document 文档基本信息
 * @param bytes 文件内容。P0 直接读入内存（有 20MB 上限）；P1 改分片后改为流
 * @param profile 切片 / 检索 / 回答参数
 */
public record DocumentSource(Document document, byte[] bytes, String profile) {

    public DocumentSource {
        if (document == null) {
            throw new IllegalArgumentException("document 不能为空");
        }
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("文档内容为空");
        }
    }
}
