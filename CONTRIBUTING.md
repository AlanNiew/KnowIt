# 贡献指南

感谢你愿意给 KnowIt 出力。这份文档不是客套，是让协作成本降到最低的操作约定。

## 先读这些

1. [docs/architecture/overview.md](docs/architecture/overview.md) — 架构总览
2. [docs/adr/README.md](docs/adr/README.md) — 15 篇架构决策记录
3. [docs/architecture/modules.md](docs/architecture/modules.md) — 模块边界

**特别重要**：PR 里如果"顺手优化"了摘录模式、SQL 层 ACL、页码引用这三件事，
请先去读对应的 ADR（0006 / 0011 / 0014），确认你理解了这个决策的代价再动手。
它们看起来很怪，但都有明确的理由。

## 环境要求

| 工具 | 版本 |
|---|---|
| JDK | 21（Temurin / Zulu 均可） |
| Maven | 3.9.9+——用仓库自带的 `./mvnw`（会按 `.mvn/wrapper/maven-wrapper.properties` 自动下载对应版本） |
| Node | 不需要。P0 前端零构建（ADR-0013） |

**建议一律用 `./mvnw` 而不是裸 `mvn`**，理由有两条：

1. 版本一致：`mvnw` 会按 `.mvn/wrapper/maven-wrapper.properties` 固定 Maven 版本，
   避免"我这儿能编过"的经典事故。
2. `mvnw` 会读取 `.mvn/jvm.config` 并把其中的 JVM 参数传给 Maven。
   本项目的格式化工具（google-java-format）在部分 JDK 组合下需要
   `--add-exports`，这些参数就放在那里。

> 注：本仓库的前几个提交是在无法执行 Maven 的环境里完成的，
> 所以 `mvnw` 是手写的最小脚本而非官方 wrapper 生成物。
> 谁有完整环境，跑一次 `./mvnw -N wrapper:wrapper -Dmaven=3.9.9`
> 用官方脚本替换即可（替换后 `.mvn/jvm.config` 会被官方脚本正常读取）。

### 行尾符必须是 LF

仓库根目录有 `.gitattributes` 强制 `eol=lf`，Spotless 也显式设了
`<lineEndings>UNIX</lineEndings>`。**不要**改这两处。

原因：CI 跑在 Linux、开发者在 Windows。若不锁定，同一个源文件会在两个平台上
被互相判定为"格式违规"，而且报错信息完全看不出是行尾问题
（diff 里每行都是 `-`/`+`，肉眼看上去一模一样）。
详见 [ADR-0015](docs/adr/0015-framework-baseline.md) 的「环境陷阱二」。

## 提 PR 前必须跑通

```bash
./mvnw clean verify
```

它会依次执行：

| 阶段 | 内容 | 失败怎么办 |
|---|---|---|
| `test` | 单元测试 | 改代码 |
| `integration-test` / `verify` (failsafe) | 集成测试 | 改代码 |
| `spotless:check` | 代码格式（google-java-format AOSP，4 空格，LF 行尾） | `./mvnw spotless:apply` |
| `checkstyle:check` | 静态检查 | 要么改代码，要么提出改规则并说明理由 |
| `enforcer` | 版本与依赖收敛 | 改 `pom.xml` |
| `ArchUnit`（在 test 内） | 架构规则 | 见下 |

> 若 `spotless:check` 报某文件"整篇都要改"但看不出差别，**先怀疑行尾符**，
> 见本文末尾「行尾符必须是 LF」。

## 架构规则

`knowit-server/src/test/java/com/knowit/server/ArchitectureRulesTest.java` 是"写在测试里的墙"。
每条规则对应一条 ADR，失败信息里带规则编号。

如果你的功能**确实需要**突破某条规则，正确做法是：
1. 先提 Issue 说明为什么现有约束不适用
2. 写/改 ADR 记录新的决策
3. 再改测试

**不要直接删测试。**

## 提交规范

Conventional Commits，中英文皆可：

```
feat(retrieval): RRF 融合改用只排名的实现
fix(ingestion): 修复进程重启后 ingestion_task 状态回退
docs(adr): 新增 ADR-0016：评测集按客户分目录
refactor(generation): 摘录模式提取为独立策略类
test(domain): 补齐 Chunk 页码不变量测试
```

`scope` 用模块名：`common` `domain` `iam` `ingestion` `retrieval`
`generation` `eval` `server` `docs` `ci`。

## PR 规范

- **一个 PR 只做一件事**。混着改会被要求拆开。
- PR 描述必须包含：动机、改了什么、**怎么验证的**（截图/命令输出/测试名）。
- 涉及决策的变更，PR 里给出对应 ADR 编号。
- 新加第三方依赖必须单独说明为什么不能自己写、不能复用现有依赖。
- 数据库变更必须带 Flyway 迁移脚本，且脚本头部写回滚说明（ADR-0012）。

## 代码风格底线

比格式更重要的是这些：

1. **公共方法必须有 Javadoc**，写清"契约"而不是复述签名——
   包括幂等性、失败时返回什么、什么情况抛什么。
2. **不许吞异常**。`catch (Exception e) {}` 是红线；
   要忽略就用 `catch (Exception ignored)` 并注释为什么可以忽略。
3. **不许把堆栈返回给客户端**（`GlobalExceptionHandler` 已统一拦，不要绕过）。
4. **新增 Magic Number 提起为具名常量**。检索阈值尤其如此——
   阈值必须能在配置里改，不能散落在比较表达式里。
5. **测试名字说人话**。`test1()` / `testParent()` 这种名字不如不写，
   要写成"在什么条件下，发生什么，期望什么"。

## 评测集纪律

`knowit-eval` 的黄金集可能含企业敏感内容。**绝不提交到公开仓库。**

客户评测集放本地 `eval-data/`（已在 `.gitignore`），
仓库里只放脱敏的样例：`knowit-eval/src/test/resources/samples/`。

## Issue

- Bug：带复现步骤、期望行为、实际行为、环境（JDK/OS/硬件档位）。
- 功能请求：先说**要解决什么问题**，再说你设想的方案。
  没有问题的方案通常不需要实现。
