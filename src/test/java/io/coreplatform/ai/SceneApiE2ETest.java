package io.coreplatform.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles({"sqlite", "local"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SceneApiE2ETest {

    private static final Path DATABASE = Path.of(
            "target",
            "scene-e2e-" + UUID.randomUUID() + ".db"
    ).toAbsolutePath();

    private static HttpServer providerServer;
    private static int providerPort;

    @LocalServerPort
    private int applicationPort;

    @Autowired
    private TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE);
        registry.add("server.address", () -> "127.0.0.1");
    }

    @BeforeAll
    static void startFakeProvider() throws IOException {
        providerServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        providerServer.createContext("/v1/models", SceneApiE2ETest::handleModels);
        providerServer.start();
        providerPort = providerServer.getAddress().getPort();
    }

    @AfterAll
    static void stopFakeProvider() {
        if (providerServer != null) {
            providerServer.stop(0);
        }
    }

    @Test
    void shouldCompleteSceneTemplatePublishExecuteVersionAndShareLifecycle() {
        String modelId = prepareEnabledModelAndAliases();
        preparePublishedPrompt();

        JsonNode templates = exchange(
                "/api/v1/ai/admin/scene-templates",
                HttpMethod.GET,
                null,
                JsonNode.class
        ).getBody();
        assertThat(templates).hasSize(8);
        JsonNode chatTemplate = StreamSupport.stream(templates.spliterator(), false)
                .filter(node -> "chat".equals(node.path("defaultCode").asText()))
                .findFirst()
                .orElseThrow();

        JsonNode created = exchange(
                "/api/v1/ai/admin/scene-templates/" + chatTemplate.path("id").asText() + "/instantiate",
                HttpMethod.POST,
                Map.of(),
                JsonNode.class
        ).getBody();
        String sceneId = created.path("id").asText();
        assertThat(created.path("status").asText()).isEqualTo("DRAFT");
        assertThat(created.path("models").get(0).path("modelId").asText()).isEqualTo(modelId);

        JsonNode updated = exchange(
                "/api/v1/ai/admin/scenes/" + sceneId,
                HttpMethod.PUT,
                sceneConfiguration(),
                JsonNode.class
        ).getBody();
        assertThat(updated.path("models")).hasSize(2);
        assertThat(updated.path("prompt").path("promptId").asText()).isEqualTo("prompt-chat");
        assertThat(updated.path("workflow")).hasSize(1);

        JsonNode testing = exchange(
                "/api/v1/ai/admin/scenes/" + sceneId + "/status",
                HttpMethod.PATCH,
                Map.of("status", "TESTING"),
                JsonNode.class
        ).getBody();
        assertThat(testing.path("status").asText()).isEqualTo("TESTING");

        ResponseEntity<JsonNode> publishBeforeTest = exchange(
                "/api/v1/ai/admin/scenes/" + sceneId + "/status",
                HttpMethod.PATCH,
                Map.of("status", "PUBLISHED"),
                JsonNode.class
        );
        assertThat(publishBeforeTest.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(publishBeforeTest.getBody().path("errorCode").asText())
                .isEqualTo("AI_SCENE_TEST_REQUIRED");

        JsonNode preview = exchange(
                "/api/v1/ai/admin/scenes/" + sceneId + "/test",
                HttpMethod.POST,
                Map.of("input", "Summarize this enterprise update.", "variables", Map.of("language", "en")),
                JsonNode.class
        ).getBody();
        assertThat(preview.path("mode").asText()).isEqualTo("PREVIEW");
        assertThat(preview.path("executed").asBoolean()).isFalse();
        assertThat(preview.path("modelAlias").asText()).isEqualTo("chat-default");
        assertThat(preview.path("promptVersion").asInt()).isEqualTo(1);
        assertThat(preview.path("estimatedCost").asDouble()).isPositive();
        assertThat(preview.path("trace").toString())
                .contains("INPUT", "PROMPT", "MODEL", "WORKFLOW", "OUTPUT");

        JsonNode published = exchange(
                "/api/v1/ai/admin/scenes/" + sceneId + "/status",
                HttpMethod.PATCH,
                Map.of("status", "PUBLISHED"),
                JsonNode.class
        ).getBody();
        assertThat(published.path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(published.path("enabled").asBoolean()).isTrue();

        JsonNode versions = exchange(
                "/api/v1/ai/admin/scenes/" + sceneId + "/versions",
                HttpMethod.GET,
                null,
                JsonNode.class
        ).getBody();
        assertThat(versions).hasSize(1);
        assertThat(versions.get(0).path("version").asInt()).isEqualTo(1);

        JsonNode catalog = exchange(
                "/api/v1/ai/scenes",
                HttpMethod.GET,
                null,
                JsonNode.class
        ).getBody();
        assertThat(catalog).hasSize(1);
        assertThat(catalog.get(0).path("code").asText()).isEqualTo("chat");

        JsonNode execution = exchange(
                "/api/v1/ai/scenes/chat/execute",
                HttpMethod.POST,
                Map.of("input", "Hello Scene Runtime", "variables", Map.of()),
                JsonNode.class
        ).getBody();
        assertThat(execution.path("mode").asText()).isEqualTo("PREVIEW");
        assertThat(execution.path("sceneCode").asText()).isEqualTo("chat");

        JsonNode scenePackage = exchange(
                "/api/v1/ai/admin/scenes/" + sceneId + "/export",
                HttpMethod.GET,
                null,
                JsonNode.class
        ).getBody();
        assertThat(scenePackage.path("formatVersion").asInt()).isEqualTo(1);
        ObjectNode importPackage = scenePackage.deepCopy();
        importPackage.put("code", "chat-imported");
        ((ObjectNode) importPackage.path("configuration")).put("name", "Imported Chat");
        JsonNode imported = exchange(
                "/api/v1/ai/admin/scenes/import",
                HttpMethod.POST,
                importPackage,
                JsonNode.class
        ).getBody();
        assertThat(imported.path("code").asText()).isEqualTo("chat-imported");
        assertThat(imported.path("status").asText()).isEqualTo("DRAFT");

        JsonNode customTemplate = exchange(
                "/api/v1/ai/admin/scenes/" + sceneId + "/templates",
                HttpMethod.POST,
                Map.of("templateName", "Custom Chat", "defaultCode", "custom-chat"),
                JsonNode.class
        ).getBody();
        assertThat(customTemplate.path("builtin").asBoolean()).isFalse();
        exchange(
                "/api/v1/ai/admin/scene-templates/" + customTemplate.path("id").asText(),
                HttpMethod.DELETE,
                null,
                Void.class
        );
        JsonNode templatesAfterDelete = exchange(
                "/api/v1/ai/admin/scene-templates",
                HttpMethod.GET,
                null,
                JsonNode.class
        ).getBody();
        assertThat(templatesAfterDelete).hasSize(8);

        exchange(
                "/api/v1/ai/admin/scenes/" + sceneId + "/status",
                HttpMethod.PATCH,
                Map.of("status", "DISABLED"),
                JsonNode.class
        );
        JsonNode rolledBack = exchange(
                "/api/v1/ai/admin/scenes/" + sceneId + "/versions/1/rollback",
                HttpMethod.POST,
                null,
                JsonNode.class
        ).getBody();
        assertThat(rolledBack.path("status").asText()).isEqualTo("DRAFT");
        assertThat(rolledBack.path("version").asInt()).isEqualTo(2);

        JsonNode archived = exchange(
                "/api/v1/ai/admin/scenes/" + sceneId + "/status",
                HttpMethod.PATCH,
                Map.of("status", "ARCHIVED"),
                JsonNode.class
        ).getBody();
        assertThat(archived.path("status").asText()).isEqualTo("ARCHIVED");

        JsonNode audit = exchange(
                "/api/v1/ai/admin/scenes/" + sceneId + "/audit",
                HttpMethod.GET,
                null,
                JsonNode.class
        ).getBody();
        assertThat(audit.size()).isGreaterThanOrEqualTo(9);
        assertThat(audit.toString()).doesNotContain("Summarize this enterprise update");
    }

    private String prepareEnabledModelAndAliases() {
        JsonNode provider = exchange(
                "/api/v1/ai/admin/providers",
                HttpMethod.POST,
                providerRequest(),
                JsonNode.class
        ).getBody();
        String providerId = provider.path("id").asText();
        exchange(
                "/api/v1/ai/admin/providers/" + providerId + "/test",
                HttpMethod.POST,
                null,
                JsonNode.class
        );
        exchange(
                "/api/v1/ai/admin/providers/" + providerId + "/enabled",
                HttpMethod.PATCH,
                Map.of("enabled", true),
                JsonNode.class
        );

        JsonNode models = exchange(
                "/api/v1/ai/admin/models",
                HttpMethod.GET,
                null,
                JsonNode.class
        ).getBody();
        String modelId = models.get(0).path("id").asText();
        exchange(
                "/api/v1/ai/admin/models/" + modelId + "/status",
                HttpMethod.PATCH,
                Map.of("status", "REGISTERED"),
                JsonNode.class
        );
        exchange(
                "/api/v1/ai/admin/models/" + modelId + "/status",
                HttpMethod.PATCH,
                Map.of("status", "ENABLED"),
                JsonNode.class
        );
        exchange(
                "/api/v1/ai/admin/models/" + modelId + "/pricing",
                HttpMethod.POST,
                Map.of(
                        "currency", "USD",
                        "promptPrice", 2.0,
                        "completionPrice", 8.0,
                        "effectiveTime", Instant.parse("2026-01-01T00:00:00Z").toString()
                ),
                JsonNode.class
        );
        createAlias("chat-default", modelId, 1);
        createAlias("chat-backup", modelId, 2);
        return modelId;
    }

    private void preparePublishedPrompt() {
        JsonNode prompt = exchange(
                "/api/v1/ai/admin/prompts",
                HttpMethod.POST,
                Map.ofEntries(
                        Map.entry("code", "prompt-chat"),
                        Map.entry("name", "Scene Chat Prompt"),
                        Map.entry("description", "Prompt used by Scene E2E"),
                        Map.entry("category", "CONVERSATION"),
                        Map.entry("visibility", "PUBLIC"),
                        Map.entry("systemPrompt", "You are an enterprise assistant."),
                        Map.entry("userPrompt", "{{content}}"),
                        Map.entry("variables", List.of(Map.of(
                                "name", "content",
                                "type", "STRING",
                                "required", true
                        ))),
                        Map.entry("outputSchema", Map.of("strictMode", false)),
                        Map.entry("guardrails", List.of()),
                        Map.entry("chain", List.of())
                ),
                JsonNode.class
        ).getBody();
        String promptId = prompt.path("prompt").path("id").asText();
        exchange(
                "/api/v1/ai/admin/prompts/" + promptId + "/test-cases",
                HttpMethod.POST,
                Map.of(
                        "name", "Scene render",
                        "inputJson", "{\"content\":\"Hello Scene\"}",
                        "expectedOutput", "",
                        "enabled", true
                ),
                JsonNode.class
        );
        exchange(
                "/api/v1/ai/admin/prompts/" + promptId + "/status",
                HttpMethod.PATCH,
                Map.of("status", "TESTING"),
                JsonNode.class
        );
        exchange(
                "/api/v1/ai/admin/prompts/" + promptId + "/tests/run",
                HttpMethod.POST,
                null,
                JsonNode.class
        );
        exchange(
                "/api/v1/ai/admin/prompts/" + promptId + "/status",
                HttpMethod.PATCH,
                Map.of("status", "PUBLISHED"),
                JsonNode.class
        );
    }

    private void createAlias(String alias, String modelId, int priority) {
        exchange(
                "/api/v1/ai/admin/model-aliases",
                HttpMethod.POST,
                Map.of(
                        "alias", alias,
                        "modelId", modelId,
                        "scene", "chat",
                        "priority", priority,
                        "enabled", true
                ),
                JsonNode.class
        );
    }

    private Map<String, Object> sceneConfiguration() {
        return Map.ofEntries(
                Map.entry("name", "Enterprise Chat"),
                Map.entry("description", "Scene E2E chat"),
                Map.entry("category", "CONVERSATION"),
                Map.entry("icon", "💬"),
                Map.entry("recommended", true),
                Map.entry("models", List.of(
                        Map.of(
                                "modelAlias", "chat-default",
                                "priority", 10,
                                "fallback", false,
                                "enabled", true
                        ),
                        Map.of(
                                "modelAlias", "chat-backup",
                                "priority", 20,
                                "fallback", true,
                                "enabled", true
                        )
                )),
                Map.entry("parameters", Map.of(
                        "temperature", 0.4,
                        "topP", 0.9,
                        "maxOutputTokens", 4096,
                        "reasoningEffort", "medium",
                        "jsonMode", false,
                        "streaming", true
                )),
                Map.entry("prompt", Map.of(
                        "promptId", "prompt-chat",
                        "promptVersion", 1
                )),
                Map.entry("permissions", List.of(Map.of(
                        "type", "EVERYONE",
                        "value", "*"
                ))),
                Map.entry("workflow", List.of(Map.of(
                        "code", "prepare",
                        "type", "MODEL_ALIAS",
                        "reference", "chat-default",
                        "optional", false
                )))
        );
    }

    private Map<String, Object> providerRequest() {
        return Map.ofEntries(
                Map.entry("code", "scene-e2e"),
                Map.entry("name", "Scene E2E Provider"),
                Map.entry("type", "OPENAI_COMPATIBLE"),
                Map.entry("endpoint", "http://127.0.0.1:" + providerPort + "/v1"),
                Map.entry("priority", 1),
                Map.entry("weight", 100),
                Map.entry("timeoutSeconds", 3),
                Map.entry("retryCount", 0),
                Map.entry("apiKey", "sk-scene-e2e"),
                Map.entry("tlsVerify", true),
                Map.entry("headers", Map.of()),
                Map.entry("customParameters", Map.of()),
                Map.entry("tags", List.of("testing"))
        );
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

    private static void handleModels(HttpExchange exchange) throws IOException {
        if (!"Bearer sk-scene-e2e".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
            return;
        }
        byte[] response = """
                {
                  "data": [
                    {"id": "gpt-4o", "context_length": 128000}
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
