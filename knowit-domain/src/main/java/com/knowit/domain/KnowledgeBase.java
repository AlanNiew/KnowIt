package com.knowit.domain;

/**
 * 知识库。一等公民，与协作组织（群组）分离。
 *
 * @param id 主键
 * @param name 名称
 * @param description 用途说明，会进入检索 prompt 帮模型判断范围
 * @param ownerId 创建者
 * @param retrievalProfile 切片 / 检索 / 回答参数；变更会使 profileHash 变化触发重建索引
 * @param profileHash retrievalProfile 的稳定哈希，用于幂等判断
 */
public record KnowledgeBase(
        Long id,
        String name,
        String description,
        Long ownerId,
        String retrievalProfile,
        String profileHash) {

    public KnowledgeBase {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("知识库名称不能为空");
        }
    }
}
