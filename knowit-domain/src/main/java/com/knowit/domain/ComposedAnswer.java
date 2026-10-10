package com.knowit.domain;

import java.util.List;

/**
 * 组装完成的回答。
 *
 * @param answered 是否给出了回答。false 时前端应展示拒答提示而非空答案
 * @param content 答案文本；摘录模式下为拼接的原文片段
 * @param citations 引用列表。必须与 evidence 中的 chunk 一一对应
 * @param level 证据分级
 * @param style 实际使用的模式
 * @param caveats 需要向用户明示的警示，如"依据有限，仅供参考"
 * @param usedChunkIds 实际被引用的 chunk id，用于引用交叉校验
 */
public record ComposedAnswer(
        boolean answered,
        String content,
        List<Citation> citations,
        EvidenceLevel level,
        AnswerStyle style,
        List<String> caveats,
        List<Long> usedChunkIds) {

    /** 主动拒答。不调用 LLM，直接告诉用户没有依据。 */
    public static ComposedAnswer refuse(EvidenceLevel level, String reason) {
        return new ComposedAnswer(
                false, null, List.of(), level, AnswerStyle.EXCERPT, List.of(reason), List.of());
    }

    /** 拒答时的默认提示。措辞刻意保守：我们宁可显得不能干，也不能显得在编。 */
    public static final String REFUSAL_HINT = "在当前知识库中没有找到足够依据回答这个问题。请补充相关文档，或换个更贴近文档的说法再试。";
}
