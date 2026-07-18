package io.coreplatform.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles({"sqlite", "local"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PromptApiE2ETest {

    private static final Path DATABASE = Path.of(
            "target",
            "prompt-e2e-" + UUID.randomUUID() + ".db"
    ).toAbsolutePath();

    @LocalServerPort
    private int applicationPort;

    @org.springframework.beans.factory.annotation.Autowired
    private TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE);
        registry.add("server.address", () -> "127.0.0.1");
    }

    @Test
    void shouldCompletePromptAsCodeLifecycleAndRuntimeIntegration() {
        createAndPublishHelperPrompt();

        JsonNode created = exchange(
                "/api/v1/ai/admin/prompts",
                HttpMethod.POST,
                mainPromptRequest("Translate {{content}} to {{language}}.", "Initial version"),
                JsonNode.class
        ).getBody();
        String promptId = created.path("prompt").path("id").asText();
        assertThat(created.path("prompt").path("status").asText()).isEqualTo("DRAFT");
        assertThat(created.path("currentVersion").path("version").asInt()).isEqualTo(1);
        assertThat(created.path("currentVersion").path("variables")).hasSize(2);

        JsonNode draftRender = exchange(
                "/api/v1/ai/admin/prompts/" + promptId + "/render",
                HttpMethod.POST,
                Map.of("variables", Map.of("content", "Hello", "language", "中文")),
                JsonNode.class
        ).getBody();
        assertThat(draftRender.path("systemPrompt").asText())
                .isEqualTo("You are a professional translator.");
        assertThat(draftRender.path("userPrompt").asText()).isEqualTo("Translate Hello to 中文.");
        assertThat(draftRender.path("chain")).hasSize(1);
        assertThat(draftRender.path("estimatedTokens").asInt()).isPositive();

        JsonNode testCase = exchange(
                "/api/v1/ai/admin/prompts/" + promptId + "/test-cases",
                HttpMethod.POST,
                testCaseRequest(
                        "Chinese translation",
                        "{\"content\":\"Hello\",\"language\":\"中文\"}",
                        "You are a professional translator.\n\nTranslate Hello to 中文."
                ),
                JsonNode.class
        ).getBody();
        assertThat(testCase.path("enabled").asBoolean()).isTrue();

        transition(promptId, "TESTING");
        ResponseEntity<JsonNode> publishBeforeTest = exchange(
                "/api/v1/ai/admin/prompts/" + promptId + "/status",
                HttpMethod.PATCH,
                Map.of("status", "PUBLISHED"),
                JsonNode.class
        );
        assertThat(publishBeforeTest.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(publishBeforeTest.getBody().path("errorCode").asText())
                .isEqualTo("AI_PROMPT_TEST_REQUIRED");

        JsonNode suite = exchange(
                "/api/v1/ai/admin/prompts/" + promptId + "/tests/run",
                HttpMethod.POST,
                null,
                JsonNode.class
        ).getBody();
        assertThat(suite.path("passed").asBoolean()).isTrue();
        assertThat(suite.path("mode").asText()).isEqualTo("PREVIEW");
        assertThat(suite.path("executed").asBoolean()).isFalse();

        JsonNode published = transition(promptId, "PUBLISHED");
        assertThat(published.path("prompt").path("publishedVersion").asInt()).isEqualTo(1);

        JsonNode catalog = exchange(
                "/api/v1/ai/prompts",
                HttpMethod.GET,
                null,
                JsonNode.class
        ).getBody();
        assertThat(catalog).hasSize(2);
        assertThat(StreamSupport.stream(catalog.spliterator(), false)
                .map(item -> item.path("code").asText()))
                .contains("review-helper", "translate");

        JsonNode runtimeRender = exchange(
                "/api/v1/ai/prompts/translate/render",
                HttpMethod.POST,
                Map.of("variables", Map.of("content", "Hello", "language", "中文")),
                JsonNode.class
        ).getBody();
        assertThat(runtimeRender.path("version").asInt()).isEqualTo(1);
        assertThat(runtimeRender.path("chain").get(0).path("promptCode").asText())
                .isEqualTo("review-helper");

        ResponseEntity<Void> validOutput = exchange(
                "/api/v1/ai/prompts/translate/validate-output",
                HttpMethod.POST,
                Map.of("version", 1, "output", "{\"translation\":\"你好\",\"confidence\":3}"),
                Void.class
        );
        assertThat(validOutput.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        ResponseEntity<JsonNode> invalidOutput = exchange(
                "/api/v1/ai/prompts/translate/validate-output",
                HttpMethod.POST,
                Map.of("version", 1, "output", "{\"translation\":\"你好\"}"),
                JsonNode.class
        );
        assertThat(invalidOutput.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(invalidOutput.getBody().path("errorCode").asText())
                .isEqualTo("AI_PROMPT_OUTPUT_SCHEMA_MISMATCH");

        Map<String, Object> updateRequest = new LinkedHashMap<>(
                mainPromptRequest(
                        "Professionally translate {{content}} to {{language}}.",
                        "Professional wording"
                )
        );
        updateRequest.remove("code");
        JsonNode versionTwo = exchange(
                "/api/v1/ai/admin/prompts/" + promptId,
                HttpMethod.PUT,
                updateRequest,
                JsonNode.class
        ).getBody();
        assertThat(versionTwo.path("prompt").path("status").asText()).isEqualTo("DRAFT");
        assertThat(versionTwo.path("prompt").path("currentVersion").asInt()).isEqualTo(2);
        assertThat(versionTwo.path("prompt").path("publishedVersion").asInt()).isEqualTo(1);

        JsonNode stillVersionOne = exchange(
                "/api/v1/ai/prompts/translate/render",
                HttpMethod.POST,
                Map.of("variables", Map.of("content", "Hello", "language", "中文")),
                JsonNode.class
        ).getBody();
        assertThat(stillVersionOne.path("version").asInt()).isEqualTo(1);
        assertThat(stillVersionOne.path("userPrompt").asText()).startsWith("Translate ");

        JsonNode copiedCases = exchange(
                "/api/v1/ai/admin/prompts/" + promptId + "/test-cases",
                HttpMethod.GET,
                null,
                JsonNode.class
        ).getBody();
        String copiedCaseId = copiedCases.get(0).path("id").asText();
        exchange(
                "/api/v1/ai/admin/prompts/" + promptId + "/test-cases/" + copiedCaseId,
                HttpMethod.PUT,
                testCaseRequest(
                        "Chinese translation v2",
                        "{\"content\":\"Hello\",\"language\":\"中文\"}",
                        "You are a professional translator.\n\nProfessionally translate Hello to 中文."
                ),
                JsonNode.class
        );
        transition(promptId, "TESTING");
        assertThat(exchange(
                "/api/v1/ai/admin/prompts/" + promptId + "/tests/run",
                HttpMethod.POST,
                null,
                JsonNode.class
        ).getBody().path("passed").asBoolean()).isTrue();
        transition(promptId, "PUBLISHED");

        JsonNode history = exchange(
                "/api/v1/ai/admin/prompts/" + promptId + "/versions",
                HttpMethod.GET,
                null,
                JsonNode.class
        ).getBody();
        assertThat(history).hasSize(2);
        assertThat(history.get(0).path("publishedTime").asText()).isNotBlank();

        JsonNode diff = exchange(
                "/api/v1/ai/admin/prompts/" + promptId + "/compare?left=1&right=2",
                HttpMethod.GET,
                null,
                JsonNode.class
        ).getBody();
        assertThat(diff.toString()).contains("REMOVED", "ADDED", "Professionally translate");

        JsonNode abTest = exchange(
                "/api/v1/ai/admin/prompts/" + promptId + "/ab-tests",
                HttpMethod.POST,
                Map.of(
                        "name", "Translation wording",
                        "versionA", 1,
                        "versionB", 2,
                        "trafficRatio", 60
                ),
                JsonNode.class
        ).getBody();
        String abTestId = abTest.path("id").asText();
        JsonNode assignmentA = exchange(
                "/api/v1/ai/admin/prompts/" + promptId + "/ab-tests/" + abTestId + "/assign",
                HttpMethod.POST,
                Map.of("subjectKey", "customer-42"),
                JsonNode.class
        ).getBody();
        JsonNode assignmentB = exchange(
                "/api/v1/ai/admin/prompts/" + promptId + "/ab-tests/" + abTestId + "/assign",
                HttpMethod.POST,
                Map.of("subjectKey", "customer-42"),
                JsonNode.class
        ).getBody();
        assertThat(assignmentB.path("variant").asText())
                .isEqualTo(assignmentA.path("variant").asText());
        JsonNode observed = exchange(
                "/api/v1/ai/admin/prompts/" + promptId + "/ab-tests/" + abTestId + "/observations",
                HttpMethod.POST,
                Map.of(
                        "variant", assignmentA.path("variant").asText(),
                        "success", true,
                        "latencyMs", 120,
                        "cost", 0.01,
                        "score", 4.5
                ),
                JsonNode.class
        ).getBody();
        assertThat(observed.path("sampleA").asLong() + observed.path("sampleB").asLong())
                .isEqualTo(1);

        JsonNode rolledBack = exchange(
                "/api/v1/ai/admin/prompts/" + promptId + "/versions/1/rollback",
                HttpMethod.POST,
                null,
                JsonNode.class
        ).getBody();
        assertThat(rolledBack.path("prompt").path("currentVersion").asInt()).isEqualTo(3);
        assertThat(rolledBack.path("prompt").path("publishedVersion").asInt()).isEqualTo(2);
        assertThat(rolledBack.path("prompt").path("status").asText()).isEqualTo("DRAFT");

        JsonNode logs = exchange(
                "/api/v1/ai/admin/prompts/" + promptId + "/render-logs",
                HttpMethod.GET,
                null,
                JsonNode.class
        ).getBody();
        assertThat(logs.size()).isGreaterThanOrEqualTo(3);
        assertThat(logs.get(0).path("contentStored").asBoolean()).isFalse();

        JsonNode audit = exchange(
                "/api/v1/ai/admin/prompts/" + promptId + "/audit",
                HttpMethod.GET,
                null,
                JsonNode.class
        ).getBody();
        assertThat(audit.size()).isGreaterThanOrEqualTo(10);
        assertThat(audit.toString()).doesNotContain("Hello", "中文");
    }

    private void createAndPublishHelperPrompt() {
        JsonNode helper = exchange(
                "/api/v1/ai/admin/prompts",
                HttpMethod.POST,
                Map.ofEntries(
                        Map.entry("code", "review-helper"),
                        Map.entry("name", "Review Helper"),
                        Map.entry("description", "Chain helper"),
                        Map.entry("category", "WORKFLOW"),
                        Map.entry("visibility", "PUBLIC"),
                        Map.entry("userPrompt", "Review: {{content}}"),
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
        String helperId = helper.path("prompt").path("id").asText();
        exchange(
                "/api/v1/ai/admin/prompts/" + helperId + "/test-cases",
                HttpMethod.POST,
                testCaseRequest("Review render", "{\"content\":\"Hello\"}", ""),
                JsonNode.class
        );
        transition(helperId, "TESTING");
        exchange(
                "/api/v1/ai/admin/prompts/" + helperId + "/tests/run",
                HttpMethod.POST,
                null,
                JsonNode.class
        );
        transition(helperId, "PUBLISHED");
    }

    private Map<String, Object> mainPromptRequest(String userPrompt, String changeLog) {
        return Map.ofEntries(
                Map.entry("code", "translate"),
                Map.entry("name", "Enterprise Translate"),
                Map.entry("description", "Versioned translation Prompt"),
                Map.entry("category", "TRANSLATE"),
                Map.entry("visibility", "PUBLIC"),
                Map.entry("systemPrompt", "You are a professional translator."),
                Map.entry("userPrompt", userPrompt),
                Map.entry("changeLog", changeLog),
                Map.entry("variables", List.of(
                        Map.of(
                                "name", "content",
                                "type", "STRING",
                                "required", true,
                                "description", "Source text"
                        ),
                        Map.of(
                                "name", "language",
                                "type", "STRING",
                                "required", true,
                                "defaultValue", "中文"
                        )
                )),
                Map.entry("outputSchema", Map.of(
                        "schemaJson", """
                                {
                                  "type":"object",
                                  "required":["translation","confidence"],
                                  "properties":{
                                    "translation":{"type":"string"},
                                    "confidence":{"type":"integer","enum":[1,2,3,4,5]}
                                  },
                                  "additionalProperties":false
                                }
                                """,
                        "strictMode", true
                )),
                Map.entry("guardrails", List.of(Map.of(
                        "type", "LENGTH",
                        "phase", "INPUT",
                        "configJson", "{\"maxChars\":10000}",
                        "enabled", true
                ))),
                Map.entry("chain", List.of(Map.of(
                        "reference", "review-helper",
                        "optional", false
                )))
        );
    }

    private Map<String, Object> testCaseRequest(
            String name,
            String inputJson,
            String expectedOutput
    ) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", name);
        request.put("inputJson", inputJson);
        request.put("expectedOutput", expectedOutput);
        request.put("enabled", true);
        return request;
    }

    private JsonNode transition(String promptId, String status) {
        return exchange(
                "/api/v1/ai/admin/prompts/" + promptId + "/status",
                HttpMethod.PATCH,
                Map.of("status", status),
                JsonNode.class
        ).getBody();
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
}
