package io.coreplatform.ai.application.service;

import io.coreplatform.ai.application.domain.Capability;
import io.coreplatform.ai.application.domain.ModelCategory;
import io.coreplatform.ai.application.domain.ModelData;
import io.coreplatform.ai.application.domain.ModelParameters;
import io.coreplatform.ai.application.domain.ModelStatus;
import io.coreplatform.ai.application.domain.SceneConfiguration;
import io.coreplatform.ai.application.domain.SceneData;
import io.coreplatform.ai.application.domain.SceneModelBinding;
import io.coreplatform.ai.application.domain.SceneParameters;
import io.coreplatform.ai.application.domain.ScenePermission;
import io.coreplatform.ai.application.domain.ScenePermissionType;
import io.coreplatform.ai.application.domain.ScenePromptBinding;
import io.coreplatform.ai.application.domain.SceneStatus;
import io.coreplatform.ai.application.exception.ProviderOperationException;
import io.coreplatform.ai.application.port.ModelRepository;
import io.coreplatform.ai.application.port.PromptReferencePort;
import io.coreplatform.ai.application.port.RequestContextPort;
import io.coreplatform.ai.application.port.SceneExecutionPort;
import io.coreplatform.ai.application.port.ScenePermissionPort;
import io.coreplatform.ai.application.port.SceneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SceneServiceTest {

    @Mock
    private SceneRepository repository;

    @Mock
    private ModelRepository modelRepository;

    @Mock
    private SceneExecutionPort executionPort;

    @Mock
    private ScenePermissionPort permissionPort;

    @Mock
    private PromptReferencePort promptReferencePort;

    @Mock
    private RequestContextPort requestContext;

    private SceneService service;

    @BeforeEach
    void setUp() {
        service = new SceneService(
                repository,
                modelRepository,
                executionPort,
                permissionPort,
                promptReferencePort,
                requestContext
        );
    }

    @Test
    void shouldRequireCurrentVersionTestBeforePublishing() {
        SceneData scene = scene(SceneStatus.TESTING, 1, null);
        when(repository.findById("scene")).thenReturn(Optional.of(scene));
        when(modelRepository.resolveAlias("chat-default")).thenReturn(List.of(model()));

        assertThatThrownBy(() -> service.transition("scene", SceneStatus.PUBLISHED))
                .isInstanceOfSatisfying(ProviderOperationException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo("AI_SCENE_TEST_REQUIRED")
                );
    }

    @Test
    void shouldRejectMultipleEnabledPrimaryAliases() {
        SceneConfiguration configuration = new SceneConfiguration(
                "Chat",
                null,
                "CONVERSATION",
                "💬",
                true,
                List.of(
                        binding("chat-default", false),
                        binding("chat-backup", false)
                ),
                SceneParameters.defaults(),
                ScenePromptBinding.empty(),
                List.of(permission()),
                List.of()
        );
        when(repository.existsByCode("chat")).thenReturn(false);
        when(requestContext.actor()).thenReturn("test");

        assertThatThrownBy(() -> service.create("chat", configuration))
                .isInstanceOfSatisfying(ProviderOperationException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo("AI_SCENE_PRIMARY_MODEL_INVALID")
                );
    }

    @Test
    void shouldCreateNextWorkingVersionWhenDisabledSceneReturnsToDraft() {
        SceneData disabled = scene(SceneStatus.DISABLED, 1, 1);
        SceneData draft = scene(SceneStatus.DRAFT, 2, null);
        when(repository.findById("scene"))
                .thenReturn(Optional.of(disabled))
                .thenReturn(Optional.of(draft));
        when(repository.maxVersion("scene")).thenReturn(1);
        when(requestContext.actor()).thenReturn("test");
        when(requestContext.traceId()).thenReturn("trace");

        var result = service.transition("scene", SceneStatus.DRAFT);

        assertThat(result.scene().version()).isEqualTo(2);
        verify(repository).updateLifecycle(
                eq("scene"),
                eq(SceneStatus.DRAFT),
                eq(false),
                eq(2),
                any(Instant.class),
                eq("test")
        );
    }

    private SceneData scene(SceneStatus status, int version, Integer testedVersion) {
        Instant now = Instant.parse("2026-07-16T00:00:00Z");
        return new SceneData(
                "scene",
                "chat",
                "Chat",
                "Chat scene",
                "CONVERSATION",
                "💬",
                status,
                status == SceneStatus.PUBLISHED,
                version,
                true,
                testedVersion == null ? null : now,
                testedVersion,
                List.of(binding("chat-default", false)),
                SceneParameters.defaults(),
                ScenePromptBinding.empty(),
                List.of(permission()),
                List.of(),
                now,
                now,
                "test",
                "test"
        );
    }

    private SceneModelBinding binding(String alias, boolean fallback) {
        Instant now = Instant.parse("2026-07-16T00:00:00Z");
        return new SceneModelBinding(
                alias,
                "scene",
                alias,
                fallback ? 20 : 10,
                fallback,
                true,
                now,
                now,
                "test",
                "test"
        );
    }

    private ScenePermission permission() {
        Instant now = Instant.parse("2026-07-16T00:00:00Z");
        return new ScenePermission(
                "permission",
                "scene",
                ScenePermissionType.EVERYONE,
                "*",
                now,
                now,
                "test",
                "test"
        );
    }

    private ModelData model() {
        Instant now = Instant.parse("2026-07-16T00:00:00Z");
        return new ModelData(
                "model",
                "provider",
                "provider",
                "Provider",
                true,
                25L,
                "gpt-4o",
                "GPT-4o",
                ModelCategory.CHAT,
                null,
                ModelStatus.ENABLED,
                true,
                true,
                true,
                false,
                128_000,
                null,
                8_000,
                4_000,
                false,
                Set.of(Capability.CHAT),
                Map.of(),
                ModelParameters.empty(),
                List.of(),
                List.of(),
                Set.of(),
                now,
                now,
                now,
                "test",
                "test"
        );
    }
}
