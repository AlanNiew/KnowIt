# ADR-0011: AccessContext 作为检索器必传入参

- 状态：已接受
- 日期：2026-10-10
- 影响范围：安全、`HybridRetriever` API、DAO 层

## 背景

RAG 系统最容易犯的安全错误是**权限过滤发生在检索之后**：
先全库检索 top-50，再在 Java 代码里按用户权限筛掉一部分，最后返回 5 条。

被参考的 Argus 就是这么做的（"向量检索和 ES 检索均附加 groupId 过滤"——听起来是在检索时过滤，
但如果过滤是在应用层做的，本质仍是"先读了再筛"）。

这个错误的实际后果：
- 越权的数据**已经被读进内存了**——堆转储、日志、异常栈、GC 前后的内存残留都可能泄漏
- 数据库执行了超出用户权限的查询，审计日志记录不到真实的越权尝试
- top-K 被权限筛掉后可能剩 0 条，用户体验莫名奇妙
- 更糟：如果某天有人"优化"成先取 top-5 再筛，就直接穿帮了

## 决定

1. `AccessContext` 是 `HybridRetriever.retrieve()` 的**必传入参**，不是可选参数，
   不允许从 `ThreadLocal` / `SecurityContextHolder` 里偷偷取
2. 权限条件必须**下推到 SQL**：向量通道与关键词通道的查询都内联 ACL 条件
3. `HybridRetriever` 内部不允许出现"无 accessContext 参数的重载"

```java
public interface HybridRetriever {
    // 唯一的方法签名。禁止提供不带 accessContext 的重载。
    EvidenceBundle retrieve(Query query, AccessContext accessContext, RetrievalProfile profile);
}
```

```sql
-- 权限条件下推进 SQL，而不是查回来再筛
SELECT c.* FROM chunk c
WHERE c.kb_id = ANY(?)                       -- 允许的知识库
  AND c.level = 'CHILD'
  AND NOT EXISTS (
      SELECT 1 FROM doc_acl a
      WHERE a.document_id = c.document_id
        AND a.effect = 'DENY'
        AND (a.subject_type = 'USER' AND a.subject_id = ?::text
          OR a.subject_type = 'ROLE' AND a.subject_id = ?::text))
ORDER BY c.embedding <=> ?
LIMIT 20;
```

## 后果

### 正面
- 越权数据**从未被读取**，不只是"没展示"——堆转储、日志、异常都干净了
- 数据库层面就有 ACL，慢查询日志与审计能看到真实的过滤条件
- 不可能被"优化"成先取 top-K 再筛——因为没有这个代码路径
- 测试好写：构造 AccessContext，断言 SQL 条件存在（或直接断言查不到）

### 负面 / 代价
- 每个 DAO 方法都要带权限参数，签名啰嗦
- `NOT EXISTS` 子查询在向量排序场景下的执行计划需要调优
  （PG 会先算向量距离再过滤 ACL，数据量大时可能全表扫描 ACL 子查询——
  需要 `kb_id` 冗余 + ACL 表 `document_id` 索引来收敛）
- 无法做"先用大一点的 top-K 粗召回，再在应用层做复杂权限判断"这类优化
  ——复杂权限逻辑必须能表达成 SQL，否则要提升到 `doc_acl` 的语义里

## 考虑过的替代方案

| 方案 | 为什么否 |
|---|---|
| 检索后过滤（Argus 方案） | 已越权读取；内存/日志泄漏面大；审计失真 |
| ThreadLocal 隐式传递 SecurityContext | 接口签名看不出需要权限，新人容易写出无权限过滤的查询；异步/虚拟线程下上下文传递易丢 |
| 在 `RetrievalProfile` 里塞权限字段 | 把安全参数和调优参数混在一起，可读性和强制力都差 |
| 每个知识库一个独立的表/ 索引做物理隔离 | 过度设计，中小规模不需要；且 ACL 的细粒度（文档级）还是要在库内解决 |

## 验证方式

- 安全测试套件：构造 20 条越权场景（跨 KB、被 DENY 的文档、不同角色），100% 查不到目标 chunk
- 代码审查规则：`HybridRetriever` 出现任何不带 `AccessContext` 的 public 方法 → PR 直接拒
- `EXPLAIN` 检查：向量查询中出现 ACL 的 `NOT EXISTS` 条件，而不是应用层过滤
