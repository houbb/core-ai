-- AI Usage Event：统一采集 Gateway、Scene、Prompt、Tool、Conversation、Knowledge、Agent 事件。
CREATE TABLE ai_usage_event (
    id VARCHAR(64) PRIMARY KEY,
    request_id VARCHAR(100),
    trace_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    resource_type VARCHAR(40) NOT NULL,
    resource_id VARCHAR(100),
    user_id VARCHAR(100),
    department_id VARCHAR(100),
    project_id VARCHAR(100),
    scene_id VARCHAR(100),
    model_id VARCHAR(100),
    provider_id VARCHAR(100),
    input_tokens BIGINT NOT NULL DEFAULT 0,
    output_tokens BIGINT NOT NULL DEFAULT 0,
    cache_tokens BIGINT NOT NULL DEFAULT 0,
    cost DECIMAL(20, 8) NOT NULL DEFAULT 0,
    currency VARCHAR(16),
    latency_ms BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    error_code VARCHAR(100),
    dimensions_json TEXT NOT NULL,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_usage_time ON ai_usage_event(create_time, status);
CREATE INDEX idx_ai_usage_trace ON ai_usage_event(trace_id);
CREATE INDEX idx_ai_usage_resource ON ai_usage_event(resource_type, resource_id, create_time);
CREATE INDEX idx_ai_usage_dimensions ON ai_usage_event(scene_id, model_id, provider_id);
CREATE INDEX idx_ai_usage_owner ON ai_usage_event(user_id, department_id, project_id);

-- AI Cost Record：由 Usage Event 派生的可审计成本事实。
CREATE TABLE ai_cost_record (
    id VARCHAR(64) PRIMARY KEY,
    usage_event_id VARCHAR(64) NOT NULL,
    resource_type VARCHAR(40) NOT NULL,
    resource_id VARCHAR(100),
    token_count BIGINT NOT NULL DEFAULT 0,
    unit_price DECIMAL(20, 8) NOT NULL DEFAULT 0,
    amount DECIMAL(20, 8) NOT NULL DEFAULT 0,
    currency VARCHAR(16) NOT NULL,
    cost_time VARCHAR(40) NOT NULL,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_cost_resource ON ai_cost_record(resource_type, resource_id, cost_time);
CREATE INDEX idx_ai_cost_event ON ai_cost_record(usage_event_id);

-- AI Metric：可扩展时间序列指标和维度。
CREATE TABLE ai_metric (
    id VARCHAR(64) PRIMARY KEY,
    metric_name VARCHAR(100) NOT NULL,
    metric_value DOUBLE NOT NULL,
    dimension_json TEXT NOT NULL,
    metric_time VARCHAR(40) NOT NULL,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_metric_name_time ON ai_metric(metric_name, metric_time);

-- AI Trace Span：统一 APM 风格父子链路。
CREATE TABLE ai_trace (
    id VARCHAR(64) PRIMARY KEY,
    trace_id VARCHAR(100) NOT NULL,
    parent_id VARCHAR(64),
    span_name VARCHAR(200) NOT NULL,
    resource_type VARCHAR(40),
    resource_id VARCHAR(100),
    duration_ms BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    attributes_json TEXT NOT NULL,
    start_time VARCHAR(40) NOT NULL,
    end_time VARCHAR(40),
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_trace_trace ON ai_trace(trace_id, start_time);
CREATE INDEX idx_ai_trace_resource ON ai_trace(resource_type, resource_id);

-- AI Evaluation：Prompt、Model、Scene、Agent 的自动或人工质量评分。
CREATE TABLE ai_evaluation (
    id VARCHAR(64) PRIMARY KEY,
    target_type VARCHAR(40) NOT NULL,
    target_id VARCHAR(100) NOT NULL,
    evaluation_type VARCHAR(40) NOT NULL,
    score DOUBLE NOT NULL,
    judge VARCHAR(100) NOT NULL,
    dimensions_json TEXT NOT NULL,
    comment TEXT,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_evaluation_target ON ai_evaluation(target_type, target_id, create_time);

-- AI Feedback：用户点赞/点踩、评分和评论。
CREATE TABLE ai_feedback (
    id VARCHAR(64) PRIMARY KEY,
    conversation_id VARCHAR(64),
    message_id VARCHAR(64),
    resource_type VARCHAR(40),
    resource_id VARCHAR(100),
    rating INTEGER NOT NULL,
    comment VARCHAR(4000),
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_feedback_resource ON ai_feedback(resource_type, resource_id, create_time);
CREATE INDEX idx_ai_feedback_conversation ON ai_feedback(conversation_id, message_id);

-- AI Alert Rule：成本、延迟、错误、Token 和质量异常规则。
CREATE TABLE ai_alert_rule (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    metric_name VARCHAR(100) NOT NULL,
    condition_operator VARCHAR(20) NOT NULL,
    threshold_value DOUBLE NOT NULL,
    action VARCHAR(100) NOT NULL,
    scope_json TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_triggered_time VARCHAR(40),
    last_triggered_value DOUBLE,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_alert_metric ON ai_alert_rule(metric_name, enabled);

-- AI Budget：Organization、Department、Project、User 的月度预算和动作。
CREATE TABLE ai_budget (
    id VARCHAR(64) PRIMARY KEY,
    owner_type VARCHAR(32) NOT NULL,
    owner_id VARCHAR(100) NOT NULL,
    period_type VARCHAR(20) NOT NULL,
    currency VARCHAR(16) NOT NULL,
    amount DECIMAL(20, 8) NOT NULL,
    warning_ratio DOUBLE NOT NULL DEFAULT 0.8,
    limit_action VARCHAR(32) NOT NULL DEFAULT 'WARN',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL,
    UNIQUE(owner_type, owner_id, period_type)
);
CREATE INDEX idx_ai_budget_owner ON ai_budget(owner_type, owner_id, enabled);
