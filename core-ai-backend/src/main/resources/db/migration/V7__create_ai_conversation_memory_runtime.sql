-- AI Conversation：统一会话容器，支持收藏、归档、标签和逻辑删除。
CREATE TABLE ai_conversation (
    id VARCHAR(64) PRIMARY KEY,
    title VARCHAR(300) NOT NULL,
    owner_id VARCHAR(100) NOT NULL,
    scene_code VARCHAR(100),
    status VARCHAR(32) NOT NULL,
    favorite BOOLEAN NOT NULL DEFAULT FALSE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    last_message_time VARCHAR(40),
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_conversation_owner ON ai_conversation(owner_id, deleted, update_time);
CREATE INDEX idx_ai_conversation_scene ON ai_conversation(scene_code, status);
CREATE INDEX idx_ai_conversation_favorite ON ai_conversation(owner_id, favorite);

-- AI Session：同一 Conversation 的可恢复执行分段。
CREATE TABLE ai_session (
    id VARCHAR(64) PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL,
    session_name VARCHAR(200) NOT NULL,
    status VARCHAR(32) NOT NULL,
    sequence_no INTEGER NOT NULL,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL,
    UNIQUE(conversation_id, sequence_no)
);
CREATE INDEX idx_ai_session_conversation ON ai_session(conversation_id, status, sequence_no);

-- AI Message：消息不可覆盖，修订通过 supersedes_message_id 新增版本。
CREATE TABLE ai_message (
    id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    role VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    content_type VARCHAR(32) NOT NULL,
    token_count INTEGER NOT NULL DEFAULT 0,
    sequence_no INTEGER NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    supersedes_message_id VARCHAR(64),
    trace_id VARCHAR(100),
    metadata_json TEXT NOT NULL,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL,
    UNIQUE(session_id, sequence_no, version)
);
CREATE INDEX idx_ai_message_session ON ai_message(session_id, sequence_no, version);
CREATE INDEX idx_ai_message_trace ON ai_message(trace_id);
CREATE INDEX idx_ai_message_content_type ON ai_message(content_type);

-- AI Message Attachment：只保存外部文件引用，不复制文件正文。
CREATE TABLE ai_message_attachment (
    id VARCHAR(64) PRIMARY KEY,
    message_id VARCHAR(64) NOT NULL,
    file_id VARCHAR(200) NOT NULL,
    attachment_type VARCHAR(40) NOT NULL,
    name VARCHAR(300),
    metadata_json TEXT NOT NULL,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_message_attachment_message ON ai_message_attachment(message_id);
CREATE INDEX idx_ai_message_attachment_file ON ai_message_attachment(file_id);

-- AI Memory：User/Conversation/Organization 独立长期记忆资产。
CREATE TABLE ai_memory (
    id VARCHAR(64) PRIMARY KEY,
    owner_type VARCHAR(32) NOT NULL,
    owner_id VARCHAR(100) NOT NULL,
    memory_type VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    importance DOUBLE NOT NULL DEFAULT 0.5,
    source VARCHAR(100) NOT NULL,
    frozen BOOLEAN NOT NULL DEFAULT FALSE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    metadata_json TEXT NOT NULL,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_memory_owner ON ai_memory(owner_type, owner_id, deleted);
CREATE INDEX idx_ai_memory_type ON ai_memory(memory_type, importance);

-- AI Summary：确定性或外部 SummaryPort 生成的消息区间摘要。
CREATE TABLE ai_summary (
    id VARCHAR(64) PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL,
    summary TEXT NOT NULL,
    message_start INTEGER NOT NULL,
    message_end INTEGER NOT NULL,
    summary_mode VARCHAR(32) NOT NULL,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_summary_conversation ON ai_summary(conversation_id, message_end);

-- AI Context Snapshot：默认只保存脱敏上下文摘要和哈希。
CREATE TABLE ai_context_snapshot (
    id VARCHAR(64) PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    prompt_summary TEXT,
    context_summary TEXT NOT NULL,
    memory_summary TEXT,
    content_hash VARCHAR(128) NOT NULL,
    token_count INTEGER NOT NULL DEFAULT 0,
    content_stored BOOLEAN NOT NULL DEFAULT FALSE,
    expire_time VARCHAR(40),
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_context_session ON ai_context_snapshot(session_id, create_time);
CREATE INDEX idx_ai_context_expire ON ai_context_snapshot(expire_time);

-- AI Conversation Tag。
CREATE TABLE ai_conversation_tag (
    id VARCHAR(64) PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL,
    tag VARCHAR(100) NOT NULL,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL,
    UNIQUE(conversation_id, tag)
);
CREATE INDEX idx_ai_conversation_tag_conversation ON ai_conversation_tag(conversation_id);
CREATE INDEX idx_ai_conversation_tag_value ON ai_conversation_tag(tag);

-- AI Conversation Share：短码、过期时间和撤销状态。
CREATE TABLE ai_conversation_share (
    id VARCHAR(64) PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL,
    share_code VARCHAR(100) NOT NULL UNIQUE,
    expired_at VARCHAR(40),
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_conversation_share_conversation ON ai_conversation_share(conversation_id, revoked);
CREATE INDEX idx_ai_conversation_share_expire ON ai_conversation_share(expired_at);

-- AI Replay Log：默认 Preview 重放，危险 Tool 必须重新审批。
CREATE TABLE ai_replay_log (
    id VARCHAR(64) PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL,
    source_message_id VARCHAR(64),
    request_json TEXT NOT NULL,
    response_json TEXT,
    status VARCHAR(32) NOT NULL,
    mode VARCHAR(32) NOT NULL,
    trace_id VARCHAR(100) NOT NULL,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_replay_conversation ON ai_replay_log(conversation_id, create_time);
CREATE INDEX idx_ai_replay_trace ON ai_replay_log(trace_id);
