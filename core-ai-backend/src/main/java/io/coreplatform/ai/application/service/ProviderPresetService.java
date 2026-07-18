package io.coreplatform.ai.application.service;

import io.coreplatform.ai.application.domain.ProviderType;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ProviderPresetService {

    private final List<ProviderPreset> presets = List.of(
            new ProviderPreset(
                    "openai",
                    "OpenAI",
                    ProviderType.OPENAI_COMPATIBLE,
                    "https://api.openai.com/v1",
                    "cloud",
                    true,
                    Map.of()
            ),
            new ProviderPreset(
                    "deepseek",
                    "DeepSeek",
                    ProviderType.OPENAI_COMPATIBLE,
                    "https://api.deepseek.com",
                    "cloud",
                    true,
                    Map.of()
            ),
            new ProviderPreset(
                    "anthropic",
                    "Anthropic Claude",
                    ProviderType.ANTHROPIC,
                    "https://api.anthropic.com",
                    "cloud",
                    true,
                    Map.of("anthropicVersion", "2023-06-01")
            ),
            new ProviderPreset(
                    "gemini",
                    "Google Gemini",
                    ProviderType.GEMINI,
                    "https://generativelanguage.googleapis.com",
                    "cloud",
                    true,
                    Map.of()
            ),
            new ProviderPreset(
                    "ollama",
                    "Ollama",
                    ProviderType.OLLAMA,
                    "http://localhost:11434",
                    "local",
                    false,
                    Map.of()
            ),
            new ProviderPreset(
                    "lm-studio",
                    "LM Studio",
                    ProviderType.LM_STUDIO,
                    "http://localhost:1234/v1",
                    "local",
                    false,
                    Map.of()
            ),
            new ProviderPreset(
                    "azure-openai",
                    "Azure OpenAI",
                    ProviderType.AZURE_OPENAI,
                    "https://{resource}.openai.azure.com",
                    "cloud",
                    true,
                    Map.of("apiVersion", "2024-10-21")
            ),
            new ProviderPreset(
                    "custom",
                    "Custom Provider",
                    ProviderType.CUSTOM,
                    "https://example.com",
                    "custom",
                    false,
                    Map.of("modelsPath", "/models")
            )
    );

    public List<ProviderPreset> findAll() {
        return presets;
    }

    public record ProviderPreset(
            String code,
            String name,
            ProviderType type,
            String endpoint,
            String location,
            boolean apiKeyRequired,
            Map<String, String> customParameters
    ) {
    }
}
