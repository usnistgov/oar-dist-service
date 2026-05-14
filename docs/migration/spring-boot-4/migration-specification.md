# Spring Boot 4 Migration Specification for oar-dist-service

Date prepared: 2026-05-14  
Repository inspected: `https://github.com/usnistgov/oar-dist-service`  
Local branch: `codex-boot4-migration-spec`  
Baseline branch inspected: `integration` at `d176ff48`

## Official Version Sources Checked

- Spring Boot 4.0.6 system requirements: <https://docs.spring.io/spring-boot/system-requirements.html>
- Spring Boot 4.0.6 dependency versions: <https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html>
- Spring Boot 4.0 migration guide: <https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide>
- Spring Boot JSON support: <https://docs.spring.io/spring-boot/reference/features/json.html>
- Spring Cloud release train compatibility: <https://spring.io/projects/spring-cloud/>
- Spring Security 7 migration guidance: <https://docs.spring.io/spring-security/reference/7.0/migration/index.html>
- Spring Framework 7 documentation: <https://docs.spring.io/spring-framework/reference/7.0/index.html>
- Apache Maven download and release status: <https://maven.apache.org/download.cgi>
- AWS SDK for Java 2.x Maven setup: <https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/setup-project-maven.html>
- AWS SDK for Java v2 repository and latest version: <https://github.com/aws/aws-sdk-java-v2>
- springdoc-openapi Boot 4 documentation: <https://springdoc.org/v4/>
- JJWT releases: <https://github.com/jwtk/jjwt/releases>

## 1. Executive Summary

### Current Baseline Discovered

This repository is not on a legacy non-Boot Spring Framework 4.x line. It is a single-module Maven Spring Boot application packaged as an executable jar:

- Parent: `org.springframework.boot:spring-boot-starter-parent:3.3.0`
- Managed Spring Framework: `6.1.8`
- Java compile target: `21`
- Maven wrapper: none
- Local Maven observed: `3.9.8`
- Local Java observed: Temurin `25.0.3`; Docker build image uses `maven:3.9.9-eclipse-temurin-21`
- Spring Cloud release train: `2023.0.2` / Leyton, compatible with Boot 3.3.x
- Embedded servlet container: Tomcat `10.1.24`, Servlet 6.0 generation
- Packaging: executable jar named `target/oar-dist-service.jar`
- Runtime context path: `/od`

The README confirms this is the OAR data distribution service. It provides a REST interface for NIST OAR Public Data Repository data products and hides the physical storage location, including local disk and AWS S3 storage. The code confirms local filesystem, S3, cache, bag storage, RPA, and download/packaging paths.

### Recommended Target Baseline

Final target: Spring Boot `4.0.6`, the current stable Spring Boot 4.x release found in official docs on 2026-05-14.

Recommended target stack:

- Spring Boot: `4.0.6`
- Spring Framework: Boot-managed `7.0.7`
- Spring Security: Boot-managed Spring Security 7.x
- Spring Cloud: `2025.1.0` / Oakwood for Boot 4.0.x
- Java: Java `21` as the certified production baseline for this service, with a separate reviewer decision for Java 25 certification
- Maven: Maven Wrapper pinned to Apache Maven `3.9.15`; do not move production builds to Maven 4 while Maven 4 is still RC/not production-safe per Apache Maven download page
- Embedded servlet container: Boot-managed Tomcat 11.0.x, Servlet 6.1
- JSON: target Boot 4 default Jackson 3; use Boot's deprecated Jackson 2 bridge only as a temporary migration aid if needed
- AWS SDK: move from aggregate `software.amazon.awssdk:aws-sdk-java:2.29.29` to AWS SDK v2 BOM `2.44.4` with service-specific modules, especially `s3`
- springdoc-openapi: `3.0.3`

The implementation should use the latest Boot 3.5.x line as a temporary compatibility checkpoint only. It is not the final target. The checkpoint is valuable because the official Boot 4 migration guide recommends upgrading to the latest 3.5.x before beginning the Boot 4 upgrade.

### Major Migration Risks

- Spring Boot 4 modularization and starter renames, especially `spring-boot-starter-web` to `spring-boot-starter-webmvc`.
- Jackson 3 migration. This code currently imports `com.fasterxml.jackson.*` widely and has custom JSON error handling.
- Spring Security 7 removals and request matcher behavior. The current cache security rule order must be verified because `/**.permitAll()` appears before `/cache/**.authenticated()`.
- Servlet 6.1/Tomcat 11 behavior around URL parsing, encoded characters, rejected requests, path matching, HEAD responses, and streaming downloads.
- Spring MVC path matching. The service relies on wildcard routes, ARK identifiers, `HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE`, semicolon preservation, and ordered controller mappings.
- Legacy `javax.activation`, `javax.inject`, and `javax.xml.bind` dependencies remain in the POM and code.
- Old `jjwt:0.9.1` is not suitable for modern Java/Jakarta and is likely tied to JAXB-era behavior.
- AWS SDK aggregate dependency creates a large dependency surface; replacing it must preserve all S3 behavior.
- Tests currently mix JUnit 5 and JUnit 4, and local baseline compile fails on Java 25 due Lombok-generated constructor issues.
- Logging and server configuration include old Boot property names such as `logging.file`, `logging.path`, `server.connection-timeout`, and `server.max-http-header-size`.
- JaCoCo `0.7.6.201602180812` is too old for Java 21/25 and current Maven/Surefire behavior.

### High-Level Implementation Strategy

1. Freeze and capture baseline behavior on a dedicated branch using the same Java 21 Docker path CI uses.
2. Stabilize the current Boot 3.3 build and test environment without functional changes.
3. Move to the latest Boot 3.5.x as a checkpoint, fix deprecations and test mechanics.
4. Upgrade to Boot 4.0.6, Framework 7, Cloud 2025.1.0, and Tomcat 11.
5. Resolve Jakarta, Jackson, Security, MVC path matching, and dependency graph gaps incrementally.
6. Validate REST behavior, local storage, S3 storage, streaming downloads, cache behavior, and RPA flows against captured baseline responses.

### Expected Compatibility Concerns

The public REST API must be treated as a contract. Controller mappings, status codes, headers, response bodies, cache headers, streaming behavior, ZIP packaging, directory listing behavior, and error semantics must be compared before and after migration. Any unavoidable framework-level change must be explicitly documented and tested.

### No Business-Logic Change Statement

This migration must not change business logic. Service-layer behavior, storage abstractions, file selection, bundle packaging, RPA request handling, cache semantics, and controller/service separation must be preserved. Any code change is allowed only to keep equivalent behavior on the new framework or to remove obsolete compatibility shims after tests prove equivalence.

## 2. Current-State Inventory

### Repository and Build Shape

- Single Maven project at repository root.
- No Maven modules.
- No Maven wrapper or `.mvn` directory.
- Artifact: `gov.nist.oar:oar-dist-service:1.0.0-SNAPSHOT`.
- Packaging: `jar`.
- Main final artifact: `target/oar-dist-service.jar`.
- Default remote branch: `origin/integration`.
- Relevant long-lived branches include `integration`, `main`, and `rel/2.1.X`; `rel/2.1.X` is much older Boot 2.1/Java 8 lineage and is not the active baseline for this migration.

### Version Inventory

| Area | Current state |
| --- | --- |
| Java target | `21` in `pom.xml` |
| Local Java observed | Temurin `25.0.3` |
| Docker Java | `maven:3.9.9-eclipse-temurin-21` |
| Maven wrapper | None |
| Local Maven observed | `3.9.8` |
| Spring Boot parent | `3.3.0` |
| Spring Framework | `6.1.8` managed by Boot 3.3.0 |
| Spring Cloud | `2023.0.2` BOM |
| Spring Cloud Config | `spring-cloud-starter-config`, resolved in 4.1.x line |
| Servlet container | Boot-managed Tomcat `10.1.24` |
| Servlet API | explicit `jakarta.servlet-api:6.1.0` provided, while Boot 3.3 normally manages Servlet 6.0 generation |
| Spring Security | `6.3.0` managed |
| Spring Data Commons | `3.3.0` managed |
| Jackson | Boot manages 2.17.1 but POM pins `jackson-databind:2.17.2`, creating a mixed Jackson 2 graph |
| Logback | `1.5.6` managed |
| SLF4J | `2.0.13` managed |
| Micrometer | `1.13.0` managed |
| JUnit Jupiter | `5.10.2` managed |
| Mockito | `5.11.0` managed |
| AWS SDK | aggregate `software.amazon.awssdk:aws-sdk-java:2.29.29` |
| springdoc-openapi | `2.6.0` |
| JJWT | `0.9.1` |
| JaCoCo | `0.7.6.201602180812` |
| Sonar Maven plugin | `3.0.1` |

### Parent POM and Dependency Management

The project inherits from `spring-boot-starter-parent:3.3.0` and imports `spring-cloud-dependencies:2023.0.2`. It also directly manages `io.github.classgraph:classgraph:4.8.154`.

The POM mixes Boot-managed and explicitly pinned dependencies. Several explicit pins should be removed after the Boot 4 parent owns them. Others must remain because Boot does not manage them.

### Direct Dependencies Likely Impacted

| Dependency | Current version | Migration impact |
| --- | ---: | --- |
| `spring-boot-starter-web` | Boot 3.3.0 | Replace with `spring-boot-starter-webmvc` or use classic starter temporarily. |
| `spring-boot-starter-test` | Boot 3.3.0 | Boot 4 starter model changes; prefer technology-specific test starters or temporary `spring-boot-starter-test-classic`. |
| `spring-boot-starter-security` | Boot 3.3.0 | Security 7 API/removal verification required. |
| `spring-boot-starter-actuator` | Boot 3.3.0 | Health probes and observability behavior must be checked. |
| `spring-cloud-starter-config` | Cloud 2023.0.2 | Must move to Cloud 2025.1.0 for Boot 4. |
| `software.amazon.awssdk:aws-sdk-java` | 2.29.29 | Replace aggregate with BOM plus `s3` and required HTTP client modules. |
| `org.apache.httpcomponents:httpclient` | 4.5.13 | Legacy Apache HttpClient 4; evaluate security and compatibility. |
| `org.json:json` | 20231013 | Boot does not manage; keep or update after tests. |
| `spring-data-commons` | Boot managed | Remove explicit direct dependency unless code needs it independently. |
| `commons-io` | 2.18.0 | Keep pinned unless Boot 4 manages suitable version. |
| `commons-lang3` | 3.12.0 and 3.8.1 | Duplicate declaration; effective version is 3.8.1. Remove duplicate and prefer Boot-managed/current version. |
| `javax.inject:javax.inject` | 1 | Remove if unused; otherwise replace with `jakarta.inject-api`. |
| `javax.activation:activation` | 1.1.1 | Replace with `jakarta.activation-api` and `jakarta.activation.MimetypesFileTypeMap`, or equivalent MIME strategy with tests. |
| `javax.xml.bind:jaxb-api` | 2.3.1 | Remove if unused; otherwise replace with Jakarta JAXB managed dependencies. |
| `org.glassfish.jaxb:jaxb-runtime` | 2.3.1 | Remove if unused; otherwise use Boot-managed Jakarta JAXB runtime. |
| `jakarta.validation-api` | 3.1.0 | Prefer `spring-boot-starter-validation`; remove direct API pin. |
| `hibernate-validator` | 8.0.1.Final | Prefer `spring-boot-starter-validation`, Boot-managed Hibernate Validator 9.x. |
| `jakarta.servlet-api` | 6.1.0 | Remove explicit provided dependency; let Boot/Tomcat own Servlet API. |
| `jackson-databind` | 2.17.2 | Migrate to Boot 4 Jackson 3 or use temporary Boot Jackson 2 bridge. |
| `jjwt` | 0.9.1 | Replace with modern split JJWT artifacts. |
| `springdoc-openapi-starter-webmvc-ui` | 2.6.0 | Upgrade to 3.0.3 for Boot 4. |
| `s3mock`, `s3mock-junit5` | 3.12.0 | Verify with AWS SDK v2 and Testcontainers changes. |
| `testcontainers:junit-jupiter` | 1.19.4 | Use Boot 4 managed Testcontainers 2.x or pin after S3Mock compatibility testing. |
| `lombok` | Boot-managed 1.18.32 | Upgrade with Boot parent or pin if Java 25 validation is required. |

### Maven Plugins and Build Lifecycle

- `maven-compiler-plugin` managed by Boot 3.3.0, configured with `source` and `target` set to `${java.version}` and `-Xlint`.
- `spring-boot-maven-plugin` repackages the jar.
- `maven-surefire-plugin` is managed by Boot, but configuration has duplicate `argLine` elements and duplicate `systemPropertyVariables` blocks. This likely overwrites JaCoCo and some system properties.
- `maven-failsafe-plugin` uses old `forkMode`, which should be replaced with supported Surefire/Failsafe 3.x configuration.
- `jacoco-maven-plugin` is pinned to `0.7.6.201602180812`, too old for Java 21/25.
- `sonar-maven-plugin` is pinned to `3.0.1`, old for current Sonar and Maven.
- `maven-javadoc-plugin` is configured with a custom stylesheet.

### Test Frameworks and Test Plugins

- Main test dependency is `spring-boot-starter-test`.
- Most tests use JUnit Jupiter.
- Some tests still import JUnit 4 APIs (`org.junit.Test`, `org.junit.Assert`, `SpringJUnit4ClassRunner`).
- Tests use `TestRestTemplate`, `MockMvc`, Mockito, S3Mock, and Testcontainers.
- Existing tests cover download endpoints, bundle planning, cache manager behavior, storage utilities, JWT/RPA helpers, controller error handling, and JSON models.
- Local `mvn test` on Java 25 failed at compilation before tests due Lombok-generated constructor issues in `EmailInfo` and `UserInfoWrapper`. This should be treated as a baseline/toolchain blocker, not a Boot 4 regression.

### Packaging, Docker, CI/CD, and Deployment Assumptions

- Executable jar with embedded Tomcat.
- README describes embedded Tomcat and `java -jar target/oar-dist-service.jar`.
- Docker build path uses `docker/build-test/Dockerfile` with Maven `3.9.9` and Eclipse Temurin `21`.
- `.travis.yml` still exists and builds/tests through Docker.
- GitHub Actions workflows exist:
  - `.github/workflows/source.yml` runs Maven tests on Java 21 using `actions/setup-java@v2`.
  - `.github/workflows/testall.yml`, `main.yml`, `integration.yml`, and `docker.yml` build Docker containers and run `docker/testall`.
- AWS CodeDeploy-style files `appspec.dev.yml` and `appspec.test.yml` copy `target/oar-dist-service.jar` to `/home/ubuntu/oar-docker/apps/dist-service/` and invoke environment scripts.
- `scripts/makedist` runs `mvn clean package -DskipTests`, generates dependency tree output, and copies the jar to `dist`.
- `scripts/run_service.sh` launches the jar with property overrides for local mode, data directory, port, base URL, and logging.

### Profiles and Runtime Configuration

No Maven profiles were found. Runtime behavior is driven by Spring config, environment properties, and command-line `-D` overrides.

Current `src/main/resources/application.yml`:

- `spring.application.name=oar-dist-service`
- `spring.config.import=optional:configserver:`
- `spring.cloud.config.uri=http://localhost:8087`
- `server.port=8083`
- `server.servlet.context-path=/od`
- `server.error.include-stacktrace=never`
- `server.connection-timeout=10s`
- `server.max-http-header-size=8192`
- Tomcat access log config under `/var/log/dist-service`
- Tomcat thread/connection settings
- `cloud.aws.region=us-east-1`
- legacy logging properties `logging.file` and `logging.path`
- springdoc Swagger UI at `/ds-api/swagger-ui.html`, OpenAPI docs at `/ds-api/v3/api-docs`

Current `src/test/resources/application.yml`:

- disables Spring Cloud Config
- uses random port
- sets `distrib.bagstore.mode=local`
- points local bagstore to test resources
- uses legacy `logging.path`

### Public REST Controllers and Endpoints

All endpoints are under the servlet context path `/od` unless deployed with a different context path.

| Controller | Base mapping | Public behavior to preserve |
| --- | --- | --- |
| `VersionController` | `/ds` | `GET /ds/`, `GET /ds`; version/info response and redirect/status behavior. |
| `DatasetAccessController` | `/ds` | Dataset AIP metadata and file distribution endpoints, including ARK routes, wildcard file paths, `GET`/`HEAD`, directory listing, download headers, and custom error handlers. |
| `AIPAccessController` | `/ds/_aip` | AIP bag download and info endpoints with `GET`/`HEAD`. |
| `DataBundleAccessController` | none class-level | `POST /ds/_bundle`, JSON request parsing, ZIP response, JSON error semantics. |
| `BundleDownloadPlanController` | none class-level | `POST /ds/_bundle_plan`, JSON plan response and validation errors. |
| `CacheManagementController` | `/cache` | Cache volume/object/queue/monitor operations. |
| `RPARequestHandlerController` | `/ds/rpa` | RPA request lifecycle, request acceptance, PATCH status, JWT validation, CORS. |
| `RPADataCachingController` | `/ds/rpa` | RPA cache operations and restricted download-set lookup. |

High-risk route features:

- `@RequestMapping` ordering is explicitly called out in `DatasetAccessController`.
- ARK paths use `/ark:/{naan:\d+}/{dsid}/**`.
- Non-ARK wildcard route uses `/{dsid:[^a][^r][^k][^:].*}/**`.
- HEAD routes must preserve headers and no body semantics.
- Semicolon content is preserved through `UrlPathHelper#setRemoveSemicolonContent(false)`.
- Code reads `HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE`.

### Service-Layer Components

Service/business code is already separated from controllers and must stay that way.

Important service components include:

- `DefaultPreservationBagService`
- `FromBagFileDownloadService`
- `NerdmDrivenFromBagFileDownloadService`
- `CacheEnabledFileDownloadService`
- `DefaultDataPackagingService`
- `NerdmDownloadService`
- `RPACachingService`
- `HttpURLConnectionRPARequestHandlerService`
- `JWTHelper`
- `RecaptchaHelper`
- `RecordResponseHandlerImpl`

### Storage Abstractions and Implementations

Storage-related abstractions and implementations are central migration risk areas:

- Core abstractions: `LongTermStorage`, `BagStorage`, `PDRBagStorageBase`
- Local filesystem: `FilesystemLongTermStorage`
- AWS S3: `AWSS3LongTermStorage`, `AWSS3ClientProvider`
- Remote/web storage: `WebLongTermStorage`
- Cache volumes: `FilesystemCacheVolume`, `AWSS3CacheVolume`, `NullCacheVolume`
- Cache managers/providers: `NISTCacheManagerConfig`, `CacheManagerProvider`, `BasicCache`, `ConfigurableCache`, `PDRCacheManager`, `PDRDatasetCacheManager`, `HeadBagCacheManager`, `PDRDatasetRestorer`, `RestrictedDatasetRestorer`
- SQLite inventory DB classes in cache-manager code

### Security, CORS, Filters, Servlet Config, and Exceptions

- `CacheSecurityConfig` defines a Spring Security `SecurityFilterChain`.
- It adds `BearerTokenAuthenticationFilter` before `AnonymousAuthenticationFilter`.
- It disables CSRF, form login, HTTP basic, and logout.
- It sets cache headers to no-store and no-transform.
- Current authorization ordering appears to call `requestMatchers("/**").permitAll()` before `requestMatchers("/cache/**").authenticated()`. This must be tested and not silently changed.
- `RejectedRequestFilter` catches `RequestRejectedException` and returns 400.
- `RPARequestHandlerController` has `@CrossOrigin`; global CORS mapping is also configured.
- Exception handling is mostly local to controllers and returns specific JSON error bodies/status codes.

### XML/JAXB, JSON/Jackson, Validation, Logging, Metrics

- Code already uses `jakarta.servlet` and `jakarta.validation`.
- Remaining legacy Java EE packages are mostly `javax.activation.MimetypesFileTypeMap` plus legacy POM dependencies for `javax.inject`, `javax.activation`, and `javax.xml.bind`.
- JDK `javax.crypto` imports are not part of the Jakarta migration.
- JSON code uses both `org.json` and Jackson 2 (`com.fasterxml.jackson.*`).
- Jackson is used for JSON models, controller parsing errors, RPA payloads, NERDm metadata, tests, and JSON serialization helpers.
- JAXB dependencies appear likely legacy or transitive support for old JJWT; no clear main-code JAXB usage was found.
- Validation uses direct Hibernate Validator/API dependencies instead of `spring-boot-starter-validation`.
- Logging uses SLF4J/Logback through Boot but config properties need Boot 4 review.
- Actuator is included, but no detailed `management.*` configuration was found.

## 3. Target-State Recommendation

### Target Versions

| Area | Recommended target | Justification |
| --- | --- | --- |
| Spring Boot | `4.0.6` | Latest stable Boot 4.x documented by Spring on 2026-05-14. |
| Spring Framework | Boot-managed `7.0.7` | Required by Boot 4.0.6 system requirements. |
| Java | `21` release/runtime baseline | Current repo and Docker already target Java 21. Boot 4 supports Java 17 through 26. Java 25 certification should be a separate reviewer decision because local Java 25 exposed Lombok/toolchain issues. |
| Maven | Wrapper pinned to `3.9.15` | Latest Maven 3 production release. Maven 4 is still preview/RC and not safe for production per Maven download page. |
| Spring Cloud | `2025.1.0` | Spring Cloud Oakwood supports Boot 4.0.x. |
| Servlet/Tomcat | Boot-managed Tomcat 11.0.x / Servlet 6.1 | Required by Boot 4. |
| Spring Security | Boot-managed Spring Security 7.x | Required by Boot 4 dependency management. |
| Jackson | Boot 4 default Jackson 3 (`tools.jackson.*`, currently 3.1.2 managed) | Boot 4 prefers and defaults to Jackson 3; Jackson 2 bridge is deprecated. |
| Jackson 2 bridge | Temporary only: `spring-boot-jackson2` if needed | Use only as a migration aid for parity testing, not final target. |
| AWS SDK | BOM `software.amazon.awssdk:bom:2.44.4`; dependencies `s3` plus required HTTP client | Latest AWS SDK v2 release found; AWS recommends BOM and service-specific modules instead of whole SDK. |
| springdoc-openapi | `3.0.3` | springdoc v3.x documents Spring Boot 4 support. |
| JJWT | `0.13.0` split artifacts | Replaces legacy `jjwt:0.9.1`; use `jjwt-api`, runtime `jjwt-impl`, and a JSON module such as `jjwt-gson` to avoid reintroducing Jackson 2 if final app is Jackson 3. |
| Testcontainers | Boot 4 managed Testcontainers 2.x, unless S3Mock requires a pin | Verify S3Mock compatibility before removing pins. |
| JaCoCo | current 0.8.x line | Required for Java 21/25 bytecode support; replace old 0.7.6. |
| Sonar plugin | current supported Sonar Maven plugin | Needed for modern Java/Maven/Sonar compatibility. |

### Dependency Management Rules

Remove explicit versions and let Spring Boot manage:

- Spring Framework artifacts
- Spring Data Commons, if still needed directly
- Spring Security artifacts
- Jackson artifacts unless using a deliberate temporary bridge
- Hibernate Validator and Jakarta Validation
- Jakarta Servlet API
- Tomcat artifacts
- Logback/SLF4J/Micrometer
- JUnit/Mockito/AssertJ/Hamcrest, except where explicit compatibility pins are required
- Lombok unless Java 25 certification requires a newer explicit pin than Boot manages

Keep explicit pins with justification:

- `org.json:json`: Boot does not own this project dependency.
- `software.amazon.awssdk:bom`: Boot does not manage AWS SDK.
- `org.springdoc:springdoc-openapi-starter-webmvc-ui`: Boot does not manage springdoc.
- `io.jsonwebtoken:*`: Boot does not manage JJWT.
- S3Mock: Boot does not manage Adobe S3Mock; pin after Boot 4 compatibility testing.
- SQLite JDBC: Boot does not manage the project-specific SQLite inventory implementation.
- Apache HttpClient 4, if retained temporarily for behavior parity; otherwise migrate separately.
- Commons Validator, JSoup, Commons Text, Commons IO if not Boot-managed or if scanner requires specific versions.

### Target-Version Decision

Spring Boot 4.0.6 is viable. The repository is already on Boot 3.3, Java 21, Jakarta Servlet imports, and Spring Security 6 lambda-style configuration. This is a Boot 3 to Boot 4 migration, not a migration to legacy Spring Framework 4.x.

Boot 3.5.x should be used only as a preparatory checkpoint to reduce risk. It should not become the final modernization target unless Boot 4 blockers are found that cannot be safely resolved in the production timeline. Any decision to stop at Boot 3.5.x must be reviewed and documented with the unresolved Boot 4 blocker.

## 4. Major Migration Concepts and Breaking Changes

### `javax.*` to `jakarta.*`

The project is mostly Jakarta-ready, but these remain:

- `javax.activation.MimetypesFileTypeMap` imports in web/service configuration and MIME handling.
- POM dependencies on `javax.inject`, `javax.activation`, and `javax.xml.bind`.
- `jjwt:0.9.1` commonly depends on JAXB-era behavior.

Required action:

- Replace `javax.activation` with `jakarta.activation` or a tested MIME alternative.
- Remove unused `javax.inject`.
- Remove JAXB 2 dependencies unless an active code path requires JAXB; if required, use Jakarta JAXB managed dependencies.
- Confirm dependency tree has no avoidable incompatible `javax.*` artifacts at runtime.

### Servlet API and Tomcat 11

Boot 4 uses Servlet 6.1 and Tomcat 11. Risks:

- URL/path parsing changes.
- Rejected request behavior.
- Encoded slash/semicolon/matrix parameter handling.
- HEAD response headers and content length.
- Streaming download flush/close behavior.

Required tests:

- GET/HEAD parity for all download endpoints.
- Semicolon path test.
- ARK and non-ARK wildcard path tests.
- Rejected request filter tests.
- Range/streaming/content-length/no-transform header tests.

### Spring Boot 3 to 4

Relevant Boot 4 changes:

- Official starter modularization and renamed starters.
- `spring-boot-starter-web` is deprecated in favor of `spring-boot-starter-webmvc`.
- Test starters are more technology-specific.
- Optional Maven dependencies are not included in executable jars unless configured.
- DevTools live reload disabled by default.
- Boot 4 is based on Jakarta EE 11 and Servlet 6.1.
- Boot 4 JSON defaults to Jackson 3; Jackson 2 support is deprecated and temporary.
- Liveness/readiness probes are enabled by default.

### Spring Framework 6/7 Implications

- Spring Framework 7 is required.
- Java baseline remains 17+, but the stack is designed for modern Java and Jakarta EE 11.
- Deprecated Framework 6 APIs may be removed.
- MVC path matching and handler mapping behavior must be tested where the service uses wildcards and ordered mappings.

### Spring Security 7

The project already uses lambda-style `SecurityFilterChain`, which is good. Still required:

- Check all deprecated methods removed in Security 7.
- Verify filter ordering with `BearerTokenAuthenticationFilter`.
- Verify `RequestRejectedException` handling.
- Verify authorization matcher ordering, especially `/cache/**`.
- If current behavior is that cache endpoints are effectively public because of matcher order, preserving behavior versus fixing security requires reviewer decision.

### Spring MVC / Controllers

High-risk behavior:

- Wildcard endpoint matching.
- ARK regex routes.
- `HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE`.
- Preserved semicolon content.
- Explicit ordering comments in `DatasetAccessController`.
- Local `@ExceptionHandler` response bodies.
- `HttpMessageNotReadableException` messages with Jackson error text.
- HEAD handlers.

No service logic should move into controllers while fixing MVC compatibility.

### Validation API

Move to `spring-boot-starter-validation` and Boot-managed Jakarta Validation/Hibernate Validator. Validate that `@Valid` request body behavior and error status/body semantics stay unchanged.

### JAXB/XML

Legacy JAXB 2 dependencies should be removed if unused. If any code path still needs XML binding, use Jakarta JAXB and Boot-managed versions.

### Jackson and Message Converters

The final Boot 4 target should use Jackson 3. This requires migration from:

- `com.fasterxml.jackson.annotation.*`
- `com.fasterxml.jackson.core.*`
- `com.fasterxml.jackson.databind.*`

to Boot 4/Jackson 3 packages and APIs where applicable.

If a temporary Jackson 2 bridge is used:

- Add `spring-boot-jackson2`.
- Use it only for intermediate parity.
- Track removal as a required task before final acceptance unless reviewers explicitly approve a temporary production exception.

Message converter tests must compare:

- JSON property names.
- Null/absent field behavior.
- Error response JSON.
- Bundle plan responses.
- RPA request/record payloads.
- NERDm metadata parsing.

### Actuator and Observability

Actuator is present. Boot 4 exposes liveness/readiness health groups by default. Required:

- Document intended actuator endpoints.
- Verify no unintended public exposure.
- Decide whether to disable probes or configure management endpoints explicitly.

### Configuration Property Binding

Review:

- `logging.file` and `logging.path` should become `logging.file.name` and `logging.file.path`.
- `server.connection-timeout` should be reviewed against Boot 4 properties.
- `server.max-http-header-size` should be reviewed against Boot 4/Tomcat properties.
- `cloud.aws.region` is custom/current app config; ensure it is not mistaken for Spring Cloud AWS.
- `@ConfigurationProperties("distrib.cachemgr")` and `@ConfigurationProperties("distrib.rpa")` binding must be tested.

### Maven Plugin and Surefire/Failsafe

Required:

- Add Maven Wrapper.
- Use Maven 3.9.15.
- Use `maven-compiler-plugin` with `<release>21</release>`.
- Fix duplicate Surefire `argLine` and duplicate `systemPropertyVariables`.
- Replace Failsafe `forkMode`.
- Upgrade JaCoCo.
- Ensure coverage XML reports are generated for Sonar.

### Java Runtime Compatibility

Java 21 is the production target. Java 25 compatibility is optional until approved. Local Java 25 already exposed Lombok/annotation-processing failures. Do not certify Java 25 by accident.

### Third-Party Library Risks

- JJWT 0.9.1 must be modernized.
- Springdoc 2.6.0 must move to 3.x.
- AWS SDK aggregate dependency should be replaced with service modules.
- Apache HttpClient 4 may trigger scanner findings.
- Commons Validator transitive dependencies include old Commons BeanUtils/Digester/Collections.
- S3Mock may pull AWS SDK v1 in test scope; keep test-only and scan separately.

## 5. Gap Analysis

| Area/component | Current state | Target requirement | Gap | Risk | Required code/build/config change | Test coverage required | Owner/reviewer notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Spring Boot parent | 3.3.0 | 4.0.6 | Major version gap | High | Upgrade parent after 3.5 checkpoint | Full build, API regression | Architecture reviewer |
| Spring Framework | 6.1.8 | 7.0.7 | Framework major upgrade | High | Let Boot manage | Compile and MVC tests | Platform reviewer |
| Java | POM 21; local 25; Docker 21 | Certified Java 21 | Toolchain drift | Medium | Add Maven Wrapper/toolchains, CI Java 21 | Build reproducibility | DevOps |
| Maven | No wrapper; local 3.9.8 | Wrapper 3.9.15 | Build not reproducible | Medium | Add wrapper/config | CI/local build | DevOps |
| Spring Cloud Config | 2023.0.2 | 2025.1.0 | Incompatible with Boot 4 | High | Update BOM | Config client startup tests | Platform reviewer |
| Servlet container | Tomcat 10.1 | Tomcat 11 | Servlet 6.1 behavior | High | Let Boot manage, remove explicit servlet API | GET/HEAD/path tests | API reviewer |
| Starter model | `spring-boot-starter-web` | `spring-boot-starter-webmvc` | Deprecated starter | Medium | Replace starter or temporary classic starter | Startup and controller tests | Platform reviewer |
| Jackson | Jackson 2 mixed versions | Jackson 3 final | Package/API migration | High | Migrate imports or temporary bridge | JSON contract tests | API reviewer |
| `javax.activation` | Used in MIME handling | Jakarta or alternative | Legacy namespace | Medium | Change imports/dependency or MIME strategy | MIME/content-type tests | API reviewer |
| JAXB | Legacy POM deps | Remove or Jakarta | Legacy runtime deps | Medium | Remove if unused | Dependency tree, JWT tests | Platform reviewer |
| JJWT | 0.9.1 | 0.13.0 split artifacts | API and JAXB gap | High | Update token builder/parser code | JWT validation and Salesforce JWT tests | Security reviewer |
| Security rules | Permissive matcher appears first | Verified intended rules | Possible existing bug | High | Preserve or fix only by decision | `/cache/**` auth tests | Security reviewer decision required |
| MVC path matching | Wildcards, ARK regex, semicolons | Same behavior on Framework 7 | Path engine risk | High | Minimal config/code fixes | Contract tests for all route shapes | API reviewer |
| HEAD/download headers | Explicit controller handling | Same headers/status/body | Container/framework risk | High | Adjust only if required | Header/content-length tests | API reviewer |
| AWS SDK | whole SDK 2.29.29 | BOM 2.44.4 + `s3` | Large dependency surface | Medium | Replace dependency shape | S3Mock/integration tests | Storage reviewer |
| Local storage | Filesystem abstractions | Same behavior | Low-medium | Avoid service rewrites | Local storage integration tests | Storage reviewer |
| Cache manager | Cache endpoints and volumes | Same behavior | High | Framework-only fixes | Cache API, filesystem/S3 cache tests | Storage reviewer |
| Validation | direct API/HV pins | Boot starter validation | Dependency cleanup | Medium | Use starter, remove pins | Invalid request body tests | API reviewer |
| Logging config | old property names | Boot 4 names | Config drift | Medium | Update YAML/scripts | Startup/log file tests | Ops reviewer |
| Actuator | included, minimal config | Explicit management policy | Possible endpoint exposure | Medium | Configure or document | Smoke/security tests | Ops/security |
| Tests | JUnit 5 plus JUnit 4 | JUnit Jupiter under Boot 4 | Vintage risk | Medium | Migrate JUnit 4 tests | Unit suite | Test owner |
| JaCoCo | 0.7.6 | current 0.8.x | Java 21/25 unsupported | Medium | Upgrade and fix argLine | Coverage report generation | QA |
| CI | GitHub Actions plus Travis/Docker | Java 21, Maven wrapper, Docker validated | Mixed old actions/images | Medium | Update actions, Docker image, scripts | CI pass | DevOps |
| Docs | README says Java 8/Maven 3.0 | Java 21/Maven 3.9.15 | Stale docs | Low | Update docs/release notes | Documentation review | Maintainers |

## 6. Migration Plan

Each stage must end with a short status report containing what changed, what passed, what failed, remaining risks, and required decisions. Each stage should be a focused commit or small PR unless reviewers approve combining documentation-only work.

### Stage 0: Baseline Branch and Behavior Capture

Goal: Freeze current behavior and establish a reproducible baseline.

Files likely affected: none initially; optional docs under migration branch.

Exact tasks:

- Create migration branch from `integration`.
- Install/use Java 21 locally or run Docker test path.
- Run current test suite and capture failures without changing code.
- Capture dependency tree and effective POM.
- Capture endpoint baseline responses for representative public routes.
- Record Docker daemon and S3Mock availability.

Expected failures:

- Local Java 25 may fail compilation due Lombok constructor generation.
- Docker tests may be unavailable if Docker daemon is not running.
- POM duplicate dependency warning for `commons-lang3`.

Validation commands:

```bash
git status --short --branch
mvn -version
java -version
mvn -DskipTests dependency:tree
mvn -DskipTests help:effective-pom -Doutput=target/effective-pom.xml
mvn test
cd docker && ./testall
```

Rollback strategy: delete the branch; no production change.

Acceptance criteria:

- Baseline branch exists.
- Current build/test status is documented.
- Known current failures are separated from migration failures.

### Stage 1: Dependency and Build Inventory

Goal: Produce a reviewed inventory of all build, runtime, and test dependencies.

Files likely affected: migration docs only.

Exact tasks:

- Document Maven dependencies and plugin versions.
- Document CI, Docker, CodeDeploy, and script assumptions.
- Identify Boot-managed dependencies to unpin.
- Identify pinned dependencies that must remain.
- Document transitive high-risk dependencies.

Expected failures: none; documentation stage.

Validation commands:

```bash
mvn -DskipTests dependency:tree -Dverbose
mvn -DskipTests dependency:tree -Dincludes=javax.*:*,jakarta.*:*
mvn -DskipTests dependency:tree -Dincludes=com.fasterxml.jackson.*:*,tools.jackson.*:*
mvn -DskipTests dependency:tree -Dincludes=software.amazon.awssdk:*
```

Rollback strategy: revert inventory documentation commit.

Acceptance criteria:

- Inventory is complete and reviewed.
- Reviewer decisions are listed before code changes.

### Stage 2: Maven, Java, and Plugin Modernization

Goal: Make the current baseline build reproducible on Java 21 without functional changes.

Files likely affected:

- `pom.xml`
- `.mvn/wrapper/*`
- `mvnw`, `mvnw.cmd`
- `.github/workflows/*.yml`
- `docker/build-test/Dockerfile`
- scripts that invoke Maven

Exact tasks:

- Add Maven Wrapper pinned to Maven 3.9.15.
- Configure compiler with `<release>21</release>`.
- Keep runtime certification on Java 21.
- Fix Surefire duplicate `argLine` and duplicate `systemPropertyVariables`.
- Update JaCoCo to current 0.8.x.
- Replace Failsafe `forkMode`.
- Update GitHub Actions Java setup action and distribution.
- Preserve Docker Java 21 or update to a newer Java 21 image.

Expected failures:

- Tests relying on overwritten system properties may start behaving differently.
- Coverage agent may expose tests that were previously not instrumented.

Validation commands:

```bash
./mvnw -version
./mvnw -B clean test
./mvnw -B -DskipTests package
./mvnw -B jacoco:report
```

Rollback strategy: revert wrapper/plugin commit.

Acceptance criteria:

- Java 21 build is reproducible through wrapper.
- Existing tests run or fail for documented pre-existing reasons only.

### Stage 3: Spring Boot/Spring Dependency Upgrade

Goal: Move dependency management to the target Spring stack.

Files likely affected:

- `pom.xml`
- `.github/workflows/*.yml`
- `docker/build-test/Dockerfile`

Exact tasks:

- Optional checkpoint: upgrade to latest Boot 3.5.x and Cloud 2025.0.x, run tests, fix deprecations.
- Upgrade parent to Boot 4.0.6.
- Update Spring Cloud BOM to 2025.1.0.
- Replace `spring-boot-starter-web` with `spring-boot-starter-webmvc` or temporarily use classic starters for compile stabilization.
- Review test starter choice.
- Remove explicit Spring/Jakarta/Hibernate/Jackson pins where Boot should manage them.

Expected failures:

- Missing auto-configuration due Boot 4 modularization.
- Compile errors from changed package names or removed APIs.
- Startup errors from Cloud Config version mismatch if BOM not updated.

Validation commands:

```bash
./mvnw -B -DskipTests compile
./mvnw -B -DskipTests dependency:tree
./mvnw -B test
```

Rollback strategy: revert the POM upgrade commit.

Acceptance criteria:

- Project compiles or all compile failures are categorized into following migration stages.
- Dependency tree shows Boot 4.0.6, Framework 7.0.7, and Cloud 2025.1.0.

### Stage 4: Jakarta Namespace Migration

Goal: Remove avoidable legacy Java EE artifacts.

Files likely affected:

- `pom.xml`
- MIME-related service/config classes
- tests for MIME/content type

Exact tasks:

- Replace `javax.activation.MimetypesFileTypeMap` with `jakarta.activation.MimetypesFileTypeMap` or tested alternative.
- Remove `javax.inject` if unused.
- Remove `javax.xml.bind:jaxb-api` and old JAXB runtime if unused.
- Use Jakarta JAXB only if a real code path needs it.

Expected failures:

- MIME type differences for file downloads.
- Runtime missing class if an untested JAXB path exists.

Validation commands:

```bash
./mvnw -B -DskipTests compile
./mvnw -B -DskipTests dependency:tree -Dincludes=javax.*:*
./mvnw -B test -Dtest='*Download*Test,*File*Test,*Mime*Test'
```

Rollback strategy: revert Jakarta cleanup commit.

Acceptance criteria:

- No avoidable legacy `javax.*` runtime artifacts remain.
- Download MIME behavior matches baseline.

### Stage 5: Impacted Library Upgrades

Goal: Upgrade third-party libraries that are incompatible or risky under Boot 4.

Files likely affected:

- `pom.xml`
- `JwtTokenValidator`
- `JWTHelper`
- JSON helper/model classes
- AWS S3 storage classes
- tests

Exact tasks:

- Replace AWS aggregate dependency with BOM `2.44.4` and `s3`.
- Add only required AWS HTTP client modules.
- Upgrade springdoc to 3.0.3.
- Upgrade JJWT to split 0.13.0 artifacts and adapt parser/builder APIs.
- Decide Jackson strategy:
  - final: migrate app code/tests to Jackson 3; or
  - temporary: add Boot Jackson 2 bridge and document removal date.
- Review Apache HttpClient 4 usage and security findings.
- Update Commons/JSoup/Validator dependencies if scanner requires it.

Expected failures:

- JJWT parser/builder compile errors.
- Jackson package import compile errors.
- AWS client dependency missing HTTP implementation.
- springdoc startup errors.

Validation commands:

```bash
./mvnw -B -DskipTests compile
./mvnw -B test -Dtest='*Jwt*Test,*RPA*Test,*S3*Test,*Storage*Test'
./mvnw -B -DskipTests dependency:tree -Dincludes=software.amazon.awssdk:*
```

Rollback strategy: revert each library-specific commit independently.

Acceptance criteria:

- JWT generation/validation is behaviorally equivalent.
- S3/local storage tests pass.
- OpenAPI paths still work or documented as intentionally disabled until fixed.

### Stage 6: Controller/Web-Layer Compatibility Fixes

Goal: Preserve public REST behavior on Spring MVC/Servlet 6.1.

Files likely affected:

- `DatasetAccessController`
- `AIPAccessController`
- `DataBundleAccessController`
- `BundleDownloadPlanController`
- `CacheManagementController`
- `RPA*Controller`
- `NISTDistribServiceConfig`
- tests

Exact tasks:

- Validate all endpoint mappings under `/od`.
- Preserve wildcard and ARK routing.
- Preserve semicolon content behavior.
- Preserve HEAD behavior.
- Preserve error status/body semantics.
- Adjust path matching configuration only as needed.
- Do not move service logic into controllers.

Expected failures:

- 404/405 from route matching changes.
- Different Jackson parse error messages.
- Different Content-Length or Content-Type.
- Semicolon paths stripped or rejected.

Validation commands:

```bash
./mvnw -B test -Dtest='*ControllerTest,*AccessControllerTest'
./mvnw -B test -Dtest='DatasetAccessControllerTest,AIPAccessControllerTest,DataBundleAccessControllerTest,BundleDownloadPlanControllerTest'
```

Rollback strategy: revert web-layer commit.

Acceptance criteria:

- Controller contract tests match baseline for URLs, status codes, headers, and response bodies.

### Stage 7: Service-Layer Compatibility Fixes Without Business-Logic Changes

Goal: Resolve framework/library mechanics while preserving service behavior.

Files likely affected:

- service classes under `src/main/java/gov/nist/oar/distrib/service`
- datapackage classes
- RPA service classes
- tests

Exact tasks:

- Adapt JSON/JWT/AWS APIs used by services.
- Keep algorithms and data-selection behavior unchanged.
- Add tests where framework mechanics changed serialization or exception behavior.

Expected failures:

- JSON field ordering/absence changes.
- JWT signature behavior changes.
- HTTP client differences.

Validation commands:

```bash
./mvnw -B test -Dtest='*ServiceTest,*PackagerTest,*PlannerTest,*HelperTest'
```

Rollback strategy: revert service compatibility commit.

Acceptance criteria:

- Existing service tests pass.
- Any changed test expectation is justified as framework mechanics, not business logic.

### Stage 8: Storage, Local Filesystem, and S3 Verification

Goal: Prove local and S3-backed distribution paths still work.

Files likely affected:

- storage/cache classes only if required
- test fixtures
- test configuration

Exact tasks:

- Run filesystem storage tests.
- Run S3Mock-backed tests.
- Verify cache volume behavior.
- Verify download streams from local and S3 paths.
- Verify no whole-SDK AWS dependency remains.

Expected failures:

- S3Mock/Testcontainers incompatibility with newer AWS SDK.
- Missing AWS HTTP client.
- Changed S3 exception classes/messages.

Validation commands:

```bash
./mvnw -B test -Dtest='*Storage*Test,*S3*Test,*Cache*Test,*Download*Test'
./mvnw -B -DskipTests dependency:tree -Dincludes=software.amazon.awssdk:*
```

Rollback strategy: revert AWS/storage-specific commit.

Acceptance criteria:

- Local and S3 paths are both validated.
- Storage abstraction boundaries remain intact.

### Stage 9: Configuration and Profile Verification

Goal: Confirm runtime configuration works across local, test, and deployed assumptions.

Files likely affected:

- `src/main/resources/application.yml`
- `src/test/resources/application.yml`
- `scripts/run_service.sh`
- Docker and deploy scripts
- README/release notes

Exact tasks:

- Update renamed Boot properties.
- Verify Spring Cloud Config optional import behavior.
- Verify `distrib.*` property binding.
- Verify Tomcat access logs.
- Verify context path `/od`.
- Verify logging path/file behavior.

Expected failures:

- Unknown/ignored properties.
- Log files not written where deployment expects.
- Config server import changes startup behavior.

Validation commands:

```bash
./mvnw -B test -Dtest='*Config*Test,*Properties*Test'
./mvnw -B spring-boot:run -Dspring-boot.run.profiles=local
java -jar target/oar-dist-service.jar --spring.profiles.active=local
```

Rollback strategy: revert config commit.

Acceptance criteria:

- Runtime config is documented and startup works without config server when optional.

### Stage 10: Test Modernization

Goal: Modernize tests without weakening coverage.

Files likely affected:

- `src/test/java/**`
- `pom.xml`

Exact tasks:

- Convert remaining JUnit 4 tests to JUnit Jupiter.
- Remove JUnit Vintage reliance unless explicitly needed.
- Update MockMvc/TestRestTemplate test support to Boot 4 starters.
- Add contract tests for known high-risk API behavior.
- Preserve or strengthen S3/local storage tests.

Expected failures:

- Lifecycle annotation differences.
- Changed assertion imports.
- Test ordering assumptions.

Validation commands:

```bash
./mvnw -B clean test
./mvnw -B failsafe:integration-test failsafe:verify
```

Rollback strategy: revert test modernization commit.

Acceptance criteria:

- Tests pass under Java 21.
- No test is removed to hide a migration failure.

### Stage 11: Security, Scanning, and Dependency Audit

Goal: Ensure migrated dependency graph has no unresolved high/critical findings.

Files likely affected:

- `pom.xml`
- CI workflows
- security docs

Exact tasks:

- Add or run a safe vulnerability scan tool approved by maintainers.
- Generate dependency tree and duplicate dependency analysis.
- Add Maven Enforcer rules for dependency convergence and Java/Maven version.
- Review legacy/transitive `javax.*`.
- Review cache endpoint authorization behavior.

Expected failures:

- Commons Validator transitive findings.
- Apache HttpClient 4 findings.
- AWS SDK v1 test-scope findings from S3Mock.
- Authorization matcher behavior requiring decision.

Validation commands:

```bash
./mvnw -B -DskipTests dependency:tree
./mvnw -B -DskipTests dependency:analyze
./mvnw -B -DskipTests enforcer:enforce
./mvnw -B -DskipTests org.owasp:dependency-check-maven:check
```

Rollback strategy: revert scanner/enforcer commit or document approved exceptions.

Acceptance criteria:

- No unresolved critical/high findings, or exceptions are documented and approved.

### Stage 12: Deployment Validation

Goal: Confirm migrated artifact runs in expected deployment model.

Files likely affected:

- Docker files
- GitHub Actions
- `.travis.yml` if retained
- `appspec.*.yml`
- scripts

Exact tasks:

- Build jar in Docker.
- Run Docker test path.
- Verify CodeDeploy artifact path still points to `target/oar-dist-service.jar`.
- Verify startup scripts use current logging/config properties.
- Confirm port/context path behavior.

Expected failures:

- Docker image Java/Maven mismatch.
- Script property names no longer work.
- Appspec path mismatch if final name changes.

Validation commands:

```bash
./mvnw -B clean package
cd docker && bash ./dockbuild.sh
cd docker && ./testall
java -jar target/oar-dist-service.jar --server.port=8083
```

Rollback strategy: revert deployment-script commit.

Acceptance criteria:

- Artifact builds and runs locally and through expected Docker/CI path.

### Stage 13: Documentation and Release Notes

Goal: Document operational impact, rollback, and reviewer-approved exceptions.

Files likely affected:

- README
- release notes
- migration spec/checklist
- deployment docs

Exact tasks:

- Update Java/Maven/Docker requirements.
- Document Spring Boot 4 runtime changes.
- Document config property changes.
- Document dependency/security exceptions.
- Document rollback plan.
- Document API parity verification results.

Expected failures: none.

Validation commands:

```bash
git diff --check
./mvnw -B clean verify
```

Rollback strategy: revert docs commit.

Acceptance criteria:

- Release notes clearly describe migration impact and rollback steps.

## 7. Testing Strategy

### Required Test Categories

- Existing unit tests must pass or be updated only for framework mechanics.
- Controller/API contract tests for all public endpoints.
- Service-layer tests for download, bundle planning, RPA, cache, and storage services.
- File-distribution behavior tests, including GET/HEAD, content length, content type, cache headers, streaming, and directory listing.
- Local filesystem storage tests.
- S3 integration tests using S3Mock or isolated mocks.
- Error-handling tests for 400, 401, 404, 500, and unreadable JSON.
- Configuration/profile tests for default, test, local, and config-server-disabled scenarios.
- Smoke tests for jar startup and `/od/ds/`.
- Regression tests comparing old and new responses.
- Performance-sensitive download/streaming tests for large files.
- Security and dependency vulnerability checks.

### Baseline Capture Commands

```bash
mvn -version
java -version
mvn -DskipTests dependency:tree
mvn -DskipTests help:effective-pom -Doutput=target/effective-pom.xml
mvn test
cd docker && ./testall
```

### Target Verification Commands

```bash
./mvnw -version
./mvnw -B clean test
./mvnw -B clean verify
./mvnw -B -DskipTests package
./mvnw -B -DskipTests dependency:tree
./mvnw -B -DskipTests dependency:tree -Dincludes=javax.*:*
./mvnw -B -DskipTests dependency:tree -Dincludes=com.fasterxml.jackson.*:*,tools.jackson.*:*
./mvnw -B -DskipTests dependency:tree -Dincludes=software.amazon.awssdk:*
./mvnw -B -DskipTests dependency:analyze
./mvnw -B -DskipTests enforcer:enforce
./mvnw -B jacoco:report
./mvnw -B -DskipTests org.owasp:dependency-check-maven:check
```

### API Regression Approach

- Start the old baseline and new migrated app on different ports with the same test data.
- Replay a curated request suite.
- Compare status, headers, response body, and file checksums.
- Include representative ARK and non-ARK URLs.
- Include semicolon paths.
- Include missing dataset/file cases.
- Include bundle plan and bundle ZIP requests.
- Include RPA JSON validation paths.

Suggested smoke probes:

```bash
curl -i http://localhost:8083/od/ds/
curl -I http://localhost:8083/od/ds/{dsid}/some/file
curl -i http://localhost:8083/od/ds/{dsid}/_aip
curl -i -X POST http://localhost:8083/od/ds/_bundle_plan -H 'Content-Type: application/json' --data @request.json
```

## 8. Code-Change Rules

- Do not change business logic unless required to preserve equivalent behavior on the new framework.
- Do not silently change API behavior.
- Do not remove tests to make the build pass.
- Do not replace working abstractions with unrelated new ones.
- Keep web/controller code separate from service/business code.
- Keep storage abstractions clear.
- Prefer Spring Boot-managed dependencies over manually pinned versions.
- Remove obsolete compatibility shims only after tests prove they are unnecessary.
- Use minimal, focused commits.
- Add comments only where migration behavior is non-obvious.
- Any behavioral change requires a named reviewer decision, a test, and release-note entry.

## 9. Deliverables

### Migration Specification Document

This document is the migration specification.

### Dependency Inventory

Dependency inventory is documented in Sections 2 and 3. A machine-generated dependency tree should be attached to the implementation PR as `target` output or CI artifact, not committed unless maintainers request it.

### Gap Analysis Table

See Section 5.

### Staged Implementation Checklist

See Section 6.

### Risk Register

| Risk | Probability | Impact | Mitigation |
| --- | --- | --- | --- |
| Jackson 3 migration changes JSON output | High | High | Contract tests; temporary Jackson 2 bridge only if needed. |
| MVC path matching changes routes | High | High | Endpoint regression suite for ARK, wildcards, semicolons, HEAD. |
| Security matcher behavior changes cache access | Medium | High | Explicit reviewer decision and tests for current vs intended behavior. |
| S3Mock/AWS SDK incompatibility | Medium | High | Isolate AWS SDK change; run S3Mock and storage tests early. |
| Java 25 local failures mask migration work | High | Medium | Certify Java 21 first; Java 25 separate decision. |
| Old Maven/Jacoco/Surefire config hides failures | Medium | Medium | Modernize build first and document any newly exposed failures. |
| Deployment scripts rely on old Boot properties | Medium | High | Test jar startup with production-like args. |
| Vulnerability scan flags old transitive libs | High | Medium | Upgrade/pin with tests or document approved exceptions. |
| Behavior of error messages changes | Medium | Medium | Compare JSON error contracts; approve unavoidable parser message changes. |
| Whole SDK removal misses needed AWS module | Medium | Medium | Compile and S3 tests; inspect imports. |

### Test Plan

See Section 7.

### Rollback Plan

- Keep each stage in a focused commit/PR.
- Tag or record the pre-migration branch SHA.
- For each stage, rollback is a normal git revert of that stage's commit(s).
- Deployment rollback is redeploying the last known Boot 3 artifact and restoring old runtime property names.
- Do not mix business changes with migration commits.
- If Boot 4 deployment fails after release, rollback artifact and config together because property names and dependency behavior may differ.

### Required Reviewer Decisions

1. Confirm Java 21 as the production target or require Java 25 certification.
2. Decide whether to use a temporary Jackson 2 bridge in production or require final Jackson 3 before release.
3. Decide whether current `/cache/**` security behavior is intended; if not, approve a security behavior change with release notes.
4. Approve replacing AWS aggregate SDK with service-specific `s3` modules.
5. Decide whether Apache HttpClient 4 may remain temporarily.
6. Decide how to handle legacy Travis CI.
7. Decide whether actuator liveness/readiness probes should remain enabled.
8. Approve vulnerability scanner exceptions, if any.
9. Decide whether springdoc/OpenAPI endpoints are required for the same release or can be fixed in a follow-up before production cutover.

### Assumptions and Unknowns

- `integration` is the correct active baseline because it is the remote HEAD.
- Java 21 is the intended production runtime because the POM and Docker build image already use Java 21.
- Docker daemon was unavailable locally during inspection, so Docker baseline tests must be rerun in CI or a Docker-capable environment.
- Local Java 25 baseline compile failure is assumed to be toolchain/Lombok related until verified on Java 21.
- No Maven profiles exist; environment behavior is driven by Spring properties and scripts.
- Some CodeDeploy scripts referenced by `appspec.*.yml` are external to this repository and must be validated by deployment owners.
- If a config server supplies additional `distrib.*` properties, those external configs must be included in the final verification.

### Proposed PR Breakdown

1. Inventory/specification only.
2. Maven wrapper, Java 21 toolchain, plugin cleanup, CI action update.
3. Boot 3.5.x checkpoint and deprecation cleanup.
4. Boot 4.0.6/Spring Cloud 2025.1.0 starter migration.
5. Jakarta cleanup and dependency unpinning.
6. Jackson/JJWT migration.
7. AWS SDK dependency narrowing and S3 verification.
8. MVC/controller contract fixes.
9. Test modernization and API regression harness.
10. Security/scanning/deployment docs and release notes.

## 10. Acceptance Criteria

The migration is complete only when:

- The application builds successfully with Java 21 and Maven Wrapper 3.9.15.
- All unit and integration tests pass.
- Public REST behavior is verified against baseline behavior.
- Local filesystem and S3-backed distribution paths are validated.
- Business logic is unchanged.
- Controller/service separation remains intact.
- Runtime configuration is documented.
- Dependency tree contains no avoidable legacy `javax.*` artifacts incompatible with the target.
- Security/dependency scans have no unresolved critical/high findings, or exceptions are documented and approved.
- The project can run locally and in the expected deployment environment.
- Release notes clearly explain migration impact, required runtime changes, and rollback steps.

