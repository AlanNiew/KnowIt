package com.knowit.domain;

/**
 * 文档。只描述"这是什么文档"，不含解析产物。
 *
 * @param id 主键
 * @param kbId 所属知识库；检索时冗余到 chunk 上用于 ACL 过滤
 * @param fileName 原始文件名
 * @param contentType MIME 类型
 * @param sizeBytes 字节数
 * @param sha256 内容哈希，用于秒传检测与幂等
 * @param blobUri 对象存储或本地路径
 */
public record Document(
        Long id,
        Long kbId,
        String fileName,
        String contentType,
        long sizeBytes,
        String sha256,
        String blobUri) {

    private static final long MAX_SIZE_BYTES = 20L * 1024 * 1024;

    /** P0 只支持单文件直传，超过 20MB 直接拒绝；分片上传在 P1。 */
    public boolean withinP0SizeLimit() {
        return sizeBytes <= MAX_SIZE_BYTES;
    }
}
