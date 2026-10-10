package com.knowit.domain;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("证据包与分级")
class EvidenceBundleTest {

    @Test
    @DisplayName("NONE 不可回答，其余三级可回答")
    void noneIsNotAnswerable() {
        assertThat(EvidenceLevel.NONE.isAnswerable()).isFalse();
        assertThat(EvidenceLevel.WEAK.isAnswerable()).isTrue();
        assertThat(EvidenceLevel.PARTIAL.isAnswerable()).isTrue();
        assertThat(EvidenceLevel.SUFFICIENT.isAnswerable()).isTrue();
    }

    @Test
    @DisplayName("空证据包自动落到 NONE")
    void emptyBundleIsNone() {
        EvidenceBundle bundle = EvidenceBundle.empty("问题", "没有命中");

        assertThat(bundle.level()).isEqualTo(EvidenceLevel.NONE);
        assertThat(bundle.isEmpty()).isTrue();
        assertThat(bundle.top(5)).isEmpty();
    }

    @Test
    @DisplayName("top(n) 不会越界——小模型上下文紧张，裁剪是常态")
    void topIsBounded() {
        RetrievedChunk first = chunk(1L, 0.9);
        RetrievedChunk second = chunk(2L, 0.5);
        EvidenceBundle bundle =
                new EvidenceBundle("q", List.of(first, second), EvidenceLevel.PARTIAL, "两路命中");

        assertThat(bundle.top(1)).containsExactly(first);
        assertThat(bundle.top(99)).containsExactly(first, second);
    }

    private static RetrievedChunk chunk(long id, double score) {
        return new RetrievedChunk(
                new Chunk(
                        id,
                        7L,
                        42L,
                        id,
                        ChunkLevel.CHILD,
                        0,
                        "内容",
                        1,
                        1,
                        0,
                        List.of("a"),
                        List.of(0.1f)),
                "父内容",
                id,
                "1",
                score,
                score);
    }
}
