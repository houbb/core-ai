package io.coreplatform.ai.application.service;

import io.coreplatform.ai.application.domain.Capability;
import io.coreplatform.ai.application.domain.DiscoveredModel;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class CapabilityDetector {

    public Set<Capability> detect(String modelId, Set<Capability> explicitCapabilities) {
        EnumSet<Capability> capabilities = explicitCapabilities == null || explicitCapabilities.isEmpty()
                ? EnumSet.noneOf(Capability.class)
                : EnumSet.copyOf(explicitCapabilities);
        String normalized = modelId == null ? "" : modelId.toLowerCase(Locale.ROOT);

        if (containsAny(normalized, "embed", "bge", "e5-", "text2vec")) {
            capabilities.add(Capability.EMBEDDING);
        }
        if (containsAny(normalized, "rerank", "re-rank")) {
            capabilities.add(Capability.RERANK);
        }
        if (containsAny(normalized, "dall-e", "flux", "stable-diffusion", "imagegen", "imagen")) {
            capabilities.add(Capability.IMAGE);
        }
        if (containsAny(normalized, "sora", "veo", "video")) {
            capabilities.add(Capability.VIDEO);
        }
        if (containsAny(normalized, "whisper", "audio", "speech-to-text", "asr")) {
            capabilities.add(Capability.AUDIO);
        }
        if (containsAny(normalized, "tts", "text-to-speech", "speech")) {
            capabilities.add(Capability.SPEECH);
        }
        if (containsAny(normalized, "vision", "-vl", "vl-", "gpt-4o", "gpt-5", "gemini", "claude-3")) {
            capabilities.add(Capability.VISION);
        }
        if (containsAny(normalized, "reason", "deepseek-r1", "o1", "o3", "o4")) {
            capabilities.add(Capability.REASONING);
        }
        if (containsAny(normalized, "moderation", "safety")) {
            capabilities.add(Capability.MODERATION);
        }
        if (containsAny(normalized, "ocr", "document-ai")) {
            capabilities.add(Capability.OCR);
        }
        if (capabilities.isEmpty() || capabilities.contains(Capability.VISION)
                || capabilities.contains(Capability.REASONING)) {
            capabilities.add(Capability.CHAT);
        }
        if (capabilities.contains(Capability.CHAT)) {
            capabilities.add(Capability.STREAMING);
        }
        if (capabilities.contains(Capability.CHAT)
                && containsAny(normalized, "gpt", "claude", "gemini", "qwen", "mistral", "deepseek")) {
            capabilities.add(Capability.TOOL_CALL);
            capabilities.add(Capability.JSON_MODE);
        }
        return Set.copyOf(capabilities);
    }

    public Set<Capability> aggregate(List<DiscoveredModel> models) {
        EnumSet<Capability> result = EnumSet.noneOf(Capability.class);
        for (DiscoveredModel model : models) {
            result.addAll(detect(model.modelId(), model.capabilities()));
        }
        return Set.copyOf(result);
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}
