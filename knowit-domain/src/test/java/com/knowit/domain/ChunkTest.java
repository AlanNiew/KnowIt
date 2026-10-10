package com.knowit.domain;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("切片不变量")
class ChunkTest {

    private static Chunk parent() {
        return new Chunk(
                1L, 7L, 42L, null, ChunkLevel.PARENT, 0, "完整的制度条款上下文", 3, 4, 120, null, null);
    }

    private static Chunk child(Long parentId) {
        return new Chunk(
                2L,
                7L,
                42L,
                parentId,
                ChunkLevel.CHILD,
                0,
                "报销单次不超过 2000 元",
                3,
                3,
                145,
                List.of("报销", "2000", "元"),
                List.of(0.1f, 0.2f, 0.3f));
    }

    @Test
    @DisplayName("child 必须有 parentId——parent-child 结构不容许悬空")
    void childMustReferenceParent() {
        assertThatThrownBy(
                        () ->
                                new Chunk(
                                        2L,
                                        7L,
                                        42L,
                                        null,
                                        ChunkLevel.CHILD,
                                        0,
                                        "内容",
                                        1,
                                        1,
                                        0,
                                        List.of("a"),
                                        List.of(0.1f)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("parent");
    }

    @Test
    @DisplayName("parent 不得携带 embedding——不参与召回，存了就是浪费 4GB")
    void parentMustNotCarryEmbedding() {
        assertThatThrownBy(
                        () ->
                                new Chunk(
                                        1L,
                                        7L,
                                        42L,
                                        null,
                                        ChunkLevel.PARENT,
                                        0,
                                        "内容",
                                        1,
                                        1,
                                        0,
                                        null,
                                        List.of(0.1f)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("embedding");
    }

    @Test
    @DisplayName("kbId 必须存在——它是 SQL 层权限过滤的前提（ADR-0011）")
    void kbIdIsMandatoryForAccessControl() {
        assertThatThrownBy(
                        () ->
                                new Chunk(
                                        1L,
                                        null,
                                        42L,
                                        null,
                                        ChunkLevel.PARENT,
                                        0,
                                        "内容",
                                        1,
                                        1,
                                        0,
                                        null,
                                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kbId");
    }

    @Test
    @DisplayName("单页切片页码展示为单数字，跨页为区间")
    void pageLabelRangesAcrossPages() {
        assertThat(parent().pageLabel()).isEqualTo("3-4");
        assertThat(child(1L).pageLabel()).isEqualTo("3");
    }

    @Test
    @DisplayName("非分页格式允许 pageNo 为 null，由 charStart 兜底")
    void pageLabelFallsBackToNull() {
        Chunk htmlChunk =
                new Chunk(
                        3L,
                        7L,
                        42L,
                        null,
                        ChunkLevel.PARENT,
                        0,
                        "html 无页码",
                        null,
                        null,
                        0,
                        null,
                        null);

        assertThat(htmlChunk.pageLabel()).isNull();
    }
}
