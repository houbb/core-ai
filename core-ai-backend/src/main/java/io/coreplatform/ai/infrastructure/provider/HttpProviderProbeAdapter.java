package io.coreplatform.ai.infrastructure.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.coreplatform.ai.application.domain.Capability;
import io.coreplatform.ai.application.domain.DiscoveredModel;
import io.coreplatform.ai.application.domain.ProviderType;
import io.coreplatform.ai.application.exception.ProviderProbeException;
import io.coreplatform.ai.application.port.ProviderProbePort;
import io.coreplatform.ai.infrastructure.config.CoreAiProperties;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class HttpProviderProbeAdapter implements ProviderProbePort {

    private final ObjectMapper objectMapper;
    private final int maxResponseBytes;

    public HttpProviderProbeAdapter(ObjectMapper objectMapper, CoreAiProperties properties) {
        this.objectMapper = objectMapper;
        this.maxResponseBytes = properties.provider().maxResponseBytes();
    }

    @Override
    public ProbeResult probe(ProviderConnection connection) {
        RequestSpec requestSpec = requestSpec(connection);
        HttpClient client = buildClient(connection);
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(requestSpec.uri())
                .timeout(Duration.ofSeconds(connection.timeoutSeconds()))
                .GET()
                .header("Accept", "application/json")
                .header("User-Agent", "core-ai/0.1");
        requestSpec.headers().forEach(requestBuilder::header);

        long started = System.nanoTime();
        HttpResponse<byte[]> response;
        try {
            response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new ProviderProbeException(
                    "PROVIDER_TIMEOUT",
                    "Provider request timed out",
                    "连接超时，请检查 Endpoint、代理或超时配置。",
                    null
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProviderProbeException(
                    "PROVIDER_INTERRUPTED",
                    "Provider request was interrupted",
                    "连接测试已中断，请重试。",
                    null
            );
        } catch (IOException | IllegalArgumentException exception) {
            throw new ProviderProbeException(
                    "PROVIDER_NETWORK_ERROR",
                    "Unable to reach provider",
                    "无法连接 Provider，请检查地址、网络、代理和 TLS 配置。",
                    null
            );
        }
        long latencyMs = Duration.ofNanos(System.nanoTime() - started).toMillis();

        if (response.body().length > maxResponseBytes) {
            throw new ProviderProbeException(
                    "PROVIDER_RESPONSE_TOO_LARGE",
                    "Provider response exceeds configured limit",
                    "Provider 返回内容过大，请检查模型列表接口。",
                    response.statusCode()
            );
        }
        assertSuccessful(response.statusCode());

        try {
            JsonNode root = objectMapper.readTree(response.body());
            List<DiscoveredModel> models = parseModels(connection.type(), root);
            return new ProbeResult(latencyMs, List.copyOf(models));
        } catch (IOException exception) {
            throw new ProviderProbeException(
                    "PROVIDER_INVALID_RESPONSE",
                    "Provider returned invalid JSON",
                    "Provider 返回格式无法识别，请检查类型或自定义 modelsPath。",
                    response.statusCode()
            );
        }
    }

    private RequestSpec requestSpec(ProviderConnection connection) {
        Map<String, String> headers = new LinkedHashMap<>(connection.headers());
        String path;
        switch (connection.type()) {
            case OPENAI_COMPATIBLE, LM_STUDIO -> {
                path = parameter(connection, "modelsPath", "/models");
                bearer(headers, connection.apiKey());
                if (hasText(connection.organization())) {
                    headers.put("OpenAI-Organization", connection.organization());
                }
            }
            case ANTHROPIC -> {
                path = parameter(connection, "modelsPath", "/v1/models");
                requireApiKey(connection.apiKey());
                headers.put("x-api-key", connection.apiKey());
                headers.put(
                        "anthropic-version",
                        parameter(connection, "anthropicVersion", "2023-06-01")
                );
            }
            case GEMINI -> {
                path = parameter(connection, "modelsPath", "/v1beta/models");
                requireApiKey(connection.apiKey());
                String delimiter = path.contains("?") ? "&" : "?";
                path = path + delimiter + "key=" + URLEncoder.encode(
                        connection.apiKey(),
                        StandardCharsets.UTF_8
                );
            }
            case OLLAMA -> path = parameter(connection, "modelsPath", "/api/tags");
            case AZURE_OPENAI -> {
                requireApiKey(connection.apiKey());
                String apiVersion = parameter(connection, "apiVersion", "2024-10-21");
                path = parameter(connection, "modelsPath", "/openai/models")
                        + "?api-version="
                        + URLEncoder.encode(apiVersion, StandardCharsets.UTF_8);
                headers.put("api-key", connection.apiKey());
            }
            case CUSTOM -> {
                path = parameter(connection, "modelsPath", "/models");
                if (hasText(connection.apiKey())) {
                    String header = parameter(connection, "authHeader", "Authorization");
                    String scheme = parameter(connection, "authScheme", "Bearer");
                    headers.put(header, scheme.isBlank() ? connection.apiKey() : scheme + " " + connection.apiKey());
                }
            }
            default -> throw new ProviderProbeException(
                    "PROVIDER_TYPE_UNSUPPORTED",
                    "Provider type is unsupported",
                    "当前 Provider 类型暂不支持连接测试。",
                    null
            );
        }
        return new RequestSpec(resolve(connection.endpoint(), path), Map.copyOf(headers));
    }

    private HttpClient buildClient(ProviderConnection connection) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connection.timeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NORMAL);
        if (hasText(connection.proxy())) {
            try {
                URI proxy = URI.create(connection.proxy());
                if (proxy.getHost() == null || proxy.getPort() < 1) {
                    throw new IllegalArgumentException("Proxy host and port are required");
                }
                builder.proxy(ProxySelector.of(new InetSocketAddress(proxy.getHost(), proxy.getPort())));
            } catch (IllegalArgumentException exception) {
                throw new ProviderProbeException(
                        "PROVIDER_PROXY_INVALID",
                        "Proxy configuration is invalid",
                        "代理地址无效，请使用包含端口的 HTTP 地址。",
                        null
                );
            }
        }
        if (!connection.tlsVerify()) {
            try {
                TrustManager[] trustAll = {
                        new X509TrustManager() {
                            @Override
                            public void checkClientTrusted(X509Certificate[] chain, String authType) {
                            }

                            @Override
                            public void checkServerTrusted(X509Certificate[] chain, String authType) {
                            }

                            @Override
                            public X509Certificate[] getAcceptedIssuers() {
                                return new X509Certificate[0];
                            }
                        }
                };
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, trustAll, new SecureRandom());
                SSLParameters sslParameters = new SSLParameters();
                sslParameters.setEndpointIdentificationAlgorithm("");
                builder.sslContext(sslContext).sslParameters(sslParameters);
            } catch (GeneralSecurityException exception) {
                throw new IllegalStateException("Unable to configure TLS", exception);
            }
        }
        return builder.build();
    }

    private List<DiscoveredModel> parseModels(ProviderType type, JsonNode root) {
        JsonNode modelsNode = switch (type) {
            case GEMINI, OLLAMA -> root.path("models");
            case OPENAI_COMPATIBLE, LM_STUDIO, ANTHROPIC, AZURE_OPENAI -> root.path("data");
            case CUSTOM -> {
                if (root.isArray()) {
                    yield root;
                }
                if (root.path("data").isArray()) {
                    yield root.path("data");
                }
                yield root.path("models");
            }
        };
        if (!modelsNode.isArray()) {
            throw new ProviderProbeException(
                    "PROVIDER_INVALID_RESPONSE",
                    "Provider response does not contain a model array",
                    "Provider 返回中没有可识别的模型列表。",
                    200
            );
        }

        List<DiscoveredModel> models = new ArrayList<>();
        for (JsonNode node : modelsNode) {
            String modelId = firstText(node, "id", "name", "model", "modelId");
            if (!hasText(modelId)) {
                continue;
            }
            if (type == ProviderType.GEMINI && modelId.startsWith("models/")) {
                modelId = modelId.substring("models/".length());
            }
            String displayName = firstText(node, "display_name", "displayName", "name", "id");
            if (!hasText(displayName)) {
                displayName = modelId;
            }
            Integer contextLength = firstInteger(node, "context_length", "contextLength", "context_window");
            Set<Capability> explicit = explicitCapabilities(type, node);
            models.add(new DiscoveredModel(modelId, displayName, explicit, contextLength));
        }
        return models;
    }

    private Set<Capability> explicitCapabilities(ProviderType type, JsonNode node) {
        EnumSet<Capability> capabilities = EnumSet.noneOf(Capability.class);
        JsonNode methods = node.path("supportedGenerationMethods");
        if (type == ProviderType.GEMINI && methods.isArray()) {
            for (JsonNode method : methods) {
                if ("embedContent".equals(method.asText())) {
                    capabilities.add(Capability.EMBEDDING);
                }
                if ("generateContent".equals(method.asText())) {
                    capabilities.add(Capability.CHAT);
                }
            }
        }
        JsonNode capabilityNode = node.path("capabilities");
        if (capabilityNode.isArray()) {
            for (JsonNode value : capabilityNode) {
                try {
                    capabilities.add(Capability.valueOf(value.asText().toUpperCase()));
                } catch (IllegalArgumentException ignored) {
                    // Ignore provider-specific capability names.
                }
            }
        }
        return Set.copyOf(capabilities);
    }

    private void assertSuccessful(int statusCode) {
        if (statusCode >= 200 && statusCode < 300) {
            return;
        }
        if (statusCode == 401 || statusCode == 403) {
            throw new ProviderProbeException(
                    "PROVIDER_AUTHENTICATION_FAILED",
                    "Provider authentication failed",
                    "认证失败，请检查 API Key、组织信息或自定义请求头。",
                    statusCode
            );
        }
        if (statusCode == 404) {
            throw new ProviderProbeException(
                    "PROVIDER_ENDPOINT_NOT_FOUND",
                    "Provider model endpoint was not found",
                    "模型列表接口不存在，请检查 Endpoint 或 modelsPath。",
                    statusCode
            );
        }
        if (statusCode == 429) {
            throw new ProviderProbeException(
                    "PROVIDER_RATE_LIMITED",
                    "Provider rate limit exceeded",
                    "Provider 请求过于频繁，请稍后重试。",
                    statusCode
            );
        }
        if (statusCode >= 500) {
            throw new ProviderProbeException(
                    "PROVIDER_UNAVAILABLE",
                    "Provider is temporarily unavailable",
                    "Provider 暂时不可用，请稍后重试。",
                    statusCode
            );
        }
        throw new ProviderProbeException(
                "PROVIDER_REQUEST_FAILED",
                "Provider returned HTTP " + statusCode,
                "Provider 拒绝了请求，请检查连接配置。",
                statusCode
        );
    }

    private URI resolve(String endpoint, String path) {
        try {
            if (path.startsWith("http://") || path.startsWith("https://")) {
                throw new IllegalArgumentException("modelsPath must be relative");
            }
            String normalizedEndpoint = endpoint.endsWith("/")
                    ? endpoint.substring(0, endpoint.length() - 1)
                    : endpoint;
            String normalizedPath = path.startsWith("/") ? path : "/" + path;
            return URI.create(normalizedEndpoint + normalizedPath);
        } catch (IllegalArgumentException exception) {
            throw new ProviderProbeException(
                    "PROVIDER_ENDPOINT_INVALID",
                    "Provider endpoint is invalid",
                    "Endpoint 或 modelsPath 无效，请检查连接配置。",
                    null
            );
        }
    }

    private String parameter(ProviderConnection connection, String name, String fallback) {
        String value = connection.customParameters().get(name);
        return hasText(value) ? value.trim() : fallback;
    }

    private void bearer(Map<String, String> headers, String apiKey) {
        requireApiKey(apiKey);
        headers.put("Authorization", "Bearer " + apiKey);
    }

    private void requireApiKey(String apiKey) {
        if (!hasText(apiKey)) {
            throw new ProviderProbeException(
                    "AI_PROVIDER_API_KEY_REQUIRED",
                    "API key is required",
                    "请填写 API Key。",
                    null
            );
        }
    }

    private String firstText(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (value.isTextual() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    private Integer firstInteger(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (value.isInt() || value.isLong()) {
                return value.asInt();
            }
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record RequestSpec(URI uri, Map<String, String> headers) {
    }
}
