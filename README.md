# spring-sample-app

A Spring Boot 4 app that serves a random motivational quote with a visit counter and Docker image tag on every page refresh.

## Tech Stack

- Java 17, Spring Boot 4.0.6
- Maven (primary build tool)
- Docker / Docker Hub (`kpkr7/springsampleapp`)
- Helm (Kubernetes packaging)
- [Devbox](https://www.jetify.com/devbox) for local dev environment
- [Harness](https://harness.io) for CI/CD

## CI/CD Pipeline

This project uses a three-pipeline CI/CD workflow managed by Harness with Git Experience (pipelines stored in `.harness/`).

### Pipeline Overview

```
Push to feature branch
        │
        ▼
┌─────────────────────────┐
│   pre-commit-pipeline   │  Runs all pre-commit hooks (lint, format,
│                         │  secret scan, Helm lint, Conventional Commits)
└─────────────────────────┘

Pull Request opened/updated
        │
        ▼
┌─────────────────────────┐
│  PR build pipeline      │  Tests → Maven build → Docker push
│                         │  Image tag: sanitized branch name
│                         │  Deploys to: dev namespace (spring-app.test)
└─────────────────────────┘

Merge to main
        │
        ▼
┌─────────────────────────┐
│   release pipeline      │  svu computes next semver from conventional
│                         │  commits → git tag → Docker push (semver + latest)
│                         │  Deploys to: staging namespace (spring-app-stg.test)
└─────────────────────────┘
```

### Semver Versioning

Commit message types control the version bump (via [svu](https://github.com/caarlos0/svu)):

| Commit type | Bump | Example |
|---|---|---|
| `fix:` | PATCH | `0.0.1` → `0.0.2` |
| `feat:` | MINOR | `0.0.2` → `0.1.0` |
| `feat!:` / `BREAKING CHANGE` | MAJOR | `0.1.0` → `1.0.0` |
| `ci:`, `chore:`, `docs:` | No bump | Release skipped |

### Environments

| Environment | Trigger | Image tag | Host |
|---|---|---|---|
| dev | PR opened/updated | branch name | `spring-app.test` |
| staging | merge to main | semver (`0.1.0`) | `spring-app-stg.test` |

### Harness Files

```
.harness/
├── spring-sample-app-pr-build.yaml     # PR build + dev deploy pipeline
├── pre-commit-pipeline.yaml            # Pre-commit checks pipeline
├── release-pipeline.yaml               # Semver + staging deploy pipeline
├── trigger-pr-build.yaml               # Webhook trigger: PR events
├── trigger-push-precommit.yaml         # Webhook trigger: push (non-main)
├── trigger-main-release.yaml           # Webhook trigger: push to main
├── inputset-pr-build.yaml              # Input set for PR build trigger
├── inputset-precommit-push.yaml        # Input set for pre-commit trigger
└── orgs/default/projects/default_project/
    ├── services/                        # Harness service definitions
    ├── envs/                            # Harness environment definitions
    └── templates/semversvu/            # Reusable semver Stage template
```

## Getting Started

### Prerequisites

Install [devbox](https://www.jetify.com/devbox/docs/installing_devbox/) then run:

```bash
devbox shell
```

This gives you Java 17, Maven, and Gradle. No manual installation needed.

### Run the app

```bash
./mvnw spring-boot:run
```

Visit [http://localhost:8080](http://localhost:8080). Refresh to get a new quote.

### Run tests

```bash
# All tests
./mvnw test

# Single test class
./mvnw test -Dtest=HelloControllerTest

# Single test method
./mvnw test -Dtest=HelloControllerTest#counterIncrementsOnEachCall
```

## Docker

```bash
# Build the image (compiles the app inside Docker)
docker build -t spring-sample-app .

# Run
docker run -p 8080:8080 spring-sample-app

# With custom JVM options
docker run -p 8080:8080 -e JAVA_OPTS="-Xmx256m" spring-sample-app

# With a custom image tag displayed in the UI
docker run -p 8080:8080 -e IMAGE_TAG="my-tag" spring-sample-app
```

## Pre-commit Hooks

Install [pre-commit](https://pre-commit.com/) then:

```bash
pip install pre-commit commitizen
pre-commit install                           # runs on git commit
pre-commit install --hook-type commit-msg   # enforces conventional commits
pre-commit install --hook-type pre-push     # runs tests on git push
```

Hooks enforce:
- Trailing whitespace, merge conflict markers, large file detection
- Secret scanning (gitleaks)
- Java formatting (Google Java Style via Spotless) and Checkstyle
- Compilation
- **Conventional Commits** format (required for semver bumping)
- Helm chart linting

To auto-fix formatting:

```bash
./mvnw spotless:apply
```

## Project Structure

```
src/
├── main/
│   ├── java/com/example/springboot/
│   │   ├── Application.java        # Entry point
│   │   └── HelloController.java    # Serves quotes, visit counter, image tag
│   └── resources/
│       └── quotes.json             # 100 motivational quotes
└── test/
    └── java/com/example/springboot/
        ├── HelloControllerTest.java            # Unit tests
        └── HelloControllerIntegrationTest.java # Integration tests (MockMvc)
charts/
└── spring-sample-app/              # Helm chart for Kubernetes deployment
```

## Adding Quotes

Edit `src/main/resources/quotes.json`:

```json
{
  "quotes": [
    { "quote": "Your quote here.", "author": "Author Name" }
  ]
}
```

Changes take effect on next app restart.
