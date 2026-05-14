# Open Decisions

This file records reviewer decisions that should be made before later migration branches proceed beyond baseline/tooling work.

## Required Reviewer Decisions

| Decision | Current evidence | Recommendation | Needed by |
| --- | --- | --- | --- |
| Java 21 vs Java 25 certification | POM declares Java 21. CI and Docker are oriented around Java 21. Local Java 25 compile fails before tests. | Certify and support Java 21 for production. Do not certify Java 25 during Boot 4 migration unless separately requested and tested. | Before accepting build/test results. |
| Temporary Jackson 2 bridge vs final Jackson 3 | Current app uses Jackson 2 only. Direct `jackson-databind:2.17.2` is mixed with Boot-managed `2.17.1` core/annotations. Boot 4 target implies Jackson 3 risk. | Prefer a direct Jackson 3 migration only after controller/API contract tests exist. Allow a temporary bridge only if Boot 4 cannot preserve behavior otherwise. | Before Boot 4 dependency branch. |
| `/cache/**` security matcher behavior | Security is Spring Security 6.3 today. Boot 4 target brings Spring Security 7 risk. | Require contract tests for `/cache/**`, bearer token auth, rejected requests, and unauthenticated/unauthorized responses before changing security dependencies. | Before Spring Security 7 migration. |
| AWS aggregate SDK replacement approval | Runtime uses `software.amazon.awssdk:aws-sdk-java:2.29.29` aggregate. S3 is the critical production path. | Replace aggregate SDK with explicit modules only with reviewer approval and S3/local storage regression tests. | Before impacted-library cleanup. |
| Apache HttpClient 4 retention | Direct dependency `org.apache.httpcomponents:httpclient:4.5.13`; Spring Cloud also brings HttpClient 5. | Retain HttpClient 4 until callers and behavior are audited. Do not remove in bulk dependency cleanup. | Before dependency cleanup branch. |
| Travis CI handling | `.travis.yml` still exists and runs Docker test flow. GitHub Actions also exist. | Decide whether Travis is still authoritative. If not, document deprecation/removal separately. | Before CI modernization branch. |
| Actuator liveness/readiness behavior | Actuator is present, but current exposed endpoint behavior still needs runtime verification. | Treat actuator endpoint availability/status as observable behavior until deployment owners say otherwise. | Before Boot 4 runtime validation. |
| Vulnerability scanner exceptions | No vulnerability scan tool was added or run in this branch. Legacy `jjwt`, Commons libraries, JAXB, and AWS v1 test dependencies are likely scan topics. | Add or select approved scanner in a dedicated branch; document any high/critical exceptions with owner approval. | Before release candidate. |
| Springdoc/OpenAPI timing | Current `springdoc-openapi-starter-webmvc-ui:2.6.0` is tied to Boot 3 era. | Upgrade only when Boot 4-compatible springdoc path is confirmed; keep OpenAPI routes under contract test if considered public. | Before or during Boot 4 branch. |

## Branch-Specific Follow-Ups

| Item | Status | Recommended next action |
| --- | --- | --- |
| Java 21 local validation | Blocked locally; only Java 17 and Java 25 were present. | Install/select Java 21 and rerun `./mvnw -B clean test`. |
| Docker validation | Blocked locally; Docker daemon was unavailable. | Start Docker Desktop/daemon or rely on GitHub Actions Docker job. |
| Baseline compile failure | Seen before and after Stage 2 edits. | Re-run under Java 21 before considering source changes. |
| Duplicate `commons-lang3` declaration | Pre-existing Maven model warning. | Fix in a later focused dependency hygiene branch, not in this baseline/tooling branch unless reviewers want it now. |
| `mvnw.cmd` generated bootstrap fix | Fixed locally in this branch. | Reviewer can either accept the minimal fix or regenerate with a wrapper version that already handles non-symlink `.m2` directories. |
| Effective POM artifact | Generated under `target/effective-pom.xml`, then removed by later `clean`. | Regenerate when needed; avoid committing huge generated effective POM unless reviewers request it. |
| GitHub Actions Maven usage | Some workflows call `mvn` directly. | Later CI branch should switch to `./mvnw` consistently after wrapper is accepted. |

## Assumptions For Later Stages

- The service remains a Spring Boot executable jar.
- Public REST behavior, URL structure, response bodies, headers, status codes, and error semantics are contractual.
- Local filesystem and AWS S3 storage paths are both production-relevant.
- Controller and service/business layers remain separate.
- No business logic change is acceptable unless required to preserve equivalent behavior on the new framework and covered by tests.
- Dependency cleanup should be reviewable and staged after baseline behavior is captured under Java 21.
