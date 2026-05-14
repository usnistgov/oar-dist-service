# Baseline Capture

This document records the Stage 0 baseline capture and Stage 2 validation for branch `feat/spring-boot-4-migration`.

## Baseline Identity

| Item | Value |
| --- | --- |
| Repository | `usnistgov/oar-dist-service` |
| Remote | `https://github.com/usnistgov/oar-dist-service.git` |
| Starting branch | `integration` |
| Starting commit | `d176ff48d4727c4b0eeec82abe5ebb147bf30467` |
| Commit summary | `Merge pull request #134 from usnistgov/fix/head-content-length-cloudflare` |
| Working branch | `feat/spring-boot-4-migration` |
| Local OS | Windows 11 |
| Shell | PowerShell with Git Bash available |

## Environment Observed

| Tool | Observed result | Notes |
| --- | --- | --- |
| Java | Eclipse Temurin OpenJDK `25.0.3` | Not the certified Java 21 baseline. This is a local toolchain issue. |
| Installed Adoptium JDKs | `jdk-17.0.18.8-hotspot`, `jdk-25.0.3.9-hotspot` | Java 21 was not present under `C:\Program Files\Eclipse Adoptium`. |
| Maven before wrapper | Apache Maven `3.9.8` | Uses Java `25.0.3`; emits Java 25 native-access warnings from Jansi. |
| Maven after wrapper | Apache Maven `3.9.15` | Added by this branch. |
| Docker CLI | Docker `26.1.4` | CLI exists, but daemon was not running during validation. |
| Git Bash | Available at `C:\Program Files\Git\usr\bin\bash.exe` | Shell startup prints `/c/Users/elmim/.bashrc: line 4: ng: command not found`; unrelated to this repo. |

## Java 21 Readiness Pass

On 2026-05-14, the branch was checked again before opening Stage 3 work. The active Java runtime was still Eclipse Temurin `25.0.3`, and `JAVA_HOME` pointed at:

```text
C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot
```

The PATH check found:

```text
C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot\bin\java.exe
C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot\bin\java.exe
```

The checked local install locations did not contain Java 21. Because Java 21 was not available, the Java 21 validation commands were not certified locally. To validate this branch against the migration baseline, install or select a Java 21 JDK and rerun:

```bash
java -version
./mvnw -version
./mvnw -B clean test
./mvnw -B -DskipTests package
./mvnw -B jacoco:report
./mvnw -B -DskipTests dependency:tree
```

The wrapper clean test was rerun under the unsupported Java 25 runtime only to refresh the blocker. It failed in the same compile phase with the same `EmailInfo` and `UserInfoWrapper` constructor symptoms. This is not accepted as Java 21 validation evidence.

The source-update GitHub Actions workflow and local build helper scripts were aligned to prefer Java 21 plus the Maven Wrapper so CI can provide the authoritative Java 21 signal.

## Branch Setup Commands

| Command | Result | Notes |
| --- | --- | --- |
| `git status --short --branch` | Passed | Initial status showed previous untracked migration spec before it was moved into this docs directory. |
| `git remote -v` | Passed | Confirmed `origin` fetch/push URL. |
| `git fetch --all --prune` | Failed in sandbox, passed with escalation | Initial failure was `.git/FETCH_HEAD` permission under sandbox. Escalated fetch succeeded. |
| `git checkout integration` | Failed in sandbox, passed with escalation | Initial failure involved `.git/index.lock` permission under sandbox. Escalated checkout succeeded. |
| `git pull --ff-only` | Failed in sandbox, passed with escalation | Initial failure was `.git/FETCH_HEAD` permission under sandbox. Escalated pull reported `Already up to date`. |
| `git checkout -b codex/boot4-migration-baseline-tooling` | Failed in sandbox, passed with escalation | Initial failure was branch ref creation permission under sandbox. Escalated branch creation succeeded. |

The sandbox-related Git failures are environmental and were not repository failures.

## Stage 0 Baseline Commands

| Command | Result | Classification | Follow-up |
| --- | --- | --- | --- |
| `java -version` | Passed | Toolchain issue found | Switch validation to Java 21. Local active runtime is Java `25.0.3`. |
| `mvn -version` | Passed | Toolchain issue found | Maven `3.9.8` used Java `25.0.3`; wrapper added later for Maven reproducibility. |
| `mvn -DskipTests dependency:tree` | Failed in sandbox, passed with escalation | Sandbox network limitation, then baseline success | Use escalated network or populated Maven cache for dependency inspection. |
| `mvn -DskipTests help:effective-pom -Doutput=target/effective-pom.xml` | Failed first due PowerShell parsing of `.xml`; passed when output property was quoted | Local shell quoting issue | Use `"-Doutput=target/effective-pom.xml"` in PowerShell. The generated target file was later removed by `clean`. |
| `mvn test` | Failed in sandbox, then failed at compile with network allowed | Pre-existing baseline/toolchain blocker | Re-run with Java 21. Do not change business code in this branch. |
| `cd docker && ./testall` | Failed | Local Docker daemon unavailable | Re-run when Docker Desktop/daemon is running or in CI. |

## Baseline Compile Failure

The pre-edit `mvn test` run reached `maven-compiler-plugin:compile` and failed before tests ran. The relevant errors were:

- `src/main/java/gov/nist/oar/distrib/service/rpa/EmailHelper.java`: constructor `EmailInfo(String,String,String,String)` not found; only no-argument constructor seen by compiler.
- `src/main/java/gov/nist/oar/distrib/service/rpa/EmailInfoProvider.java`: same `EmailInfo` constructor failure.
- `src/main/java/gov/nist/oar/distrib/service/rpa/HTMLCleaner.java`: constructor `UserInfoWrapper()` not found; compiler sees required `(UserInfo,String)`.

The same failure pattern appears after Stage 2 validation. Because it was observed before the POM/tooling edits, it is classified as pre-existing or local-toolchain-related. The active Java `25.0.3` runtime is the leading suspect because this project declares Java 21 and Lombok-managed model constructors are involved. The next validation should run under Java 21 before any application-code fix is considered.

The compile step also emitted warnings that matter for later migration work:

- Unknown enum constant `javax.annotation.meta.When.MAYBE`.
- Deprecated Java wrapper constructors such as `new Integer(...)`, `new Long(...)`, and `new Boolean(...)`.
- Deprecated `java.net.URL` constructors.
- Deprecated `finalize()`.
- Several unchecked casts.

These warnings were not addressed in this branch.

## Docker Baseline Attempt

Command executed through Git Bash on Windows:

```bash
cd docker && ./testall
```

Result: failed before tests because the Docker daemon was unavailable:

```text
run.sh: Docker image build-test not found; building now...
Docker daemon error: open //./pipe/docker_engine: The system cannot find the file specified.
```

This is an environmental blocker, not a repository regression. The command also printed the unrelated Git Bash startup warning:

```text
/c/Users/elmim/.bashrc: line 4: ng: command not found
```

The generated `docker/dockbuild.log` from the failed Docker attempt was removed because it was a local generated artifact from this run.

## Stage 2 Validation Commands

| Command | Result | Classification | Notes |
| --- | --- | --- | --- |
| `./mvnw -version` through Git Bash | Passed | Stage 2 success | Maven `3.9.15`; Java `25.0.3`. |
| `.\mvnw.cmd -version` | Initially failed, then passed after wrapper bootstrap fix | Stage 2 tooling fix | Generated wrapper script indexed a null PowerShell `.Target`; fixed in `mvnw.cmd`. |
| `.\mvnw.cmd -B clean test` | Failed in sandbox, then failed at compile with network allowed | Pre-existing/toolchain blocker | Same constructor errors as baseline `mvn test`. |
| `.\mvnw.cmd -B -DskipTests package` | Failed in sandbox, then failed at compile with network allowed | Pre-existing/toolchain blocker | Same constructor errors as baseline `mvn test`; `-DskipTests` still compiles main sources. |
| `.\mvnw.cmd -B jacoco:report` | Failed in sandbox, then passed with network allowed | Stage 2 success | JaCoCo `0.8.14` goal ran and skipped report because no execution data file existed after compile failure. |
| `.\mvnw.cmd -B -DskipTests dependency:tree` | Failed in sandbox, then passed with network allowed | Stage 2 success | Dependency tree generated under wrapper Maven `3.9.15`. |

## Failure Register

| Failure | Exact command | Likely cause | Pre-existing or introduced | Follow-up required |
| --- | --- | --- | --- | --- |
| Maven dependency resolution blocked | Multiple `mvn` and `mvnw` commands inside sandbox | Network restrictions to Maven Central | Environmental | Use escalated network, CI, Docker, or pre-populated cache. |
| Java 25 active instead of Java 21 | `java -version`, `mvn -version`, `./mvnw -version` | Local workstation default JDK | Environmental | Install/select Java 21 before certification. |
| Compile constructor errors in RPA model use | `mvn test`; `.\mvnw.cmd -B clean test`; `.\mvnw.cmd -B -DskipTests package` | Likely Java 25 plus Lombok compatibility or annotation processing behavior | Pre-existing, seen before Stage 2 edits | Re-run under Java 21 before code changes. |
| Docker test runner unavailable | `cd docker && ./testall` | Docker daemon not running | Environmental | Re-run with Docker daemon available or in CI. |
| Duplicate `commons-lang3` POM declaration warning | All Maven model builds | POM has `commons-lang3` declared twice with versions `3.12.0` and `3.8.1` | Pre-existing | Decide whether to remove duplicate in a later focused dependency-cleanup branch. |
| Generated `mvnw.cmd` failed on null `.Target` | `.\mvnw.cmd -version` | Wrapper script assumed `.m2` `Target` is indexable | Introduced by generated wrapper, fixed in this branch | Reviewer should accept or replace with a newer official wrapper if available. |
| Java 21 validation unavailable | `where java`, `Get-Command java`, `$env:JAVA_HOME`, local JDK directory checks | Java 21 is not installed/selected locally | Environmental | Install/select Java 21 or run Docker/CI validation. |
| Docker daemon unavailable | `docker version`, `docker info` | Docker client exists but server pipe is unavailable | Environmental | Start Docker daemon or run Docker workflow in CI. |

## Behavior Capture Status

No application behavior was changed in this branch. REST behavior, file-distribution behavior, controller/service separation, local filesystem storage, S3 storage, security behavior, and error semantics remain unmodified.

Because Java 21 is unavailable locally and Docker is not running, this branch does not yet have a passing local Java 21 behavioral baseline. It is ready for PR review as a documented Stage 0-2 baseline/tooling branch, but the required next validation is:

```bash
JAVA_HOME=<Java 21 home> ./mvnw -B clean test
cd docker && ./testall
```
