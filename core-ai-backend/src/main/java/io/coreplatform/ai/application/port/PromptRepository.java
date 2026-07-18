package io.coreplatform.ai.application.port;

import io.coreplatform.ai.application.domain.AuditEntry;
import io.coreplatform.ai.application.domain.PromptAbTest;
import io.coreplatform.ai.application.domain.PromptData;
import io.coreplatform.ai.application.domain.PromptRenderLog;
import io.coreplatform.ai.application.domain.PromptSearchCriteria;
import io.coreplatform.ai.application.domain.PromptStatus;
import io.coreplatform.ai.application.domain.PromptTestCase;
import io.coreplatform.ai.application.domain.PromptVersionData;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PromptRepository {

    boolean existsByCode(String code);

    PromptData insertPrompt(PromptData prompt);

    void insertVersion(PromptVersionData version);

    void updatePrompt(
            PromptData prompt,
            PromptStatus status,
            int currentVersion,
            Integer publishedVersion,
            Instant now,
            String actor
    );

    Optional<PromptData> findById(String id);

    Optional<PromptData> findByCode(String code);

    List<PromptData> search(PromptSearchCriteria criteria);

    Optional<PromptVersionData> findVersion(String promptId, int version);

    Optional<PromptVersionData> findVersionById(String versionId);

    List<PromptVersionData> findVersions(String promptId);

    int maxVersion(String promptId);

    void markVersionTested(String versionId, boolean passed, Instant now, String actor);

    void markVersionPublished(String versionId, Instant now, String actor);

    PromptTestCase insertTestCase(PromptTestCase testCase);

    PromptTestCase updateTestCase(PromptTestCase testCase);

    void deleteTestCase(String id);

    Optional<PromptTestCase> findTestCase(String id);

    List<PromptTestCase> findTestCases(String versionId);

    void updateTestResult(
            String id,
            String actualOutput,
            boolean passed,
            Instant now,
            String actor
    );

    void copyTestCases(String sourceVersionId, String targetVersionId, Instant now, String actor);

    PromptAbTest insertAbTest(PromptAbTest abTest);

    Optional<PromptAbTest> findAbTest(String id);

    List<PromptAbTest> findAbTests(String promptId);

    void recordAbObservation(
            String id,
            String variant,
            boolean success,
            long latencyMs,
            java.math.BigDecimal cost,
            double score,
            Instant now,
            String actor
    );

    void addRenderLog(PromptRenderLog log, Instant now, String actor);

    List<PromptRenderLog> findRenderLogs(String promptId, int limit);

    void pruneRenderLogs(String promptId, int keep);

    void pruneExpiredRenderLogs(Instant now);

    void addAudit(AuditEntry entry);

    List<AuditEntry> findAudit(String promptId);
}
