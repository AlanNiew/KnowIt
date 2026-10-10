package com.knowit.domain;

/**
 * 检索结果：一条命中。
 *
 * @param chunk 命中的 child chunk（用于精确匹配与打分）
 * @param parentContent 展开后的 parent 内容（喂 LLM / 给用户看的完整上下文）
 * @param parentId parent chunk 的 id
 * @param pageLabel 合并后的页码展示，如 "3" 或 "3-4"
 * @param rrfScore RRF 融合分，只反映排名先后
 * @param rerankScore rerank 校准分，绝对可比；未开启 rerank 时为 null
 */
public record RetrievedChunk(
        Chunk chunk,
        String parentContent,
        Long parentId,
        String pageLabel,
        double rrfScore,
        Double rerankScore) {

    /** 用于排序与分级的分数：有 rerank 分就用它，否则退化为 RRF 分。 */
    public double effectiveScore() {
        return rerankScore != null ? rerankScore : rrfScore;
    }
}
