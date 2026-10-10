# 硬件档位与模型矩阵

> 这份文档同时是**交付手册**和**售前材料**。目标：让客户在 5 分钟内知道自己该买什么配置。

## 1. 核心约束：CPU 上的 prefill 瓶颈

本地 LLM 部署的性能瓶颈**不是显存容量，是 prefill（提示词预填充）吞吐**。

一次有效 RAG 问答需要 1500–3000 token 上下文（2–3 个 parent chunk + 系统模板 + 历史）。
CPU 上 8 线程 llama.cpp 的经验值：

| 模型规模 | 量化 | 内存占用 | prefill | 生成 |
|---|---|---|---|---|
| 1.7B | Q8 | ~2.0 GB | 30–50 tok/s | 20–30 tok/s |
| 4B | Q4_K_M | ~2.8 GB | 10–20 tok/s | 12–20 tok/s |
| 8B | Q4_K_M | ~5.5 GB | 5–10 tok/s | 8–14 tok/s |
| 14B | AWQ | ~9 GB | 需 GPU | 需 GPU |
| 32B | AWQ | ~19 GB | 需 GPU | 需 GPU |

算一笔账：4B 模型 + 1500 token 上下文 = 1500 ÷ 15 ≈ **100 秒首字延迟**。
这不是"慢一点"，是交互体验崩掉。**因此 8C16G 无显卡跑不出可交互的 AI 总结。**

## 2. 由此推导的产品决策：摘录模式优先

在弱硬件上，**默认问答不调用 LLM**：

```
提问 → 混合检索 topN → rerank → 直接呈现原文片段（高亮 + 页码）
                              ↘ [让 AI 总结] 按钮（用户主动触发，愿意等）
```

好处：
- 响应 < 1 秒，零幻觉，100% 准确
- 对"制度/手册/规范"查询，用户要的就是原文，不是 AI 改写
- LLM 从必需品变成增强项

有 GPU 时自动升级为 AI 总结模式。见 [ADR-0006](adr/0006-excerpt-first-answering-mode.md)。

## 3. 四档硬件矩阵

| 档位 | 硬件 | Chat 模型 | Embedding | Rerank | 上下文预算 | 预期首字 | 默认模式 |
|---|---|---|---|---|---|---|---|
| **POC** | 8C16G 无卡 | Qwen3-1.7B-Q8 | bge-m3 (TEI CPU) | bge-reranker-v2-m3 | 512 tok | 15–25s | 摘录优先 |
| **可用** | 8C32G 无卡 | Qwen3-4B-Q4_K_M | bge-m3 | bge-reranker-v2-m3 | 1024 tok | 40–80s | 摘录 + 可选 AI 总结 |
| **标准** | 1×24G 显存<br/>(4090 / L4 / 5880) | Qwen3-14B-AWQ | bge-m3 (GPU) | bge-reranker-v2-m3 (GPU) | 3000 tok | 3–8s | 完整 AI 交互 |
| **生产** | 1×48G / 2×24G | Qwen3-32B-AWQ | bge-m3 | bge-reranker-v2-m3 | 6000 tok | 1–3s | 完整 + Agent 模式 |

### 内存预算（POC 档 16G 总内存拆解）

| 组件 | 内存 |
|---|---|
| OS + 基础服务 | ~2 GB |
| PostgreSQL 16（shared_buffers 1G） | ~1.8 GB |
| knowit-server（JVM heap 512M–1G） | ~1.2 GB |
| TEI（bge-m3 FP32 ~2.2GB 权重 + 运行时） | ~2.5 GB |
| llama.cpp（Qwen3-1.7B-Q8） | ~2.5 GB |
| MinIO / 本地磁盘 | ~0.3 GB |
| **余量** | **~5.7 GB** |

POC 档能塞进 16G，但余量不宽裕。**建议 POC 演示机至少 32G 内存。**

## 4. 模型选择说明

### Chat

选 **Qwen3 系列**的理由：
- 中文能力强，制度/手册类中文语料表现好
- Apache-2.0 / Qwen License，商用友好
- 全尺寸覆盖 0.6B → 235B，同一个家族跨档位切换，Prompt 不用重调
- tool calling 与 structured output（`json_schema`）支持良好，这是 RAG 引用输出的前提

### Embedding：bge-m3

- 1024 维，支持中英法等 100+ 语言
- 8192 长上下文
- CPU 可跑（FP32 约 2.2GB 权重）
- 备选：`bge-small-zh-v1.5`（512 维，24M 参数，CPU 快 5–10 倍，精度明显下降）——
  在 POC 档如果 TEI 索引吞吐不达标，切到这个

### Rerank：bge-reranker-v2-m3

- 0.57B，CPU 可跑
- 输出真分数，是证据分级唯一可信的输入
- 多语言，与 bge-m3 同源，tokenizer 一致

## 5. 启动自检向导

P3 交付的功能，但在 P0 就要把探测逻辑的接口留出来。

启动时 knowit-server 执行 `HardwareProbe`：

```
1. 读 /proc/meminfo 与 nvidia-smi（不存在则跳过）
2. 匹配到最近的硬件档位
3. 若当前配置与推荐档位不符 → 在 UI 顶部显示醒目提示：
   "检测到 16G 内存无显卡，已为你选择 POC 档配置（Qwen3-1.7B-Q8）。
    点击应用 / 手动修改 deploy/.env"
4. 一键应用：覆写 compose 的环境变量，提示重启容器
```

**这个向导是私有化交付体验的关键**——客户不知道什么是 AWQ，也不需要知道。

## 6. 部署形态对照

| 形态 | compose 文件 | 组件数 | 内存下限 |
|---|---|---|---|
| `dev` | `deploy/compose.dev.yml` | Postgres + Ollama（llama3.2 3B 或 qwen3:4b） | 8G |
| `poc` | `deploy/compose.poc.yml` | Postgres + TEI + llama.cpp(Qwen3-1.7B-Q8) | 16G（建议 32G） |
| `standard` | `deploy/compose.standard.yml` | Postgres + TEI + vLLM(Qwen3-14B-AWQ) | 24G + 24G 显存 |
| `production` | `deploy/compose.production.yml` | Postgres + TEI + vLLM(Qwen3-32B-AWQ) | 32G + 48G 显存 |

模型权重通过 ModelScope 拉取（国内无需梯子），`deploy/models/download.sh` 封装好。
**安装全过程零外网依赖**——拉模型这一步是唯一需要外网的，可提前在办公网下载后拷贝到机房。

## 7. 待实测校准项

下表数字为经验预估，**必须在 P0/P1 实测后回填**：

| 待测项 | 测法 | 影响 |
|---|---|---|
| TEI CPU 索引吞吐 | 索引 5000 chunk 计时 | 决定是否切 bge-small-zh |
| Qwen3-1.7B-Q8 首字延迟 | P0 验收标准第 6 条 | 决定 POC 档上下文预算 |
| bge-reranker CPU 单次打分 | 微基准 | 决定 rerank 是否可默认开启 |
| Qwen3-14B 引用格式遵循率 | knowit-eval 50 问 | 决定是否需 Prompt 加固或微调 |
