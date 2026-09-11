# Engineering Summary

## Goal
Build a runnable URL shortener and demonstrate a governed, stateful engineering workflow that can take a requirement through analysis, planning, design, implementation, testing, review, validation, documentation and release readiness.

## Engineering approach
The URL service and the workflow control plane are intentionally separate. The URL service owns product behavior. `AgenticOrchestrator` owns workflow state, dependencies, gates, retries, rollback, replanning, audit history and metrics. AI output is advisory through `AiAgentProvider`; it is never allowed to approve a design, approve a release, or mutate workflow state directly.

## Key decisions
- H2 is used locally and PostgreSQL is supported through environment configuration.
- The database remains authoritative. A future cache is an optimization and must fall back to the database.
- Architecture and security analysis can run in parallel after planning. Tests and review can run in parallel after implementation. Validation synchronizes those paths.
- Design approval and release approval are human-owned gates.
- Ambiguous requirements block downstream planning until measurable acceptance criteria are supplied.
- Retry count is bounded. Exhaustion results in a safe stop rather than an autonomous release.
- An unavailable AI provider falls back to deterministic engineering output and records the fallback in the audit trail.

## Scenario coverage
### Greenfield
A new URL shortener requirement is decomposed, designed, approved, implemented, tested/reviewed, validated, approved for release and documented.

### Brownfield
Enhancement planning identifies impacted controller/service/entity/repository modules, API compatibility, data flow and regression scope. Persistent failure demonstrates bounded retries and safe-stop. A transient failure demonstrates recovery and MTTR. Rollback returns the workflow to the design checkpoint.

### Ambiguous
A vague request such as “Make popular shortened URLs faster” enters `CLARIFICATION_REQUIRED`. Planning remains pending. Human-supplied thresholds cause dependent work to be recomputed and require renewed design approval.

## Risk and validation
- Malformed/non-HTTP URLs are rejected.
- Expired URLs do not redirect.
- Short-code collisions are checked before persistence.
- Analytics must not expose PII.
- AI credentials are external configuration and are not committed.
- AI failure cannot bypass deterministic controls.
- Release is blocked after failed validation or exhausted retries.
- Audit events and decision lineage are retained across replans.

## Metrics
The workflow reports stage success/failure counts, retry count, rollback count, replan count, end-to-end latency and success rate. MTTR is populated when a transient test failure recovers inside the retry budget. A persistent unresolved failure is safe-stopped rather than incorrectly reported as recovered.

## Limitations
- Workflow state is in-memory and single-node; production deployment would persist workflow state and use durable eventing/locking.
- Runtime LLM use is optional so the evaluator can run the prototype without external credentials.
- Cache behavior is represented as an architectural fallback decision; Redis is not required for the local prototype.
- The test-failure switches are demonstration hooks, not production API features.

## AI usage and ownership
AI assistance is used as an engineering accelerator for advisory analysis and review. The deterministic baseline makes the prototype reproducible. The engineer owns architecture, acceptance criteria, approval decisions, risk trade-offs, validation and final quality.
