package com.knowit.domain;

/**
 * 检索器入参：一个问题。
 *
 * <p>与 {@link Question} 分开是有意的：Query 是"去历史化"的检索请求， Question 是"用户当下真正问的那句话"。QueryRewriter 负责把后者变成前者。
 *
 * @param text 检索用文本
 * @param kbId 目标知识库
 * @param topK 期望的候选数
 * @param origin 来源：DIRECT（原文）、REWRITE（改写）、DECOMPOSE（拆分）
 */
public record Query(String text, Long kbId, int topK, String origin) {

    public Query {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("query 不能为空");
        }
        if (kbId == null) {
            throw new IllegalArgumentException("query 必须指定知识库");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("query.topK 必须为正");
        }
    }
}
