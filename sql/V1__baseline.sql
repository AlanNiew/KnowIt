-- =============================================================================
-- KnowIt V1 · baseline schema
-- =============================================================================
-- 变更内容：
--   首个迁移脚本，建立全部核心表、索引与约束。
--   关键设计见 docs/architecture/data-model.md，决策见 docs/adr/。
--
-- 预期耗时：
--   空库执行 < 1 秒。不含任何数据回填。
--
-- 回滚说明：
--   这是基线迁移，回滚方式是 DROP DATABASE 后重建。
--   若已在该库上产生数据，请改用：
--     DROP SCHEMA public CASCADE; CREATE SCHEMA public;
--   回滚脚本：V999__drop_baseline.sql（当前不提供，按需自建）。
--
-- 纪律（ADR-0012）：
--   本脚本一旦发布到任何环境，永不修改。
--   任何调整一律追加 V2、V3… 新脚本。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 扩展
-- -----------------------------------------------------------------------------
-- pgvector 官方镜像（pgvector/pgvector:pg16）自带该扩展，无需编译。
-- 不安装 pg_jieba / zhparser / pg_bigm —— 理由见 ADR-0004（国产化平台部署）。
CREATE EXTENSION IF NOT EXISTS vector;

-- -----------------------------------------------------------------------------
-- updated_at 自动维护
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION knowit_touch_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- =============================================================================
-- 1. 身份与组织
-- =============================================================================

-- 注意：表名用 app_user 而不是 user，user 是 SQL 保留字。
CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(128) NOT NULL DEFAULT '',
    role          VARCHAR(32)  NOT NULL DEFAULT 'USER',
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_app_user_username  UNIQUE (username),
    CONSTRAINT ck_app_user_role     CHECK (role IN ('ADMIN', 'USER'))
);

CREATE TRIGGER trg_app_user_updated_at
    BEFORE UPDATE ON app_user
    FOR EACH ROW EXECUTE FUNCTION knowit_touch_updated_at();

-- 知识库是一等公民，与协作组织分离。检索配置按知识库隔离。
CREATE TABLE knowledge_base (
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(128) NOT NULL,
    description       TEXT         NOT NULL DEFAULT '',
    owner_id          BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    retrieval_profile JSONB        NOT NULL DEFAULT '{}'::jsonb,
    -- retrieval_profile 的稳定哈希；变更即触发索引重建（ADR-0007 幂等判断依据）
    profile_hash      VARCHAR(64)  NOT NULL DEFAULT '',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_kb_name_not_blank CHECK (length(btrim(name)) > 0)
);

CREATE INDEX idx_kb_owner ON knowledge_base (owner_id);
CREATE TRIGGER trg_kb_updated_at
    BEFORE UPDATE ON knowledge_base
    FOR EACH ROW EXECUTE FUNCTION knowit_touch_updated_at();

-- 知识库成员与角色。OWNER / MANAGER / MEMBER 三级（见 docs/architecture/overview.md）
CREATE TABLE kb_member (
    kb_id      BIGINT      NOT NULL REFERENCES knowledge_base (id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    role       VARCHAR(32) NOT NULL DEFAULT 'MEMBER',
    joined_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (kb_id, user_id),
    CONSTRAINT ck_kb_member_role CHECK (role IN ('OWNER', 'MANAGER', 'MEMBER'))
);

CREATE INDEX idx_kb_member_user ON kb_member (user_id);

-- =============================================================================
-- 2. 文档
-- =============================================================================

CREATE TABLE document (
    id           BIGSERIAL PRIMARY KEY,
    kb_id        BIGINT       NOT NULL REFERENCES knowledge_base (id) ON DELETE CASCADE,
    filename     VARCHAR(512) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    -- 内容哈希，用于秒传检测与 ingestion 幂等
    sha256       VARCHAR(64)  NOT NULL,
    blob_uri     VARCHAR(1024) NOT NULL,
    status       VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_document_kb_sha UNIQUE (kb_id, sha256),
    CONSTRAINT ck_document_status CHECK (status IN ('PENDING', 'INDEXED', 'FAILED')),
    CONSTRAINT ck_document_size   CHECK (size_bytes >= 0)
);

CREATE INDEX idx_doc_kb_status ON document (kb_id, status);
CREATE TRIGGER trg_doc_updated_at
    BEFORE UPDATE ON document
    FOR EACH ROW EXECUTE FUNCTION knowit_touch_updated_at();

-- 文档级 ACL。DENY 优先于 ALLOW（见 retrieval-flow.md 的 SQL 示例）。
CREATE TABLE doc_acl (
    document_id  BIGINT      NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    subject_type VARCHAR(16) NOT NULL,
    subject_id   VARCHAR(64) NOT NULL,
    effect       VARCHAR(16) NOT NULL,
    PRIMARY KEY (document_id, subject_type, subject_id),
    CONSTRAINT ck_acl_subject_type CHECK (subject_type IN ('USER', 'ROLE', 'GROUP')),
    CONSTRAINT ck_acl_effect       CHECK (effect IN ('ALLOW', 'DENY'))
);

-- ADR-0011：检索期的 NOT EXISTS 子查询依赖此索引收敛，否则会退化成全表扫描
CREATE INDEX idx_doc_acl_lookup ON doc_acl (subject_type, subject_id, effect);

-- =============================================================================
-- 3. 摄取任务（状态机，ADR-0007）
-- =============================================================================

CREATE TABLE ingestion_task (
    id            BIGSERIAL PRIMARY KEY,
    document_id   BIGINT       NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    state         VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    retry_count   INTEGER      NOT NULL DEFAULT 0,
    error_message TEXT,
    -- 各步骤产物指针（分片 id、切片批次 id 等），用于断点续跑
    step_payload  JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_ingestion_state CHECK (
        state IN ('PENDING', 'PARSED', 'CLEANED', 'CHUNKED',
                  'EMBEDDED', 'INDEXED', 'COMPLETED', 'FAILED', 'DEAD')
    ),
    CONSTRAINT ck_ingestion_retry CHECK (retry_count >= 0)
);

-- 消费循环的轮询入口：WHERE state='PENDING' ORDER BY updated_at
CREATE INDEX idx_ingestion_state ON ingestion_task (state, updated_at);
-- 一个文档同一时刻只允许一个未完成任务
CREATE UNIQUE INDEX uq_ingestion_active_doc
    ON ingestion_task (document_id)
    WHERE state NOT IN ('COMPLETED', 'DEAD');

CREATE TRIGGER trg_ingestion_updated_at
    BEFORE UPDATE ON ingestion_task
    FOR EACH ROW EXECUTE FUNCTION knowit_touch_updated_at();

-- =============================================================================
-- 4. 切片
-- =============================================================================
-- parent-child 双层结构（ADR-0005）。
--   CHILD  参与召回，带 embedding，必须有 parent
--   PARENT 不参与召回，只提供完整上下文，禁止携带 embedding
-- kb_id 冗余自 document：使权限过滤能合并进同一条 SQL（ADR-0011）。
CREATE TABLE chunk (
    id            BIGSERIAL PRIMARY KEY,
    kb_id         BIGINT       NOT NULL REFERENCES knowledge_base (id) ON DELETE CASCADE,
    document_id   BIGINT       NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    parent_id     BIGINT       REFERENCES chunk (id) ON DELETE CASCADE,
    level         VARCHAR(16)  NOT NULL,
    ordinal       INTEGER      NOT NULL DEFAULT 0,
    content       TEXT         NOT NULL,
    page_start    INTEGER,
    page_end      INTEGER,
    -- 原文偏移，用于引用精确高亮（ADR-0014）
    char_start    INTEGER,
    -- jieba4j 分词结果，空格连接。tsv 由它生成，PG 保证二者一致（ADR-0004）。
    tokens_joined TEXT         NOT NULL DEFAULT '',
    tsv           TSVECTOR     GENERATED ALWAYS AS (to_tsvector('simple', tokens_joined)) STORED,
    embedding     VECTOR(1024),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_chunk_level   CHECK (level IN ('PARENT', 'CHILD')),
    CONSTRAINT ck_chunk_content CHECK (length(content) > 0),
    CONSTRAINT ck_chunk_ordinal CHECK (ordinal >= 0),
    -- 在数据库层强制 parent-child 的一致性，与 domain.Chunk 的不变量一致：
    -- child 必须有 parent 且必须有向量；parent 必须两者皆无。
    CONSTRAINT ck_chunk_parent_child CHECK (
        (level = 'CHILD' AND parent_id IS NOT NULL AND embedding IS NOT NULL)
        OR
        (level = 'PARENT' AND parent_id IS NULL AND embedding IS NULL)
    ),
    -- 同一父切片下的序号唯一
    CONSTRAINT uq_chunk_parent_ordinal UNIQUE (parent_id, ordinal)
);

-- 向量语义召回。HNSW，m/ef_construction 为召回率与内存的平衡点（ADR-0003）。
CREATE INDEX idx_chunk_embedding_hnsw
    ON chunk USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- 关键词全文召回。走生成列，无需应用层维护。
CREATE INDEX idx_chunk_tsv ON chunk USING gin (tsv);

-- ACL + 层级过滤的复合索引
CREATE INDEX idx_chunk_kb_level ON chunk (kb_id, level);
CREATE INDEX idx_chunk_document  ON chunk (document_id);
-- 父切片定位（命中 child 后取 parent）
CREATE INDEX idx_chunk_parent ON chunk (parent_id) WHERE parent_id IS NOT NULL;

-- =============================================================================
-- 5. 会话与引用
-- =============================================================================

CREATE TABLE conversation (
    id         BIGSERIAL PRIMARY KEY,
    kb_id      BIGINT       NOT NULL REFERENCES knowledge_base (id) ON DELETE CASCADE,
    user_id    BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    title      VARCHAR(255) NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_conv_kb_user ON conversation (kb_id, user_id, updated_at DESC);
CREATE TRIGGER trg_conv_updated_at
    BEFORE UPDATE ON conversation
    FOR EACH ROW EXECUTE FUNCTION knowit_touch_updated_at();

CREATE TABLE message (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT       NOT NULL REFERENCES conversation (id) ON DELETE CASCADE,
    role            VARCHAR(16)  NOT NULL,
    content         TEXT,
    -- 证据分级（ADR-0006）：不足时 answered=false，主动拒答
    evidence_level  VARCHAR(16),
    answered        BOOLEAN      NOT NULL DEFAULT FALSE,
    usage           JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_message_role         CHECK (role IN ('USER', 'ASSISTANT')),
    CONSTRAINT ck_message_evidence     CHECK (
        evidence_level IS NULL
        OR evidence_level IN ('NONE', 'WEAK', 'PARTIAL', 'SUFFICIENT')
    )
);

CREATE INDEX idx_msg_conversation ON message (conversation_id, id);

-- 引用溯源（ADR-0014）。page_no / char_start / snippet 三者保证用户可回原文核对。
CREATE TABLE citation (
    id         BIGSERIAL PRIMARY KEY,
    message_id BIGINT       NOT NULL REFERENCES message (id) ON DELETE CASCADE,
    chunk_id   BIGINT       NOT NULL REFERENCES chunk (id) ON DELETE CASCADE,
    -- rerank cross-encoder 的校准分，绝对可比；不是 embedding 相似度
    score      NUMERIC(8, 6),
    page_no    INTEGER,
    char_start INTEGER,
    snippet    TEXT         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_citation_snippet CHECK (length(snippet) > 0),
    CONSTRAINT ck_citation_locator CHECK (page_no IS NOT NULL OR char_start IS NOT NULL),
    CONSTRAINT uq_citation_msg_chunk UNIQUE (message_id, chunk_id)
);

CREATE INDEX idx_citation_message ON citation (message_id);
CREATE INDEX idx_citation_chunk   ON citation (chunk_id);