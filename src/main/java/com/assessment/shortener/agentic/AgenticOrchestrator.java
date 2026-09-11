package com.assessment.shortener.agentic;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
public class AgenticOrchestrator {
    private final Map<String, WorkflowRun> runs = new ConcurrentHashMap<>();
    private final AiAgentProvider aiProvider;

    public AgenticOrchestrator(AiAgentProvider aiProvider) {
        this.aiProvider = aiProvider;
    }

    public WorkflowRun start(String requirement, String scenario, boolean simulateTestFailure) {
        return start(requirement, scenario, simulateTestFailure, false);
    }

    public WorkflowRun start(String requirement, String scenario, boolean simulateTestFailure, boolean simulateTransientTestFailure) {
        WorkflowRun run = new WorkflowRun(requirement, scenario);
        run.simulateTestFailure = simulateTestFailure;
        run.simulateTransientTestFailure = simulateTransientTestFailure;
        buildWorkflow(run);
        audit(run, "SYSTEM", "RUN_STARTED", "Requirement accepted; autonomy is bounded by approval gates and retry policy");

        runStage(run, "requirement-understanding", () -> buildRequirementAnalysis(run));
        if (isAmbiguous(requirement)) {
            requestClarification(run);
            runs.put(run.id, run);
            updateMetrics(run);
            return run;
        }

        runPlanningAndDesign(run, "Architecture/security synchronization complete. Engineer approval required before code-changing actions.");
        runs.put(run.id, run);
        updateMetrics(run);
        return run;
    }

    public WorkflowRun approveDesign(String runId) {
        WorkflowRun run = get(runId);
        WorkflowStage designGate = run.graph.get("design-approval");
        if (designGate.status != StageStatus.WAITING_FOR_HUMAN) {
            return run;
        }

        approveGate(run, designGate, "Design approved by engineer");
        runStage(run, "implementation", () -> buildImplementationSummary(run));

        CompletableFuture<Void> testWork = CompletableFuture.runAsync(() -> runTestsWithRetry(run));
        CompletableFuture<Void> reviewWork = CompletableFuture.runAsync(() -> runStage(run, "review", () -> buildReviewSummary(run)));
        CompletableFuture.allOf(testWork, reviewWork).join();

        if (run.graph.get("tests").status == StageStatus.SAFE_STOPPED) {
            run.status = "SAFE_STOPPED";
            audit(run, "ORCHESTRATOR", "SAFE_STOP", "Retry budget exhausted; release path blocked");
            updateMetrics(run);
            return run;
        }

        runStage(run, "validation", () -> buildValidationSummary(run));
        waitForHuman(run, "release-approval", "Validation passed. Production/release decision remains human-owned.");
        updateMetrics(run);
        return run;
    }

    public WorkflowRun approveRelease(String runId) {
        WorkflowRun run = get(runId);
        WorkflowStage releaseGate = run.graph.get("release-approval");
        if (releaseGate.status != StageStatus.WAITING_FOR_HUMAN) {
            return run;
        }

        approveGate(run, releaseGate, "Release readiness approved by engineer");
        runStage(run, "documentation", () -> buildDocumentationSummary(run));
        run.status = "COMPLETED";
        run.completedAt = Instant.now();
        audit(run, "SYSTEM", "RUN_COMPLETED", "All exit gates satisfied");
        updateMetrics(run);
        return run;
    }

    public WorkflowRun replan(String runId, String changedRequirement) {
        WorkflowRun run = get(runId);
        String previousRequirement = run.requirement;
        run.requirement = changedRequirement;
        run.replans++;

        audit(run, "HUMAN", "UPSTREAM_CHANGE", "Requirement changed; invalidating dependent planning/design stages");
        recordDecision(run, "replan", "Upstream requirement changed", "Recompute dependent work while preserving audit and decision history");
        resetDependentStages(run);

        run.status = "RUNNING";
        runStage(run, "requirement-understanding", () -> buildRequirementAnalysis(run));
        if (isAmbiguous(changedRequirement)) {
            requestClarification(run);
            updateMetrics(run);
            return run;
        }

        runPlanningAndDesign(run, "Replanned design requires renewed human approval. Previous requirement: " + previousRequirement);
        updateMetrics(run);
        return run;
    }

    public WorkflowRun clarify(String runId, String clarifiedRequirement) {
        WorkflowRun run = get(runId);
        if (!"CLARIFICATION_REQUIRED".equals(run.status)) {
            return run;
        }
        audit(run, "HUMAN", "CLARIFICATION_PROVIDED", "Missing acceptance criteria supplied by engineer/product owner");
        return replan(runId, clarifiedRequirement);
    }

    public WorkflowRun rollback(String runId) {
        WorkflowRun run = get(runId);
        for (String stageName : List.of("implementation", "tests", "review", "validation")) {
            WorkflowStage stage = run.graph.get(stageName);
            if (stage.status == StageStatus.PASSED) {
                stage.status = StageStatus.ROLLED_BACK;
            }
        }

        run.metrics.rollbacks++;
        run.status = "WAITING_FOR_HUMAN";
        audit(run, "HUMAN", "ROLLBACK", "Implementation path rolled back to the last approved design checkpoint");
        waitForHuman(run, "design-approval", "Rollback completed; renewed design approval required");
        updateMetrics(run);
        return run;
    }

    public WorkflowRun get(String runId) {
        WorkflowRun run = runs.get(runId);
        if (run == null) {
            throw new IllegalArgumentException("Run not found: " + runId);
        }
        return run;
    }

    private void runPlanningAndDesign(WorkflowRun run, String approvalReason) {
        runStage(run, "task-decomposition", () -> buildPlan(run));

        CompletableFuture<Void> architectureWork = CompletableFuture.runAsync(
                () -> runStage(run, "architecture", () -> buildArchitectureReview(run)));
        CompletableFuture<Void> securityWork = CompletableFuture.runAsync(
                () -> runStage(run, "security-policy", () -> buildSecurityReview(run)));
        CompletableFuture.allOf(architectureWork, securityWork).join();

        waitForHuman(run, "design-approval", approvalReason);
    }

    private void buildWorkflow(WorkflowRun run) {
        addStage(run, "requirement-understanding", "RequirementAgent");
        addStage(run, "task-decomposition", "PlannerAgent", "requirement-understanding");
        addStage(run, "architecture", "ArchitectureAgent", "task-decomposition");
        addStage(run, "security-policy", "SecurityAgent", "task-decomposition");
        addStage(run, "design-approval", "HumanGate", "architecture", "security-policy");
        addStage(run, "implementation", "DeveloperAgent", "design-approval");
        addStage(run, "tests", "TestAgent", "implementation");
        addStage(run, "review", "ReviewAgent", "implementation");
        addStage(run, "validation", "ValidationAgent", "tests", "review");
        addStage(run, "release-approval", "HumanGate", "validation");
        addStage(run, "documentation", "DocumentationAgent", "release-approval");
    }

    private void addStage(WorkflowRun run, String name, String agent, String... dependencies) {
        run.graph.put(name, new WorkflowStage(name, agent, dependencies));
    }

    private void runStage(WorkflowRun run, String stageName, Supplier<Map<String, Object>> work) {
        WorkflowStage stage = run.graph.get(stageName);
        if (!dependenciesPassed(run, stage)) {
            throw new IllegalStateException("Dependencies not satisfied for " + stageName);
        }

        stage.status = StageStatus.RUNNING;
        stage.startedAt = Instant.now();
        stage.attempts++;
        audit(run, stage.agent, "STAGE_STARTED", stageName);

        try {
            stage.output = work.get();
            stage.status = StageStatus.PASSED;
            auditAiFallbackIfNeeded(run, stage);
        } catch (Exception exception) {
            stage.status = StageStatus.FAILED;
            stage.output = Map.of("error", exception.getMessage());
        }

        stage.completedAt = Instant.now();
        stage.latencyMs = Duration.between(stage.startedAt, stage.completedAt).toMillis();
        audit(run, stage.agent, "STAGE_" + stage.status, stageName);
    }

    private boolean dependenciesPassed(WorkflowRun run, WorkflowStage stage) {
        return stage.dependencies.stream().allMatch(name -> run.graph.get(name).status == StageStatus.PASSED);
    }

    private void waitForHuman(WorkflowRun run, String stageName, String reason) {
        WorkflowStage gate = run.graph.get(stageName);
        gate.status = StageStatus.WAITING_FOR_HUMAN;
        gate.output = Map.of(
                "checkpoint", reason,
                "allowedActions", List.of("approve", "replan", "rollback where applicable"));
        run.status = "WAITING_FOR_HUMAN";
        audit(run, "HumanGate", "APPROVAL_REQUIRED", reason);
    }

    private void approveGate(WorkflowRun run, WorkflowStage gate, String reason) {
        gate.status = StageStatus.PASSED;
        gate.attempts++;
        gate.startedAt = Instant.now();
        gate.completedAt = gate.startedAt;
        gate.output = Map.of("decision", "APPROVED", "reason", reason);
        run.status = "RUNNING";
        audit(run, "HUMAN", "APPROVED", gate.name);
        recordDecision(run, gate.name, "High-impact action requires human ownership", reason);
    }

    private void runTestsWithRetry(WorkflowRun run) {
        WorkflowStage testStage = run.graph.get("tests");
        long firstFailureAt = 0;

        for (int attempt = 1; attempt <= run.maxRetries + 1; attempt++) {
            testStage.status = StageStatus.RUNNING;
            testStage.startedAt = Instant.now();
            testStage.attempts++;
            audit(run, "TestAgent", "TEST_ATTEMPT", "Attempt " + attempt);

            boolean persistentFailure = run.simulateTestFailure;
            boolean transientFailure = run.simulateTransientTestFailure && attempt == 1;
            boolean shouldFail = persistentFailure || transientFailure;

            if (!shouldFail) {
                testStage.output = Map.of(
                        "testSuite", "unit + integration",
                        "result", "PASSED",
                        "coverageNote", "Core service behavior, API flow, and workflow state transitions are covered");
                testStage.status = StageStatus.PASSED;
                testStage.completedAt = Instant.now();
                testStage.latencyMs = Duration.between(testStage.startedAt, testStage.completedAt).toMillis();
                if (firstFailureAt > 0) {
                    run.metrics.mttrMs = Math.max(1, System.currentTimeMillis() - firstFailureAt);
                    audit(run, "ORCHESTRATOR", "RECOVERY_RECORDED", "Transient test failure recovered within retry budget; MTTR recorded");
                }
                audit(run, "TestAgent", "STAGE_PASSED", "Tests passed");
                return;
            }

            if (firstFailureAt == 0) {
                firstFailureAt = System.currentTimeMillis();
            }
            testStage.status = StageStatus.FAILED;
            testStage.output = Map.of(
                    "failedTest", "expirationTimezoneBoundary",
                    "diagnosis", "Simulated validation failure for retry/recovery demonstration");
            audit(run, "TestAgent", "STAGE_FAILED", "Test failure detected");

            if (attempt <= run.maxRetries) {
                run.metrics.retries++;
                recordDecision(run, "tests", "Validation failed", "Retry within bounded budget of " + run.maxRetries);
                audit(run, "DeveloperAgent", "REPAIR_ATTEMPT", "Failure context returned to developer agent");
            }
        }

        testStage.status = StageStatus.SAFE_STOPPED;
        testStage.completedAt = Instant.now();
        testStage.output = Map.of(
                "reason", "Retry budget exhausted",
                "maxRetries", run.maxRetries,
                "nextAction", "Human investigation required; release blocked");
    }

    private boolean isAmbiguous(String requirement) {
        String normalized = requirement.toLowerCase(Locale.ROOT);
        boolean usesVagueTarget = normalized.contains("popular") || normalized.contains("faster") || normalized.contains("better") || normalized.contains("scalable");
        boolean hasMeasurableTarget = normalized.matches(".*\\d+.*") || normalized.contains("p95") || normalized.contains("p99") || normalized.contains("ms") || normalized.contains("second") || normalized.contains("minute");
        return usesVagueTarget && !hasMeasurableTarget;
    }

    private void requestClarification(WorkflowRun run) {
        WorkflowStage requirementStage = run.graph.get("requirement-understanding");
        requirementStage.status = StageStatus.CLARIFICATION_REQUIRED;
        requirementStage.output = new LinkedHashMap<>(requirementStage.output);
        requirementStage.output.put("blocking", true);
        requirementStage.output.put("clarificationQuestions", List.of(
                "What threshold defines a popular URL and over what time window?",
                "What measurable redirect latency target (for example p95) is required?",
                "Is a cache allowed, and what consistency/fallback behavior is acceptable?"));
        run.status = "CLARIFICATION_REQUIRED";
        audit(run, "RequirementAgent", "CLARIFICATION_REQUIRED", "Ambiguous acceptance criteria detected; downstream planning blocked until human clarification");
        recordDecision(run, "requirement-understanding", "Can downstream agents proceed safely?", "No. Missing measurable acceptance criteria requires human clarification.");
    }

    private Map<String, Object> buildRequirementAnalysis(WorkflowRun run) {
        boolean ambiguous = isAmbiguous(run.requirement);
        Map<String, Object> baseline = new LinkedHashMap<>();
        baseline.put("normalizedProblem", run.requirement.trim());
        baseline.put("acceptanceCriteria", List.of(
                "Create stable short-code mapping",
                "Redirect only active/non-expired URLs",
                "Record click analytics",
                "Return deterministic API errors"));
        baseline.put("ambiguities", ambiguous
                ? List.of("Define measurable latency target", "Define popular threshold/time window", "Confirm cache availability and consistency expectation")
                : List.of("No blocking ambiguity detected; assumptions recorded"));
        baseline.put("assumptions", List.of("HTTP/HTTPS destinations only", "PostgreSQL/H2 is system of record", "Production release is never autonomous"));
        recordDecision(run, "requirement-understanding", "Normalize intent before planning", "Acceptance criteria and assumptions made explicit");
        return aiProvider.enhance("RequirementAgent", run.requirement, baseline);
    }

    private Map<String, Object> buildPlan(WorkflowRun run) {
        Map<String, Object> baseline = new LinkedHashMap<>();
        baseline.put("tasks", List.of("API/schema", "persistence", "short-code generation", "redirect/expiration", "analytics", "tests", "documentation"));
        baseline.put("dependencyModel", "architecture + security run in parallel; tests + review run in parallel; validation synchronizes both");
        baseline.put("scenario", run.scenario);
        if ("brownfield".equalsIgnoreCase(run.scenario)) {
            baseline.put("impactAnalysis", Map.of(
                    "modules", List.of("UrlController", "UrlShortenerService", "ShortUrl", "ShortUrlRepository"),
                    "apiImpact", "Existing create/redirect/analytics contracts must remain backward compatible",
                    "dataFlow", "HTTP request -> controller -> service -> repository -> database; redirect increments analytics after active/expiry validation",
                    "regressionScope", List.of("redirect behavior", "expiration boundary", "analytics count", "persistence mapping", "API error responses")));
        }
        return aiProvider.enhance("PlannerAgent", run.requirement, baseline);
    }

    private Map<String, Object> buildArchitectureReview(WorkflowRun run) {
        List<String> impactedModules = "brownfield".equalsIgnoreCase(run.scenario)
                ? List.of("UrlController", "UrlShortenerService", "ShortUrl", "ShortUrlRepository", "tests", "API documentation")
                : List.of("REST API", "service layer", "persistence", "workflow orchestrator");
        recordDecision(run, "architecture", "Durability vs redirect latency", "Database remains source of truth; cache can be added without making it authoritative");
        return aiProvider.enhance("ArchitectureAgent", run.requirement, Map.of(
                "components", List.of("Spring Boot REST API", "URL service", "JPA repository", "H2 local/PostgreSQL production", "workflow orchestrator"),
                "impactedModules", impactedModules,
                "keyDecision", "Durable store is authoritative; caching is an optimization",
                "fallback", "If cache or AI advisory services are unavailable, core URL operations continue against the database and deterministic workflow baseline"));
    }

    private Map<String, Object> buildSecurityReview(WorkflowRun run) {
        return aiProvider.enhance("SecurityAgent", run.requirement, Map.of(
                "securityGuardrails", List.of("Allow only http/https URLs", "Reject malformed input", "Do not expose PII in analytics", "Audit every state transition"),
                "complianceGuardrails", List.of("Human approval before code-changing execution", "Human approval before release", "Preserve decision history across replans", "No secrets in source control"),
                "changeControl", "Design and release require human gates; changed upstream requirements invalidate dependent stages",
                "retryPolicy", "Maximum retries = " + run.maxRetries,
                "riskRating", "LOW-MEDIUM prototype"));
    }

    private Map<String, Object> buildImplementationSummary(WorkflowRun run) {
        return aiProvider.enhance("DeveloperAgent", run.requirement, Map.of(
                "artifacts", List.of("URL REST endpoints", "JPA entity/repository", "expiration + analytics logic", "workflow graph/state machine"),
                "changeScope", "Only the approved plan is eligible for implementation",
                "ownership", "AI output is advisory; workflow transitions and release authority remain deterministic and human-governed"));
    }

    private Map<String, Object> buildReviewSummary(WorkflowRun run) {
        return aiProvider.enhance("ReviewAgent", run.requirement, Map.of(
                "findings", List.of("Short-code collision handled by existence check", "Expiration checked server-side", "Release blocked behind human gate"),
                "severity", "No blocking finding",
                "maintainability", "Layered Spring design with isolated workflow domain"));
    }

    private Map<String, Object> buildValidationSummary(WorkflowRun run) {
        return aiProvider.enhance("ValidationAgent", run.requirement, Map.of(
                "entryGate", "tests + review PASSED",
                "checks", List.of("functional acceptance criteria", "security/compliance guardrails", "retry budget", "audit completeness"),
                "result", "RELEASE_CANDIDATE"));
    }

    private Map<String, Object> buildDocumentationSummary(WorkflowRun run) {
        return aiProvider.enhance("DocumentationAgent", run.requirement, Map.of(
                "artifacts", List.of("README", "architecture", "scenario walkthroughs", "OpenAPI schema", "engineering summary", "decision/audit trail"),
                "limitations", List.of("Single-node in-memory workflow state", "Runtime LLM is optional", "Redis/cache is an architectural extension rather than a local dependency")));
    }

    private void auditAiFallbackIfNeeded(WorkflowRun run, WorkflowStage stage) {
        Object providerStatus = stage.output.get("aiProviderStatus");
        if ("FALLBACK".equals(providerStatus)) {
            audit(run, "AI_PROVIDER", "FALLBACK_USED", stage.name + " retained deterministic baseline; workflow governance and core execution continued");
        }
    }

    private synchronized void recordDecision(WorkflowRun run, String stage, String question, String outcome) {
        Map<String, String> decision = new LinkedHashMap<>();
        decision.put("timestamp", Instant.now().toString());
        decision.put("stage", stage);
        decision.put("question", question);
        decision.put("outcome", outcome);
        run.decisionLineage.add(decision);
    }

    private synchronized void audit(WorkflowRun run, String actor, String event, String detail) {
        Map<String, Object> auditEvent = new LinkedHashMap<>();
        auditEvent.put("timestamp", Instant.now());
        auditEvent.put("actor", actor);
        auditEvent.put("event", event);
        auditEvent.put("detail", detail);
        run.auditTrail.add(auditEvent);
    }

    private void resetDependentStages(WorkflowRun run) {
        for (String stageName : List.of("requirement-understanding", "task-decomposition", "architecture", "security-policy", "design-approval", "implementation", "tests", "review", "validation", "release-approval", "documentation")) {
            resetStage(run.graph.get(stageName));
        }
    }

    private void resetStage(WorkflowStage stage) {
        stage.status = StageStatus.PENDING;
        stage.startedAt = null;
        stage.completedAt = null;
        stage.output = new LinkedHashMap<>();
    }

    private void updateMetrics(WorkflowRun run) {
        long passed = run.graph.values().stream().filter(stage -> stage.status == StageStatus.PASSED).count();
        long failed = run.graph.values().stream().filter(stage -> stage.status == StageStatus.FAILED || stage.status == StageStatus.SAFE_STOPPED).count();
        run.metrics.stagesPassed = (int) passed;
        run.metrics.stagesFailed = (int) failed;
        run.metrics.replans = run.replans;
        run.metrics.endToEndLatencyMs = Duration.between(run.startedAt, Instant.now()).toMillis();
        run.metrics.successRate = passed + failed == 0 ? 0.0 : 100.0 * passed / (passed + failed);
    }
}
