package com.knowit.domain;

import java.util.List;

/**
 * 用户当下提出的问题（含会话上下文）。
 *
 * @param text 原始问题
 * @param kbId 目标知识库
 * @param conversationId 会话 id；首轮为 null
 * @param history 近期消息，用于消解指代；上限由会话层控制
 * @param style 期望的回答模式；null 表示按硬件档位自动选择
 */
public record Question(
        String text, Long kbId, Long conversationId, List<String> history, AnswerStyle style) {

    public Question {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("问题不能为空");
        }
        if (kbId == null) {
            throw new IllegalArgumentException("问题必须指定知识库");
        }
    }
}
