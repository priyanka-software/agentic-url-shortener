package com.assessment.shortener.controller;

import com.assessment.shortener.agentic.AgenticOrchestrator;
import com.assessment.shortener.agentic.WorkflowRun;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/agentic")
public class AgenticController {
    private final AgenticOrchestrator orchestrator;

    public AgenticController(AgenticOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/runs")
    public WorkflowRun startRun(@RequestBody Map<String, Object> request) {
        String requirement = String.valueOf(request.getOrDefault("requirement", "Build URL shortener"));
        String scenario = String.valueOf(request.getOrDefault("scenario", "greenfield"));
        boolean simulateTestFailure = Boolean.parseBoolean(String.valueOf(request.getOrDefault("simulateTestFailure", false)));
        boolean simulateTransientTestFailure = Boolean.parseBoolean(String.valueOf(request.getOrDefault("simulateTransientTestFailure", false)));
        return orchestrator.start(requirement, scenario, simulateTestFailure, simulateTransientTestFailure);
    }

    @GetMapping("/runs/{runId}")
    public WorkflowRun getRun(@PathVariable String runId) {
        return orchestrator.get(runId);
    }

    @PostMapping("/runs/{runId}/approve-design")
    public WorkflowRun approveDesign(@PathVariable String runId) {
        return orchestrator.approveDesign(runId);
    }

    @PostMapping("/runs/{runId}/approve-release")
    public WorkflowRun approveRelease(@PathVariable String runId) {
        return orchestrator.approveRelease(runId);
    }

    @PostMapping("/runs/{runId}/clarify")
    public WorkflowRun clarifyRequirement(@PathVariable String runId, @RequestBody Map<String, String> request) {
        String requirement = request.getOrDefault("requirement", orchestrator.get(runId).requirement);
        return orchestrator.clarify(runId, requirement);
    }

    @PostMapping("/runs/{runId}/replan")
    public WorkflowRun replan(@PathVariable String runId, @RequestBody Map<String, String> request) {
        String requirement = request.getOrDefault("requirement", orchestrator.get(runId).requirement);
        return orchestrator.replan(runId, requirement);
    }

    @PostMapping("/runs/{runId}/rollback")
    public WorkflowRun rollback(@PathVariable String runId) {
        return orchestrator.rollback(runId);
    }
}
