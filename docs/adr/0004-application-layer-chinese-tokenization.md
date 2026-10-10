# ADR-0004: 中文分词放应用层，不装 PG C 扩展

- 状态：已接受
- 日期：2026-10-10
- 影响范围：ingestion、检索、国产化部署

## 背景

PG 内置的全文检索对中文是残废的（没有词边界，`simple` 配置只会按空格和标点切）。
要让它支持中文，需要装扩展：`pg_jieba`、`zhparser`、`pg_bigm`。

用户已明确要在中小企业机房跑，且这类客户有相当比例在**国产化环境**（银河麒麟 / 统信 UOS / 鲲鹏 / 昇腾）。

三个扩展都**需要编译**：
- `pg_jieba` 依赖 C++ 编译和 GCC 版本匹配
- `zhparser` 依赖 SCWS 词典
- `pg_bigm` 是 n-gram，虽然纯 C 但仍需针对具体 PG 版本编译

在生产现场这意味着：要么自己打 Docker 镜像（cross-compile arm64 + x86），
要么让客户在麒麟上装 gcc 和 PG 开发头文件——**交付现场的噩梦**。
而且每次升 PG 小版本都要重编一次。

## 决定

**分词在 Java 应用层完成，绝不在 PG 装 C 扩展。**

1. 用 jieba4j（结巴分词的 Java 移植）在 ingestion 时对 chunk 分词
2. 结果落到 `chunk` 表的一个普通 `text` 列（见下节）
3. 关键词召回就是一条 GIN 索引上的普通查询

## 落库形态：`tsv` 用生成列

```sql
tokens_joined text NOT NULL,
tsv tsvector GENERATED ALWAYS AS (to_tsvector('simple', tokens_joined)) STORED,
CREATE INDEX idx_chunk_tsv ON chunk USING gin (tsv);
```

**`tsv` 由 PG 生成列派生，不交给应用层写。** 分词是应用层的事，
但从分词结果派生出的 `tsv` 不是——如果由应用维护，就会出现
"content 写了、tsv 忘了"的不一致状态，而这种 bug 只在检索时才暴露，
排查成本极高。生成列让 PG 自己保证二者一致，应用层只写一列，
不需要触发器，也不需要在 DAO 里重复这段表达式。

用 `'simple'` 配置而非 `english`：分词已由 jieba 完成，PG 只需按空格切，
`english` 配置反而会错误处理中文 token。

领域模型的 `Chunk.tokens` 仍是 `List<String>`，由 DAO 层
`String.join(" ", tokens)` 写入本列——领域层不受存储细节影响。

## 后果

### 正面
- **零编译依赖**：`pgvector/pgvector:pg16` 官方镜像直接可用，arm64 / x86 通吃，
  麒麟/鲲鹏环境无痛部署。这对交付现场价值极大
- 分词逻辑在 Java 里，**可单测**（分词质量回归测试，比如"通义千问"不被切成"通义/千问"）
  装在 PG 里的分词器没法测
- 分词词典可自行增补（企业专有名词、产品型号）
- 分词结果落 `tokens_joined` 列，将来想换成别的分词器或做关键词高亮，数据都在

### 负面 / 代价
- **chunk 表多一列 `tokens_joined`**，存储冗余约 30–50%
- 不再保留 `text[]` 数组结构，若将来要做词频统计（倒排表）需要重新拆分。
  当前没有该计划，因此代价为零
- 分词错误无法修数据修复——改词典必须重新走一遍 ingestion（实际上 PG 分词方案也一样）
- 每次写 chunk 多一次 jieba 调用（~ms 级，可忽略）
- 失去了 PG 内置分词的 `ts_rank_cd` 等高级排序能力——但 RRF 只用排名，且 rerank 提供真分数，影响可控（ADR-0003）

## 考虑过的替代方案

| 方案 | 为什么否 |
|---|---|
| `pg_jieba` | 需编译，arm64/麒麟构建痛苦，版本升级要重编 |
| `zhparser` + SCWS | 同上，且 SCWS 词典质量和许可证都不理想 |
| `pg_bigm` | 需编译；且 n-gram 不做真分词，对"通义千问"这类词的准确率不如 jieba |
| 引入 Elasticsearch + IK | 见 ADR-0003，运维负担与目标冲突 |
| 用 PG 的 `unaccent` + `simple` 按字切 | 毫无分词能力，keyowrd 通道形同虚设 |

## 验证方式

- 在 arm64 的麒麟容器里 `docker compose up` 成功，无编译步骤
- jieba4j 分词单元测试覆盖 20 个企业专有词，"通义千问"/"达梦数据库"/"鲲鹏920" 切分正确
- 20 万 chunk 的 GIN 关键词查询 P95 < 80ms
