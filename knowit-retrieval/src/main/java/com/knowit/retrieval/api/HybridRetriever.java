package com.knowit.retrieval.api;

import com.knowit.domain.EvidenceBundle;
import com.knowit.domain.Query;
import com.knowit.iam.api.AccessContext;

/**
 * 双路混合检索：pgvector 语义 + PG 关键词全文，RRF 融合，rerank 校准。
 *
 * <p><b>{@code accessContext} 是必传入参（ADR-0011）。</b> 权限过滤必须发生在 SQL 层，而不是查回来再筛——
 * 后者意味着越权数据已经被读进内存，堆转储、日志、异常栈都可能泄漏。 本接口因此<b>不提供</b>不带 accessContext 的重载，从接口层面杜绝这种写法。
 */
public interface HybridRetriever {

    /**
     * @param query 问题（已经 QueryRewriter 处理过）
     * @param accessContext 访问上下文；无任何可访问 scope 时直接返回空证据包
     * @param profile 知识库的检索配置 JSON
     * @return 排好序、带页码、已去除重的证据包
     */
    EvidenceBundle retrieve(Query query, AccessContext accessContext, String profile);
}
