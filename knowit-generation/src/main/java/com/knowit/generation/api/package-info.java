/**
 * 回答组装的对外契约。
 *
 * <p>{@link AnswerComposer} 是三个深模块之一（ADR-0008）。 本模块禁止依赖 knowit-retrieval——两者之间的唯一桥梁是 {@code
 * domain.EvidenceBundle}。
 */
package com.knowit.generation.api;
