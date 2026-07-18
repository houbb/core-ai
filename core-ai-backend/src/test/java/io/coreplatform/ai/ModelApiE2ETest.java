package io.coreplatform.ai;

import com.fasterxml.jackson.databind.JsonNode;
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
class ModelApiE2ETest {

    private static final Path DATABASE = Path.of(
            "target",
            "model-e2e-" + UUID.randomUUID() + ".db"
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
        providerServer.createContext("/v1/models", ModelApiE2ETest::handleModels);
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
    void shouldCompleteModelRegistryLifecycleFromProviderDiscovery() {
        JsonNode provider = exchange(
                "/api/v1/ai/admin/providers",
                HttpMethod.POST,
                providerRequest(),
                JsonNode.class
        ).getBody();
        String providerId = provider.path("id").asText();

        JsonNode connection = exchange(
                "/api/v1/ai/admin/providers/" + providerId + "/test",
                HttpMethod.POST,
                null,
                JsonNode.class
        ).getBody();
        assertThat(connection.path("success").asBoolean()).isTrue();

        exchange(
                "/api/v1/ai/admin/providers/" + providerId + "/enabled",
                HttpMethod.PATCH,
                Map.of("enabled", true),
                JsonNode.class
        );

        JsonNode discovered = exchange(
                "/api/v1/ai/admin/models",
                HttpMethod.GET,
                null,
                JsonNode.class
        ).getBody();
        assertThat(discovered).hasSize(4);
        assertThat(StreamSupport.stream(discovered.spliterator(), false)
                .map(node -> node.path("status").asText())
                .distinct()).containsExactly("DISCOVERED");

        JsonNode gpt = findByRemoteId(discovered, "gpt-4o");
        JsonNode reasoning = findByRemoteId(discovered, "o1-mini");
        String gptId = gpt.path("id").asText();
        String reasoningId = reasoning.path("id").asText();
        assertThat(gpt.path("displayName").asText()).isEqualTo("GPT 4O");
        assertThat(gpt.path("capabilities").toString())
                .contains("CHAT", "VISION", "TOOL_CALL", "JSON_MODE", "STREAMING");

        JsonNode updated = exchange(
                "/api/v1/ai/admin/models/" + gptId,
                HttpMethod.PUT,
                Map.ofEntries(
                        Map.entry("displayName", "GPT-4o Production"),
                        Map.entry("category", "CHAT"),
                        Map.entry("description", "Primary multimodal model"),
                        Map.entry("maxContextTokens", 128000),
                        Map.entry("maxInputTokens", 120000),
                        Map.entry("maxOutputTokens", 8000),
                        Map.entry("defaultMaxTokens", 4096),
                        Map.entry("contextManuallyOverridden", true),
                        Map.entry("tags", List.of("production", "fast"))
                ),
                JsonNode.class
        ).getBody();
        assertThat(updated.path("maxContextTokens").asInt()).isEqualTo(128000);

        JsonNode capabilities = exchange(
                "/api/v1/ai/admin/models/" + gptId + "/capabilities",
                HttpMethod.PUT,
                Map.of("overrides", Map.of("OCR", true, "VISION", false)),
                JsonNode.class
        ).getBody();
        assertThat(capabilities.path("capabilities").toString()).contains("OCR").doesNotContain("VISION");

        exchange(
                "/api/v1/ai/admin/models/" + gptId + "/status",
                HttpMethod.PATCH,
                Map.of("status", "REGISTERED"),
                JsonNode.class
        );
        JsonNode enabled = exchange(
                "/api/v1/ai/admin/models/" + gptId + "/status",
                HttpMethod.PATCH,
                Map.of("status", "ENABLED"),
                JsonNode.class
        ).getBody();
        assertThat(enabled.path("status").asText()).isEqualTo("ENABLED");

        JsonNode parameters = exchange(
                "/api/v1/ai/admin/models/" + gptId + "/parameters",
                HttpMethod.PUT,
                Map.of(
                        "temperature", 0.7,
                        "topP", 0.95,
                        "frequencyPenalty", 0.1,
                        "presencePenalty", 0.0,
                        "maxOutputTokens", 4096,
                        "reasoningEffort", "medium",
                        "seed", 42
                ),
                JsonNode.class
        ).getBody();
        assertThat(parameters.path("parameters").path("temperature").asDouble()).isEqualTo(0.7);

        JsonNode price = exchange(
                "/api/v1/ai/admin/models/" + gptId + "/pricing",
                HttpMethod.POST,
                Map.of(
                        "currency", "USD",
                        "promptPrice", 2.5,
                        "completionPrice", 10.0,
                        "cacheReadPrice", 1.0,
                        "cacheWritePrice", 2.0,
                        "effectiveTime", Instant.parse("2026-01-01T00:00:00Z").toString(),
                        "notes", "E2E price"
                ),
                JsonNode.class
        ).getBody();
        assertThat(price.path("source").asText()).isEqualTo("MANUAL");

        exchange(
                "/api/v1/ai/admin/models/" + gptId + "/flags",
                HttpMethod.PATCH,
                Map.of("favorite", true, "recommended", true),
                JsonNode.class
        );

        JsonNode alias = exchange(
                "/api/v1/ai/admin/model-aliases",
                HttpMethod.POST,
                Map.of(
                        "alias", "chat-default",
                        "modelId", gptId,
                        "scene", "chat",
                        "priority", 1,
                        "enabled", true
                ),
                JsonNode.class
        ).getBody();
        assertThat(alias.path("alias").asText()).isEqualTo("chat-default");

        JsonNode resolved = exchange(
                "/api/v1/ai/admin/model-aliases/chat-default/resolve",
                HttpMethod.GET,
                null,
                JsonNode.class
        ).getBody();
        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0).path("id").asText()).isEqualTo(gptId);

        JsonNode defaults = exchange(
                "/api/v1/ai/admin/models/defaults",
                HttpMethod.GET,
                null,
                JsonNode.class
        ).getBody();
        JsonNode chatDefault = StreamSupport.stream(defaults.spliterator(), false)
                .filter(node -> "chat-default".equals(node.path("alias").asText()))
                .findFirst()
                .orElseThrow();
        assertThat(chatDefault.path("model").path("id").asText()).isEqualTo(gptId);

        JsonNode recommendation = exchange(
                "/api/v1/ai/admin/models/recommend?capability=CHAT&mode=BEST&limit=5",
                HttpMethod.GET,
                null,
                JsonNode.class
        ).getBody();
        assertThat(recommendation).hasSize(1);
        assertThat(recommendation.get(0).path("model").path("id").asText()).isEqualTo(gptId);
        assertThat(recommendation.get(0).path("reason").asText()).contains("Quality score");

        JsonNode comparison = exchange(
                "/api/v1/ai/admin/models/compare",
                HttpMethod.POST,
                Map.of("ids", List.of(gptId, reasoningId)),
                JsonNode.class
        ).getBody();
        assertThat(comparison).hasSize(2);

        exchange(
                "/api/v1/ai/admin/providers/" + providerId + "/models/refresh",
                HttpMethod.POST,
                null,
                JsonNode.class
        );
        JsonNode afterSync = exchange(
                "/api/v1/ai/admin/models/" + gptId,
                HttpMethod.GET,
                null,
                JsonNode.class
        ).getBody();
        assertThat(afterSync.path("maxContextTokens").asInt()).isEqualTo(128000);
        assertThat(afterSync.path("capabilities").toString()).contains("OCR").doesNotContain("VISION");
        assertThat(afterSync.path("capabilityOverrides").path("OCR").asBoolean()).isTrue();

        JsonNode audit = exchange(
                "/api/v1/ai/admin/models/" + gptId + "/audit",
                HttpMethod.GET,
                null,
                JsonNode.class
        ).getBody();
        assertThat(audit.size()).isGreaterThanOrEqualTo(7);

        exchange(
                "/api/v1/ai/admin/models/" + gptId + "/status",
                HttpMethod.PATCH,
                Map.of("status", "DISABLED"),
                JsonNode.class
        );
        ResponseEntity<Void> deleted = exchange(
                "/api/v1/ai/admin/models/" + gptId,
                HttpMethod.DELETE,
                null,
                Void.class
        );
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        ResponseEntity<JsonNode> unresolved = exchange(
                "/api/v1/ai/admin/model-aliases/chat-default/resolve",
                HttpMethod.GET,
                null,
                JsonNode.class
        );
        assertThat(unresolved.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        JsonNode aliasesAfterDelete = exchange(
                "/api/v1/ai/admin/model-aliases?alias=chat-default",
                HttpMethod.GET,
                null,
                JsonNode.class
        ).getBody();
        assertThat(aliasesAfterDelete).isEmpty();
    }

    private JsonNode findByRemoteId(JsonNode models, String remoteId) {
        return StreamSupport.stream(models.spliterator(), false)
                .filter(node -> remoteId.equals(node.path("remoteModelId").asText()))
                .findFirst()
                .orElseThrow();
    }

    private Map<String, Object> providerRequest() {
        return Map.ofEntries(
                Map.entry("code", "model-e2e"),
                Map.entry("name", "Model E2E Provider"),
                Map.entry("type", "OPENAI_COMPATIBLE"),
                Map.entry("endpoint", "http://127.0.0.1:" + providerPort + "/v1"),
                Map.entry("priority", 1),
                Map.entry("weight", 100),
                Map.entry("timeoutSeconds", 3),
                Map.entry("retryCount", 0),
                Map.entry("apiKey", "sk-model-e2e"),
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
        if (!"Bearer sk-model-e2e".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
            return;
        }
        byte[] response = """
                {
                  "data": [
                    {"id": "gpt-4o", "context_length": 64000},
                    {"id": "text-embedding-3-large", "context_length": 8192},
                    {"id": "o1-mini", "context_length": 128000},
                    {"id": "flux-1"}
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
