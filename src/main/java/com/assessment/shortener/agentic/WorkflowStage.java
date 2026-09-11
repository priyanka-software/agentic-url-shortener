package com.assessment.shortener.agentic;
import java.time.Instant; import java.util.*;
public class WorkflowStage {
 public String name; public String agent; public List<String> dependencies=new ArrayList<>(); public StageStatus status=StageStatus.PENDING;
 public Instant startedAt; public Instant completedAt; public long latencyMs; public int attempts; public Map<String,Object> output=new LinkedHashMap<>();
 public WorkflowStage(String name,String agent,String...deps){this.name=name;this.agent=agent;this.dependencies.addAll(Arrays.asList(deps));}
}
