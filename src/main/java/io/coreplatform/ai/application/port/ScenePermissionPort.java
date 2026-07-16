package io.coreplatform.ai.application.port;

import io.coreplatform.ai.application.domain.ScenePermission;

import java.util.List;

public interface ScenePermissionPort {

    boolean hasAccess(List<ScenePermission> permissions);
}
