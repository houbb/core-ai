-- AI Model 统一模型定义：Provider 只负责连接，Model 负责能力和使用配置。
CREATE TABLE ai_model (
    id VARCHAR(64) PRIMARY KEY,
    provider_id VARCHAR(64) NOT NULL,
    remote_model_id VARCHAR(300) NOT NULL,
    display_name VARCHAR(300) NOT NULL,
    category VARCHAR(40) NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    available_from_provider BOOLEAN NOT NULL DEFAULT TRUE,
    recommended BOOLEAN NOT NULL DEFAULT FALSE,
    favorite BOOLEAN NOT NULL DEFAULT FALSE,
    max_context_tokens INTEGER,
    max_input_tokens INTEGER,
    max_output_tokens INTEGER,
    default_max_tokens INTEGER,
    context_manually_overridden BOOLEAN NOT NULL DEFAULT FALSE,
    last_discovered_at VARCHAR(40),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL,
    UNIQUE(provider_id, remote_model_id)
);

CREATE INDEX idx_ai_model_provider ON ai_model(provider_id);
CREATE INDEX idx_ai_model_status ON ai_model(status);
CREATE INDEX idx_ai_model_category ON ai_model(category);
CREATE INDEX idx_ai_model_enabled ON ai_model(enabled);
CREATE INDEX idx_ai_model_recommend ON ai_model(recommended, favorite);
CREATE INDEX idx_ai_model_context ON ai_model(max_context_tokens);

-- AI Model 能力：保存自动识别结果和逐能力人工覆盖值。
CREATE TABLE ai_model_capability (
    id VARCHAR(64) PRIMARY KEY,
    model_id VARCHAR(64) NOT NULL UNIQUE,
    chat BOOLEAN NOT NULL DEFAULT FALSE,
    reasoning BOOLEAN NOT NULL DEFAULT FALSE,
    vision BOOLEAN NOT NULL DEFAULT FALSE,
    embedding BOOLEAN NOT NULL DEFAULT FALSE,
    rerank BOOLEAN NOT NULL DEFAULT FALSE,
    image BOOLEAN NOT NULL DEFAULT FALSE,
    video BOOLEAN NOT NULL DEFAULT FALSE,
    audio BOOLEAN NOT NULL DEFAULT FALSE,
    speech BOOLEAN NOT NULL DEFAULT FALSE,
    moderation BOOLEAN NOT NULL DEFAULT FALSE,
    ocr BOOLEAN NOT NULL DEFAULT FALSE,
    tool_call BOOLEAN NOT NULL DEFAULT FALSE,
    json_mode BOOLEAN NOT NULL DEFAULT FALSE,
    streaming BOOLEAN NOT NULL DEFAULT FALSE,
    manual_override_json TEXT NOT NULL,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);

CREATE INDEX idx_ai_model_capability_model ON ai_model_capability(model_id);
CREATE INDEX idx_ai_model_capability_chat ON ai_model_capability(chat);
CREATE INDEX idx_ai_model_capability_embedding ON ai_model_capability(embedding);
CREATE INDEX idx_ai_model_capability_vision ON ai_model_capability(vision);

-- AI Model 默认参数：为业务提供推荐默认值，调用方仍可覆盖。
CREATE TABLE ai_model_parameter (
    id VARCHAR(64) PRIMARY KEY,
    model_id VARCHAR(64) NOT NULL UNIQUE,
    temperature DOUBLE,
    top_p DOUBLE,
    frequency_penalty DOUBLE,
    presence_penalty DOUBLE,
    max_output_tokens INTEGER,
    reasoning_effort VARCHAR(40),
    seed BIGINT,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);

CREATE INDEX idx_ai_model_parameter_model ON ai_model_parameter(model_id);

-- AI Model 价格历史：每个生效时间保留一条价格版本，单位默认为每百万 Token。
CREATE TABLE ai_model_pricing (
    id VARCHAR(64) PRIMARY KEY,
    model_id VARCHAR(64) NOT NULL,
    currency VARCHAR(16) NOT NULL,
    prompt_price DECIMAL(20, 8),
    completion_price DECIMAL(20, 8),
    cache_read_price DECIMAL(20, 8),
    cache_write_price DECIMAL(20, 8),
    effective_time VARCHAR(40) NOT NULL,
    source VARCHAR(40) NOT NULL,
    notes TEXT,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL,
    UNIQUE(model_id, effective_time)
);

CREATE INDEX idx_ai_model_pricing_model ON ai_model_pricing(model_id, effective_time);

-- AI Model Alias：同一稳定别名可按优先级绑定多个候选模型。
CREATE TABLE ai_model_alias (
    id VARCHAR(64) PRIMARY KEY,
    alias_code VARCHAR(100) NOT NULL,
    model_id VARCHAR(64) NOT NULL,
    scene VARCHAR(100),
    priority INTEGER NOT NULL DEFAULT 100,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL,
    UNIQUE(alias_code, model_id)
);

CREATE INDEX idx_ai_model_alias_code ON ai_model_alias(alias_code, priority);
CREATE INDEX idx_ai_model_alias_model ON ai_model_alias(model_id);
CREATE INDEX idx_ai_model_alias_scene ON ai_model_alias(scene);

-- AI Model 标签：支持 Production、Coding、OCR、Cheap、Fast 等筛选。
CREATE TABLE ai_model_tag (
    id VARCHAR(64) PRIMARY KEY,
    model_id VARCHAR(64) NOT NULL,
    tag VARCHAR(100) NOT NULL,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL,
    UNIQUE(model_id, tag)
);

CREATE INDEX idx_ai_model_tag_model ON ai_model_tag(model_id);
CREATE INDEX idx_ai_model_tag_value ON ai_model_tag(tag);
