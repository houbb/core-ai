package io.coreplatform.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.coreplatform.ai.application.port.ProviderRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles({"sqlite", "local"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProviderApiE2ETest {

    private static final Path DATABASE = Path.of(
            "target",
            "provider-e2e-" + UUID.randomUUID() + ".db"
    ).toAbsolutePath();

    private static HttpServer providerServer;
    private static int providerPort;

    @LocalServerPort
    private int applicationPort;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProviderRepository providerRepository;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE);
        registry.add("server.address", () -> "127.0.0.1");
    }

    @BeforeAll
    static void startFakeProvider() throws IOException {
        providerServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        providerServer.createContext("/v1/models", ProviderApiE2ETest::handleModels);
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
    void shouldCompleteProviderLifecycleWithoutExternalCoreServices() throws Exception {
        String apiKey = "sk-e2e-secret-1234";
        Map<String, Object> createRequest = providerRequest(apiKey);

        ResponseEntity<JsonNode> createResponse = exchange(
                "",
                HttpMethod.POST,
                createRequest,
                JsonNode.class
        );
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode created = createResponse.getBody();
        assertThat(created).isNotNull();
        String providerId = created.path("id").asText();
        assertThat(created.path("status").asText()).isEqualTo("DRAFT");
        assertThat(created.path("enabled").asBoolean()).isFalse();
        assertThat(created.path("apiKeyMasked").asText()).isEqualTo("sk-****1234");
        assertThat(created.path("customParameters").path("accessToken").asText()).isEqualTo("****");
        assertThat(created.toString()).doesNotContain("param-secret");

        Map<String, Object> storedSecret = jdbcTemplate.queryForMap("""
                SELECT api_key_cipher, headers_json, custom_parameters_json
                  FROM ai_provider_secret
                 WHERE provider_id = ?
                """, providerId);
        assertThat(storedSecret.get("api_key_cipher").toString())
                .startsWith("v1:")
                .doesNotContain(apiKey);
        assertThat(storedSecret.get("headers_json").toString())
                .startsWith("v1:")
                .doesNotContain("X-E2E");
        assertThat(storedSecret.get("custom_parameters_json").toString())
                .startsWith("v1:")
                .doesNotContain("param-secret");

        ResponseEntity<JsonNode> testResponse = exchange(
                "/" + providerId + "/test",
                HttpMethod.POST,
                null,
                JsonNode.class
        );
        assertThat(testResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(testResponse.getBody().path("success").asBoolean()).isTrue();
        assertThat(testResponse.getBody().path("modelCount").asInt()).isEqualTo(4);
        assertThat(testResponse.getBody().path("capabilities"))
                .extracting(JsonNode::asText)
                .contains("CHAT", "VISION", "EMBEDDING", "IMAGE", "REASONING");

        JsonNode detail = exchange(
                "/" + providerId,
                HttpMethod.GET,
                null,
                JsonNode.class
        ).getBody();
        assertThat(detail.path("status").asText()).isEqualTo("AVAILABLE");
        assertThat(detail.path("models")).hasSize(4);
        assertThat(detail.toString()).doesNotContain(apiKey);

        JsonNode enabled = exchange(
                "/" + providerId + "/enabled",
                HttpMethod.PATCH,
                Map.of("enabled", true),
                JsonNode.class
        ).getBody();
        assertThat(enabled.path("enabled").asBoolean()).isTrue();
        assertThat(enabled.path("status").asText()).isEqualTo("AVAILABLE");

        JsonNode filtered = exchange(
                "?capability=EMBEDDING&enabled=true",
                HttpMethod.GET,
                null,
                JsonNode.class
        ).getBody();
        assertThat(filtered).hasSize(1);
        assertThat(filtered.get(0).path("id").asText()).isEqualTo(providerId);

        Map<String, Object> wrongKeyUpdate = new HashMap<>(providerRequest("wrong-key"));
        wrongKeyUpdate.put(
                "customParameters",
                Map.of("modelsPath", "/models", "accessToken", "****")
        );
        exchange("/" + providerId, HttpMethod.PUT, wrongKeyUpdate, JsonNode.class);
        assertThat(providerRepository.findById(providerId).orElseThrow().customParameters())
                .containsEntry("accessToken", "param-secret");
        JsonNode failedTest = exchange(
                "/" + providerId + "/test",
                HttpMethod.POST,
                null,
                JsonNode.class
        ).getBody();
        assertThat(failedTest.path("success").asBoolean()).isFalse();
        assertThat(failedTest.path("errorCode").asText())
                .isEqualTo("PROVIDER_AUTHENTICATION_FAILED");
        assertThat(failedTest.path("status").asText()).isEqualTo("AVAILABLE");

        exchange("/" + providerId, HttpMethod.PUT, createRequest, JsonNode.class);
        JsonNode refresh = exchange(
                "/" + providerId + "/models/refresh",
                HttpMethod.POST,
                null,
                JsonNode.class
        ).getBody();
        assertThat(refresh.path("success").asBoolean()).isTrue();

        JsonNode audit = exchange(
                "/" + providerId + "/audit",
                HttpMethod.GET,
                null,
                JsonNode.class
        ).getBody();
        assertThat(audit).isNotNull();
        assertThat(audit.size()).isGreaterThanOrEqualTo(7);
        assertThat(audit.toString()).doesNotContain(apiKey).doesNotContain("wrong-key");

        ResponseEntity<Void> deleteResponse = exchange(
                "/" + providerId,
                HttpMethod.DELETE,
                null,
                Void.class
        );
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        JsonNode providers = exchange("", HttpMethod.GET, null, JsonNode.class).getBody();
        assertThat(providers).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM ai_provider WHERE id = ?",
                String.class,
                providerId
        )).isEqualTo("DELETED");
    }

    private Map<String, Object> providerRequest(String apiKey) {
        return Map.ofEntries(
                Map.entry("code", "e2e-openai"),
                Map.entry("name", "E2E OpenAI"),
                Map.entry("description", "End-to-end provider"),
                Map.entry("type", "OPENAI_COMPATIBLE"),
                Map.entry("endpoint", "http://127.0.0.1:" + providerPort + "/v1"),
                Map.entry("priority", 1),
                Map.entry("weight", 100),
                Map.entry("timeoutSeconds", 3),
                Map.entry("retryCount", 0),
                Map.entry("apiKey", apiKey),
                Map.entry("tlsVerify", true),
                Map.entry("headers", Map.of("X-E2E", "header-secret")),
                Map.entry(
                        "customParameters",
                        Map.of("modelsPath", "/models", "accessToken", "param-secret")
                ),
                Map.entry("tags", List.of("testing", "cloud"))
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
                "http://127.0.0.1:" + applicationPort + "/api/v1/ai/admin/providers" + path,
                method,
                new HttpEntity<>(body, headers),
                responseType
        );
    }

    private static void handleModels(HttpExchange exchange) throws IOException {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (!"Bearer sk-e2e-secret-1234".equals(authorization)) {
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
            return;
        }
        byte[] response = """
                {
                  "data": [
                    {"id": "gpt-4o"},
                    {"id": "text-embedding-3-large"},
                    {"id": "o1-mini"},
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
