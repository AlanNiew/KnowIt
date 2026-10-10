package com.knowit.domain;

/**
 * 切片层级。
 *
 * <p>child 参与召回（带 embedding），parent 不参与召回但负责提供完整上下文。 见 ADR-0005。
 */
public enum ChunkLevel {
    /** 语义完整的段落群，约 1024 token，喂给 LLM 或直接给用户看。 */
    PARENT,
    /** 精确切片，约 256 token，带 embedding，参与向量与关键词召回。 */
    CHILD
}
