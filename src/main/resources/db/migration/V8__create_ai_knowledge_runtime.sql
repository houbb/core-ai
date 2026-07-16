-- AI Knowledge：企业知识库定义、权限范围和生命周期。
CREATE TABLE ai_knowledge (
    id VARCHAR(64) PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    category VARCHAR(100) NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_version INTEGER NOT NULL DEFAULT 1,
    published_version INTEGER,
    visibility VARCHAR(32) NOT NULL DEFAULT 'PUBLIC',
    project_code VARCHAR(100),
    department_code VARCHAR(100),
    owner_user VARCHAR(100) NOT NULL,
    progress INTEGER NOT NULL DEFAULT 0,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_knowledge_status ON ai_knowledge(status, progress);
CREATE INDEX idx_ai_knowledge_category ON ai_knowledge(category);
CREATE INDEX idx_ai_knowledge_visibility ON ai_knowledge(visibility, project_code, department_code);

-- AI Knowledge Source：Upload、Git、Wiki、Database、Website、API 等来源配置。
CREATE TABLE ai_knowledge_source (
    id VARCHAR(64) PRIMARY KEY,
    knowledge_id VARCHAR(64) NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    name VARCHAR(200) NOT NULL,
    config_json TEXT NOT NULL,
    sync_status VARCHAR(32) NOT NULL,
    last_sync_time VARCHAR(40),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_knowledge_source_knowledge ON ai_knowledge_source(knowledge_id, enabled);
CREATE INDEX idx_ai_knowledge_source_type ON ai_knowledge_source(source_type, sync_status);

-- AI Document：解析后的文档元数据和原始文本。
CREATE TABLE ai_document (
    id VARCHAR(64) PRIMARY KEY,
    knowledge_id VARCHAR(64) NOT NULL,
    source_id VARCHAR(64),
    title VARCHAR(500) NOT NULL,
    path VARCHAR(2000),
    size_bytes BIGINT NOT NULL DEFAULT 0,
    language VARCHAR(40),
    mime_type VARCHAR(200),
    status VARCHAR(32) NOT NULL,
    content TEXT,
    content_hash VARCHAR(128) NOT NULL,
    metadata_json TEXT NOT NULL,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_document_knowledge ON ai_document(knowledge_id, status);
CREATE INDEX idx_ai_document_hash ON ai_document(knowledge_id, content_hash);
CREATE INDEX idx_ai_document_language ON ai_document(language);

-- AI Chunk：支持多种 Chunk 策略、Overlap 和细粒度权限元数据。
CREATE TABLE ai_chunk (
    id VARCHAR(64) PRIMARY KEY,
    knowledge_id VARCHAR(64) NOT NULL,
    document_id VARCHAR(64) NOT NULL,
    chunk_no INTEGER NOT NULL,
    content TEXT NOT NULL,
    token_count INTEGER NOT NULL DEFAULT 0,
    heading VARCHAR(500),
    page_no INTEGER,
    metadata_json TEXT NOT NULL,
    permission_json TEXT NOT NULL,
    content_hash VARCHAR(128) NOT NULL,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL,
    UNIQUE(document_id, chunk_no)
);
CREATE INDEX idx_ai_chunk_knowledge ON ai_chunk(knowledge_id, document_id, chunk_no);
CREATE INDEX idx_ai_chunk_hash ON ai_chunk(content_hash);

-- AI Embedding：保存外部向量引用，不直接存储高维向量。
CREATE TABLE ai_embedding (
    id VARCHAR(64) PRIMARY KEY,
    knowledge_id VARCHAR(64) NOT NULL,
    chunk_id VARCHAR(64) NOT NULL,
    model_alias VARCHAR(100) NOT NULL,
    vector_id VARCHAR(500) NOT NULL,
    embedding_version INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL,
    UNIQUE(chunk_id, model_alias, embedding_version)
);
CREATE INDEX idx_ai_embedding_knowledge ON ai_embedding(knowledge_id, status);
CREATE INDEX idx_ai_embedding_vector ON ai_embedding(vector_id);

-- AI Index：Keyword、Vector、Hybrid、Graph 索引配置。
CREATE TABLE ai_index (
    id VARCHAR(64) PRIMARY KEY,
    knowledge_id VARCHAR(64) NOT NULL,
    index_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    config_json TEXT NOT NULL,
    document_count INTEGER NOT NULL DEFAULT 0,
    chunk_count INTEGER NOT NULL DEFAULT 0,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_index_knowledge ON ai_index(knowledge_id, status);

-- AI Retriever Policy：TopK、Score、MMR、Hybrid、Metadata 和时间权重。
CREATE TABLE ai_retriever_policy (
    id VARCHAR(64) PRIMARY KEY,
    knowledge_id VARCHAR(64) NOT NULL UNIQUE,
    top_k INTEGER NOT NULL DEFAULT 5,
    strategy VARCHAR(32) NOT NULL DEFAULT 'HYBRID',
    score_threshold DOUBLE NOT NULL DEFAULT 0,
    mmr_lambda DOUBLE NOT NULL DEFAULT 0.5,
    metadata_filter_json TEXT NOT NULL,
    time_weight DOUBLE NOT NULL DEFAULT 0,
    chunk_strategy VARCHAR(32) NOT NULL DEFAULT 'PARAGRAPH',
    chunk_size INTEGER NOT NULL DEFAULT 512,
    chunk_overlap INTEGER NOT NULL DEFAULT 64,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_retriever_strategy ON ai_retriever_policy(strategy);

-- AI Reranker：可替换模型和启用状态。
CREATE TABLE ai_reranker (
    id VARCHAR(64) PRIMARY KEY,
    knowledge_id VARCHAR(64) NOT NULL UNIQUE,
    model_alias VARCHAR(100),
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    top_n INTEGER NOT NULL DEFAULT 5,
    config_json TEXT NOT NULL,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_reranker_enabled ON ai_reranker(enabled, model_alias);

-- AI Knowledge Permission：文档/Chunk 检索前的权限规则。
CREATE TABLE ai_knowledge_permission (
    id VARCHAR(64) PRIMARY KEY,
    knowledge_id VARCHAR(64) NOT NULL,
    permission_type VARCHAR(40) NOT NULL,
    permission_value VARCHAR(200) NOT NULL,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL,
    UNIQUE(knowledge_id, permission_type, permission_value)
);
CREATE INDEX idx_ai_knowledge_permission_lookup ON ai_knowledge_permission(permission_type, permission_value);

-- AI Knowledge Version：索引配置和文档集合快照。
CREATE TABLE ai_knowledge_version (
    id VARCHAR(64) PRIMARY KEY,
    knowledge_id VARCHAR(64) NOT NULL,
    version INTEGER NOT NULL,
    snapshot_json TEXT NOT NULL,
    published_time VARCHAR(40),
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL,
    UNIQUE(knowledge_id, version)
);
CREATE INDEX idx_ai_knowledge_version_knowledge ON ai_knowledge_version(knowledge_id, version);

-- AI Search Log：问题哈希、检索策略、结果和 Trace。
CREATE TABLE ai_search_log (
    id VARCHAR(64) PRIMARY KEY,
    knowledge_id VARCHAR(64) NOT NULL,
    question_hash VARCHAR(128) NOT NULL,
    question_text TEXT,
    strategy VARCHAR(32) NOT NULL,
    result_count INTEGER NOT NULL,
    latency_ms BIGINT NOT NULL,
    trace_id VARCHAR(100) NOT NULL,
    status VARCHAR(32) NOT NULL,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_search_knowledge ON ai_search_log(knowledge_id, create_time);
CREATE INDEX idx_ai_search_trace ON ai_search_log(trace_id);

-- AI Reference：搜索/回答到 Chunk 的可信引用。
CREATE TABLE ai_reference (
    id VARCHAR(64) PRIMARY KEY,
    search_log_id VARCHAR(64) NOT NULL,
    answer_id VARCHAR(100),
    chunk_id VARCHAR(64) NOT NULL,
    score DOUBLE NOT NULL,
    rank_no INTEGER NOT NULL,
    citation_label VARCHAR(200),
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_reference_search ON ai_reference(search_log_id, rank_no);
CREATE INDEX idx_ai_reference_chunk ON ai_reference(chunk_id);
