# ADR-0008: 三个深模块作为系统脊梁

- 状态：已接受
- 日期：2026-10-10
- 影响范围：全项目架构

## 背景

RAG 系统的复杂度天然分布不均：
- 文档解析、切片策略的复杂度极高且多变
- 检索融合、rerank 的算法细节极其琐碎
- Prompt 组装、流式推送的坑很深

如果把所有这些细节都暴露给 Controller 层，代码会迅速腐化——
每个端点都要懂"怎么切分、怎么融合、怎么拼 Prompt"。

反过来，如果做成一个巨型 `RagService`，接口会膨胀到几十个方法，
每次调优切片策略都要改接口签名，调用方全部受影响。

我们要的是**接口很小、内部很深**的模块：调用方只需要懂三件事，
其余复杂度封在里面。

## 决定

系统脊梁是三个深模块接口：

```java
// 入口：一个文件流。出口：索引好的 chunk。
public interface IngestionPipeline {
    IngestionResult ingest(DocumentSource source, IngestionProfile profile);
}

// 入口：一个问题 + 权限上下文。出口：排好序、带页码、已过滤权限的证据。
public interface HybridRetriever {
    EvidenceBundle retrieve(Query query, AccessContext accessContext, RetrievalProfile profile);
}

// 入口：一份证据。出口：答案 + 引用 + 置信度 + 是否拒答。
public interface AnswerComposer {
    ComposedAnswer compose(EvidenceBundle evidence, Question question, AnswerStyle style);
}
```

配套规则：
1. 三个接口的方法签名**从 P0 起就不再变**，内部实现可以任意重构
2. 它们之间的唯一数据契约是 `domain.EvidenceBundle`（以及 `Query` / `Question`）——
   `retrieval` 与 `generation` 因此**互不依赖**
3. 其他模块只允许 import `knowit-<module>/api/`，`internal/` 由 ArchUnit 拦死
4. 新增能力优先往这三个模块内部加，不往上加接口

## 后果

### 正面
- Controller 层极薄：拿到问题 → 调 retriever → 调 composer → 返回
- 切分策略从 256 token 调成 384 token，**不需要改任何接口**，调用方无感
- 单元测试可以直接对着接口测纯逻辑，不需要起 Spring
- 新人读代码的路径被固定：先读三个接口，再深入感兴趣的模块
- 将来真要拆微服务，切口已经画好了

### 负面 / 代价
- **接口设计错误代价极高**——一旦签名定错，全项目跟着改。所以 P0 要花时间把签名想透
- 有些场景接口装不下，容易诱发"万能参数对象"（一个 `Map` 传所有）。
  缓解：参数对象必须是强类型的 `profile`，不允许裸 Map
- 三个接口之间的 `EvidenceBundle` 会成为事实上的"万能 DTO"，
  有膨胀风险。缓解：字段只加不改，废弃字段标注 `@Deprecated` 保留一个大版本

## 考虑过的替代方案

| 方案 | 为什么否 |
|---|---|
| 一个 `RagService` 大接口 | 方法会膨胀到几十个，任何调优都改签名 |
| 按技术分层切割（Parser / Embedder / Searcher） | 调用方要自己编排流程，"怎么串起来"的知识散落在每个调用点，无法统一治理 |
| 直接用 Spring AI 的 `RetrievalAugmentationAdvisor` 一把梭 | 黑盒，无法做权限 SQL 内联（ADR-0011）、无法做页码级引用、无法做摘录模式 |

## 验证方式

- Controller 层任一方法体不超过 20 行有效代码
- 改一次切分参数（256→384 token）的 PR，diff 只涉及 `knowit-retrieval/internal/`
- 单一模块的单元测试覆盖率 ≥ 80%（parser / retriever / grader / composer）
