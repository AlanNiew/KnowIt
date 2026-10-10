/**
 * 文档摄取（ETL）的对外契约。
 *
 * <p>{@link IngestionPipeline} 是三个深模块之一（ADR-0008）： 接口很小（一个方法），内部很深（7 步状态机 + 幂等 + 断点续跑 + 重试 + 死信）。
 * 调用方完全不需要知道中间发生了什么。
 */
package com.knowit.ingestion.api;
