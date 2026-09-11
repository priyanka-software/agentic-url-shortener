package com.assessment.shortener.agentic;
import java.util.Map;
public interface AiAgentProvider {
 Map<String,Object> enhance(String agent, String requirement, Map<String,Object> deterministicOutput);
 String mode();
}
