package com.knowit.common;

/**
 * 领域事件标记接口。
 *
 * <p>P0 只有接口没有实现。T0.3 的 Ingestion 状态机会用它在 "文档索引进完成"时通知检索层失效缓存。 之所以不用 Spring 的 {@code
 * ApplicationEvent}：发布方不应感知 Spring 容器， 否则 domain / ingestion 模块会引入框架依赖，破坏分层。
 */
public interface DomainEvent {}
