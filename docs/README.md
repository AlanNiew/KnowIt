# KnowIt 文档

> 用 Java 技术栈为中小企业搭建**私有化 RAG 知识库**。
> 核心承诺：**数据不出机房，每一次提问都有据可查。**

## 阅读顺序

| 顺序 | 文档 | 适合谁 |
|---|---|---|
| 1 | [架构总览](architecture/overview.md) | 所有人，先读这篇 |
| 2 | [模块划分与深模块设计](architecture/modules.md) | 后端开发 |
| 3 | [核心数据模型](architecture/data-model.md) | 后端开发、DBA |
| 4 | [检索与生成链路](architecture/retrieval-flow.md) | 后端开发、算法调优 |
| 5 | [硬件档位与模型矩阵](architecture/hardware-tiers.md) | 交付、运维、售前 |
| 6 | [路线图与 P0 计划](architecture/roadmap.md) | 项目管理 |
| 7 | [ADR 索引](adr/README.md) | 想了解"为什么这么设计"的人 |

## 一句话架构

单进程 Spring Boot（Java 21 虚拟线程）+ 一个 PostgreSQL 16（pgvector 兼向量库）+ 全 OpenAI 兼容的本地推理层。
**Lite 模式下整套系统只有一个数据库容器。**

## 设计立场（所有 ADR 的共同出发点）

1. **私有化是硬约束，不是宣传语** —— P0 验收标准之一是抓包证明零外网连接。
2. **中小企业运维不起分布式系统** —— Lite 版必须是"一台机器 + 一条 compose 命令"。
3. **客户的机器很弱，但不能因此不给答案** —— 由此推导出[摘录模式优先](adr/0006-excerpt-first-answering-mode.md)。
4. **可信 > 聪明** —— 页码级引用、证据分级、主动拒答，优先级高于回答的流畅度。

## 文档结构

```
docs/
├── architecture/
│   ├── overview.md            架构总览（从这里开始）
│   ├── modules.md             模块划分与三个深模块
│   ├── data-model.md          核心数据模型与 DDL 基线
│   ├── retrieval-flow.md      检索与生成链路细节
│   ├── hardware-tiers.md      硬件档位 → 模型矩阵
│   ├── roadmap.md             路线图与 P0 任务清单
│   └── diagrams/              Mermaid 源文件（IDE Mermaid 插件 / 文档站可直接渲染）
│       ├── 01-system-context.mmd
│       ├── 02-deployment-topology.mmd
│       ├── 03-ingestion-pipeline.mmd
│       ├── 04-retrieval-flow.mmd
│       ├── 05-data-model.mmd
│       └── 06-roadmap.mmd
└── adr/                       架构决策记录（14 篇）
```

## License

MIT
