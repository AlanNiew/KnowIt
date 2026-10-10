# ADR-0005: Parent-Child 双层切片

- 状态：已接受
- 日期：2026-10-10
- 影响范围：ingestion、检索、`chunk` 表结构

## 背景

RAG 有一个经典的张力：

- chunk 切**小** → 向量语义聚焦，召回精准，但喂给 LLM 时上下文碎片化，
  "第三条款"找不到"第一款"的前置条件，模型只能答一半
- chunk 切**大** → 上下文完整，但一个 chunk 混了多个主题，向量被稀释，
  召回时匹配度下降，且浪费宝贵的上下文预算

在 CPU 上这个问题被放大：本地小模型上下文窗口本来就紧张（512–1024 token 预算），
我们没有余量反复塞大 chunk。

## 决定

**索引时就把 chunk 组织成 parent / child 两层**：

```
Parent chunk (~1024 token)  ← 语义完整的段落群，用于"读"
    ├── Child chunk (~256 token, ordinal 0)  ← 用于"找"
    ├── Child chunk (~256 token, ordinal 1)
    └── Child chunk (~256 token, ordinal 2)
```

- **child** 携带 embedding，参与向量召回与关键词召回
- **parent** 不参与召回，命中的 child 展开回 parent 后送给 LLM / 给用户看
- `chunk.parent_id` 建立关联；parent 行的 `parent_id IS NULL` 且 `level='PARENT'`

对比被参考项目 Argus 的做法（命中的 chunk 向前后各扩展 1 个邻居窗口）——
那个方案是**查询时猜邻居**，扩展出来的内容可能跨主题、可能不完整。
parent-child 是**索引时就定好的语义边界**，更可控。

## 后果

### 正面
- 召回精准度（child 的功劳）和答案完整性（parent 的功劳）兼得
- 上下文预算可控：只用 2–3 个 parent，不用 10 个碎 chunk
- 用户看到的引用是**语义完整的段落**而不是断章取义的半句话——直接影响可信度
- 页码信息挂在 parent 上，引用定位准确

### 负面 / 代价
- `chunk` 表数据量翻倍（child + parent 各存一份 content）
- 结构感知切分的实现复杂度明显上升：要识别标题层级、列表、表格边界来对齐 parent 边界
- 一个 parent 被多个 child 命中时要**去重**，且要记录命中的是哪几个 child（用于高亮）

## 考虑过的替代方案

| 方案 | 为什么否 |
|---|---|
| 固定 512 token 滑动窗口 | 简单，但上下文与召回两头不讨好 |
| 大 chunk 直接召回 | 上下文完整但召回差，且本地小模型塞不下 |
| Argus 的"邻居窗口扩展" | 查询时猜边界，不可控，跨主题污染常见 |
| 句子窗口（sentence-window） | 介于两者之间，但 parent-child 的段落级边界更符合中文制度文档结构 |

## 验证方式

- 黄金 50 问中，parent-child 相比纯 child 召回，"答案完整度"人工评分提升 ≥ 20%
- `chunk` 表 parent 行的 `page_start == page_end` 或跨页连续（抽样人工检查边界合理性）
- P1 验收：50 页 PDF 切分后，parent 平均落在 3–6 个 child 之间
