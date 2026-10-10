package com.knowit.domain;

import java.math.BigDecimal;

/**
 * 引用。见 ADR-0014：必须携带页码与原文定位信息。
 *
 * <p>{@code score} 是 rerank cross-encoder 的校准分，不是 embedding 相似度—— 前者绝对可比，后者不是。
 *
 * @param id 主键
 * @param chunkId 引用的切片
 * @param documentId 所属文档
 * @param fileName 文件名，前端展示
 * @param pageNo 页码；非分页格式允许为 null，但必须有 charStart 兜底
 * @param charStart 原文偏移，前端精确高亮
 * @param score rerank 校准分
 * @param snippet 引用原文片段
 */
public record Citation(
        Long id,
        Long chunkId,
        Long documentId,
        String fileName,
        Integer pageNo,
        Integer charStart,
        BigDecimal score,
        String snippet) {

    public Citation {
        if (chunkId == null) {
            throw new IllegalArgumentException("citation 必须指向具体 chunk");
        }
        // 页码与偏移至少要有一个，否则用户无法回到原文
        if (pageNo == null && charStart == null) {
            throw new IllegalArgumentException("citation 必须带 pageNo 或 charStart，否则不可验证");
        }
        if (snippet == null || snippet.isBlank()) {
            throw new IllegalArgumentException("citation 必须带 snippet，否则用户看不到依据");
        }
    }
}
