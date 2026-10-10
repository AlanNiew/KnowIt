# ADR-0002: 全本地推理 + OpenAI 兼容网关抽象

- 状态：已接受
- 日期：2026-10-10
- 影响范围：推理层、部署、`EmbeddingProvider` / `ChatProvider` SPI

## 背景

客户的核心诉求是"数据绝对不能传出去"。这意味着**文档内容和用户问题都不能离开机房**。

市面上 Java RAG 项目（包括被参考的 DevYangJC/Argus）普遍直接接 DashScope 云 API：
开发爽、零部署成本，但每次问答都把企业私有文档发到第三方云。
这与 KnowIt 的立项目标**根本冲突**——如果模型层上云，私有化就是个宣传语。

同时，本地推理的工程现实是：推理框架迭代极快（vLLM / SGLang / llama.cpp / Ollama / Xinference），
今天的最优选择半年后就过时。如果应用层和某个框架绑定，就会被拖着走。

## 决定

1. **推理层全部本地部署**：LLM、Embedding、Rerank 三个角色都在客户机房内以容器运行。
2. **应用层只认 OpenAI 兼容 HTTP 契约**（`/v1/chat/completions`、`/v1/embeddings`、`/v1/rerank`）。
   `ChatProvider` 与 `EmbeddingProvider` SPI 的唯一实现是 `OpenAiCompatibleChatProvider` /
   `OpenAiCompatibleEmbeddingProvider`，通过 Spring AI 的 `OpenAiApi` 指向可配置的 `base-url`。
3. DashScope、以及任何云 API，**只允许出现在开发 profile**，通过 `@Profile("dev")` 隔离，
   CI 和生产的构建产物中不存在该配置。
4. 生产默认栈：vLLM / SGLang（有 GPU）、llama.cpp server（CPU）；
   Embedding 与 Rerank 用 HuggingFace TEI（CPU/GPU 双支持）。

## 后果

### 正面
- 私有化承诺可被验证：P0 验收标准第 5 条用抓包证明零外网连接
- 推理后端可随时替换而不动应用代码——vLLM 出新版本、或者客户内部已有 GPU 集群，都是改一个 URL
- 本地模型可按硬件分档（Qwen3-1.7B 到 32B），一套应用覆盖 POC 到生产
- 不受任何云厂商的限流、涨价、政策变化影响

### 负面 / 代价
- **部署复杂度显著上升**：客户至少要跑一个额外容器。这是私有化绕不开的，只能在硬件自检向导和 compose 模板上补偿
- **本地小模型能力弱于云端旗舰**：引用格式遵循度、长上下文、指令遵循都更差，需要 Prompt 加固和 eval 回归兜底
- **CPU 上 prefill 慢到不可交互**，直接催生了 ADR-0006（摘录模式优先）
- 失去了云厂商的模型自动升级红利，换模型要自己评估

## 考虑过的替代方案

| 方案 | 为什么否 |
|---|---|
| 直接 DashScope / 百炼 RAG 知识库 | 数据出境，与立项目标根本冲突 |
| 本地嵌入 + 云端 LLM 的"半私有化" | 用户问题本身常含敏感信息（客户名、金额、人事），仍属数据出境；且会让客户误以为安全 |
| 绑定 llama.cpp JNI 做进程内推理 | 与 JVM 进程抢内存、崩溃会拖死主进程、升级要重编 native；HTTP 隔离更稳 |
| ONNX Runtime Java 进程内跑 embedding | 内存与故障隔离差于独立容器，且不利于硬件分档；留作备选，不作为默认 |

## 验证方式

- `deploy/compose.poc.yml` 中不存在任何指向公网的模型端点
- 容器内抓包 30 秒（一次完整问答），除 127.0.0.1 与 Docker 网段外无连接
- `mvn -Pdev` 之外的所有 profile 构建产物中，`grep -r dashscope` 无命中
