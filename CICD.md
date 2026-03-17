# CI/CD Manual

This document describes the CI/CD pipeline for OBP-Rabbit-Cats-Adapter — how to build locally,
how automated builds work on GitHub Actions, and how releases are published to JitPack.

---

## Table of Contents

1. [Overview](#overview)
2. [Repository layout](#repository-layout)
3. [Local build](#local-build)
4. [CI pipeline — GitHub Actions](#ci-pipeline--github-actions)
5. [Release pipeline — GitHub Actions](#release-pipeline--github-actions)
6. [JitPack publishing](#jitpack-publishing)
7. [Consuming a release (downstream projects)](#consuming-a-release-downstream-projects)
8. [Troubleshooting](#troubleshooting)

---

## Overview

```
Developer pushes code
        │
        ▼
GitHub Actions CI (.github/workflows/ci.yml)
  • Runs on every push to main/master and on every pull request
  • Compiles and packages the fat JAR
  • Uploads the JAR as a build artifact (7-day retention)
        │
        ▼  (when ready to release)
GitHub Actions Release (.github/workflows/release.yml)
  • Triggered manually from the GitHub Actions UI
  • Validates the version number format
  • Builds the fat JAR
  • Creates and pushes an annotated Git tag  (e.g. v1.0.1)
  • Pre-warms the JitPack build
  • Creates a GitHub Release with the JAR attached
        │
        ▼
JitPack (jitpack.io)
  • Detects the new Git tag
  • Builds the library using jitpack.yml (Java 11, mvn clean package)
  • Publishes the artifact as:
      com.github.OpenBankProject:OBP-Rabbit-Cats-Adapter:<tag>
```

---

## Repository layout

```
OBP-Rabbit-Cats-Adapter/
├── jitpack.yml                        # JitPack build instructions
├── pom.xml                            # Maven project descriptor
├── .github/
│   └── workflows/
│       ├── ci.yml                     # CI — runs on push / PR
│       └── release.yml                # Release — manual trigger
├── src/
│   ├── main/
│   │   ├── scala/                     # Scala source files
│   │   └── protobuf/                  # .proto files (gRPC)
│   └── test/
│       └── scala/                     # Test files (none yet)
└── target/
    └── obp-rabbit-cats-adapter.jar    # Fat JAR (generated, not committed)
```

---

## Local build

### Prerequisites

| Tool    | Required version | Check with          |
|---------|-----------------|---------------------|
| Java    | 11              | `java -version`     |
| Maven   | 3.8+            | `mvn -version`      |
| Docker  | any             | `docker --version`  |

### Start RabbitMQ (required for running the adapter)

```bash
./start_rabbitmq.sh
```

This starts a RabbitMQ container on ports `5672` (AMQP) and `15672` (Management UI).
Management UI: http://localhost:15672 (guest / guest)

### Build only

```bash
mvn clean package -DskipTests -Dscalatest.skip=true
```

Output: `target/obp-rabbit-cats-adapter.jar`

### Build and run

```bash
./build_and_run.sh
```

This script:
1. Creates `.env` from `.env.example` if missing
2. Validates Java version
3. Checks RabbitMQ connectivity, starts Docker container if needed
4. Checks Redis connectivity, starts Docker container if needed
5. Runs `mvn clean package`
6. Starts the adapter with `java -jar`

### Run only (JAR already built)

```bash
./run.sh
```

### Build flags reference

| Flag                    | Effect                                      |
|-------------------------|---------------------------------------------|
| `-DskipTests`           | Skips the Surefire (JUnit) test runner      |
| `-Dscalatest.skip=true` | Skips the ScalaTest Maven plugin            |
| `-DskipTests -Dscalatest.skip=true` | Skips all tests — use for packaging only |

> **Note:** The project has no test files yet. Both flags are used defensively so the build
> does not fail if tests are added without updating CI scripts.

---

## CI pipeline — GitHub Actions

**File:** `.github/workflows/ci.yml`

### When it runs

- Every push to `main` or `master`
- Every pull request targeting `main` or `master`

### What it does

```
Checkout → Java 11 (Temurin) → mvn clean package → Upload JAR artifact
```

| Step               | Detail                                                        |
|--------------------|---------------------------------------------------------------|
| Checkout           | Full clone of the repository at the triggering commit         |
| Java 11 (Temurin)  | Eclipse Temurin JDK 11; Maven dependency cache enabled        |
| Build & package    | `mvn clean package -DskipTests -Dscalatest.skip=true`         |
| Upload artifact    | JAR saved as `obp-rabbit-cats-adapter-jar`, retained 7 days   |

### Downloading a CI artifact

1. Go to **GitHub → Actions → CI → (select a run)**
2. Scroll to **Artifacts** at the bottom of the run summary
3. Click `obp-rabbit-cats-adapter-jar` to download the ZIP containing the JAR

### Adding tests to CI in the future

When test files are added under `src/test/scala/`, update `ci.yml` — replace the build step with:

```yaml
- name: Start RabbitMQ
  uses: namoshek/rabbitmq-github-action@v1
  with:
    ports: '5672:5672'

- name: Run tests
  run: mvn test
```

And remove `-DskipTests -Dscalatest.skip=true` from the build step.

---

## Release pipeline — GitHub Actions

**File:** `.github/workflows/release.yml`

### When it runs

Manually — triggered from the GitHub Actions UI by a developer.

### How to trigger a release

1. Go to **GitHub → Actions → Release**
2. Click **Run workflow** (top-right, next to the branch selector)
3. Enter the version in `X.Y.Z` format — for example: `1.0.1`
4. Click **Run workflow**

> Do **not** include the `v` prefix when typing the version — the workflow adds it automatically.
> `1.0.1` → creates tag `v1.0.1`.

### What it does

```
Validate version format
        ↓
mvn clean package (verify the build is green before tagging)
        ↓
git tag -a v<version> + git push origin v<version>
        ↓
curl https://jitpack.io/.../build.log  (pre-warm JitPack)
        ↓
Create GitHub Release (with JAR attached + Maven snippet in body)
```

| Step                  | Detail                                                                      |
|-----------------------|-----------------------------------------------------------------------------|
| Validate version      | Rejects input that does not match `X.Y.Z`; fails before any tag is created  |
| Build                 | Same Maven command as CI — ensures the exact commit that will be tagged builds cleanly |
| Create Git tag        | Annotated tag `v<version>` signed by `github-actions[bot]`                  |
| Push tag              | Pushes the tag to `origin`; this is what JitPack detects                    |
| Pre-warm JitPack      | Curls the JitPack build log URL to trigger an immediate build               |
| GitHub Release        | Creates a release entry with the fat JAR and a ready-to-use Maven snippet   |

### Required GitHub repository permission

The workflow uses `permissions: contents: write`. This is set in the workflow file itself —
no extra configuration needed in repository settings for public repos.
For private repos, verify that Actions have write access under:
**Settings → Actions → General → Workflow permissions → Read and write permissions**.

### Version numbering convention

This project uses [Semantic Versioning](https://semver.org/):

| Segment | When to increment                                         | Example               |
|---------|-----------------------------------------------------------|-----------------------|
| MAJOR   | Breaking change to the `LocalAdapter` interface           | `1.0.0` → `2.0.0`    |
| MINOR   | New feature, backwards-compatible                         | `1.0.0` → `1.1.0`    |
| PATCH   | Bug fix, no interface change                              | `1.0.0` → `1.0.1`    |

### What to do if the release workflow fails

| Step that failed    | Action                                                                          |
|---------------------|---------------------------------------------------------------------------------|
| Validate version    | Re-run with correct `X.Y.Z` format. No tag was created.                         |
| Build               | Fix the compilation error, push to main, then trigger the release again.        |
| Create Git tag      | Check if the tag already exists locally or remotely (`git tag -l`). Delete and retry. |
| Pre-warm JitPack    | Not critical — JitPack will build on the first consumer request. Check manually at https://jitpack.io/#OpenBankProject/OBP-Rabbit-Cats-Adapter |
| GitHub Release      | Re-run only the failed step via **Re-run failed jobs** in the Actions UI.        |

---

## JitPack publishing

**File:** `jitpack.yml`

```yaml
jdk:
  - openjdk11

install:
  - mvn clean package -DskipTests -Dscalatest.skip=true
```

### How JitPack works

JitPack is a package repository that builds directly from GitHub. It does **not** require
uploading to Maven Central or Sonatype. The flow is:

1. A Git tag is pushed to GitHub (done by the release workflow)
2. A downstream project references the artifact using JitPack coordinates
3. On the first `mvn install` in the downstream project, Maven requests the artifact from JitPack
4. JitPack clones the tagged commit, runs `jitpack.yml`, and caches the built JAR

The pre-warm step in the release workflow triggers step 3 immediately so downstream projects
do not incur a build wait on their first use.

### Maven coordinates on JitPack

| pom.xml field    | Value in OBP-Rabbit-Cats-Adapter | JitPack transforms to              |
|------------------|----------------------------------|------------------------------------|
| GitHub org/repo  | `OpenBankProject/OBP-Rabbit-Cats-Adapter` | —                           |
| `groupId`        | `com.tesobe`                     | `com.github.OpenBankProject`       |
| `artifactId`     | `obp-rabbit-cats-adapter`        | `OBP-Rabbit-Cats-Adapter`         |
| `version`        | `1.0.0-SNAPSHOT`                 | Git tag, e.g. `v1.0.1`            |

> JitPack ignores the `version` in `pom.xml` and uses the Git tag as the version.

### Monitoring JitPack builds

- Build status and logs: https://jitpack.io/#OpenBankProject/OBP-Rabbit-Cats-Adapter
- Direct build log for a specific tag: `https://jitpack.io/com/github/OpenBankProject/OBP-Rabbit-Cats-Adapter/<tag>/build.log`

---

## Consuming a release (downstream projects)

Add to your `pom.xml`:

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>com.github.OpenBankProject</groupId>
    <artifactId>OBP-Rabbit-Cats-Adapter</artifactId>
    <version>v1.0.1</version>   <!-- use the latest released tag -->
  </dependency>
</dependencies>
```

To update to a newer release, change only the `<version>` value to the new tag.

### Using a snapshot of main (not recommended for production)

JitPack supports building from a branch or commit hash:

```xml
<!-- Latest commit on main (changes on every build — use only in development) -->
<version>main-SNAPSHOT</version>

<!-- Specific commit (reproducible but not a proper release) -->
<version>abc1234</version>
```

---

## Troubleshooting

### `mvn clean package` fails locally with protobuf errors

The project generates gRPC stubs from `.proto` files. The `os-maven-plugin` detects the OS
to download the correct `protoc` binary. If it fails:

```bash
# Verify Maven can detect the OS
mvn validate -X | grep os.detected
```

On unsupported platforms, set the classifier manually:

```bash
mvn clean package -Dos.detected.classifier=linux-x86_64 -DskipTests -Dscalatest.skip=true
```

### GitHub Actions CI fails with `Cannot find symbol` or version errors

Ensure the Java version in the workflow matches the project requirement:
- `pom.xml`: `<java.version>11</java.version>`
- `ci.yml` and `release.yml`: `java-version: '11'`
- `jitpack.yml`: `jdk: - openjdk11`

All three must align.

### JitPack returns 404 for a tag

1. Confirm the tag exists on the remote: `git ls-remote --tags origin`
2. Check the JitPack build log: `https://jitpack.io/com/github/OpenBankProject/OBP-Rabbit-Cats-Adapter/<tag>/build.log`
3. Trigger a manual rebuild from the JitPack dashboard: https://jitpack.io/#OpenBankProject/OBP-Rabbit-Cats-Adapter

### Release workflow cannot push the tag

Error: `remote: Permission to ... denied`

Check that the repository is not using a personal access token with insufficient scope.
For public repositories, the default `GITHUB_TOKEN` with `contents: write` permission is sufficient.
