package com.knowit.domain;

/**
 * Ingestion 状态机的状态。见 ADR-0007。
 *
 * <p>合法迁移见 {@code docs/architecture/diagrams/03-ingestion-pipeline.mmd}。
 * 每一步的产物落库，因此进程重启可以从中间状态续跑，不必从头再来。
 */
public enum IngestionState {
    /** 已入队，等待消费。 */
    PENDING,
    /** 原文与元数据已提取。 */
    PARSED,
    /** 去噪、编码归一、页眉页脚剔除完成。 */
    CLEANED,
    /** 结构感知切片完成，parent-child 结构已建立。 */
    CHUNKED,
    /** child chunk 向量化完成。 */
    EMBEDDED,
    /** pgvector 与 tsvector 双路索引写入完成。 */
    INDEXED,
    /** 完成。 */
    COMPLETED,
    /** 失败，retryCount 未达上限，可回到上一步重试。 */
    FAILED,
    /** 超过重试上限，需人工介入。 */
    DEAD;

    /** 是否为终态——终态的任务不会再被消费循环领取。 */
    public boolean isTerminal() {
        return this == COMPLETED || this == DEAD;
    }
}
