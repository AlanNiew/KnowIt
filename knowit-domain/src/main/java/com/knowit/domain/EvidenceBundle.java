package com.knowit.domain;

import java.util.List;

/**
 * 证据包。{@code retrieval} 与 {@code generation} 两个模块之间的唯一数据契约 （ADR-0008）。正因为有它在，两个模块可以互不依赖。
 *
 * @param query 实际用于检索的 query 列表（多条来自 DECOMPOSE 策略）
 * @param chunks 排好序、已按权限过滤的证据
 * @param level 证据分级结论
 * @param reasoning 分级理由，进入日志与 UI 提示，便于排查"为什么拒答"
 */
public record EvidenceBundle(
        String query, List<RetrievedChunk> chunks, EvidenceLevel level, String reasoning) {

    public static EvidenceBundle empty(String query, String reasoning) {
        return new EvidenceBundle(query, List.of(), EvidenceLevel.NONE, reasoning);
    }

    public boolean isEmpty() {
        return chunks == null || chunks.isEmpty();
    }

    /** 取前 n 条证据。小模型上下文紧张，必须能裁剪。 */
    public List<RetrievedChunk> top(int n) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        return chunks.subList(0, Math.min(n, chunks.size()));
    }
}
