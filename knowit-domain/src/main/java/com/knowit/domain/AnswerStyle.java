package com.knowit.domain;

/** 回答模式。见 ADR-0006。 */
public enum AnswerStyle {
    /** 摘录模式：不调用 LLM，直接呈现原文片段（关键词高亮 + 页码）。 零幻觉、100% 可解释、延迟低于一秒。弱硬件下的默认模式。 */
    EXCERPT,

    /** AI 总结模式：调用本地 LLM 做结构化输出（答案 + 引用）。 有 GPU 时的默认模式。 */
    SUMMARY
}
