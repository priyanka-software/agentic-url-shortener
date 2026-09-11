# Agentic URL Shortener

I built this project as part of the Agentic-Proficient Software Engineer assessment. It is a Java 21 / Spring Boot URL shortener combined with a small agentic workflow that models how a requirement can move through an engineering lifecycle.

The project has two main parts:

* A URL shortener that supports URL creation, redirects, expiration, click analytics and deletion.
* An agentic SDLC workflow that handles requirement analysis, planning, architecture, security, implementation, testing, review, validation and documentation.

The main goal of the agentic part was to keep the workflow automated where it makes sense, while still keeping important decisions under human control.

## Tech Stack

* Java 21
* Spring Boot
* Spring Data JPA
* H2 for local development
* PostgreSQL as a production database option
* Gradle
* JUnit 5 / Mockito / MockMvc

## Running the Application

The project requires JDK 21.

If you are using IntelliJ, make sure both the Project SDK and Gradle JVM are set to Java 21.

Run:

`com.assessment.shortener.AgenticUrlShortenerApplication`

The application starts on port `8080`.

H2 is used by default, so no external database setup is required for local testing.

## URL Shortener API

Create a shortened URL:

```powershell
$u = Invoke-RestMethod -Method POST `
  -Uri "http://localhost:8080/api/v1/urls" `
  -ContentType "application/json" `
  -Body '{"url":"https://example.com","expiresInMinutes":60}'

$u
```

The response contains the generated short code and short URL.

To test the redirect:

```powershell
Start-Process $u.shortUrl
```

The browser should redirect to the original URL.

Check analytics:

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8080/api/v1/urls/$($u.shortCode)/analytics"
```

The click count increases when the short URL is used.

Only HTTP and HTTPS destinations are accepted. Invalid or unsafe schemes are rejected.

## Agentic Workflow

I modeled the engineering workflow as a dependency graph instead of having every step execute as one long sequential process.

The normal flow is:

```text
RequirementAgent
      |
PlannerAgent
      |
      +-------------------+
      |                   |
ArchitectureAgent    SecurityAgent
      |                   |
      +---------+---------+
                |
        Human Design Approval
                |
         DeveloperAgent
                |
          +-----+-----+
          |           |
      TestAgent    ReviewAgent
          |           |
          +-----+-----+
                |
        ValidationAgent
                |
       Human Release Approval
                |
      DocumentationAgent
                |
            COMPLETED
```

Architecture and security can be evaluated independently. Testing and review are also separate paths, and validation waits for both of them.

I added human approval before implementation and again before final release. The workflow cannot approve either gate by itself.

## Running a Greenfield Workflow

```powershell
$run = Invoke-RestMethod -Method POST `
  -Uri "http://localhost:8080/api/v1/agentic/runs" `
  -ContentType "application/json" `
  -Body '{
    "requirement":"Build a URL shortener with expiration and click analytics",
    "scenario":"greenfield"
  }'

$run.status
```

The workflow stops at the design approval gate.

Approve the design:

```powershell
$run = Invoke-RestMethod -Method POST `
  -Uri "http://localhost:8080/api/v1/agentic/runs/$($run.id)/approve-design"

$run.status
```

After implementation, testing, review and validation, the workflow waits for release approval.

```powershell
$run = Invoke-RestMethod -Method POST `
  -Uri "http://localhost:8080/api/v1/agentic/runs/$($run.id)/approve-release"

$run.status
$run.metrics
```

A successful run ends with `COMPLETED`.

## Handling Ambiguous Requirements

I didn't want the workflow to invent important business requirements.

For example:

```text
Make popular shortened URLs faster
```

doesn't define what "popular" means or what "faster" means.

Starting this as an ambiguous scenario:

```powershell
$amb = Invoke-RestMethod -Method POST `
  -Uri "http://localhost:8080/api/v1/agentic/runs" `
  -ContentType "application/json" `
  -Body '{
    "requirement":"Make popular shortened URLs faster",
    "scenario":"ambiguous"
  }'

$amb.status
```

returns `CLARIFICATION_REQUIRED`.

The RequirementAgent identifies questions such as the popularity threshold, latency target and cache consistency expectations.

A human can then clarify the requirement:

```powershell
$amb = Invoke-RestMethod -Method POST `
  -Uri "http://localhost:8080/api/v1/agentic/runs/$($amb.id)/clarify" `
  -ContentType "application/json" `
  -Body '{
    "requirement":"A URL is popular after 100 redirects within 10 minutes. Target p95 redirect latency is under 50ms. Popular URLs may be cached, the database remains the source of truth, and cache failures must fall back to the database."
  }'

$amb.status
$amb.metrics
```

The workflow replans the dependent work and requires design approval again.

## Brownfield Changes

The brownfield scenario is used when a requirement changes an existing system.

For example:

```text
Add optional custom aliases without breaking existing short URLs.
```

The workflow identifies the existing modules that may be affected, including:

* `UrlController`
* `UrlShortenerService`
* `ShortUrl`
* `ShortUrlRepository`
* tests
* API documentation

It also records API compatibility, existing data flow and regression areas that should be validated before the change is released.

## Retry and Recovery

Failures are handled with a bounded retry policy.

A transient test failure can be simulated with:

```powershell
$recovery = Invoke-RestMethod -Method POST `
  -Uri "http://localhost:8080/api/v1/agentic/runs" `
  -ContentType "application/json" `
  -Body '{
    "requirement":"Add expiration validation",
    "scenario":"brownfield",
    "simulateTransientTestFailure":true
  }'

$recovery = Invoke-RestMethod -Method POST `
  -Uri "http://localhost:8080/api/v1/agentic/runs/$($recovery.id)/approve-design"

$recovery.metrics
```

The first test attempt fails, the failure context is returned to the developer stage, and testing is attempted again.

When recovery succeeds, the workflow records the retry and MTTR.

For a persistent failure, use `simulateTestFailure=true`. The workflow retries only within its configured retry budget. Once that budget is exhausted, the run moves to `SAFE_STOPPED` instead of continuing indefinitely.

## Rollback

A workflow that has progressed through implementation and validation can also be rolled back:

```powershell
$rolledBack = Invoke-RestMethod -Method POST `
  -Uri "http://localhost:8080/api/v1/agentic/runs/$($run.id)/rollback"

$rolledBack.status
$rolledBack.metrics
```

The affected downstream stages are marked as rolled back and design approval is required again.

## AI Usage and Autonomy

I used AI during development as an accelerator for requirement analysis, design alternatives, implementation assistance, test ideas and documentation.

For runtime execution, I separated AI reasoning from workflow control.

The Java orchestrator owns workflow state, dependencies, retries, rollback, safe-stop and human approval gates. An `AiAgentProvider` can provide advisory output, but it cannot approve a design, approve a release or directly change workflow state.

By default, the application runs in `DETERMINISTIC_FALLBACK` mode. This allows the project to be evaluated without requiring an external API key.

An OpenAI-compatible provider can optionally be configured using:

```powershell
$env:AGENT_AI_BASE_URL="https://YOUR_OPENAI_COMPATIBLE_ENDPOINT/v1"
$env:AGENT_AI_API_KEY="YOUR_KEY"
$env:AGENT_AI_MODEL="YOUR_MODEL"
```

If the AI provider is unavailable, the workflow falls back to the deterministic implementation. The governance flow continues to work independently of the external AI service.

## Testing

Run the automated tests with:

```powershell
.\gradlew test
```

The test suite covers the URL-shortener service, HTTP integration behavior and agentic orchestration scenarios including human approval, ambiguity handling, retry/recovery and rollback.

I also manually exercised the URL APIs and agentic workflows through PowerShell to verify the end-to-end behavior.

## Documentation

Additional project documentation is available under `docs/`:

* `docs/ARCHITECTURE.md` - architecture and workflow design
* `docs/DEMO.md` - evaluator walkthrough
* `docs/openapi.yaml` - API definition
* `docs/ENGINEERING_SUMMARY.md` - implementation decisions and tradeoffs
* `docs/REQUIREMENTS_TRACEABILITY.md` - mapping between assessment requirements and implementation

The three assessment scenarios are documented under:

* `scenarios/greenfield`
* `scenarios/brownfield`
* `scenarios/ambiguous`

## Current Limitations

This is a prototype rather than a fully deployed production platform.

Workflow state and audit history are currently stored in memory. H2 is used for local development, while PostgreSQL is available as the production database option. Authentication and RBAC for human approvers are not implemented, and the submitted configuration does not require a live LLM provider.

For a production implementation, I would persist workflow and audit state, authenticate approvers, add distributed tracing and operational metrics, introduce rate limiting and secrets management, and consider Redis for high-volume redirect caching.

I intentionally kept those concerns outside the prototype so the implementation stays focused on the engineering workflow, controlled autonomy and failure handling required by the assessment.
