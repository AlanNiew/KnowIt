package com.knowit.iam.api;

import java.util.Set;

/**
 * 检索期的访问上下文。见 ADR-0011。
 *
 * <p><b>为什么它必须是 {@code HybridRetriever.retrieve()} 的必传入参， 而不是从 ThreadLocal / SecurityContextHolder
 * 里取：</b> 接口签名必须让人一眼看出"这里需要权限"，否则调用方会写出无 ACL 过滤的查询。 把安全参数藏在线程上下文中，是权限漏防最常见的原因。
 *
 * @param userId 当前用户
 * @param roles 角色集合，如 ADMIN / OWNER / MEMBER
 * @param allowedKbIds 有权限访问的知识库 id；检索时直接作为 SQL 的 kb_id 过滤条件
 * @param admin 管理员可跨库，仍保留 allowedKbIds 以便审计
 */
public record AccessContext(Long userId, Set<String> roles, Set<Long> allowedKbIds, boolean admin) {

    public AccessContext {
        if (userId == null && !admin) {
            throw new IllegalArgumentException("非管理员上下文必须带 userId");
        }
        allowedKbIds = allowedKbIds == null ? Set.of() : Set.copyOf(allowedKbIds);
    }

    /** 没有可访问范围时，任何查询都应当返回空，而不是全库。 */
    public boolean hasAnyScope() {
        return admin || !allowedKbIds.isEmpty();
    }
}
