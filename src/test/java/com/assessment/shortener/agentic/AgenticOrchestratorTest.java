package com.assessment.shortener.agentic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgenticOrchestratorTest {
    @Test
    void requiresDesignAndReleaseApproval() {
        AgenticOrchestrator orchestrator = newOrchestrator();
        WorkflowRun run = orchestrator.start("Build URL shortener", "greenfield", false);

        assertEquals("WAITING_FOR_HUMAN", run.status);
        orchestrator.approveDesign(run.id);
        assertEquals(StageStatus.WAITING_FOR_HUMAN, run.graph.get("release-approval").status);

        orchestrator.approveRelease(run.id);
        assertEquals("COMPLETED", run.status);
    }

    @Test
    void stopsAfterRetryBudgetIsExhausted() {
        AgenticOrchestrator orchestrator = newOrchestrator();
        WorkflowRun run = orchestrator.start("Add expiration", "brownfield", true);

        orchestrator.approveDesign(run.id);

        assertEquals("SAFE_STOPPED", run.status);
        assertEquals(2, run.metrics.retries);
    }

    @Test
    void blocksPlanningUntilAmbiguousRequirementIsClarified() {
        AgenticOrchestrator orchestrator = newOrchestrator();
        WorkflowRun run = orchestrator.start("Make popular URLs faster", "ambiguous", false);

        assertEquals("CLARIFICATION_REQUIRED", run.status);
        assertEquals(StageStatus.PENDING, run.graph.get("task-decomposition").status);

        orchestrator.clarify(run.id, "Cache URLs after 100 redirects in 10 minutes; p95 redirect latency under 50ms");

        assertEquals(1, run.replans);
        assertEquals(StageStatus.WAITING_FOR_HUMAN, run.graph.get("design-approval").status);
    }

    @Test
    void recordsRecoveryTimeForTransientFailure() {
        AgenticOrchestrator orchestrator = newOrchestrator();
        WorkflowRun run = orchestrator.start("Add expiration", "brownfield", false, true);

        orchestrator.approveDesign(run.id);

        assertEquals(StageStatus.PASSED, run.graph.get("tests").status);
        assertEquals(1, run.metrics.retries);
        assertTrue(run.metrics.mttrMs > 0);
    }

    @Test
    void rollbackReturnsWorkflowToDesignApproval() {
        AgenticOrchestrator orchestrator = newOrchestrator();
        WorkflowRun run = orchestrator.start("Build URL shortener", "greenfield", false);
        orchestrator.approveDesign(run.id);

        orchestrator.rollback(run.id);

        assertEquals("WAITING_FOR_HUMAN", run.status);
        assertEquals(StageStatus.ROLLED_BACK, run.graph.get("implementation").status);
        assertEquals(StageStatus.WAITING_FOR_HUMAN, run.graph.get("design-approval").status);
        assertEquals(1, run.metrics.rollbacks);
    }
    private AgenticOrchestrator newOrchestrator() {
        return new AgenticOrchestrator(new AdaptiveAiAgentProvider("", "", ""));
    }
}

