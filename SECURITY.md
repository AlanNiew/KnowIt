# Security Policy

## Supported Versions

P0 阶段尚无正式发布版本。下表说明哪些分支会收到安全修复。

| Version | Supported          |
|---------|--------------------|
| `main`  | :white_check_mark: |
| < 0.1.0 | :x:                |

## Reporting a Vulnerability

**请不要用公开 Issue 报告安全问题。**

优先使用 GitHub 私有漏洞报告：
**Security → Report a vulnerability**（仓库右上角 Security 标签页内）。
若找不到该入口，请发邮件至 <SECURITY_EMAIL>。

请在标题里标注 `[SECURITY]`，并说明：

- 受影响的 KnowIt 版本或 commit
- 复现步骤
- 影响范围（尤其是能否越权读取到不属于当前用户的文档）

我们会在收到后 72 小时内确认收到，14 天内给出修复或缓解方案。

## 本项目特别关注的安全边界

KnowIt 的核心承诺是「数据不出机房」和「越权数据从未被读取」。
以下几类问题会被视为高优先级：

### 1. 权限绕过（最高优先级）

`HybridRetriever` 的 `AccessContext` 是必传参数，ACL 过滤被内联进 SQL
（见 [ADR-0011](../docs/adr/0011-access-context-as-required-parameter.md)）。
**任何能让检索返回用户无权访问的 chunk 的改动，都是严重漏洞。**

验证方式：任何对检索 SQL 的改动，都必须有一条对应的越权回归测试。

### 2. 出网

产品承诺推理与检索全程不出内网。任何引入外部网络调用的改动
（遥测、字体 CDN、云端 API 回退）都违背这一承诺。

### 3. 文档内容泄露到不该去的地方

- 日志中打印文档正文
- 引用片段被缓存到共享位置
- `.gitignore` 失效导致企业文档入库

### 4. 模型权重与密钥

- `deploy/models/` 下的权重文件不应进仓库（已被 `.gitignore` 排除）
- 数据库口令不应有可用的默认值

## 部署方须知

KnowIt 定位为私有化部署。部署方负责：

- 修改所有默认口令（数据库口令、初始管理员口令）
- 不要将 PostgreSQL 端口暴露到局域网之外（`compose.poc.yml` 已默认只绑 `127.0.0.1`）
- 定期备份 `pgdata` 卷