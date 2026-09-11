# 10-minute demo

## 1. URL service
Create a URL, open the short URL, then query analytics. Show expiration and click count.

## 2. Greenfield happy path
POST `/api/v1/agentic/runs` with scenario `greenfield`. Point out the explicit graph and the first `WAITING_FOR_HUMAN` gate. Approve design, show tests/review synchronization and second human gate, then approve release.

## 3. Brownfield failure path
Start with scenario `brownfield` and `simulateTestFailure:true`. Approve design. The TestAgent consumes its bounded retry budget and the run becomes `SAFE_STOPPED`; release is never reached.

## 4. Ambiguous + dynamic replan
Start scenario `ambiguous` with `Make popular URLs faster`. Show ambiguities. Call `/replan` with measurable thresholds. Downstream stages are invalidated/recomputed and renewed design approval is required.

## 5. Defensible talking point
"I used AI to accelerate engineering work, but I kept the autonomy boundary explicit. Agents can analyze and execute bounded work; the orchestrator owns dependencies, retries and state; humans own high-impact approvals and final release quality."


## Ambiguity demo (final behavior)
Start `Make popular shortened URLs faster`. Expected status: `CLARIFICATION_REQUIRED`, with task decomposition still `PENDING`. Call `/clarify` with measurable threshold and latency SLO. Expected: requirement/planning/design recomputed and status `WAITING_FOR_HUMAN` at design approval.

## AI provider demo
Without credentials, show `aiExecutionMode=DETERMINISTIC_FALLBACK`. With an approved OpenAI-compatible provider configured via environment variables, restart and show `aiExecutionMode=LLM_ENHANCED` plus `aiAdvisory`. Explain that AI is advisory; Java governance remains authoritative.
