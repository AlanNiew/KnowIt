package com.knowit.ingestion.api;

import java.io.InputStream;
import java.util.Optional;

/**
 * 解析器 SPI。见 ADR-0009。
 *
 * <p>默认实现是 {@code internal/parser/TikaDocumentParser}，也是整个项目中 <b>唯一</b>引用 LangChain4j 的地方。将来要接 OCR
 * sidecar（PaddleOCR / MinerU / Docling），新增一个 HTTP 实现即可，RAG 主链路零改动。
 *
 * <p>返回值类型全部用 JDK 自有类型，不许把 LangChain4j 的 {@code dev.langchain4j.data.document.Document} 泄漏出实现类。
 */
public interface DocumentParser {

    /** 支持的 MIME 类型。 */
    boolean supports(String mimeType);

    /**
     * 解析为纯文本 + 元数据。
     *
     * @param source 文件流
     * @param mimeType MIME 类型
     * @return 解析结果；空表示该文件没有可提取文本（纯图片等）
     */
    Optional<ParsedDocument> parse(InputStream source, String mimeType);

    /**
     * 解析产物。页码信息必须在这里提取并挂到段落上—— 一旦在这里丢掉页码，就要重跑整个知识库的 ingestion 才能补（ADR-0014）。
     *
     * @param text 正文文本
     * @param paragraphs 按页拆分的段落；页码为 null 表示该格式无页码概念
     * @param metadata 标题、作者、创建时间等
     */
    record ParsedDocument(
            String text,
            java.util.List<Paragraph> paragraphs,
            java.util.Map<String, String> metadata) {

        public ParsedDocument {
            paragraphs =
                    paragraphs == null ? java.util.List.of() : java.util.List.copyOf(paragraphs);
            metadata = metadata == null ? java.util.Map.of() : java.util.Map.copyOf(metadata);
        }

        /**
         * 带页码的段落。
         *
         * @param text 段落正文
         * @param pageNo 页码；非分页格式为 null
         * @param offset 在全文中的字符偏移，用于引用高亮
         */
        public record Paragraph(String text, Integer pageNo, int offset) {}
    }
}
