package io.coreplatform.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles({"sqlite", "local"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RemainingRuntimesE2ETest {

    private static final Path DATABASE = Path.of(
            "target",
            "remaining-runtimes-e2e-" + UUID.randomUUID() + ".db"
    ).toAbsolutePath();

    @LocalServerPort
    private int applicationPort;

    @Autowired
    private TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE);
        registry.add("server.address", () -> "127.0.0.1");
        registry.add("core.agent.schedule-delay-ms", () -> "3600000");
    }

    @Test
    void shouldRunToolGatewayMemoryKnowledgeAgentAndAnalyticsEndToEnd() {
        ToolFixture tool = toolLifecycle();
        String gatewayTraceId = gatewayLifecycle();
        conversationAndMemoryLifecycle();
        knowledgeLifecycle();
        agentLifecycle(tool.id());
        analyticsLifecycle(gatewayTraceId);
    }

    private ToolFixture toolLifecycle() {
        JsonNode created = exchange(
                "/api/v1/ai/admin/tools",
                HttpMethod.POST,
                toolRequest(),
                JsonNode.class
        ).getBody();
        String id = created.path("tool").path("id").asText();
        String code = created.path("tool").path("code").asText();
        assertThat(created.path("tool").path("status").asText()).isEqualTo("DRAFT");
        assertThat(created.path("currentVersion").path("executorType").asText()).isEqualTo("MOCK");

        exchange(
                "/api/v1/ai/admin/tools/" + id + "/test-cases",
                HttpMethod.POST,
                Map.of(
                        "name", "Mock assertion",
                        "inputJson", "{\"name\":\"Codex\"}",
                        "expectedResult", "{\"ok\":true,\"source\":\"e2e\"}",
                        "enabled", true
                ),
                JsonNode.class
        );
        JsonNode suite = exchange(
                "/api/v1/ai/admin/tools/" + id + "/tests/run",
                HttpMethod.POST,
                null,
                JsonNode.class
        ).getBody();
        assertThat(suite.path("allPassed").asBoolean()).isTrue();
        assertThat(suite.path("passed").asInt()).isEqualTo(1);

        JsonNode testing = patchStatus("/api/v1/ai/admin/tools/" + id + "/status", "TESTING");
        assertThat(testing.path("tool").path("status").asText()).isEqualTo("TESTING");
        JsonNode published = patchStatus("/api/v1/ai/admin/tools/" + id + "/status", "PUBLISHED");
        assertThat(published.path("tool").path("publishedVersion").asInt()).isEqualTo(1);

        JsonNode execution = exchange(
                "/api/v1/ai/tools/" + code + "/execute",
                HttpMethod.POST,
                Map.of("name", "Codex", "apiKey", "must-not-leak"),
                JsonNode.class
        ).getBody();
        assertThat(execution.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(execution.path("mode").asText()).isEqualTo("LOCAL");
        assertThat(execution.path("response").path("ok").asBoolean()).isTrue();
        assertThat(execution.path("request").path("apiKey").asText()).isEqualTo("***");

        JsonNode logs = exchange(
                "/api/v1/ai/admin/tools/" + id + "/executions",
                HttpMethod.GET,
                null,
                JsonNode.class
        ).getBody();
        assertThat(logs).hasSize(1);
        assertThat(logs.toString()).doesNotContain("must-not-leak");
        return new ToolFixture(id, code);
    }

    private String gatewayLifecycle() {
        JsonNode gateways = exchange(
                "/api/v1/ai/admin/gateways",
                HttpMethod.GET,
                null,
                JsonNode.class
        ).getBody();
        assertThat(gateways).hasSize(1);
        String id = gateways.get(0).path("id").asText();

        Map<String, Object> request = Map.of(
                "sceneCode", "e2e",
                "aliasCode", "chat-default",
                "input", "Gateway cache probe",
                "parameters", Map.of("temperature", 0),
                "cacheable", true,
                "streaming", false
        );
        JsonNode first = exchange(
                "/api/v1/ai/gateway/invoke",
                HttpMethod.POST,
                request,
                JsonNode.class
        ).getBody();
        JsonNode second = exchange(
                "/api/v1/ai/gateway/invoke",
                HttpMethod.POST,
                request,
                JsonNode.class
        ).getBody();
        assertThat(first.path("mode").asText()).isEqualTo("PREVIEW");
        assertThat(first.path("cacheHit").asBoolean()).isFalse();
        assertThat(second.path("cacheHit").asBoolean()).isTrue();
        assertThat(second.path("output").asText()).isEqualTo(first.path("output").asText());

        JsonNode traces = exchange(
                "/api/v1/ai/admin/gateways/" + id + "/traces",
                HttpMethod.GET,
                null,
                JsonNode.class
        ).getBody();
        assertThat(traces.size()).isGreaterThanOrEqualTo(2);
        JsonNode dashboard = exchange(
                "/api/v1/ai/admin/gateways/" + id + "/dashboard",
                HttpMethod.GET,
                null,
                JsonNode.class
        ).getBody();
        assertThat(dashboard.path("requests").asLong()).isGreaterThanOrEqualTo(2);
        assertThat(dashboard.path("cacheHits").asLong()).isGreaterThanOrEqualTo(1);
        return first.path("traceId").asText();
    }

    private void conversationAndMemoryLifecycle() {
        JsonNode created = exchange(
                "/api/v1/ai/conversations",
                HttpMethod.POST,
                Map.of("title", "E2E Conversation", "sceneCode", "chat", "tags", List.of("e2e")),
                JsonNode.class
        ).getBody();
        String id = created.path("conversation").path("id").asText();
        assertThat(created.path("sessions")).hasSize(1);

        JsonNode chat = exchange(
                "/api/v1/ai/conversations/" + id + "/messages",
                HttpMethod.POST,
                Map.of("content", "Hello memory runtime", "contentType", "TEXT", "parameters", Map.of()),
                JsonNode.class
        ).getBody();
        String userMessageId = chat.path("userMessage").path("id").asText();
        assertThat(chat.path("gatewayMode").asText()).isEqualTo("PREVIEW");
        assertThat(chat.path("context").path("contentStored").asBoolean()).isFalse();

        JsonNode revision = exchange(
                "/api/v1/ai/messages/" + userMessageId,
                HttpMethod.PUT,
                Map.of("content", "Hello immutable memory runtime"),
                JsonNode.class
        ).getBody();
        assertThat(revision.path("version").asInt()).isEqualTo(2);
        assertThat(revision.path("supersedesMessageId").asText()).isEqualTo(userMessageId);

        JsonNode summary = exchange(
                "/api/v1/ai/conversations/" + id + "/summary",
                HttpMethod.POST,
                null,
                JsonNode.class
        ).getBody();
        assertThat(summary.path("mode").asText()).isEqualTo("DETERMINISTIC");

        JsonNode memory = exchange(
                "/api/v1/ai/memories",
                HttpMethod.POST,
                Map.of(
                        "ownerType", "USER",
                        "ownerId", "local-admin",
                        "memoryType", "PREFERENCE",
                        "content", "Prefer concise runtime reports",
                        "importance", 0.9,
                        "source", "E2E",
                        "metadata", Map.of("scope", "testing")
                ),
                JsonNode.class
        ).getBody();
        String memoryId = memory.path("id").asText();
        JsonNode frozen = exchange(
                "/api/v1/ai/memories/" + memoryId + "/frozen",
                HttpMethod.PATCH,
                Map.of("frozen", true),
                JsonNode.class
        ).getBody();
        assertThat(frozen.path("frozen").asBoolean()).isTrue();
        ResponseEntity<JsonNode> deleteFrozen = exchange(
                "/api/v1/ai/memories/" + memoryId,
                HttpMethod.DELETE,
                null,
                JsonNode.class
        );
        assertThat(deleteFrozen.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        exchange(
                "/api/v1/ai/memories/" + memoryId + "/frozen",
                HttpMethod.PATCH,
                Map.of("frozen", false),
                JsonNode.class
        );
        exchange(
                "/api/v1/ai/memories/" + memoryId,
                HttpMethod.DELETE,
                null,
                Void.class
        );
        JsonNode memories = exchange(
                "/api/v1/ai/memories?ownerType=USER&ownerId=local-admin",
                HttpMethod.GET,
                null,
                JsonNode.class
        ).getBody();
        assertThat(memories).isEmpty();

        JsonNode share = exchange(
                "/api/v1/ai/conversations/" + id + "/shares",
                HttpMethod.POST,
                Map.of("expiresInHours", 24),
                JsonNode.class
        ).getBody();
        JsonNode shared = exchange(
                "/api/v1/ai/conversation-shares/" + share.path("shareCode").asText(),
                HttpMethod.GET,
                null,
                JsonNode.class
        ).getBody();
        assertThat(shared.path("conversation").path("id").asText()).isEqualTo(id);

        JsonNode exported = exchange(
                "/api/v1/ai/conversations/" + id + "/export",
                HttpMethod.GET,
                null,
                JsonNode.class
        ).getBody();
        assertThat(exported.path("formatVersion").asInt()).isEqualTo(1);
        assertThat(exported.path("messages").size()).isGreaterThanOrEqualTo(2);

        JsonNode replay = exchange(
                "/api/v1/ai/conversations/" + id + "/replay",
                HttpMethod.POST,
                Map.of(),
                JsonNode.class
        ).getBody();
        assertThat(replay.path("mode").asText()).isEqualTo("PREVIEW");
        assertThat(replay.path("dangerousToolsRequireNewApproval").asBoolean()).isTrue();
    }

    private void knowledgeLifecycle() {
        JsonNode created = exchange(
                "/api/v1/ai/admin/knowledge",
                HttpMethod.POST,
                knowledgeRequest(),
                JsonNode.class
        ).getBody();
        String id = created.path("knowledge").path("id").asText();
        String code = created.path("knowledge").path("code").asText();
        assertThat(created.path("knowledge").path("status").asText()).isEqualTo("DRAFT");

        exchange(
                "/api/v1/ai/admin/knowledge/" + id + "/documents",
                HttpMethod.POST,
                Map.of(
                        "title", "Core Runtime Guide",
                        "path", "/docs/runtime.md",
                        "content", "The local Gateway routes every AI request.\n\n"
                                + "Knowledge retrieval applies permissions before scoring and returns citations.",
                        "language", "en",
                        "mimeType", "text/markdown",
                        "metadata", Map.of("product", "core-ai"),
                        "permissions", List.of("EVERYONE:*")
                ),
                JsonNode.class
        );
        JsonNode ready = exchange(
                "/api/v1/ai/admin/knowledge/" + id + "/process",
                HttpMethod.POST,
                null,
                JsonNode.class
        ).getBody();
        assertThat(ready.path("knowledge").path("status").asText()).isEqualTo("READY");
        assertThat(ready.path("knowledge").path("progress").asInt()).isEqualTo(100);

        JsonNode published = exchange(
                "/api/v1/ai/admin/knowledge/" + id + "/publish",
                HttpMethod.POST,
                null,
                JsonNode.class
        ).getBody();
        assertThat(published.path("knowledge").path("publishedVersion").asInt()).isEqualTo(1);

        JsonNode result = exchange(
                "/api/v1/ai/knowledge/" + code + "/search",
                HttpMethod.POST,
                Map.of("query", "local Gateway routes", "topK", 5),
                JsonNode.class
        ).getBody();
        assertThat(result.path("hits").size()).isGreaterThanOrEqualTo(1);
        assertThat(result.path("hits").get(0).path("citation").asText()).startsWith("[1]");
        assertThat(result.path("strategy").asText()).isEqualTo("HYBRID");
    }

    private void agentLifecycle(String toolId) {
        JsonNode created = exchange(
                "/api/v1/ai/admin/agents",
                HttpMethod.POST,
                agentRequest(toolId),
                JsonNode.class
        ).getBody();
        String id = created.path("agent").path("id").asText();
        String code = created.path("agent").path("code").asText();
        assertThat(created.path("tasks")).hasSize(1);

        JsonNode tested = exchange(
                "/api/v1/ai/admin/agents/" + id + "/tests/run",
                HttpMethod.POST,
                null,
                JsonNode.class
        ).getBody();
        assertThat(tested.path("passed").asBoolean()).isTrue();
        patchStatus("/api/v1/ai/admin/agents/" + id + "/status", "TESTING");
        JsonNode published = patchStatus("/api/v1/ai/admin/agents/" + id + "/status", "PUBLISHED");
        assertThat(published.path("agent").path("publishedVersion").asInt()).isEqualTo(1);

        JsonNode waiting = exchange(
                "/api/v1/ai/agents/" + code + "/execute",
                HttpMethod.POST,
                Map.of("goal", "Use the approved mock Tool"),
                JsonNode.class
        ).getBody();
        String executionId = waiting.path("execution").path("id").asText();
        assertThat(waiting.path("execution").path("status").asText()).isEqualTo("WAITING_APPROVAL");
        assertThat(waiting.path("approval").path("approvalType").asText()).isEqualTo("AGENT_POLICY");

        JsonNode completed = exchange(
                "/api/v1/ai/agent-executions/" + executionId + "/approval",
                HttpMethod.POST,
                Map.of("approved", true),
                JsonNode.class
        ).getBody();
        assertThat(completed.path("execution").path("status").asText()).isEqualTo("SUCCESS");
        assertThat(completed.path("execution").path("result").asText()).contains("approved tool");
        assertThat(completed.path("trace").toString()).contains("PLANNER", "TOOL", "APPROVAL");
    }

    private void analyticsLifecycle(String gatewayTraceId) {
        JsonNode evaluation = exchange(
                "/api/v1/ai/admin/analytics/evaluations",
                HttpMethod.POST,
                Map.of(
                        "targetType", "AGENT",
                        "targetId", "e2e-agent",
                        "evaluationType", "ASSERTION",
                        "score", 4.8,
                        "judge", "e2e",
                        "dimensions", Map.of("safety", 5),
                        "comment", "Validated"
                ),
                JsonNode.class
        ).getBody();
        assertThat(evaluation.path("score").asDouble()).isEqualTo(4.8);

        JsonNode feedback = exchange(
                "/api/v1/ai/admin/analytics/feedback",
                HttpMethod.POST,
                Map.of("resourceType", "GATEWAY", "resourceId", "default", "rating", 5, "comment", "Stable"),
                JsonNode.class
        ).getBody();
        assertThat(feedback.path("rating").asInt()).isEqualTo(5);

        JsonNode budget = exchange(
                "/api/v1/ai/admin/analytics/budgets",
                HttpMethod.POST,
                Map.of(
                        "ownerType", "USER",
                        "ownerId", "local-admin",
                        "periodType", "MONTH",
                        "currency", "USD",
                        "amount", 100,
                        "warningRatio", 0.8,
                        "limitAction", "WARN",
                        "enabled", true
                ),
                JsonNode.class
        ).getBody();
        assertThat(budget.path("amount").asInt()).isEqualTo(100);

        exchange(
                "/api/v1/ai/admin/analytics/alerts",
                HttpMethod.POST,
                Map.of(
                        "name", "Any request",
                        "metricName", "request_count",
                        "operator", "GTE",
                        "threshold", 1,
                        "action", "WARN",
                        "scope", Map.of(),
                        "enabled", true
                ),
                JsonNode.class
        );

        JsonNode dashboard = exchange(
                "/api/v1/ai/admin/analytics/dashboard",
                HttpMethod.GET,
                null,
                JsonNode.class
        ).getBody();
        assertThat(dashboard.path("requestCount").asLong()).isGreaterThanOrEqualTo(6);
        assertThat(dashboard.path("successRate").asDouble()).isGreaterThan(0.9);
        assertThat(dashboard.path("rankings").size()).isGreaterThanOrEqualTo(4);
        assertThat(dashboard.path("budgets")).hasSize(1);
        assertThat(dashboard.path("alerts")).hasSize(1);
        assertThat(dashboard.path("averageQuality").asDouble()).isEqualTo(4.8);

        JsonNode insight = exchange(
                "/api/v1/ai/admin/analytics/insight",
                HttpMethod.GET,
                null,
                JsonNode.class
        ).getBody();
        assertThat(insight.path("mode").asText()).isEqualTo("DETERMINISTIC");
        assertThat(insight.path("insight").asText()).contains("requests", "success");

        JsonNode trace = exchange(
                "/api/v1/ai/admin/analytics/traces/" + gatewayTraceId,
                HttpMethod.GET,
                null,
                JsonNode.class
        ).getBody();
        assertThat(trace.size()).isGreaterThanOrEqualTo(1);
        assertThat(trace.toString()).contains("GATEWAY_INVOKE");
    }

    private Map<String, Object> toolRequest() {
        return Map.ofEntries(
                Map.entry("code", "e2e-mock-tool"),
                Map.entry("name", "E2E Mock Tool"),
                Map.entry("description", "Deterministic Tool E2E"),
                Map.entry("category", "PLUGIN"),
                Map.entry("toolType", "PLUGIN"),
                Map.entry("schemaJson", "{\"type\":\"object\"}"),
                Map.entry("outputSchemaJson", "{\"type\":\"object\"}"),
                Map.entry("executorType", "MOCK"),
                Map.entry("executorConfig", Map.of("response", Map.of("ok", true, "source", "e2e"))),
                Map.entry("parameters", List.of(Map.of(
                        "name", "name",
                        "type", "STRING",
                        "required", false,
                        "description", "Caller name"
                ))),
                Map.entry("chain", List.of()),
                Map.entry("changeLog", "Initial"),
                Map.entry("policy", Map.of(
                        "accessLevel", "READ_ONLY",
                        "readonly", true,
                        "manualConfirm", false,
                        "approvalRequired", false,
                        "timeoutSeconds", 15,
                        "retryCount", 0,
                        "logContent", false,
                        "retentionDays", 7
                ))
        );
    }

    private Map<String, Object> knowledgeRequest() {
        return Map.ofEntries(
                Map.entry("code", "e2e-knowledge"),
                Map.entry("name", "E2E Knowledge"),
                Map.entry("description", "Knowledge E2E"),
                Map.entry("category", "DOCUMENT"),
                Map.entry("visibility", "PUBLIC"),
                Map.entry("permissions", List.of("EVERYONE:*")),
                Map.entry("policy", Map.of(
                        "topK", 5,
                        "strategy", "HYBRID",
                        "scoreThreshold", 0,
                        "mmrLambda", 0.5,
                        "metadataFilter", Map.of(),
                        "timeWeight", 0,
                        "chunkStrategy", "PARAGRAPH",
                        "chunkSize", 256,
                        "chunkOverlap", 32
                ))
        );
    }

    private Map<String, Object> agentRequest(String toolId) {
        return Map.ofEntries(
                Map.entry("code", "e2e-agent"),
                Map.entry("name", "E2E Agent"),
                Map.entry("description", "Agent approval E2E"),
                Map.entry("sceneCode", "agent"),
                Map.entry("tags", List.of("e2e")),
                Map.entry("profile", Map.of(
                        "role", "Runtime operator",
                        "goal", "Use approved tools safely",
                        "personality", "Precise",
                        "style", "Concise",
                        "language", "en",
                        "constraints", "Require approval"
                )),
                Map.entry("planner", Map.of(
                        "type", "DETERMINISTIC",
                        "config", Map.of(),
                        "maxSteps", 10,
                        "maxDepth", 3,
                        "retryCount", 1
                )),
                Map.entry("tasks", List.of(Map.of(
                        "name", "Run approved tool",
                        "orderNo", 1,
                        "type", "TOOL",
                        "referenceId", toolId,
                        "executionMode", "SERIAL",
                        "condition", Map.of(),
                        "config", Map.of(),
                        "enabled", true
                ))),
                Map.entry("tools", List.of(Map.of(
                        "toolId", toolId,
                        "permission", "EXECUTE",
                        "approvalRequired", true
                ))),
                Map.entry("knowledge", List.of()),
                Map.entry("memory", Map.of(
                        "policy", "USER",
                        "ownerTypes", List.of("USER"),
                        "writeEnabled", false,
                        "maxItems", 20,
                        "config", Map.of()
                ))
        );
    }

    private JsonNode patchStatus(String path, String status) {
        return exchange(path, HttpMethod.PATCH, Map.of("status", status), JsonNode.class).getBody();
    }

    private <T> ResponseEntity<T> exchange(
            String path,
            HttpMethod method,
            Object body,
            Class<T> responseType
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
                "http://127.0.0.1:" + applicationPort + path,
                method,
                new HttpEntity<>(body, headers),
                responseType
        );
    }

    private record ToolFixture(String id, String code) {
    }
}
