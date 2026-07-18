-- AI Agent：稳定 Code、生命周期、图标和 Draft/Published 版本。
CREATE TABLE ai_agent (
    id VARCHAR(64) PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL,
    owner_user VARCHAR(100) NOT NULL,
    scene_code VARCHAR(100),
    icon VARCHAR(40),
    color VARCHAR(40),
    tags_json TEXT NOT NULL,
    current_version INTEGER NOT NULL DEFAULT 1,
    published_version INTEGER,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_agent_status ON ai_agent(status, published_version);
CREATE INDEX idx_ai_agent_owner ON ai_agent(owner_user);
CREATE INDEX idx_ai_agent_scene ON ai_agent(scene_code);

-- AI Agent Profile：Role、Goal、Personality、Language、Style 和 Constraints。
CREATE TABLE ai_agent_profile (
    id VARCHAR(64) PRIMARY KEY,
    agent_id VARCHAR(64) NOT NULL UNIQUE,
    role_text VARCHAR(1000) NOT NULL,
    goal_text TEXT NOT NULL,
    personality VARCHAR(1000),
    style VARCHAR(1000),
    language VARCHAR(40),
    constraint_text TEXT,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_agent_profile_agent ON ai_agent_profile(agent_id);

-- AI Agent Planner：确定性或可替换 LLM Planner 配置。
CREATE TABLE ai_agent_planner (
    id VARCHAR(64) PRIMARY KEY,
    agent_id VARCHAR(64) NOT NULL UNIQUE,
    planner_type VARCHAR(40) NOT NULL,
    config_json TEXT NOT NULL,
    max_steps INTEGER NOT NULL DEFAULT 20,
    max_depth INTEGER NOT NULL DEFAULT 5,
    retry_count INTEGER NOT NULL DEFAULT 1,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_agent_planner_type ON ai_agent_planner(planner_type);

-- AI Agent Task：串行、并行、条件、循环、重试和委派任务。
CREATE TABLE ai_agent_task (
    id VARCHAR(64) PRIMARY KEY,
    agent_id VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL,
    order_no INTEGER NOT NULL,
    task_type VARCHAR(40) NOT NULL,
    reference_id VARCHAR(100),
    execution_mode VARCHAR(32) NOT NULL,
    condition_json TEXT NOT NULL,
    config_json TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL,
    UNIQUE(agent_id, order_no)
);
CREATE INDEX idx_ai_agent_task_agent ON ai_agent_task(agent_id, enabled, order_no);
CREATE INDEX idx_ai_agent_task_type ON ai_agent_task(task_type, reference_id);

-- AI Agent Tool 绑定：允许等级和是否需要审批。
CREATE TABLE ai_agent_tool (
    id VARCHAR(64) PRIMARY KEY,
    agent_id VARCHAR(64) NOT NULL,
    tool_id VARCHAR(64) NOT NULL,
    permission VARCHAR(32) NOT NULL,
    approval_required BOOLEAN NOT NULL DEFAULT FALSE,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL,
    UNIQUE(agent_id, tool_id)
);
CREATE INDEX idx_ai_agent_tool_agent ON ai_agent_tool(agent_id);

-- AI Agent Knowledge 绑定。
CREATE TABLE ai_agent_knowledge (
    id VARCHAR(64) PRIMARY KEY,
    agent_id VARCHAR(64) NOT NULL,
    knowledge_id VARCHAR(64) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL,
    UNIQUE(agent_id, knowledge_id)
);
CREATE INDEX idx_ai_agent_knowledge_agent ON ai_agent_knowledge(agent_id);

-- AI Agent Memory 策略。
CREATE TABLE ai_agent_memory (
    id VARCHAR(64) PRIMARY KEY,
    agent_id VARCHAR(64) NOT NULL UNIQUE,
    memory_policy VARCHAR(32) NOT NULL,
    owner_types_json TEXT NOT NULL,
    write_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    max_items INTEGER NOT NULL DEFAULT 20,
    config_json TEXT NOT NULL,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_agent_memory_policy ON ai_agent_memory(memory_policy);

-- AI Agent Execution：每次运行可暂停、审批、恢复和重放。
CREATE TABLE ai_agent_execution (
    id VARCHAR(64) PRIMARY KEY,
    agent_id VARCHAR(64) NOT NULL,
    agent_version INTEGER NOT NULL,
    conversation_id VARCHAR(64),
    goal TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_task_no INTEGER NOT NULL DEFAULT 0,
    result TEXT,
    error_code VARCHAR(100),
    trace_id VARCHAR(100) NOT NULL,
    started_at VARCHAR(40),
    ended_at VARCHAR(40),
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_agent_execution_agent ON ai_agent_execution(agent_id, status, create_time);
CREATE INDEX idx_ai_agent_execution_trace ON ai_agent_execution(trace_id);

-- AI Agent Trace：Planner、Task、Tool、Knowledge、Memory、Gateway 全步骤。
CREATE TABLE ai_agent_trace (
    id VARCHAR(64) PRIMARY KEY,
    execution_id VARCHAR(64) NOT NULL,
    step_no INTEGER NOT NULL,
    stage VARCHAR(40) NOT NULL,
    action VARCHAR(200) NOT NULL,
    result TEXT,
    status VARCHAR(32) NOT NULL,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    metadata_json TEXT NOT NULL,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL,
    UNIQUE(execution_id, step_no)
);
CREATE INDEX idx_ai_agent_trace_execution ON ai_agent_trace(execution_id, step_no);

-- AI Agent Schedule：Spring CronExpression 可执行计划。
CREATE TABLE ai_agent_schedule (
    id VARCHAR(64) PRIMARY KEY,
    agent_id VARCHAR(64) NOT NULL,
    cron_expression VARCHAR(100) NOT NULL,
    goal_template TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    next_run_time VARCHAR(40),
    last_run_time VARCHAR(40),
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_agent_schedule_due ON ai_agent_schedule(enabled, next_run_time);

-- AI Agent Approval：危险 Tool 或业务操作的人机协同门禁。
CREATE TABLE ai_agent_approval (
    id VARCHAR(64) PRIMARY KEY,
    execution_id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64),
    approval_type VARCHAR(40) NOT NULL,
    request_detail TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    approval_token VARCHAR(100) NOT NULL UNIQUE,
    approved_by VARCHAR(100),
    approved_at VARCHAR(40),
    rejection_reason VARCHAR(1000),
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);
CREATE INDEX idx_ai_agent_approval_execution ON ai_agent_approval(execution_id, status);

-- AI Agent Version：Profile、Planner、Task 和资源绑定的不可变快照。
CREATE TABLE ai_agent_version (
    id VARCHAR(64) PRIMARY KEY,
    agent_id VARCHAR(64) NOT NULL,
    version INTEGER NOT NULL,
    config_json TEXT NOT NULL,
    tests_passed BOOLEAN NOT NULL DEFAULT FALSE,
    published_time VARCHAR(40),
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL,
    UNIQUE(agent_id, version)
);
CREATE INDEX idx_ai_agent_version_agent ON ai_agent_version(agent_id, version);
