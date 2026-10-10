# KnowIt

**私有化 RAG 知识库 · 数据不出机房，每一次提问都有据可查**

用 Java 技术栈为中小企业搭建的私有化 RAG 知识库。文档与推理全部留在客户机房内，
不依赖任何云端 API。

> 状态：**P0 骨架阶段**（路线图见 [docs/architecture/roadmap.md](docs/architecture/roadmap.md)）
> License：MIT

## 为什么是另一个 RAG 项目

市面上的 Java RAG 项目大多直接接 DashScope / OpenAI 云 API——
每次问答都把企业私有文档发到第三方云。这与"私有化"根本冲突。

KnowIt 的取舍完全不同：

| 维度 | 常见做法 | KnowIt |
|---|---|---|
| 推理 | 云端 LLM + 云端 Embedding | **全本地**，OpenAI 兼容 HTTP 契约，按硬件分档 |
| 部署 | PG + ES + MinIO + MQ，7+ 组件 | **Lite 模式 4 个容器**，一条 `docker compose up` |
| 中文全文 | ES + IK 插件 | **应用层 jieba + PG tsvector**，零 C 扩展（麒麟/鲲鹏无痛） |
| 弱硬件 | 假设有 GPU | **摘录模式优先**：不调 LLM，直接呈现原文片段 |
| 权限 | 检索后按 groupId 过滤 | **SQL 层 ACL 内联**，越权数据从未被读取 |
| 引用 | 文件名 + 相似度 | **页码 + 原文偏移**，用户可逐字核对 |
| 质量 | 无评测 | 黄金评测集 + recall@k / 拒答准确率回归 |

**核心洞察**：中小企业的机器通常是 8C16G 无显卡。CPU 上跑本地 LLM，
prefill 是瓶颈——一次有效 RAG 问答需 1500–3000 token 上下文，
4B 模型首字延迟约 100 秒，交互体验直接崩掉。
所以 KnowIt 的默认问答模式是**摘录**：混合检索 + rerank → 直接呈现原文片段，
响应 <1 秒、零幻觉、100% 可解释。有 GPU 时自动升级为 AI 总结。

## 架构一句话

单进程 Spring Boot（Java 21 虚拟线程）+ 一个 PostgreSQL 16（pgvector 兼向量库）
+ 全 OpenAI 兼容的本地推理层。

```
docs/architecture/overview.md    ← 先读这个
```

## 快速开始

> ⚠️ P0 骨架阶段，尚未接入数据库与推理层。以下只验证工程骨架可用。
> 构建已跑通：9 个模块、19 个测试、7 条架构规则全绿。

```bash
# 需要 JDK 21
java -version

# 构建 + 单元测试 + 格式检查 + 静态检查 + 架构守护
# 建议用仓库自带的 mvnw，而不是裸 mvn
./mvnw clean verify
```

```bash
# 启动（P0 阶段零外部依赖，不需要数据库）
./mvnw -pl knowit-server spring-boot:run

# 验证
curl http://localhost:8080/ping
# {"data":{"service":"knowit","status":"UP",...},"success":true}

# 浏览器打开即见界面（零构建 htmx 前端）
# http://localhost:8080/
```

## 模块结构

```
knowit-parent/
├── knowit-common       零依赖基础：响应包装、异常体系、分页
├── knowit-domain       纯领域模型（record/enum），零框架注解
├── knowit-iam          用户、角色、AccessContext、AccessPolicy
├── knowit-ingestion    IngestionPipeline、DocumentParser SPI、ETL 状态机
├── knowit-retrieval     HybridRetriever、RRF 融合、rerank、证据分级
├── knowit-generation   AnswerComposer、摘录/AI 双模式、引用溯源
├── knowit-eval         评测 harness（独立 CLI，不进主 jar）
└── knowit-server       装配：REST/SSE API、配置、静态前端
```

三个"深模块"接口构成系统脊梁：

```java
IngestionPipeline.ingest(source, profile)                       // 进文件流，出索引好的 chunk
HybridRetriever.retrieve(query, accessContext, profile)         // 进问题+权限，出证据包
AnswerComposer.compose(evidence, question, style)                // 进证据，出带引用的答案
```

## 技术栈

Java 21 · Spring Boot 3.5.16 · Spring AI 1.1.8 · PostgreSQL 16 + pgvector ·
jieba4j · Flyway · bge-m3 + bge-reranker-v2-m3 · Qwen3 · htmx

基线选择的完整论证见 [ADR-0015](docs/adr/0015-framework-baseline.md)。

## 文档

| 文档 | 内容 |
|---|---|
| [docs/architecture/overview.md](docs/architecture/overview.md) | 架构总览 |
| [docs/architecture/modules.md](docs/architecture/modules.md) | 模块划分与深模块 |
| [docs/architecture/retrieval-flow.md](docs/architecture/retrieval-flow.md) | 检索与生成链路 |
| [docs/architecture/hardware-tiers.md](docs/architecture/hardware-tiers.md) | 硬件档位 → 模型矩阵 |
| [docs/architecture/roadmap.md](docs/architecture/roadmap.md) | 路线图与 P0 任务清单 |
| [docs/adr/](docs/adr/README.md) | 15 篇架构决策记录 |

## P0 验收标准

P0 的目标是把最大技术风险清零：证明"全本地 + 单 PG + 弱硬件"下 RAG 闭环能跑通。

1. `docker compose up` 一条命令起全栈，`:8080` 见到对话界面
2. 上传真实制度文件，进度可见，完成后可查
3. 10 个文档内问题，≥8 正确且引用落到正确页码
4. 5 个文档外问题，≥4 被拒答（不编造）
5. 除 127.0.0.1 与 Docker 内网，**零外网连接**
6. 8C16G 实测首字延迟，回填硬件矩阵表

第 5 条是"私有化"的硬证明。

## 贡献

见 [CONTRIBUTING.md](CONTRIBUTING.md)。提交 PR 前请确保 `./mvnw clean verify` 通过。

## License

[MIT](LICENSE)
