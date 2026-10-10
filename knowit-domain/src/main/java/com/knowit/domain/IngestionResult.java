package com.knowit.domain;

/**
 * Ingestion 结果。
 *
 * @param documentId 文档 id
 * @param parentChunks 产出的 parent 切片数
 * @param childChunks 产出的 child 切片数
 * @param state 终态：COMPLETED 或 DEAD
 * @param errorMessage 失败原因；成功为 null
 */
public record IngestionResult(
        Long documentId,
        int parentChunks,
        int childChunks,
        IngestionState state,
        String errorMessage) {}
