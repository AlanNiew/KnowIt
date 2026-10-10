# ADR-0009: LangChain4j 只用于文档解析

- 状态：已接受
- 日期：2026-10-10
- 影响范围：依赖边界、`knowit-ingestion`

## 背景

用户的技术栈选择是：**Spring AI Alibaba 为主框架，特定模块按需引入 LangChain4j**。

两个框架在 RAG 领域能力有真实重叠：
- Spring AI：`spring-ai-rag` 模块化管道（QueryTransformer / Retriever / Compressor）、VectorStore 抽象、DocumentReader
- LangChain4j：文档解析器生态（Tika / Apache POI / 自研 splitter）、EmbeddingStore 抽象、
  `EmbeddingStoreIngestor` 等高层封装

如果为了让两边都干活而在应用里混用，代价是真实的：
- 两套 `Document` / `TextSegment` 类型要互转
- 两套 EmbeddingModel 抽象，同一份代码两处配置
- 新人要学两套 API 心智，CSR / 排障时分不清是哪边的问题
- Spring 自动配置会互相打架（都扫描 starter）

## 决定

**LangChain4j 只在一个地方出现：`DocumentParser` SPI 的 Tika 实现内部。**

```java
// knowit-ingestion/api/DocumentParser.java  —— 我们自己的抽象
public interface DocumentParser {
    ParsedDocument parse(InputStream source, String mimeType, ParseOptions options);
}

// knowit-ingestion/internal/parser/TikaDocumentParser.java —— 唯一引用 LangChain4j 的地方
public class TikaDocumentParser implements DocumentParser {
    private final org.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser delegate;
    // 转成我们自己的 ParsedDocument，不让 langchain4j 的类型泄漏出包外
}
```

规则：
1. `langchain4j` 的依赖只允许出现在 `knowit-ingestion` 的 `internal/parser/` 包及其实现类
2. **LangChain4j 的任何类型不得出现在任何 `api/` 包、任何 public 方法签名、任何数据库实体里**
3. Turn off LangChain4j 的 Spring Boot starter（我们只用它的核心库，不用它的自动配置）

RAG 的检索、融合、生成骨架**全部用 Spring AI**（ADR-0010）。

## 后果

### 正面
- 类型系统干净：泄漏面被 ArchUnit 测试锁死，一处违规 CI 就红
- 排障时一眼能分清是"解析问题"还是"检索/生成问题"
- 换掉 LangChain4j（比如它停止维护）只需要重写一个 parser 类，
  不影响 RAG 主链路——这比"框架选错就推倒重来"强得多
- 保留了 LangChain4j 真实的优势：它的 Tika / POI 解析器久经考验，
  且它有多语言的文档加载器，比 Spring AI 的 DocumentReader 生态更丰富

### 负面 / 代价
- 多一层薄薄的自有抽象（`DocumentParser` → `ParsedDocument`）
-  LangChain4j 的新解析器（比如未来的 PDF 版面分析）要先包一层才能用，有滞后
- 团队里"有人会 LangChain4j"的经验不能直接复用

## 考虑过的替代方案

| 方案 | 为什么否 |
|---|---|
| 全用 Spring AI，自己写 Tika 封装 | 重复劳动，LangChain4j 的 Tika 解析器已经处理了很多边角（编码探测、容器格式、元数据提取） |
| 全用 LangChain4j，Spring AI 只做 Chat | 放弃 Spring AI 的 `spring-ai-rag` 模块化管道，自己拼 Retriever，重复劳动且失去 Spring 生态整合 |
| 两边都深度使用 | 类型互转地狱 + 自动配置打架 + 双重心智负担，2-3 人团队扛不住 |
| 直接用 Apache Tika 原生 API，不引 LangChain4j | 更纯粹的选项，但 LangChain4j 对 POI/OOXML 的封装和 metadata 处理更省事；如果后续嫌依赖重，退回这个方案成本低 |

## 验证方式

- ArchUnit 规则：`noClasses().that().resideInAPackage("..api..")`
  `should().dependOnClassesThat().haveFullyQualifiedNameStartingWith("dev.langchain4j")`
- `mvn dependency:tree | grep langchain4j` 只出现在 `knowit-ingestion` 子树下
- 新增一种格式（如 PPTX）的 PR，diff 不涉及 ingestion 之外的模块
