-- AI Gateway 集群定义：默认单节点，可扩展为多个 Published Gateway。
CREATE TABLE ai_gateway (
    id VARCHAR(64) PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    current_version INTEGER NOT NULL DEFAULT 1,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_gateway_status ON ai_gateway(status, enabled);

-- Gateway 路由：Scene + Alias 到候选模型/Provider 的可解释规则。
CREATE TABLE ai_gateway_route (
    id VARCHAR(64) PRIMARY KEY,
    gateway_id VARCHAR(64) NOT NULL,
    scene_code VARCHAR(100),
    alias_code VARCHAR(100) NOT NULL,
    model_id VARCHAR(64),
    provider_id VARCHAR(64),
    routing_strategy VARCHAR(40) NOT NULL,
    priority INTEGER NOT NULL DEFAULT 100,
    weight INTEGER NOT NULL DEFAULT 100,
    local_preferred BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_gateway_route_lookup ON ai_gateway_route(gateway_id, scene_code, alias_code, enabled);
CREATE INDEX idx_ai_gateway_route_provider ON ai_gateway_route(provider_id, enabled);

-- Gateway Policy：统一保存 Pipeline 策略对象和默认超时。
CREATE TABLE ai_gateway_policy (
    id VARCHAR(64) PRIMARY KEY,
    gateway_id VARCHAR(64) NOT NULL UNIQUE,
    policy_json TEXT NOT NULL,
    timeout_seconds INTEGER NOT NULL DEFAULT 30,
    fallback_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    streaming_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_gateway_policy_gateway ON ai_gateway_policy(gateway_id);

-- Gateway Retry：同一 Provider 的重试策略，与 Fallback 分离。
CREATE TABLE ai_gateway_retry (
    id VARCHAR(64) PRIMARY KEY,
    policy_id VARCHAR(64) NOT NULL UNIQUE,
    max_retry INTEGER NOT NULL DEFAULT 1,
    strategy VARCHAR(32) NOT NULL DEFAULT 'EXPONENTIAL',
    interval_ms BIGINT NOT NULL DEFAULT 200,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_gateway_retry_policy ON ai_gateway_retry(policy_id);

-- Gateway Circuit：Provider 级失败计数和恢复窗口。
CREATE TABLE ai_gateway_circuit (
    id VARCHAR(64) PRIMARY KEY,
    gateway_id VARCHAR(64) NOT NULL,
    provider_id VARCHAR(64) NOT NULL,
    failure_threshold INTEGER NOT NULL DEFAULT 5,
    recover_seconds INTEGER NOT NULL DEFAULT 30,
    state VARCHAR(20) NOT NULL DEFAULT 'CLOSED',
    failure_count INTEGER NOT NULL DEFAULT 0,
    opened_time VARCHAR(40),
    last_failure_time VARCHAR(40),
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL,
    UNIQUE(gateway_id, provider_id)
);
CREATE INDEX idx_ai_gateway_circuit_state ON ai_gateway_circuit(state, opened_time);

-- Gateway Rate Limit：User/Department/Project/Provider/Scene/IP 多维规则。
CREATE TABLE ai_gateway_rate_limit (
    id VARCHAR(64) PRIMARY KEY,
    gateway_id VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_value VARCHAR(200) NOT NULL,
    rpm INTEGER,
    tpm BIGINT,
    daily_limit BIGINT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL,
    UNIQUE(gateway_id, target_type, target_value)
);
CREATE INDEX idx_ai_gateway_rate_target ON ai_gateway_rate_limit(target_type, target_value, enabled);

-- Gateway Cache：本地 SQLite Prompt/Response Cache，未来可替换 Redis。
CREATE TABLE ai_gateway_cache (
    id VARCHAR(64) PRIMARY KEY,
    gateway_id VARCHAR(64) NOT NULL,
    scene_code VARCHAR(100) NOT NULL,
    cache_key VARCHAR(128) NOT NULL UNIQUE,
    strategy VARCHAR(32) NOT NULL,
    response_json TEXT NOT NULL,
    hit_count BIGINT NOT NULL DEFAULT 0,
    expire_time VARCHAR(40) NOT NULL,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_gateway_cache_scene ON ai_gateway_cache(scene_code, expire_time);
CREATE INDEX idx_ai_gateway_cache_expire ON ai_gateway_cache(expire_time);

-- Gateway Trace：每次请求的完整路由、重试、Fallback、成本和状态。
CREATE TABLE ai_gateway_trace (
    id VARCHAR(64) PRIMARY KEY,
    gateway_id VARCHAR(64) NOT NULL,
    request_id VARCHAR(100) NOT NULL,
    trace_id VARCHAR(100) NOT NULL,
    scene_code VARCHAR(100),
    alias_code VARCHAR(100),
    provider_id VARCHAR(64),
    model_id VARCHAR(64),
    retry_count INTEGER NOT NULL DEFAULT 0,
    fallback_count INTEGER NOT NULL DEFAULT 0,
    cache_hit BOOLEAN NOT NULL DEFAULT FALSE,
    input_tokens BIGINT NOT NULL DEFAULT 0,
    output_tokens BIGINT NOT NULL DEFAULT 0,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    cost DECIMAL(20, 8) NOT NULL DEFAULT 0,
    currency VARCHAR(16),
    status VARCHAR(32) NOT NULL,
    error_code VARCHAR(100),
    trace_json TEXT NOT NULL,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_gateway_trace_request ON ai_gateway_trace(request_id);
CREATE INDEX idx_ai_gateway_trace_trace ON ai_gateway_trace(trace_id);
CREATE INDEX idx_ai_gateway_trace_time ON ai_gateway_trace(create_time, status);
CREATE INDEX idx_ai_gateway_trace_resource ON ai_gateway_trace(scene_code, model_id, provider_id);

-- Gateway Cluster：其他 Gateway 节点元数据。
CREATE TABLE ai_gateway_cluster (
    id VARCHAR(64) PRIMARY KEY,
    gateway_id VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL,
    endpoint VARCHAR(1000) NOT NULL,
    weight INTEGER NOT NULL DEFAULT 100,
    status VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_heartbeat VARCHAR(40),
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_gateway_cluster_gateway ON ai_gateway_cluster(gateway_id, enabled);

-- Gateway Dashboard 日聚合快照；原始事实仍来自 Trace。
CREATE TABLE ai_gateway_dashboard (
    id VARCHAR(64) PRIMARY KEY,
    gateway_id VARCHAR(64) NOT NULL,
    metric_date VARCHAR(20) NOT NULL,
    request_count BIGINT NOT NULL DEFAULT 0,
    success_count BIGINT NOT NULL DEFAULT 0,
    error_count BIGINT NOT NULL DEFAULT 0,
    avg_latency DOUBLE NOT NULL DEFAULT 0,
    total_cost DECIMAL(20, 8) NOT NULL DEFAULT 0,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL,
    UNIQUE(gateway_id, metric_date)
);
CREATE INDEX idx_ai_gateway_dashboard_date ON ai_gateway_dashboard(metric_date);

INSERT INTO ai_gateway(
    id, code, name, description, status, enabled, current_version,
    create_time, update_time, create_user, update_user
) VALUES (
    'default-gateway', 'default', 'Default AI Gateway',
    'Local production pipeline with replaceable provider execution adapter.',
    'PUBLISHED', TRUE, 1,
    '2026-07-16T00:00:00Z', '2026-07-16T00:00:00Z', 'system', 'system'
);
INSERT INTO ai_gateway_policy(
    id, gateway_id, policy_json, timeout_seconds, fallback_enabled, streaming_enabled,
    create_time, update_time, create_user, update_user
) VALUES (
    'default-gateway-policy', 'default-gateway',
    '{"cacheEnabled":true,"cacheTtlSeconds":300,"rateLimitEnabled":true,"circuitEnabled":true}',
    30, TRUE, TRUE,
    '2026-07-16T00:00:00Z', '2026-07-16T00:00:00Z', 'system', 'system'
);
INSERT INTO ai_gateway_retry(
    id, policy_id, max_retry, strategy, interval_ms,
    create_time, update_time, create_user, update_user
) VALUES (
    'default-gateway-retry', 'default-gateway-policy', 1, 'EXPONENTIAL', 200,
    '2026-07-16T00:00:00Z', '2026-07-16T00:00:00Z', 'system', 'system'
);
