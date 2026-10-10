# 模块划分与深模块设计

## 1. 为什么是模块化单体，而不是微服务

目标客户是中小企业，硬件 8C16G 起步。微服务在这个规模下是纯负担：多进程内存开销、服务发现、
分布式事务、部署复杂度，全部落在"无人专职运维"的客户机房里。

**模块化单体**（modular monolith）给出的是：**编译期隔离 + 运行期单进程**。
模块之间只通过接口依赖，边界由 Maven `compile` 依赖表达，违规依赖由 ArchUnit 测试在 CI 里拦死。
将来真要拆服务，切口已经画好了。

---

## 2. 八个 Maven 模块

```
knowit-parent                       (pom: 依赖统一管理 + BOM)
├── knowit-common                   响应包装 / 异常体系 / 分页 / 领域事件基类
├── knowit-domain                   纯领域模型与仓储接口，零框架注解，零业务依赖
├── knowit-iam                      User / Role / Permission / AccessPolicy / AuthenticationProvider SPI
├── knowit-ingestion                DocumentParser SPI / IngestionPipeline / 切分 / 向量化 / 双路索引
├── knowit-retrieval                HybridRetriever / RRF / DocumentCompressor / EvidenceGrader
├── knowit-generation               AnswerComposer / PromptBuilder / 结构化输出 / SSE 流式
├── knowit-eval                     评测 harness，独立 CLI 入口，不打进主 jar
└── knowit-server                   装配：Controller / 配置 / 静态资源 / SpringBootApplication
```

### 依赖规则（ArchUnit 强制）

```
knowit-server   → iam, ingestion, retrieval, generation
iam             → domain
ingestion       → domain
retrieval       → domain, iam
generation      → domain, iam
domain          → common
common          → （无）
knowit-eval     → retrieval, generation, domain   （测试期专用，不被 server 依赖）
```

**三条红线**：
1. `domain` 不 import 任何 Spring / MyBatis / 第三方注解——它是纯 POJO 与接口。
2. `common` 不允许反向依赖任何业务模块。
3. `retrieval` 与 `generation` 互相不依赖；它们之间的唯一桥梁是 `domain.EvidenceBundle`。

---

## 3. 三个深模块

系统质量取决于少数几个接口的清晰度。KnowIt 把复杂度压进三个"深模块"：
**接口很小，内部很深**。详见 [ADR-0008](adr/0008-deep-module-interfaces.md)。

### 3.1 `IngestionPipeline` — 从文件到索引

```java
public interface IngestionPipeline {
    /**
     *  ingest 是幂等的：同一 (documentId, profileHash) 重复调用不会重复索引。
     */
    IngestionResult ingest(DocumentSource source, IngestionProfile profile);
}
```

**内部**：7 步有状态机驱动的 ETL，每步产物落库，可断点续跑、可重试、可观测。
调用方完全不需要知道中间有多少步骤、失败怎么恢复。

### 3.2 `HybridRetriever` — 从问题到证据

```java
public interface HybridRetriever {
    /**
     * accessContext 是必传入参：权限过滤必须发生在 SQL 层，不能事后筛。
     */
    EvidenceBundle retrieve(Query query, AccessContext accessContext, RetrievalProfile profile);
}
```

**内部**：query 改写 → 向量通道 + 关键词通道并行召回 → RRF 融合 → parent 展开 →
rerank 校准 → 去重聚类。调用方拿到的是已经排好序、带页码、带得分、带权限过滤的证据。

### 3.3 `AnswerComposer` — 从证据到回答

```java
public interface AnswerComposer {
    /**
     *  返回必须包含：答案、引用列表、置信度、是否拒答。
     *  LLM 不可用时退化为摘录模式，调用方无需感知。
     */
    ComposedAnswer compose(EvidenceBundle evidence, Question question, AnswerStyle style);
}
```

**内部**：证据分级判定 → 选择摘录/AI 总结模式 → Prompt 组装 → 结构化输出 →
引用回填与页码校验 → SSE 流式推送。

---

## 4. SPI 扩展点

私有化交付会遇到各种客户的异构环境。KnowIt 在以下位置预留 SPI，
**每个 SPI 都有一个"本地默认实现"和一个"生产替换实现"**：

| SPI | 默认实现 | 可替换为 |
|---|---|---|
| `DocumentParser` | `TikaDocumentParser`（LangChain4j） | OCR sidecar / MinerU / Docling HTTP 服务 |
| `EmbeddingProvider` | `OpenAiCompatibleEmbeddingProvider` | ONNX Runtime Java 内嵌 / 阿里 DashScope（仅开发） |
| `ChatProvider` | `OpenAiCompatibleChatProvider` | vLLM / SGLang / llama.cpp / 任何 OpenAI 兼容端点 |
| `BlobStorage` | `FileSystemBlobStorage` | MinIO / S3 / NAS |
| `KeywordSearchEngine` | `PostgresFtsEngine`（应用层分词 + tsvector） | Meilisearch / OpenSearch |
| `AuthenticationProvider` | `LocalAccountProvider`（账号密码） | OIDC / LDAP / 钉钉 / 企微 / 飞书 |

**SPI 纪律**：SPI 一旦发布不许改方法签名；只许加 `default` 方法。
新增实现必须带一个集成测试（Testcontainers 起真实依赖或 WireMock 起契约桩）。

---

## 5. 目录约定

```
knowit-<module>/src/main/java/com/knowit/<module>/
├── api/            # 对外暴露的接口与 DTO（其他模块只许 import 这一层）
├── internal/       # 实现细节，其他模块禁止 import
│   ├── service/
│   ├── repository/
│   └── mapper/
└── config/         # Spring 配置类（只在 knowit-server 装配，模块内只暴露 @ConfigurationProperties）
```

`api/` 与 `internal/` 的分离同样由 ArchUnit 强制。
这是"能拆微服务"的前提，也是新人读代码时的导航图。

---

## 6. 测试分层

| 层级 | 位置 | 工具 | 覆盖目标 |
|---|---|---|---|
| 单元 | 各模块 `test/` | JUnit5 + Mockito | 领域逻辑：Chunker、RRF、EvidenceGrader、PromptBuilder |
| 集成 | 各模块 `test/integration/` | Testcontainers（PG） | 仓储、SQL、索引、管道全链路 |
| 契约 | `retrieval` / `generation` | WireMock | OpenAI 兼容端点的请求/响应契约 |
| 评测 | `knowit-eval` | 真实模型，手动/夜间触发 | recall@k / MRR / 拒答准确率 / 引用正确率 |

**CI 不调用真实模型。** 模型相关的测试全部走接口注入的 fake 或 WireMock 桩，
真实模型只在 `knowit-eval` 里离线跑。这样 CI 稳定在分钟级，且不泄漏任何文档数据到外网。
