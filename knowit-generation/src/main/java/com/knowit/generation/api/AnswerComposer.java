package com.knowit.generation.api;

import com.knowit.domain.ComposedAnswer;
import com.knowit.domain.EvidenceBundle;
import com.knowit.domain.Question;

/**
 * 从证据到回答。
 *
 * <p>两种模式（ADR-0006）：
 *
 * <ul>
 *   <li>{@code EXCERPT} 摘录模式——不调用 LLM，直接呈现原文片段。 零幻觉、延迟低于一秒，是弱硬件下的默认模式。
 *   <li>{@code SUMMARY} AI 总结模式——调用本地 LLM 做结构化输出， 输出与证据包交叉校验，模型编造的引用一律丢弃。
 * </ul>
 *
 * <p>调用方不需要感知当前是哪种模式，也不需要感知 LLM 是否可用—— 后端不可用时自动降级为摘录模式。
 */
public interface AnswerComposer {

    /**
     * @param evidence 已经过权限过滤与分级的证据包
     * @param question 用户当下的问题
     * @param style 期望模式；null 表示按硬件档位自动选择
     * @return 回答；证据不足时 {@code answered=false} 且带拒答说明
     */
    ComposedAnswer compose(EvidenceBundle evidence, Question question, String style);
}
