package com.knowit.domain;

/**
 * 四级证据充分度。证据不足时主动拒答，是"不编造"的最后一道闸。
 *
 * <p>阈值必须按客户实测校准——不同 embedding 模型、不同文档类型的分数分布完全不同。 校准工具在 knowit-eval 中。
 */
public enum EvidenceLevel {
    /** 没有可用证据。直接拒答，不调用 LLM。 */
    NONE,
    /** 证据薄弱。回答但标注"依据有限，仅供参考"。 */
    WEAK,
    /** 证据部分充分。正常回答，标注未覆盖的方面。 */
    PARTIAL,
    /** 证据充分。正常回答，禁止臆测。 */
    SUFFICIENT;

    /** 该等级是否值得花 token 生成回答。 */
    public boolean isAnswerable() {
        return this == WEAK || this == PARTIAL || this == SUFFICIENT;
    }
}
