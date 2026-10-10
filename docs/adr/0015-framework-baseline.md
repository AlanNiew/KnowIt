# ADR-0015: 框架基线锁定 Spring Boot 3.5.16 + Spring AI 1.1.8

- 状态：已接受
- 日期：2026-10-10
- 影响范围：全项目构建、P0 骨架

## 背景

P0 搭建骨架时核查 Maven Central，发现（2026-10-10）：

| 组件 | 当前 GA | 最后 3.x / 1.x 补丁 |
|---|---|---|
| Spring Boot | **4.1.1**（4.2.0-M2 为最新） | **3.5.16** |
| Spring AI | **2.0.1**（2.1.0-M1 为最新） | **1.1.8** |
| Spring AI Alibaba | 1.1.2.0（2026-02 发布） | 仍基于 Spring AI 1.1.2 |

也就是说，我们文档里写的"Spring Boot 3.5.x / Spring AI 1.1.x"已经不是主线，
而是一个进入维护期的大版本最后补丁。

需要在"追新"和"求稳"之间做一次明确选择。

## 决定

**P0 基线锁定：Java 21 + Spring Boot 3.5.16 + Spring AI 1.1.8。**

所有版本集中在 `pom.xml` 的 `<properties>` 里，升版本是一次改动。

## 后果

### 正面
- **与 Spring AI Alibaba 1.1.2.0 兼容**。SAA 官方明确说明"与 Spring AI 2.0 对齐需要时间，
  目前基于 Spring AI 1.1.2"。我们的 P4 计划要用 SAA Graph（ADR-0010），
  留在 1.1.x 线上等于给 P4 留好了门
- 3.5 是 Spring Boot 3.x 的**最终补丁线**，剩余生命周期内只会有 bugfix 和安全补丁，
  私有化交付现场吃补丁零压力
- Java 生态对 3.5 的适配最成熟：MyBatis-Plus、Flyway、Testcontainers、各类 starter
  都已验证；升级到 4.x 时这些第三方库的 4.x 兼容版本未必都齐
- 文档（`overview.md` 技术选型表）无需改写

### 负面 / 代价
- **用的是即将进入 EOL 的大版本**。3.5.x 不会获得新特性，只吃补丁
- Spring AI 1.1.x 同样是补丁线；2.0 的新能力（如果 RAG 抽象有增强）享受不到
- 需要一份明确的升级评估计划，否则会无限期停留在 3.5

## 考虑过的替代方案

| 方案 | 为什么否 |
|---|---|
| Spring Boot 4.1.x + Spring AI 2.0.x | 追新最激进，但 P4 的 SAA Graph 兼容性未经验证；且第三方库 4.x 兼容性未知，P0 会变成"调依赖版本"而不是"验证技术风险" |
| 一开始就上 Spring Boot 4.x，P4 再降回 | 无意义的来回横跳 |
| 继续等 SAA 对齐 Spring AI 2.0 | 时间不可控，且我们 P4 才需要它，现在无需等待 |

## 升级触发条件

满足**任意两条**时启动升级评估（写一条新 ADR 记录结论）：

1. Spring AI Alibaba 发布基于 Spring AI 2.0 的版本
2. Spring Boot 3.5.x 出现一个我们无法自行修复的安全问题
3. 需要 Spring AI 2.0 的某个新能力（升级前先确认 `spring-ai-rag` 抽象没有破坏性变更）
4. MyBatis-Plus 等核心依赖发布对 Spring Boot 4.x 的稳定支持

## 验证方式

- `mvn dependency:tree` 中 `spring-boot` 与 `spring-ai-*` 版本与 `pom.xml` 属性一致
- `pom.xml` 中版本号不散落在子模块，全部来自父 POM 属性

## 环境陷阱（踩过一次，记下来）

本机 Maven 全局配置 `$MAVEN_HOME/conf/settings.xml` 里有一个 profile：

```xml
<profile>
    <id>jdk-17</id>
    <activation><activeByDefault>true</activeByDefault></activation>
    <properties><maven.compiler.release>17</maven.compiler.release></properties>
</profile>
```

`activeByDefault=true` 让它对**每一个** Maven 构建都生效，且 settings 里的 profile
属性会**覆盖 POM 里声明的同名属性**。结果是本文档要求的 Java 21 编译被静默降为
release 17，`mvn` 的日志里只会写 `release 17`，非常难察觉。

**P0 实际踩到的后果**：`knowit-domain` 用 release 17 编译通过（record 在 17 可用），
但一旦用到虚拟线程等 21 特性就会失败，而且失败得莫名其妙。

**应对（已落地）**：项目不用 `maven.compiler.release` 这个公共属性名，
改用私有属性 `knowit.java.release` 并显式配置 `maven-compiler-plugin`，
使编译级别不受任何外部 settings 篡改。

**建议同时修正全局配置**（影响该用户所有 Java 项目）：把三个 JDK profile 改成
按 `jdk` 版本前缀自动激活，而不是 `activeByDefault`：

```xml
<profile>
    <id>jdk-21</id>
    <activation><jdk>21</jdk></activation>
    <properties><maven.compiler.release>21</maven.compiler.release></properties>
</profile>
```

## 环境陷阱二：Spotless 的平台行尾会让跨平台 CI 必然失败

**现象**：Windows 上 `./mvnw spotless:apply` 产出的文件是 CRLF；
把同样的文件拿到 Linux CI 上跑 `spotless:check`，**整个文件被判为格式违规**
（diff 里每一行都是 `-`/`+`，但肉眼看上去完全一样）。

**根因**：Spotless 默认 `lineEndings` 跟随平台。Windows 产出 CRLF，Linux 产出 LF，
两边都把对方判定为"不合规"。这不是配置错误，是默认行为。

**P0 实际踩到的后果**：本地构建反复失败且报错看不出原因；如果没发现，
第一批在 Windows 上提交、由 Linux CI 校验的 PR 会全部红，且提示极难理解。

**应对（已落地，两处缺一不可）**：

1. `pom.xml` 的 Spotless 配置里显式 `<lineEndings>UNIX</lineEndings>`
   （注意枚举值是 `UNIX` 而不是 `LF`——写 `LF` 会报
   `No enum constant com.diffplug.spotless.LineEnding.LF`）。
2. 仓库根目录 `.gitattributes` 写 `* text=auto eol=lf`，并给 `*.cmd` / `*.bat`
   单独保留 CRLF。

`docker-compose.yml` 与 `.editorconfig` 里的 `end_of_line = lf` 与之呼应。

## 环境陷阱三：google-java-format 的版本与 JDK 兼容性

**现象**：Spotless 的 `google-java-format` 步骤对**每一个** java 文件报
`java.lang.reflect.InvocationTargetException`，且 Spotless 把根因折叠成 `(...)`，
从 Maven 日志里看不到真正原因；加 `--add-exports` 也不解决。

**根因**：`google-java-format` 1.37.0（2026 版本）与 JDK 21 不兼容。
换成 **1.24.0** 后无需任何 `--add-exports` 即正常工作。

**应对（已落地）**：`pom.xml` 把 `google-java-format.version` 钉在 `1.24.0`。
仓库保留 `.mvn/jvm.config`，里面的 `--add-exports=jdk.compiler/...` 是给将来
升级 gjf 版本时准备的**安全网**（当前版本用不到），`mvnw` 会读取该文件。

**升级 gjf 版本时必须做**：`./mvnw spotless:apply` 后 `git diff` 检查改动量是否合理，
并确认日志里 `release 21` 仍在。

## 验证方式（补充）

- `mvn -X compile` 的日志中 `javac [debug parameters release 21]`，
  而不是 `release 17`。这条要写进 CI 的断言里，防止将来又被环境悄悄改掉。
- `./mvnw clean verify` 在 Windows 与 Linux 上都绿，且 `git diff` 为空
  （即格式化不产生平台相关的改动）。
