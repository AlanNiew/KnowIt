/**
 * 评测 harness。
 *
 * <p>独立 CLI 入口，打成一个不含 Spring Boot repackage 的普通 jar， 由 {@code knowit-server} 用 {@code
 * spring-boot-maven-plugin} 的 excludes 排除， 因此<b>不会</b>并进可执行 jar。
 *
 * <p>真实模型只在这里离线调用。CI 绝不碰真实模型——那会让 CI 慢、贵、 且可能把企业敏感文档发到外网。
 */
package com.knowit.eval;
