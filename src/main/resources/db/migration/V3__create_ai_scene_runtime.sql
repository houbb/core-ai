-- AI Scene 定义：业务通过稳定 Scene Code 消费能力，不直接依赖 Provider 或 Model。
CREATE TABLE ai_scene (
    id VARCHAR(64) PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    category VARCHAR(100) NOT NULL,
    icon VARCHAR(40),
    status VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    version INTEGER NOT NULL DEFAULT 1,
    recommended BOOLEAN NOT NULL DEFAULT FALSE,
    workflow_json TEXT NOT NULL,
    last_tested_at VARCHAR(40),
    last_tested_version INTEGER,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);

CREATE INDEX idx_ai_scene_status ON ai_scene(status);
CREATE INDEX idx_ai_scene_enabled ON ai_scene(enabled);
CREATE INDEX idx_ai_scene_category ON ai_scene(category);
CREATE INDEX idx_ai_scene_recommended ON ai_scene(recommended);

-- AI Scene 模型绑定：只保存稳定 Alias，支持一个主绑定和多个 fallback。
CREATE TABLE ai_scene_model (
    id VARCHAR(64) PRIMARY KEY,
    scene_id VARCHAR(64) NOT NULL,
    model_alias VARCHAR(100) NOT NULL,
    priority INTEGER NOT NULL DEFAULT 100,
    fallback BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL,
    UNIQUE(scene_id, model_alias)
);

CREATE INDEX idx_ai_scene_model_scene ON ai_scene_model(scene_id, priority);
CREATE INDEX idx_ai_scene_model_alias ON ai_scene_model(model_alias);
CREATE INDEX idx_ai_scene_model_fallback ON ai_scene_model(scene_id, fallback, enabled);

-- AI Scene 参数：覆盖 Model 默认参数，但仍可在真实调用时由业务请求覆盖。
CREATE TABLE ai_scene_parameter (
    id VARCHAR(64) PRIMARY KEY,
    scene_id VARCHAR(64) NOT NULL UNIQUE,
    temperature DOUBLE,
    top_p DOUBLE,
    max_output_tokens INTEGER,
    reasoning_effort VARCHAR(40),
    json_mode BOOLEAN NOT NULL DEFAULT FALSE,
    streaming BOOLEAN NOT NULL DEFAULT TRUE,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);

CREATE INDEX idx_ai_scene_parameter_scene ON ai_scene_parameter(scene_id);

-- AI Scene Prompt 绑定：只保存 Prompt Runtime 的 ID 和版本，不保存 Prompt 内容。
CREATE TABLE ai_scene_prompt (
    id VARCHAR(64) PRIMARY KEY,
    scene_id VARCHAR(64) NOT NULL UNIQUE,
    prompt_id VARCHAR(100),
    prompt_version INTEGER,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL
);

CREATE INDEX idx_ai_scene_prompt_scene ON ai_scene_prompt(scene_id);
CREATE INDEX idx_ai_scene_prompt_reference ON ai_scene_prompt(prompt_id, prompt_version);

-- AI Scene 权限：策略类型支持 Everyone、Role、Department 和 User Group。
CREATE TABLE ai_scene_permission (
    id VARCHAR(64) PRIMARY KEY,
    scene_id VARCHAR(64) NOT NULL,
    permission_type VARCHAR(40) NOT NULL,
    permission_value VARCHAR(200) NOT NULL,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL,
    UNIQUE(scene_id, permission_type, permission_value)
);

CREATE INDEX idx_ai_scene_permission_scene ON ai_scene_permission(scene_id);
CREATE INDEX idx_ai_scene_permission_lookup ON ai_scene_permission(permission_type, permission_value);

-- AI Scene 发布版本：配置快照不可变，Rollback 会复制为新的 Draft 版本。
CREATE TABLE ai_scene_version (
    id VARCHAR(64) PRIMARY KEY,
    scene_id VARCHAR(64) NOT NULL,
    version INTEGER NOT NULL,
    config_json TEXT NOT NULL,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL,
    UNIQUE(scene_id, version)
);

CREATE INDEX idx_ai_scene_version_scene ON ai_scene_version(scene_id, version);

-- AI Scene 模板：内置模板和用户模板使用同一结构，可安全导入导出。
CREATE TABLE ai_scene_template (
    id VARCHAR(64) PRIMARY KEY,
    default_code VARCHAR(100) NOT NULL,
    template_name VARCHAR(200) NOT NULL,
    description TEXT,
    category VARCHAR(100) NOT NULL,
    icon VARCHAR(40),
    builtin BOOLEAN NOT NULL DEFAULT FALSE,
    recommended BOOLEAN NOT NULL DEFAULT FALSE,
    config_json TEXT NOT NULL,
    create_time VARCHAR(40) NOT NULL,
    update_time VARCHAR(40) NOT NULL,
    create_user VARCHAR(100) NOT NULL,
    update_user VARCHAR(100) NOT NULL,
    UNIQUE(default_code, template_name)
);

CREATE INDEX idx_ai_scene_template_builtin ON ai_scene_template(builtin, recommended);
CREATE INDEX idx_ai_scene_template_category ON ai_scene_template(category);

INSERT INTO ai_scene_template(
    id, default_code, template_name, description, category, icon, builtin, recommended,
    config_json, create_time, update_time, create_user, update_user
) VALUES (
    'builtin-scene-chat', 'chat', 'AI Chat', 'General enterprise conversation scene.',
    'CONVERSATION', '💬', TRUE, TRUE,
    '{"name":"AI Chat","description":"General enterprise conversation scene.","category":"CONVERSATION","icon":"💬","recommended":true,"models":[{"modelAlias":"chat-default","priority":10,"fallback":false,"enabled":true}],"parameters":{"temperature":0.7,"topP":0.95,"maxOutputTokens":4096,"reasoningEffort":"medium","jsonMode":false,"streaming":true},"prompt":{"promptId":null,"promptVersion":null},"permissions":[{"type":"EVERYONE","value":"*"}],"workflow":[]}',
    '2026-07-16T00:00:00Z', '2026-07-16T00:00:00Z', 'system', 'system'
);

INSERT INTO ai_scene_template(
    id, default_code, template_name, description, category, icon, builtin, recommended,
    config_json, create_time, update_time, create_user, update_user
) VALUES (
    'builtin-scene-translate', 'translate', 'AI Translate', 'Translation scene with conservative creativity.',
    'TRANSLATE', '🌍', TRUE, TRUE,
    '{"name":"AI Translate","description":"Translation scene with conservative creativity.","category":"TRANSLATE","icon":"🌍","recommended":true,"models":[{"modelAlias":"chat-default","priority":10,"fallback":false,"enabled":true}],"parameters":{"temperature":0.2,"topP":0.9,"maxOutputTokens":8192,"reasoningEffort":"low","jsonMode":false,"streaming":true},"prompt":{"promptId":null,"promptVersion":null},"permissions":[{"type":"EVERYONE","value":"*"}],"workflow":[]}',
    '2026-07-16T00:00:00Z', '2026-07-16T00:00:00Z', 'system', 'system'
);

INSERT INTO ai_scene_template(
    id, default_code, template_name, description, category, icon, builtin, recommended,
    config_json, create_time, update_time, create_user, update_user
) VALUES (
    'builtin-scene-ocr', 'ocr', 'AI OCR', 'Extract structured text from images and documents.',
    'OCR', '👁', TRUE, TRUE,
    '{"name":"AI OCR","description":"Extract structured text from images and documents.","category":"OCR","icon":"👁","recommended":true,"models":[{"modelAlias":"ocr-default","priority":10,"fallback":false,"enabled":true}],"parameters":{"temperature":0.0,"topP":1.0,"maxOutputTokens":8192,"reasoningEffort":"low","jsonMode":true,"streaming":false},"prompt":{"promptId":null,"promptVersion":null},"permissions":[{"type":"EVERYONE","value":"*"}],"workflow":[]}',
    '2026-07-16T00:00:00Z', '2026-07-16T00:00:00Z', 'system', 'system'
);

INSERT INTO ai_scene_template(
    id, default_code, template_name, description, category, icon, builtin, recommended,
    config_json, create_time, update_time, create_user, update_user
) VALUES (
    'builtin-scene-meeting', 'meeting-summary', 'AI Meeting Summary', 'Summarize meetings into decisions and actions.',
    'SUMMARIZE', '📝', TRUE, TRUE,
    '{"name":"AI Meeting Summary","description":"Summarize meetings into decisions and actions.","category":"SUMMARIZE","icon":"📝","recommended":true,"models":[{"modelAlias":"chat-default","priority":10,"fallback":false,"enabled":true}],"parameters":{"temperature":0.3,"topP":0.9,"maxOutputTokens":4096,"reasoningEffort":"medium","jsonMode":false,"streaming":true},"prompt":{"promptId":null,"promptVersion":null},"permissions":[{"type":"EVERYONE","value":"*"}],"workflow":[]}',
    '2026-07-16T00:00:00Z', '2026-07-16T00:00:00Z', 'system', 'system'
);

INSERT INTO ai_scene_template(
    id, default_code, template_name, description, category, icon, builtin, recommended,
    config_json, create_time, update_time, create_user, update_user
) VALUES (
    'builtin-scene-sql', 'sql-assistant', 'AI SQL Assistant', 'Generate and review SQL with structured output.',
    'SQL', '🗄', TRUE, TRUE,
    '{"name":"AI SQL Assistant","description":"Generate and review SQL with structured output.","category":"SQL","icon":"🗄","recommended":true,"models":[{"modelAlias":"coding-default","priority":10,"fallback":false,"enabled":true}],"parameters":{"temperature":0.1,"topP":0.9,"maxOutputTokens":4096,"reasoningEffort":"high","jsonMode":true,"streaming":true},"prompt":{"promptId":null,"promptVersion":null},"permissions":[{"type":"ROLE","value":"DEVELOPER"}],"workflow":[]}',
    '2026-07-16T00:00:00Z', '2026-07-16T00:00:00Z', 'system', 'system'
);

INSERT INTO ai_scene_template(
    id, default_code, template_name, description, category, icon, builtin, recommended,
    config_json, create_time, update_time, create_user, update_user
) VALUES (
    'builtin-scene-coding', 'coding-assistant', 'AI Coding Assistant', 'Coding and code review scene.',
    'CODING', '💻', TRUE, TRUE,
    '{"name":"AI Coding Assistant","description":"Coding and code review scene.","category":"CODING","icon":"💻","recommended":true,"models":[{"modelAlias":"coding-default","priority":10,"fallback":false,"enabled":true}],"parameters":{"temperature":0.2,"topP":0.95,"maxOutputTokens":8192,"reasoningEffort":"high","jsonMode":false,"streaming":true},"prompt":{"promptId":null,"promptVersion":null},"permissions":[{"type":"ROLE","value":"DEVELOPER"}],"workflow":[]}',
    '2026-07-16T00:00:00Z', '2026-07-16T00:00:00Z', 'system', 'system'
);

INSERT INTO ai_scene_template(
    id, default_code, template_name, description, category, icon, builtin, recommended,
    config_json, create_time, update_time, create_user, update_user
) VALUES (
    'builtin-scene-knowledge', 'knowledge-search', 'AI Knowledge Search', 'Knowledge answer scene with an embedding preparation step.',
    'KNOWLEDGE', '🔎', TRUE, TRUE,
    '{"name":"AI Knowledge Search","description":"Knowledge answer scene with an embedding preparation step.","category":"KNOWLEDGE","icon":"🔎","recommended":true,"models":[{"modelAlias":"chat-default","priority":10,"fallback":false,"enabled":true}],"parameters":{"temperature":0.2,"topP":0.9,"maxOutputTokens":4096,"reasoningEffort":"medium","jsonMode":false,"streaming":true},"prompt":{"promptId":null,"promptVersion":null},"permissions":[{"type":"EVERYONE","value":"*"}],"workflow":[{"code":"embedding","type":"MODEL_ALIAS","reference":"embedding-default","optional":false}]}',
    '2026-07-16T00:00:00Z', '2026-07-16T00:00:00Z', 'system', 'system'
);

INSERT INTO ai_scene_template(
    id, default_code, template_name, description, category, icon, builtin, recommended,
    config_json, create_time, update_time, create_user, update_user
) VALUES (
    'builtin-scene-email', 'email-writer', 'AI Email Writer', 'Draft professional business email.',
    'WRITING', '✉', TRUE, TRUE,
    '{"name":"AI Email Writer","description":"Draft professional business email.","category":"WRITING","icon":"✉","recommended":true,"models":[{"modelAlias":"chat-default","priority":10,"fallback":false,"enabled":true}],"parameters":{"temperature":0.6,"topP":0.95,"maxOutputTokens":2048,"reasoningEffort":"low","jsonMode":false,"streaming":true},"prompt":{"promptId":null,"promptVersion":null},"permissions":[{"type":"EVERYONE","value":"*"}],"workflow":[]}',
    '2026-07-16T00:00:00Z', '2026-07-16T00:00:00Z', 'system', 'system'
);
