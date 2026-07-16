-- AI Tool 定义：稳定 Code、生命周期和 Draft/Published 双版本指针。
CREATE TABLE ai_tool (
    id VARCHAR(64) PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    category VARCHAR(100) NOT NULL,
    tool_type VARCHAR(40) NOT NULL,
    icon VARCHAR(40),
    owner_user VARCHAR(100) NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_version INTEGER NOT NULL DEFAULT 1,
    published_version INTEGER,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_tool_status ON ai_tool(status);
CREATE INDEX idx_ai_tool_category ON ai_tool(category);
CREATE INDEX idx_ai_tool_type ON ai_tool(tool_type);
CREATE INDEX idx_ai_tool_published ON ai_tool(published_version);

-- AI Tool 不可变版本：统一保存 Schema、Executor、Output 和 Chain。
CREATE TABLE ai_tool_version (
    id VARCHAR(64) PRIMARY KEY,
    tool_id VARCHAR(64) NOT NULL,
    version INTEGER NOT NULL,
    schema_json TEXT NOT NULL,
    output_schema_json TEXT,
    executor_type VARCHAR(40) NOT NULL,
    executor_config_json TEXT NOT NULL,
    chain_json TEXT NOT NULL,
    change_log VARCHAR(1000),
    tests_passed BOOLEAN NOT NULL DEFAULT FALSE,
    last_tested_time VARCHAR(40),
    published_time VARCHAR(40),
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL,
    UNIQUE(tool_id, version)
);
CREATE INDEX idx_ai_tool_version_tool ON ai_tool_version(tool_id, version);
CREATE INDEX idx_ai_tool_version_published ON ai_tool_version(tool_id, published_time);

-- AI Tool 参数：强类型、默认值、枚举、Regex 和范围校验。
CREATE TABLE ai_tool_parameter (
    id VARCHAR(64) PRIMARY KEY,
    tool_version_id VARCHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    parameter_type VARCHAR(32) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    default_value TEXT,
    validation_rule TEXT,
    description VARCHAR(1000),
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL,
    UNIQUE(tool_version_id, name)
);
CREATE INDEX idx_ai_tool_parameter_version ON ai_tool_parameter(tool_version_id);

-- AI Tool 权限：Everyone、Role、Department、User、AI Only。
CREATE TABLE ai_tool_permission (
    id VARCHAR(64) PRIMARY KEY,
    tool_id VARCHAR(64) NOT NULL,
    permission_type VARCHAR(40) NOT NULL,
    permission_value VARCHAR(200) NOT NULL,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL,
    UNIQUE(tool_id, permission_type, permission_value)
);
CREATE INDEX idx_ai_tool_permission_tool ON ai_tool_permission(tool_id);
CREATE INDEX idx_ai_tool_permission_lookup ON ai_tool_permission(permission_type, permission_value);

-- AI Tool 执行策略：读写等级、确认、审批、超时和重试。
CREATE TABLE ai_tool_policy (
    id VARCHAR(64) PRIMARY KEY,
    tool_id VARCHAR(64) NOT NULL UNIQUE,
    access_level VARCHAR(32) NOT NULL,
    readonly BOOLEAN NOT NULL DEFAULT TRUE,
    manual_confirm BOOLEAN NOT NULL DEFAULT FALSE,
    approval_required BOOLEAN NOT NULL DEFAULT FALSE,
    timeout_seconds INTEGER NOT NULL DEFAULT 15,
    retry_count INTEGER NOT NULL DEFAULT 0,
    log_content BOOLEAN NOT NULL DEFAULT FALSE,
    retention_days INTEGER NOT NULL DEFAULT 7,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_tool_policy_level ON ai_tool_policy(access_level);

-- AI Tool 业务绑定：REST、MCP、Database、Plugin、Workflow 等目标。
CREATE TABLE ai_tool_binding (
    id VARCHAR(64) PRIMARY KEY,
    tool_id VARCHAR(64) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id VARCHAR(500) NOT NULL,
    config_json TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_tool_binding_tool ON ai_tool_binding(tool_id, enabled);
CREATE INDEX idx_ai_tool_binding_target ON ai_tool_binding(target_type, target_id);

-- AI Tool 测试用例：版本级输入与预期结果。
CREATE TABLE ai_tool_testcase (
    id VARCHAR(64) PRIMARY KEY,
    tool_version_id VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL,
    input_json TEXT NOT NULL,
    expected_result TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_actual_result TEXT,
    last_passed BOOLEAN,
    last_run_time VARCHAR(40),
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_tool_testcase_version ON ai_tool_testcase(tool_version_id, enabled);

-- AI Tool 执行日志：默认保存脱敏请求/响应，并承载确认与审批状态。
CREATE TABLE ai_tool_execution_log (
    id VARCHAR(64) PRIMARY KEY,
    tool_id VARCHAR(64) NOT NULL,
    tool_version INTEGER NOT NULL,
    request_json TEXT,
    response_json TEXT,
    request_hash VARCHAR(128) NOT NULL,
    status VARCHAR(40) NOT NULL,
    mode VARCHAR(32) NOT NULL,
    approval_token VARCHAR(100),
    confirmed_by VARCHAR(100),
    approved_by VARCHAR(100),
    latency_ms BIGINT NOT NULL DEFAULT 0,
    error_code VARCHAR(100),
    error_message TEXT,
    trace_id VARCHAR(100) NOT NULL,
    expire_time VARCHAR(40),
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_tool_execution_tool ON ai_tool_execution_log(tool_id, create_time);
CREATE INDEX idx_ai_tool_execution_status ON ai_tool_execution_log(status, create_time);
CREATE INDEX idx_ai_tool_execution_trace ON ai_tool_execution_log(trace_id);
CREATE INDEX idx_ai_tool_execution_expire ON ai_tool_execution_log(expire_time);

-- AI Tool Marketplace：本地可安装模板及未来外部市场元数据。
CREATE TABLE ai_tool_market (
    id VARCHAR(64) PRIMARY KEY,
    tool_name VARCHAR(200) NOT NULL,
    tool_code VARCHAR(100) NOT NULL,
    publisher VARCHAR(200) NOT NULL,
    version VARCHAR(40) NOT NULL,
    category VARCHAR(100) NOT NULL,
    description TEXT,
    manifest_json TEXT NOT NULL,
    install_count BIGINT NOT NULL DEFAULT 0,
    builtin BOOLEAN NOT NULL DEFAULT FALSE,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL,
    UNIQUE(tool_code, publisher, version)
);
CREATE INDEX idx_ai_tool_market_category ON ai_tool_market(category, builtin);

INSERT INTO ai_tool_market(
    id, tool_name, tool_code, publisher, version, category, description,
    manifest_json, install_count, builtin,
    create_time, update_time, create_user, update_user
) VALUES (
    'builtin-tool-http', 'HTTP API Tool', 'http-api', 'core-ai', '1.0.0', 'HTTP',
    'Call an allow-listed HTTP or REST endpoint.',
    '{"toolType":"REST","executorType":"HTTP","schema":{"type":"object","properties":{"body":{"type":"object"}}},"policy":{"accessLevel":"READ_WRITE","manualConfirm":false,"approvalRequired":false}}',
    0, TRUE, '2026-07-16T00:00:00Z', '2026-07-16T00:00:00Z', 'system', 'system'
);
INSERT INTO ai_tool_market(
    id, tool_name, tool_code, publisher, version, category, description,
    manifest_json, install_count, builtin,
    create_time, update_time, create_user, update_user
) VALUES (
    'builtin-tool-mock', 'Mock Development Tool', 'mock-tool', 'core-ai', '1.0.0', 'PLUGIN',
    'Deterministic local Tool for development and testing.',
    '{"toolType":"PLUGIN","executorType":"MOCK","schema":{"type":"object"},"executorConfig":{"response":{"ok":true}},"policy":{"accessLevel":"READ_ONLY","manualConfirm":false,"approvalRequired":false}}',
    0, TRUE, '2026-07-16T00:00:00Z', '2026-07-16T00:00:00Z', 'system', 'system'
);
INSERT INTO ai_tool_market(
    id, tool_name, tool_code, publisher, version, category, description,
    manifest_json, install_count, builtin,
    create_time, update_time, create_user, update_user
) VALUES (
    'builtin-tool-mcp', 'MCP Connector', 'mcp-connector', 'core-ai', '1.0.0', 'MCP',
    'Register a local or remote MCP server through a replaceable adapter.',
    '{"toolType":"MCP","executorType":"MCP","schema":{"type":"object"},"policy":{"accessLevel":"READ_ONLY","manualConfirm":false,"approvalRequired":false}}',
    0, TRUE, '2026-07-16T00:00:00Z', '2026-07-16T00:00:00Z', 'system', 'system'
);
