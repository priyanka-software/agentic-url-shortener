# Architecture and Agentic Control Model

## Business service
Spring Boot REST API -> UrlShortenerService -> JPA repository -> H2 locally / PostgreSQL in production. The database is authoritative. A cache such as Redis is an optional redirect optimization, not the source of truth.

## Agentic SDLC orchestration
The orchestrator is a stateful dependency graph, not a prompt chain.

```text
RequirementAgent
      |
PlannerAgent
   /      \
Architecture  Security       (parallel)
   \      /
 Design Human Gate            (sync + approval)
       |
 Developer
    /      \
 Tests     Review             (parallel)
    \      /
  Validation                  (sync)
       |
 Release Human Gate
       |
 Documentation
```

Every stage exposes dependencies, status, attempts, latency and structured output. Every transition is appended to an audit trail. Important decisions are preserved separately as decision lineage.

## Governance
- Human approval before implementation and before release.
- Bounded retry budget (2 retries) for failed tests.
- Exhausted retries trigger SAFE_STOPPED; release is blocked.
- Rollback returns execution to the last approved design boundary.
- Re-plan invalidates dependent stages when the upstream requirement changes and requires renewed approval.
- Security policy forbids non-http(s) destinations at the business-service boundary and forbids autonomous production release.

## Reliability metrics
Each run reports passed/failed stages, retries, rollbacks, replans, MTTR where applicable, success rate, and end-to-end latency.

## AI boundary
The runtime prototype uses deterministic, inspectable specialized agent executors so an evaluator can run it without an API key. AI assistance is an engineering accelerator for requirement analysis, alternatives, implementation review, test ideas and documentation. Architecture choices, acceptance criteria, policy, approvals and final release quality remain engineer-owned. A production extension can replace individual executors with an approved LLM adapter without changing orchestration/governance semantics.


## AI execution boundary
`AiAgentProvider` separates reasoning assistance from workflow control. `AdaptiveAiAgentProvider` can call an OpenAI-compatible `/chat/completions` endpoint when explicitly configured. AI output is advisory and is merged into structured stage output. The Java orchestrator alone controls transitions, retries, safe-stop, rollback and human approval gates. Missing credentials or provider failure triggers deterministic fallback rather than workflow failure.

## Clarification gate
The RequirementAgent blocks vague, non-measurable requirements with `CLARIFICATION_REQUIRED`. Downstream stages remain pending. `/clarify` records a human clarification event and replans from the changed requirement; design still requires a separate human approval.
