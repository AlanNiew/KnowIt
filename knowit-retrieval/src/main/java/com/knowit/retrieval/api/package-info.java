/**
 * 检索引擎的对外契约。
 *
 * <p>{@link HybridRetriever} 是三个深模块之一（ADR-0008）。 内部复杂度（RRF 融合、pgvector、PG 全文、rerank、parent
 * 展开）全部封在这里。
 */
package com.knowit.retrieval.api;
