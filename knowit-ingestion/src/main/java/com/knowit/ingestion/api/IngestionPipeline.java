package com.knowit.ingestion.api;

import com.knowit.domain.DocumentSource;
import com.knowit.domain.IngestionResult;

/**
 * 从文件流到"索引好的 chunk"。
 *
 * <p><b>幂等性契约</b>：对同一 {@code (documentId, profileHash)} 重复调用， 不得产生重复切片、不得重复写入向量索引。这是 ADR-0007
 * 状态机可重试、 可断点续跑、可增量重建索引的前提。
 *
 * <p><b>失败契约</b>：步骤失败时返回 {@code state=FAILED/DEAD} 的结果对象， 不抛异常——调用方（上传接口）应当把任务结果如实告诉用户， 而不是收到一个 500
 * 后不知道文档到底进没进库。
 */
public interface IngestionPipeline {

    /**
     * @param source 待处理文档（含内容字节与 metadata）
     * @param profile 切片 / 检索 / 回答参数
     * @return 摄取结果；成功为 COMPLETED，失败为 FAILED 或 DEAD
     */
    IngestionResult ingest(DocumentSource source, String profile);
}
