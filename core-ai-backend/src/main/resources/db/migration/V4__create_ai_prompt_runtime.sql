-- AI Prompt 定义：稳定 code 与当前 Draft/线上 Published 版本分离，避免编辑影响生产。
CREATE TABLE ai_prompt (
    id VARCHAR(64) PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    category VARCHAR(100) NOT NULL,
    scene_id VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    current_version INTEGER NOT NULL DEFAULT 1,
    published_version INTEGER,
    visibility VARCHAR(32) NOT NULL DEFAULT 'PUBLIC',
    project_code VARCHAR(100),
    department_code VARCHAR(100),
    owner_user VARCHAR(100) NOT NULL,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);

CREATE INDEX idx_ai_prompt_status ON ai_prompt(status);
CREATE INDEX idx_ai_prompt_category ON ai_prompt(category);
CREATE INDEX idx_ai_prompt_scene ON ai_prompt(scene_id);
CREATE INDEX idx_ai_prompt_visibility ON ai_prompt(visibility, project_code, department_code);
CREATE INDEX idx_ai_prompt_published ON ai_prompt(published_version);

-- AI Prompt 版本：内容不可变；每次编辑、Rollback 都创建新版本。
CREATE TABLE ai_prompt_version (
    id VARCHAR(64) PRIMARY KEY,
    prompt_id VARCHAR(64) NOT NULL,
    version INTEGER NOT NULL,
    system_prompt TEXT,
    user_prompt TEXT NOT NULL,
    assistant_prompt TEXT,
    change_log VARCHAR(1000),
    chain_json TEXT NOT NULL,
    tests_passed BOOLEAN NOT NULL DEFAULT FALSE,
    last_tested_time VARCHAR(40),
    published_time VARCHAR(40),
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL,
    UNIQUE(prompt_id, version)
);

CREATE INDEX idx_ai_prompt_version_prompt ON ai_prompt_version(prompt_id, version);
CREATE INDEX idx_ai_prompt_version_published ON ai_prompt_version(prompt_id, published_time);
CREATE INDEX idx_ai_prompt_version_tested ON ai_prompt_version(prompt_id, tests_passed);

-- AI Prompt 变量：版本级强类型变量、默认值和必填约束。
CREATE TABLE ai_prompt_variable (
    id VARCHAR(64) PRIMARY KEY,
    prompt_version_id VARCHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    variable_type VARCHAR(32) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    default_value TEXT,
    description VARCHAR(1000),
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL,
    UNIQUE(prompt_version_id, name)
);

CREATE INDEX idx_ai_prompt_variable_version ON ai_prompt_variable(prompt_version_id);
CREATE INDEX idx_ai_prompt_variable_type ON ai_prompt_variable(variable_type);

-- AI Prompt 输出 Schema：保存可验证 JSON Schema 子集及严格模式。
CREATE TABLE ai_prompt_output_schema (
    id VARCHAR(64) PRIMARY KEY,
    prompt_version_id VARCHAR(64) NOT NULL UNIQUE,
    schema_json TEXT NOT NULL,
    strict_mode BOOLEAN NOT NULL DEFAULT FALSE,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);

CREATE INDEX idx_ai_prompt_schema_version ON ai_prompt_output_schema(prompt_version_id);

-- AI Prompt 测试用例：默认 evaluator 验证渲染结果，未来可替换为真实 Gateway。
CREATE TABLE ai_prompt_testcase (
    id VARCHAR(64) PRIMARY KEY,
    prompt_version_id VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL,
    input_json TEXT NOT NULL,
    expected_output TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_actual_output TEXT,
    last_passed BOOLEAN,
    last_run_time VARCHAR(40),
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);

CREATE INDEX idx_ai_prompt_testcase_version ON ai_prompt_testcase(prompt_version_id, enabled);
CREATE INDEX idx_ai_prompt_testcase_result ON ai_prompt_testcase(prompt_version_id, last_passed);

-- AI Prompt A/B：提供稳定分桶和观测聚合，不伪造线上流量。
CREATE TABLE ai_prompt_abtest (
    id VARCHAR(64) PRIMARY KEY,
    prompt_id VARCHAR(64) NOT NULL,
    scene_id VARCHAR(64),
    name VARCHAR(200) NOT NULL,
    version_a INTEGER NOT NULL,
    version_b INTEGER NOT NULL,
    traffic_ratio INTEGER NOT NULL DEFAULT 50,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sample_a BIGINT NOT NULL DEFAULT 0,
    sample_b BIGINT NOT NULL DEFAULT 0,
    success_a BIGINT NOT NULL DEFAULT 0,
    success_b BIGINT NOT NULL DEFAULT 0,
    latency_a_total BIGINT NOT NULL DEFAULT 0,
    latency_b_total BIGINT NOT NULL DEFAULT 0,
    cost_a_total DECIMAL(20, 8) NOT NULL DEFAULT 0,
    cost_b_total DECIMAL(20, 8) NOT NULL DEFAULT 0,
    score_a_total DOUBLE NOT NULL DEFAULT 0,
    score_b_total DOUBLE NOT NULL DEFAULT 0,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);

CREATE INDEX idx_ai_prompt_abtest_prompt ON ai_prompt_abtest(prompt_id, enabled);
CREATE INDEX idx_ai_prompt_abtest_scene ON ai_prompt_abtest(scene_id, enabled);

-- AI Prompt Guardrail：版本级输入/输出规则，默认本地确定性执行。
CREATE TABLE ai_prompt_guardrail (
    id VARCHAR(64) PRIMARY KEY,
    prompt_version_id VARCHAR(64) NOT NULL,
    rule_type VARCHAR(40) NOT NULL,
    phase VARCHAR(20) NOT NULL,
    config_json TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);

CREATE INDEX idx_ai_prompt_guardrail_version ON ai_prompt_guardrail(prompt_version_id, enabled);
CREATE INDEX idx_ai_prompt_guardrail_rule ON ai_prompt_guardrail(rule_type, phase);

-- AI Prompt Render Log：默认只保存摘要和哈希；正文列仅在显式配置时写入。
CREATE TABLE ai_prompt_render_log (
    id VARCHAR(64) PRIMARY KEY,
    prompt_id VARCHAR(64) NOT NULL,
    prompt_version_id VARCHAR(64) NOT NULL,
    variable_names TEXT NOT NULL,
    variables_json TEXT,
    rendered_prompt TEXT,
    content_hash VARCHAR(128) NOT NULL,
    estimated_tokens INTEGER NOT NULL,
    mode VARCHAR(32) NOT NULL,
    expire_time VARCHAR(40),
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);

CREATE INDEX idx_ai_prompt_render_prompt ON ai_prompt_render_log(prompt_id, create_time);
CREATE INDEX idx_ai_prompt_render_version ON ai_prompt_render_log(prompt_version_id, create_time);
CREATE INDEX idx_ai_prompt_render_expire ON ai_prompt_render_log(expire_time);
