# ADR-0003: Lite 版单数据库拓扑（不引 Elasticsearch）

- 状态：已接受
- 日期：2026-10-10
- 影响范围：基础设施、部署、`KeywordSearchEngine` SPI

## 背景

混合检索（向量 + 关键词）是 RAG 质量的标配。主流实现是 pgvector + Elasticsearch。

但我们的目标客户是中小企业，机房可能只有一台 8C16G 的机器，且**无人专职运维**。
Elasticsearch 的代价被严重低估：空跑就占 1–2G 堆内存、要调 JVM 参数、
集群黄了要看日志、升级要滚动重启、IK 分词插件要匹配 ES 版本（版本错一位就装不上）。
对一个只想"查内部文档"的客户来说，这是不可接受的运维负担。

## 决定

**Lite 版只用 PostgreSQL 16 + pgvector 一个数据库**，同时承担：

1. 业务数据（用户、知识库、文档、会话）
2. 向量索引（HNSW + 余弦距离）
3. 中文全文索引（GIN + tsvector，分词在应用层做，见 ADR-0004）

关键词检索抽象为 `KeywordSearchEngine` SPI，默认实现 `PostgresFtsEngine`。
**不引入 Elasticsearch、Meilisearch、Redis。**

**为什么可以这么做**——关键在于 RRF 融合算法（ADR 无关但这里是前提）：

```
rrf(d) = Σ 1 / (k + rank(d))
```

RRF **只消耗排名，不消耗绝对分数**。PG 全文检索的 `ts_rank` 分数不校准、
不是真 BM25，这一点被 RRF 天然吸收掉了，因为各路排名的先后顺序大致还是对的。
真正需要绝对分数的环节是 rerank——那个由本地的 bge-reranker（cross-encoder）提供，它是准的。

## 后果

### 正面
- Lite 部署从"7 个组件"降到"2 个组件"（PG + 应用），运维成本断崖式下降
- 备份 / 恢复就是一次 `pg_dump`，现场交付极其简单
- 事务能力：ingestion 的状态机可复用同一个事务，不需要分布式事务
- 权限过滤（ADR-0011）能直接内联成一条 SQL，不需要跨 ES 查询再合并 ACL

### 负面 / 代价
- **关键词排序质量低于真 BM25**。靠 rerank 补偿，如果关掉 rerank 会明显变差
- **向量检索上限受限于单机 PG**：百万级 chunk 后 HNSW 索引内存和查询延迟都会成为问题（百万级 1024 维向量索引约 3–4GB 内存）
- PG 全文检索没有 ES 那种丰富的 query DSL（fuzzy、span、phrase proiximity）
- PG 成为单点，虽然 ES 也是

## 考虑过的替代方案

| 方案 | 为什么否 |
|---|---|
| PG + Elasticsearch（Argus 方案） | 运维负担与"零运维"目标冲突；且 ES 的增量同步、IK 插件版本坑都是交付现场的雷 |
| PG + Meilisearch | 比 ES 轻，但仍是一个要运维的组件、一种要备份的数据；Lite 版意义不大 |
| 纯向量检索，砍掉关键词通道 | 企业专有术语（型号、制度编号、化学式）召回率崩溃，RAG 质量不可接受 |
| 纯 SQL + LIKE 模糊匹配 | 效率与分词质量都不行，只能作为演示 |

**何时重新评估**：单 KB 超过 100 万 chunk，或 CPU rerank 成为不可绕开的延迟瓶颈时，
通过 `KeywordSearchEngine` SPI 切到 Meilisearch，业务代码零改动。

## 验证方式

- `docker compose -f compose.poc.yml up` 起的容器数 ≤ 4
- 知识库 20 万 chunk 时，`EXPLAIN ANALYZE` 显示向量查询 P95 < 200ms
- 黄金 50 问评测中，双路（vs 纯向量）recall@5 提升 ≥ 0.1
