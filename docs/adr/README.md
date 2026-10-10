# ADR 索引

Architecture Decision Records。每条记录一个**不可逆或难以逆转**的架构决策。
> 原则：**没有 ADR 的技术债不算技术债。**

## 决策列表

| 编号 | 标题 | 状态 |
|---|---|---|
| [0001](0001-record-architecture-decisions.md) | 用 ADR 记录架构决策 | 已接受 |
| [0002](0002-full-local-inference-openai-compatible-gateway.md) | 全本地推理 + OpenAI 兼容网关抽象 | 已接受 |
| [0003](0003-lite-single-database-topology.md) | Lite 版单数据库拓扑（不引 Elasticsearch） | 已接受 |
| [0004](0004-application-layer-chinese-tokenization.md) | 中文分词放应用层，不装 PG C 扩展 | 已接受 |
| [0005](0005-parent-child-chunking.md) | Parent-Child 双层切片 | 已接受 |
| [0006](0006-excerpt-first-answering-mode.md) | 摘录模式优先 | 已接受 |
| [0007](0007-db-driven-ingestion-state-machine.md) | Ingestion 用数据库驱动的状态机 | 已接受 |
| [0008](0008-deep-module-interfaces.md) | 三个深模块作为系统脊梁 | 已接受 |
| [0009](0009-langchain4j-only-for-document-parsing.md) | LangChain4j 只用于文档解析 | 已接受 |
| [0010](0010-spring-ai-rag-as-backbone.md) | RAG 骨架用 Spring AI，不用 SAA Graph | 已接受 |
| [0011](0011-access-context-as-retriever-parameter.md) | AccessContext 作为检索器必传入参 | 已接受 |
| [0012](0012-flyway-versioned-ddl.md) | DDL 全部 Flyway 版本化 | 已接受 |
| [0013](0013-minimal-htmx-frontend-for-p0.md) | P0 用零构建 htmx 前端 | 已接受 |
| [0014](0014-citation-carry-page-number.md) | 引用必须携带页码 | 已接受 |
| [0015](0015-framework-baseline.md) | 框架基线锁定 Spring Boot 3.5.16 + Spring AI 1.1.8 | 已接受 |

## 已实现的守护规则

| ADR | 守护方式 | 位置 |
|---|---|---|
| 0008 | ArchUnit：domain 只依赖 JDK 与自身 | `ArchitectureRulesTest` |
| 0008 | ArchUnit：internal 包不许被跨模块 import | 同上 |
| 0008 | ArchUnit：retrieval 与 generation 互不依赖 | 同上 |
| 0009 | ArchUnit：api 包不出现 langchain4j | 同上 |
| 0011 | 接口签名约定 + ArchUnit 反向依赖检查 | 同上 |
| 0012 | 计划中：Flyway validate 进 CI | T0.2 |
| 0013 | `spotless` / `checkstyle` 门禁 | `pom.xml` |
