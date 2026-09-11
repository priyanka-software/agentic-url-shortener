package com.assessment.shortener.agentic;

import java.time.Instant;
import java.util.*;

public class WorkflowRun {
    public String id = UUID.randomUUID().toString();
    public String requirement;
    public String scenario;
    public Instant startedAt = Instant.now();
    public Instant completedAt;
    public String status = "RUNNING";
    public int maxRetries = 2;
    public boolean simulateTestFailure;
    public boolean simulateTransientTestFailure;
    public int replans;
    public Map<String, WorkflowStage> graph = new LinkedHashMap<>();
    public List<Map<String, Object>> auditTrail = new ArrayList<>();
    public List<Map<String, String>> decisionLineage = new ArrayList<>();
    public WorkflowMetrics metrics = new WorkflowMetrics();

    public WorkflowRun(String requirement, String scenario) {
        this.requirement = requirement;
        this.scenario = scenario;
    }
}
