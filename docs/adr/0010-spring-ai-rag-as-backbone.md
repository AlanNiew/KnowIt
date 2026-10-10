# ADR-0010: RAG 骨架用 Spring AI，不用 SAA Graph

- 状态：已接受
- 日期：2026-10-10
- 影响范围：框架选型、`knowit-retrieval` / `knowit-generation`

## 背景

用户指定 Spring AI Alibaba 为主框架。但需要厘清一件事：
**Spring AI Alibaba 的核心价值在 Graph / Multi-Agent 编排，而不是 RAG 检索本身。**

它提供：
- Graph 工作流引擎（Sequential / Parallel / Routing / Loop Agent）
- 可视化编排（对齐 Dify / 百炼的节点库）
- 自带 `KnowledgeRetrievalNode` 等预置节点
- 基于阿里云百炼的 RAG 知识库集成

对一个"私有化 RAG 知识库"来说，这些能力的匹配度需要仔细区分：
- Graph 编排：P4 才需要的 Agent 模式，P0-P3 完全不需要
- `KnowledgeRetrievalNode`：封装的是百炼云知识库（数据出境，见 ADR-0002），
  且是黑盒，做不到权限 SQL 内联、页码级引用、摘录模式
- 用它做检索骨架，等于把核心逻辑交给一个为云平台设计的黑盒

## 决定

**RAG 骨架建在 Spring AI（社区版）的 `spring-ai-rag` 模块化管道上，不用 Spring AI Alibaba 的
RAG 封装。**

具体：
- 用 `spring-ai-rag` 的 `QueryTransformer` / `DocumentRetriever` / `DocumentCompressor` 抽象，
  但**实现全部自己写**（`HybridRetriever` / rerank compressor）
- Chat 层用 Spring AI 的 `ChatClient` + OpenAI 兼容 `base-url`（ADR-0002），
  不引入 DashScope starter
- `spring-ai-alibaba-starter-graph` **只在 P4 引入**，仅用于 Agent 模式
- P4 之前，`knowit-retrieval` / `knowit-generation` 的依赖树里不出现 `com.alibaba.cloud.ai`

## 后果

### 正面
- **核心检索逻辑完全自主可控**：权限 SQL 内联（ADR-0011）、页码级引用（ADR-0014）、
  摘录模式（ADR-0006）、证据分级——这些都需要深度定制，黑盒做不到
- 不被云平台绑定，符合私有化目标
- Graph 引擎的学习曲线（状态合并、KeyStrategy、快照）不必在 P0 就爬
- 依赖更轻：P0 不需要为了 RAG 而拖进一整套 Agent 框架

### 负面 / 代价
- 放弃了 Spring AI Alibaba 的预置节点带来的开发便利
  （`QuestionClassifierNode`、`LlmNode` 这些确实省事）
- P4 引入 Graph 时，Agent 模式与 P0-P3 的命令式 RAG 链路会是两套风格，
  需要文档说明边界
- 用户原本"主框架用 SAA"的设想需要调整为"主框架用 Spring AI，SAA 用于 Agent 层"——
  这是本 ADR 最重要的结论

## 考虑过的替代方案

| 方案 | 为什么否 |
|---|---|
| 用 SAA 的 `DocumentRetrievalAdvisor` + 百炼知识库 | 数据出境；黑盒，无法定制权限/引用/摘录 |
| 用 SAA Graph 编排整个 RAG 流程 | 用图引擎做线性管道是杀鸡用牛刀，状态管理开销大且难调试 |
| 全用 Spring AI Alibaba，不碰社区 Spring AI | SAA 本身构建在 Spring AI 之上，等于放弃了更通用的抽象层，且 RAG 定制依然要自己写 |
| 一开始就引入 Graph 为 P4 做准备 | 过早引入，学习成本压垮 P0 进度 |

## 验证方式

- `mvn dependency:tree` 在 `knowit-retrieval` / `knowit-generation` 子树中无 `com.alibaba.cloud.ai`（P4 前）
- 一条 RAG 链路可以`@SpringBootTest` 不启动 Graph 引擎而完整跑通
- P4 引入 Graph 时，新增 ADR 说明采用它的具体场景与边界
