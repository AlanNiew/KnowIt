# 变更日志

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [未发布]

### 新增
- Maven 多模块工程骨架（8 个模块 + 统一 BOM）
- 架构守护：ArchUnit 规则覆盖"domain 纯领域""internal 不外泄"
  "retrieval/generation 隔离""api 无 LangChain4j"
- 代码质量门禁：Spotless（google-java-format AOSP）+ Checkstyle + Enforcer
- GitHub Actions CI：JDK 21，build → test → verify → package
- 领域模型：`KnowledgeBase` / `Document` / `Chunk` / `Citation` / `EvidenceBundle`
  / `ComposedAnswer` / `Query` / `Question` 及四个枚举
- 三个深模块接口：`IngestionPipeline` / `HybridRetriever` / `AnswerComposer`
- `AccessContext` 与 `AccessPolicy`（ADR-0011 的 SQL 层权限过滤基础）
- `DocumentParser` SPI（ADR-0009 的唯一 LangChain4j 接入点）
- PingController + GlobalExceptionHandler（不向客户端泄漏堆栈）
- 零构建 htmx 占位页（ADR-0013）
- 完整文档体系：6 篇架构文档 + 6 张 Mermaid 图 + 15 篇 ADR

### 修复
- `EvidenceBundleTest` 缺 `import java.util.List` 导致编译失败
- `knowit-retrieval` 未声明对 `knowit-iam` 的依赖（与 modules.md 的依赖规则不一致）
- `PingController` 使用 `KnowItApplication` 未 import
- `EvidenceBundleTest` 的文件名与类名不一致（Checkstyle `OuterTypeFilename`）
- `knowit-server` 缺 `spring-boot-starter-test`，测试代码无法编译
- `checkstyle.xml` 的 XML 声明误写为 `charset`（应为 `encoding`）导致配置无法解析
- Checkstyle `NoWhitespaceAfter` 与 google-java-format 在类型转换后的空格上冲突，
  移除 `TYPECAST` 检查项
- 日志字段名 `log` 改为 `LOG` 以满足常量命名规则
- 未固化插件版本（checkstyle / enforcer / spring-boot-maven-plugin）

### 变更
- 框架基线锁定 Spring Boot 3.5.16 + Spring AI 1.1.8（ADR-0015）。
  说明：Spring Boot 4.1 / Spring AI 2.0 已 GA，但 Spring AI Alibaba 1.1.2.0
  仍基于 Spring AI 1.1.x，留在 1.1 线是为 P4 的 Agent 层保留兼容门。
- 编译级别改用私有属性 `knowit.java.release` 并显式配置 compiler 插件，
  避免被 Maven 全局 settings.xml 的同名 profile 属性覆盖（ADR-0015 环境陷阱一）
- Spotless 显式锁定 LF 行尾（`<lineEndings>UNIX</lineEndings>`），
  并新增 `.gitattributes`（ADR-0015 环境陷阱二）
- google-java-format 钉在 1.24.0：1.37.0 与 JDK 21 不兼容（ADR-0015 环境陷阱三）
- 关闭 Spotless 的 markdown/sortPom 步骤：会破坏 Mermaid 与中文表格、
  并与 POM 的属性分组注释冲突

### 计划中（P0 剩余）
- T0.2：PostgreSQL + pgvector + Flyway + docker compose
- T0.3：Ingestion / Retrieval / Generation 最小实现
- T0.4：REST + SSE API 与完整前端
- T0.5：测试与六条验收标准
