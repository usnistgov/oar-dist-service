# Spring Boot 4 Migration Workspace

This directory captures the Spring Boot 4 modernization work for `usnistgov/oar-dist-service`.

The source-of-truth migration specification for the full program is [migration-specification.md](migration-specification.md). This branch, `feat/spring-boot-4-migration`, intentionally implements only Stage 0, Stage 1, and Stage 2 from that specification:

- Stage 0: baseline branch creation and current behavior capture.
- Stage 1: dependency and build inventory.
- Stage 2: Maven, Java, and build-plugin modernization for the current Spring Boot 3.3.0 baseline.

This branch does not upgrade Spring Boot, Spring Framework, Spring Cloud, Jackson, JJWT, Spring Security, controllers, service logic, storage behavior, endpoint behavior, request/response formats, status codes, headers, or distribution semantics. Business behavior must remain unchanged.

## Current And Target Baselines

| Area | Current baseline | Final target from migration spec | This branch |
| --- | --- | --- | --- |
| Git baseline | `integration` at `d176ff48` | New staged migration branches | New branch from `integration` |
| Spring Boot | `3.3.0` | `4.0.6` | unchanged |
| Spring Framework | `6.1.8` managed by Boot 3.3.0 | `7.0.7` managed by Boot 4.0.6 | unchanged |
| Spring Cloud | `2023.0.2` | `2025.1.0` | unchanged |
| Java baseline | POM declares `21`; local shell is Java `25.0.3` | Java 21 production baseline | compiler release set to 21 |
| Maven | Local Maven `3.9.8`; Docker image Maven `3.9.9` | Maven Wrapper pinned to `3.9.15` | wrapper added and CI/scripts aligned |
| Packaging | executable jar | executable jar unless later deployment review changes it | unchanged |

## Documents In This Directory

- [migration-specification.md](migration-specification.md): full migration specification prepared before this branch.
- [baseline-capture.md](baseline-capture.md): environment, branch setup, baseline command results, failures, and Stage 2 validation results.
- [dependency-inventory.md](dependency-inventory.md): reviewed inventory of dependency, plugin, CI, Docker, and migration-risk areas.
- [command-log.md](command-log.md): chronological command log with outcomes.
- [open-decisions.md](open-decisions.md): reviewer decisions and unresolved migration questions.

## Branch Rules

- No business logic changes.
- No controller behavior changes.
- No storage behavior changes.
- No Spring Boot 4 upgrade in this branch.
- No Spring Cloud upgrade in this branch.
- No Jackson 3 migration in this branch.
- No JJWT migration in this branch.
- No test deletion or weakening.
- Every failed command is documented with result, likely cause, pre-existing/new classification, and follow-up.

## Stage 2 Build-Tooling Changes

This branch makes only reproducibility and build-tooling changes:

- Adds Maven Wrapper pinned to Apache Maven `3.9.15`.
- Keeps Java `21` as the certified build/runtime baseline and uses Maven Compiler Plugin `release` configuration.
- Updates JaCoCo from the legacy `0.7.6` line to `0.8.14` for Java 21 compatibility.
- Merges duplicate Surefire `argLine` configuration without dropping the JaCoCo agent property or the intended `java.io.tmpdir`.
- Merges duplicate Surefire `systemPropertyVariables` blocks without dropping `basedir`, `conf.path`, or `project.test.resourceDirectory`.
- Replaces obsolete Failsafe `forkMode` with modern fork configuration.
- Applies a minimal Windows Maven Wrapper bootstrap fix so `mvnw.cmd` works when the local `.m2` directory is not a symlink.
- Aligns the source-update GitHub Actions workflow with Temurin Java 21 and the Maven Wrapper.
- Aligns build helper scripts with the Maven Wrapper while preserving `MAVEN_CMD=...` override support.

## Current Validation Status

Wrapper and dependency inspection run under Maven `3.9.15`. Local Java 21 validation is still blocked because this workstation has Java `25.0.3` active and only Temurin 17 and 25 were found in the checked install paths. Compilation still fails under Java `25.0.3` with Lombok/model constructor symptoms that were also seen during the initial pre-edit baseline `mvn test`; Java 25 remains unsupported and unverified for this migration.

Docker validation was attempted through `docker/testall`, `docker version`, and `docker info`, but the local Docker daemon was not running.

The branch is ready for PR review as a Stage 0-2 baseline/tooling branch with environment blockers documented. Stage 3 / the Boot 3.5 checkpoint should begin only after reviewer acceptance and a Java 21 or Docker-based validation run.
