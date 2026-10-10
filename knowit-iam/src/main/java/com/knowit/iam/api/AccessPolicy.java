package com.knowit.iam.api;

import com.knowit.domain.KnowledgeBase;

/**
 * 从当前用户解析出 {@link AccessContext}。
 *
 * <p>P0 只有本地账号密码一种实现（{@code internal/}）。 P3 通过 {@code spis/AuthenticationProvider} 接入 OIDC / LDAP /
 * 钉钉 / 企微 / 飞书，本接口签名不变。
 */
public interface AccessPolicy {

    /**
     * @param userId 当前用户 id
     * @param kbIds 本次要访问的知识库；策略会与用户实际权限求交
     * @return 访问上下文；用户无任何权限时返回空 scope，由检索器短路返回空结果
     */
    AccessContext resolve(Long userId, Iterable<Long> kbIds);

    /** 用户是否有权访问指定知识库。单个判定用，避免为了一次判断构造整个上下文。 */
    boolean canAccess(Long userId, KnowledgeBase kb);
}
