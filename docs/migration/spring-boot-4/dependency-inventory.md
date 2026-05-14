# Dependency And Build Inventory

This document records the Stage 1 dependency and build inventory for the `integration` baseline at `d176ff48`, plus the Stage 2 build-tooling updates made on branch `codex/boot4-migration-baseline-tooling`.

## Inventory Commands

The following commands were run and recorded in [command-log.md](command-log.md):

```bash
mvn -DskipTests dependency:tree
mvn -DskipTests dependency:tree -Dverbose
mvn -DskipTests dependency:tree -Dincludes=javax.*:*,jakarta.*:*
mvn -DskipTests dependency:tree -Dincludes=com.fasterxml.jackson.*:*,tools.jackson.*:*
mvn -DskipTests dependency:tree -Dincludes=software.amazon.awssdk:*
./mvnw -B -DskipTests dependency:tree
```

Networked Maven commands initially failed inside the sandbox with `Permission denied: getsockopt` and then succeeded when rerun with network access.

## Current Build Baseline

| Area | Current state |
| --- | --- |
| Maven artifact | `gov.nist.oar:oar-dist-service:1.0.0-SNAPSHOT` |
| Packaging | `jar` |
| Parent POM | `org.springframework.boot:spring-boot-starter-parent:3.3.0` |
| Java property | `21` |
| Active local Java | `25.0.3`, not certified for this project |
| Maven before branch | local Maven `3.9.8` |
| Maven after branch | Maven Wrapper `3.9.15` |
| Spring Cloud BOM | `org.springframework.cloud:spring-cloud-dependencies:2023.0.2` |
| Spring Framework | `6.1.8` managed by Spring Boot 3.3.0 |
| Spring Security | `6.3.0` managed by Spring Boot 3.3.0 |
| Embedded servlet container | Tomcat `10.1.24` from `spring-boot-starter-tomcat` |
| Servlet API | direct `jakarta.servlet:jakarta.servlet-api:6.1.0` with `provided` scope |
| Logging | Boot logging with Logback `1.5.6`, Log4j-to-SLF4J `2.23.1`, SLF4J `2.0.13` |
| Metrics/observability | Spring Boot Actuator `3.3.0`, Micrometer `1.13.0` |

## Dependency Management Strategy

The project uses `spring-boot-starter-parent` for dependency and plugin management and imports the Spring Cloud BOM through `dependencyManagement`. Many dependencies are still explicitly pinned in the application POM.

Boot-managed dependencies should generally be left unversioned unless there is a clear, documented compatibility reason. This branch does not remove existing pins except by adding build-tooling properties required for Stage 2.

## Direct Dependencies

| Dependency | Version source | Current version/effective state | Migration note |
| --- | --- | --- | --- |
| `spring-boot-starter-web` | Boot managed | `3.3.0` | Current MVC/Tomcat baseline. |
| `spring-boot-starter-actuator` | Boot managed | `3.3.0` | Later verify health/liveness/readiness behavior. |
| `spring-boot-starter-security` | Boot managed | `3.3.0` | Later Spring Security 7 matcher behavior review required. |
| `spring-boot-starter-thymeleaf` | Boot managed | `3.3.0`; Thymeleaf Spring 6 integration | Verify template rendering and static resources later. |
| `spring-cloud-starter-config` | Spring Cloud BOM | Config client `4.1.2`, starter/context `4.1.3` | Must move with Cloud release train in later branch. |
| `software.amazon.awssdk:aws-sdk-java` | Explicit | `2.29.29` aggregate SDK | High-risk and overly broad; later replace with specific AWS modules if approved. |
| `org.apache.httpcomponents:httpclient` | Explicit | `4.5.13` | Legacy Apache HttpClient 4 retained for now; reviewer decision required. |
| `org.json:json` | Explicit | `20231013` | Keep pinned until JSON behavior review. |
| `commons-io:commons-io` | Explicit | `2.18.0` | Explicit pin. |
| `org.apache.commons:commons-lang3` | Explicit duplicate | POM declares `3.12.0` and `3.8.1`; effective tree uses `3.8.1` | Pre-existing malformed model; fix in later focused cleanup. |
| `javax.inject:javax.inject` | Explicit | `1` | Legacy `javax.*`; Boot 4 cleanup required. |
| `org.xerial:sqlite-jdbc` | Explicit | `3.41.2.2` | Used by cache/storage inventory; verify native/platform behavior. |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | Explicit | `2.6.0` | Springdoc timing must be reviewed for Boot 4. |
| `spring-boot-devtools` | Boot managed | runtime scope | Should not be packaged into production runtime. |
| `com.adobe.testing:s3mock` | Explicit test | `3.12.0` | Pulls Jetty 12 test stack and AWS/Jackson modules. |
| `com.adobe.testing:s3mock-junit5` | Explicit test | `3.12.0` | Pulls AWS SDK v1 test dependency. |
| `org.testcontainers:junit-jupiter` | Explicit test | declared `1.19.4`, tree resolves `testcontainers:1.19.8` transitively | Docker-dependent tests need environment clarity. |
| `javax.activation:activation` | Explicit | `1.1.1` | Legacy `javax.*`; Boot 4 cleanup required. |
| `org.projectlombok:lombok` | Boot managed | effective `1.18.32` | Java 25 compile symptoms suggest Java 21 validation is required before app-code changes. |
| `io.jsonwebtoken:jjwt` | Explicit | `0.9.1` | Old monolithic JJWT; likely Jackson 2/JAXB implications. |
| `org.apache.commons:commons-text` | Explicit | `1.10.0`; dependency tree notes conflicts with newer managed transitive requests | Later vulnerability and duplicate-class review. |
| `org.jsoup:jsoup` | Explicit | `1.15.3` | HTML sanitization/RPA flow risk area. |
| `commons-validator:commons-validator` | Explicit | `1.7` | Pulls old Commons libraries. |
| `org.hibernate.validator:hibernate-validator` | Explicit | `8.0.1.Final` | Boot may manage validation stack; review pin later. |
| `jakarta.validation:jakarta.validation-api` | Explicit | `3.1.0` | Direct API pin may conflict with target managed version. |
| `jakarta.servlet:jakarta.servlet-api` | Explicit provided | `6.1.0` | Currently ahead of Boot 3.3 Tomcat baseline; Boot 4/Tomcat 11 will require Servlet 6.1. |
| `com.fasterxml.jackson.core:jackson-databind` | Explicit | `2.17.2`; core/annotations remain `2.17.1` | Mixed Jackson 2 patch level; later Jackson 3 plan required. |
| `javax.xml.bind:jaxb-api` | Explicit | `2.3.1` | Legacy `javax.*`; conflicts conceptually with Jakarta JAXB 4 stack. |
| `org.glassfish.jaxb:jaxb-runtime` | Explicit | POM pins `2.3.1`; tree includes mixed JAXB 4 transitive components | High cleanup risk for Boot 4. |

## Legacy `javax.*` And Jakarta State

The `javax.*`/`jakarta.*` filtered tree shows a mixed namespace state:

- `jakarta.annotation:jakarta.annotation-api:2.1.1` from Boot.
- `jakarta.xml.bind:jakarta.xml.bind-api:4.0.2` and `jakarta.activation:jakarta.activation-api:2.1.3` from the test starter.
- Direct `javax.inject:javax.inject:1`.
- Direct `javax.activation:activation:1.1.1`.
- Direct `javax.xml.bind:jaxb-api:2.3.1`, which pulls `javax.activation-api:1.2.0`.
- Direct `jakarta.validation-api:3.1.0`.
- Direct `jakarta.servlet-api:6.1.0`.
- Test-scope Jakarta artifacts from S3Mock/Jetty 12, including WebSocket, CDI, interceptor, transaction, and inject APIs.

Follow-up for Boot 4: eliminate avoidable runtime `javax.*` artifacts and verify that any retained test-only `javax.*` artifacts are isolated and documented.

## Jackson State

The Jackson-filtered tree shows Jackson 2 only. No `tools.jackson.*` artifacts are currently present.

Important current details:

- Direct `jackson-databind:2.17.2`.
- `jackson-core:2.17.1` and `jackson-annotations:2.17.1` remain at Boot-managed versions.
- Boot starter JSON contributes `jackson-module-parameter-names:2.17.1`.
- Springdoc contributes `jackson-dataformat-yaml:2.17.1`.
- S3Mock contributes `jackson-dataformat-xml`, `jackson-datatype-jsr310`, and `jackson-datatype-jdk8` at `2.17.1`.
- S3Mock/AWS SDK v1 test dependency contributes `jackson-dataformat-cbor:2.17.1`.

Follow-up for Boot 4: decide whether to temporarily use any Jackson 2 compatibility bridge if Boot 4/Jackson 3 migration cannot be completed in one step. The migration spec treats Jackson 3 as a critical risk because request/response JSON compatibility must remain exact.

## AWS SDK State

The project uses the aggregate AWS SDK v2 dependency:

```xml
software.amazon.awssdk:aws-sdk-java:2.29.29
```

The dependency tree pulls a very large set of AWS service modules. S3-related modules are present, including:

- `software.amazon.awssdk:s3`
- `software.amazon.awssdk:s3control`
- `software.amazon.awssdk:s3-transfer-manager`
- `software.amazon.awssdk:s3-event-notifications`
- `software.amazon.awssdk:apache-client`
- `software.amazon.awssdk:netty-nio-client`
- `software.amazon.awssdk:url-connection-client` in test scope via S3Mock support

S3Mock test support also pulls AWS SDK v1:

- `com.amazonaws:aws-java-sdk-s3:1.12.779`
- `com.amazonaws:aws-java-sdk-core:1.12.779`
- `com.amazonaws:aws-java-sdk-kms:1.12.779`

Follow-up for Boot 4: seek reviewer approval before replacing the aggregate AWS SDK dependency with explicit modules. Storage behavior must be validated against both local filesystem and S3-backed distribution paths.

## JJWT State

The project uses:

```xml
io.jsonwebtoken:jjwt:0.9.1
```

This is the old monolithic JJWT artifact. It is a migration risk because modern JJWT uses separate `jjwt-api`, `jjwt-impl`, and JSON integration artifacts, and because JWT behavior must remain byte-for-byte or contract equivalent where externally visible.

No JJWT migration is included in this branch.

## Springdoc/OpenAPI State

The project uses:

```xml
org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0
```

This is appropriate for the current Spring Boot 3 generation but needs explicit timing review for Spring Boot 4/Spring Framework 7 compatibility. OpenAPI documentation routes and UI behavior should be treated as public surface unless reviewers decide otherwise.

## Test Framework State

Current test stack includes:

- JUnit Jupiter `5.10.2` from Spring Boot test starter.
- Mockito `5.11.0`.
- AssertJ `3.25.3`.
- Hamcrest `2.2`.
- JSONassert `1.5.1`.
- Spring Test `6.1.8`.
- S3Mock `3.12.0`.
- Testcontainers JUnit Jupiter declared at `1.19.4`, with `testcontainers:1.19.8` in the tree.
- Some JUnit 4 transitive presence from Testcontainers.

Current local tests did not execute because compilation failed first. Docker-dependent tests need an available Docker daemon or CI.

## Maven Plugin State

Before Stage 2:

- Compiler plugin used separate `<source>` and `<target>` values from `${java.version}`.
- JaCoCo was pinned to `0.7.6.201602180812`, which is too old for Java 21 bytecode.
- Surefire had duplicate `argLine` elements, so one value could overwrite the other.
- Surefire had duplicate `systemPropertyVariables` blocks.
- Failsafe used obsolete `forkMode`.

After Stage 2 on this branch:

- Maven Wrapper pins Maven `3.9.15`.
- Compiler plugin uses `<release>${java.version}</release>`.
- JaCoCo uses `${jacoco.version}` with `0.8.14`.
- Surefire keeps both JaCoCo agent injection and `java.io.tmpdir` through a combined `argLine`.
- Surefire preserves all intended system properties in one block.
- Surefire and Failsafe use `forkCount` and `reuseForks`.
- Spring Boot Maven Plugin still repackages the executable jar.
- Sonar plugin remains `3.0.1`; not changed in this branch.

## CI, Docker, And Deployment Assumptions

Observed CI/Docker files:

- `.github/workflows/source.yml`: sets up Java `21` using `actions/setup-java@v2` with distribution `adopt`, then runs `mvn --batch-mode test`.
- `.github/workflows/main.yml`, `.github/workflows/integration.yml`, and `.github/workflows/testall.yml`: build Docker test container and run `cd docker && ./testall`.
- `.github/workflows/docker.yml`: Docker/script workflow on `ubuntu-20.04`.
- `.travis.yml`: legacy Travis file that runs Docker setup and `bash ./testall`.
- `docker/build-test/Dockerfile`: uses `maven:3.9.9-eclipse-temurin-21`.
- `docker/testall`, `docker/run.sh`, `scripts/testall`, and related scripts assume Docker is available.
- `appspec.dev.yml` and `appspec.test.yml` indicate deployment automation assumptions outside this branch.

Follow-up: later migration stages should update CI to prefer `./mvnw`, confirm Java 21, decide whether Travis remains supported, and validate Docker images against the final Boot 4 runtime.

## High-Risk Follow-Up Areas

| Area | Current state | Boot 4 risk | Required follow-up |
| --- | --- | --- | --- |
| REST controllers | Spring MVC 6.1 on Boot 3.3 | Path matching, media conversion, exception semantics | Contract tests before Boot 4. |
| Security | Spring Security 6.3 | Spring Security 7 matcher and filter changes | Preserve `/cache/**` and bearer token behavior. |
| Servlet/Tomcat | Tomcat 10.1, direct Servlet 6.1 API | Tomcat 11 / Servlet 6.1 behavior | Validate headers, streaming, range/download behavior. |
| Jackson | Mixed Jackson 2.17.2/2.17.1 | Jackson 3 package/artifact changes | Baseline JSON contract tests and bridge decision. |
| AWS/S3 | Aggregate AWS SDK v2 plus AWS v1 in tests | Dependency size, HTTP clients, S3 behavior | Reviewer approval before replacing aggregate SDK. |
| JJWT | `jjwt:0.9.1` | API split and JSON/JAXB implications | Dedicated security-token compatibility tests. |
| JAXB/XML | Mixed legacy `javax` JAXB and Jakarta JAXB transitive artifacts | Namespace incompatibility | Remove/replace legacy runtime artifacts later. |
| Build model | Duplicate `commons-lang3` | Maven 4/build reproducibility | Later focused dependency cleanup. |
| Docker/CI | Maven direct in some workflows; Docker image Maven 3.9.9 | Reproducibility drift | Prefer wrapper and Java 21 across workflows. |

## No Business Logic Change Statement

This inventory branch does not alter controllers, services, storage implementations, security configuration, request/response models, or file-distribution logic. All observed risks are documented for later staged branches.
