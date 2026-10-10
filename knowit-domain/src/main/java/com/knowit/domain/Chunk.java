package com.knowit.domain;

import java.util.List;

/**
 * 切片。parent-child 双层，见 ADR-0005。
 *
 * <p>{@code kbId} 与 acl 判定所需的冗余字段在此层保留， 使权限过滤能合并进同一条 SQL（ADR-0011），而不是查回来再筛。
 *
 * @param id 主键
 * @param kbId 冗余的知识库 id，权限过滤用
 * @param documentId 所属文档
 * @param parentId child 指向 parent；parent 行为 null
 * @param level PARENT 或 CHILD
 * @param ordinal 在父切片内的序号
 * @param content 文本内容
 * @param pageStart 起始页码；非分页格式可为 null
 * @param pageEnd 结束页码
 * @param charStart 在原文中的偏移量，用于前端精确高亮
 * @param tokens jieba 分词结果；写入 tsvector 用
 * @param embedding 向量，1024 维；仅 CHILD 有值
 */
public record Chunk(
        Long id,
        Long kbId,
        Long documentId,
        Long parentId,
        ChunkLevel level,
        int ordinal,
        String content,
        Integer pageStart,
        Integer pageEnd,
        Integer charStart,
        List<String> tokens,
        List<Float> embedding) {

    public Chunk {
        if (kbId == null) {
            // kbId 冗余是权限过滤的前提，不允许缺失
            throw new IllegalArgumentException("chunk.kbId 必须存在，否则无法做 SQL 层权限过滤");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("chunk.content 不能为空");
        }
        if (level == ChunkLevel.CHILD && parentId == null) {
            throw new IllegalArgumentException("child chunk 必须指向 parent");
        }
        if (level == ChunkLevel.PARENT && parentId != null) {
            throw new IllegalArgumentException("parent chunk 不能有 parentId");
        }
        if (level == ChunkLevel.CHILD && embedding == null) {
            throw new IllegalArgumentException("child chunk 必须带 embedding，否则无法参与向量召回");
        }
        if (level == ChunkLevel.PARENT && embedding != null) {
            throw new IllegalArgumentException("parent chunk 不参与召回，不应存 embedding");
        }
    }

    public boolean isParent() {
        return level == ChunkLevel.PARENT;
    }

    /** 页码展示文本。非分页格式返回 null，由前端降级为区块号。 */
    public String pageLabel() {
        if (pageStart == null) {
            return null;
        }
        if (pageEnd == null || pageStart.equals(pageEnd)) {
            return String.valueOf(pageStart);
        }
        return pageStart + "-" + pageEnd;
    }
}
