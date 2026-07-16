-- AI Provider 基本信息：一个记录代表一个可独立配置的 AI 服务连接。
CREATE TABLE ai_provider (
    id VARCHAR(64) PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    provider_type VARCHAR(40) NOT NULL,
    endpoint VARCHAR(1000) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL,
    priority INTEGER NOT NULL DEFAULT 100,
    weight INTEGER NOT NULL DEFAULT 100,
    timeout_seconds INTEGER NOT NULL DEFAULT 15,
    retry_count INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);

CREATE INDEX idx_ai_provider_status ON ai_provider(status);
CREATE INDEX idx_ai_provider_enabled ON ai_provider(enabled);
CREATE INDEX idx_ai_provider_type ON ai_provider(provider_type);
CREATE INDEX idx_ai_provider_priority ON ai_provider(priority, weight);

-- AI Provider 敏感配置：密钥仅保存 AES-GCM 密文，禁止返回明文。
CREATE TABLE ai_provider_secret (
    id VARCHAR(64) PRIMARY KEY,
    provider_id VARCHAR(64) NOT NULL UNIQUE,
    api_key_cipher TEXT,
    api_key_mask VARCHAR(100),
    organization VARCHAR(200),
    proxy VARCHAR(1000),
    tls_verify BOOLEAN NOT NULL DEFAULT TRUE,
    headers_json TEXT NOT NULL,
    custom_parameters_json TEXT NOT NULL,
    encrypted BOOLEAN NOT NULL DEFAULT TRUE,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);

CREATE INDEX idx_ai_provider_secret_provider ON ai_provider_secret(provider_id);

-- AI Provider 能力缓存：由远程模型列表保守识别，可在后续 Model Runtime 中修正。
CREATE TABLE ai_provider_capability (
    id VARCHAR(64) PRIMARY KEY,
    provider_id VARCHAR(64) NOT NULL UNIQUE,
    chat BOOLEAN NOT NULL DEFAULT FALSE,
    vision BOOLEAN NOT NULL DEFAULT FALSE,
    embedding BOOLEAN NOT NULL DEFAULT FALSE,
    image BOOLEAN NOT NULL DEFAULT FALSE,
    audio BOOLEAN NOT NULL DEFAULT FALSE,
    speech BOOLEAN NOT NULL DEFAULT FALSE,
    rerank BOOLEAN NOT NULL DEFAULT FALSE,
    reasoning BOOLEAN NOT NULL DEFAULT FALSE,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);

CREATE INDEX idx_ai_provider_capability_provider ON ai_provider_capability(provider_id);

-- AI Provider 健康快照：记录最近连接结果和可观测字段。
CREATE TABLE ai_provider_health (
    id VARCHAR(64) PRIMARY KEY,
    provider_id VARCHAR(64) NOT NULL UNIQUE,
    latency_ms INTEGER,
    availability DOUBLE NOT NULL DEFAULT 0,
    rpm INTEGER NOT NULL DEFAULT 0,
    tpm INTEGER NOT NULL DEFAULT 0,
    last_success VARCHAR(40),
    last_error VARCHAR(40),
    last_error_message TEXT,
    last_status_code INTEGER,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);

CREATE INDEX idx_ai_provider_health_provider ON ai_provider_health(provider_id);
CREATE INDEX idx_ai_provider_health_success ON ai_provider_health(last_success);

-- AI Provider 模型缓存：只缓存远程目录，不承载 Model Runtime 的完整模型配置。
CREATE TABLE ai_provider_model_cache (
    id VARCHAR(64) PRIMARY KEY,
    provider_id VARCHAR(64) NOT NULL,
    model_id VARCHAR(300) NOT NULL,
    display_name VARCHAR(300) NOT NULL,
    capability_json TEXT NOT NULL,
    context_length INTEGER,
    status VARCHAR(32) NOT NULL,
    last_sync_at VARCHAR(40) NOT NULL,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL,
    UNIQUE(provider_id, model_id)
);

CREATE INDEX idx_ai_provider_model_provider ON ai_provider_model_cache(provider_id);
CREATE INDEX idx_ai_provider_model_status ON ai_provider_model_cache(status);

-- AI Provider 标签：用于 Cloud、Local、Production、Vision 等筛选。
CREATE TABLE ai_provider_tag (
    id VARCHAR(64) PRIMARY KEY,
    provider_id VARCHAR(64) NOT NULL,
    tag VARCHAR(100) NOT NULL,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL,
    UNIQUE(provider_id, tag)
);

CREATE INDEX idx_ai_provider_tag_provider ON ai_provider_tag(provider_id);
CREATE INDEX idx_ai_provider_tag_value ON ai_provider_tag(tag);

-- AI 审计日志：记录 Provider 写操作和连接操作，不保存任何密钥明文。
CREATE TABLE ai_audit_log (
    id VARCHAR(64) PRIMARY KEY,
    resource_type VARCHAR(80) NOT NULL,
    resource_id VARCHAR(64) NOT NULL,
    action VARCHAR(80) NOT NULL,
    result VARCHAR(32) NOT NULL,
    detail TEXT,
    trace_id VARCHAR(100),
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);

CREATE INDEX idx_ai_audit_resource ON ai_audit_log(resource_type, resource_id);
CREATE INDEX idx_ai_audit_action ON ai_audit_log(action);
CREATE INDEX idx_ai_audit_time ON ai_audit_log(create_time);
