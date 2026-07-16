package io.coreplatform.ai.application.port;

import io.coreplatform.ai.application.domain.ToolModels.Execution;
import io.coreplatform.ai.application.domain.ToolModels.MarketItem;
import io.coreplatform.ai.application.domain.ToolModels.Policy;
import io.coreplatform.ai.application.domain.ToolModels.TestCase;
import io.coreplatform.ai.application.domain.ToolModels.Tool;
import io.coreplatform.ai.application.domain.ToolModels.Version;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ToolRepository {

    boolean existsByCode(String code);

    List<Tool> search(String query);

    Optional<Tool> findTool(String id);

    Optional<Tool> findToolByCode(String code);

    void insertTool(Tool tool);

    void updateTool(Tool tool);

    void insertVersion(Version version);

    Optional<Version> findVersion(String toolId, int version);

    List<Version> findVersions(String toolId);

    Policy findPolicy(String toolId);

    void savePolicy(Policy policy);

    TestCase insertTestCase(TestCase testCase);

    List<TestCase> findTestCases(String versionId);

    void updateTestResult(TestCase testCase);

    void markVersionTested(String versionId, boolean passed, Instant now, String actor);

    void markVersionPublished(String versionId, Instant now, String actor);

    Execution insertExecution(Execution execution);

    void updateExecution(Execution execution);

    Optional<Execution> findExecution(String id);

    List<Execution> findExecutions(String toolId, int limit);

    List<MarketItem> findMarket();

    Optional<MarketItem> findMarketItem(String id);

    void incrementInstall(String id, Instant now, String actor);
}
