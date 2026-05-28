# Harness Lab Notes

## About the App

A minimal **Spring Boot 4.x** web app (`GET /`) that serves a styled HTML page displaying a random motivational quote (picked from 100 in `quotes.json`) alongside an in-memory visit counter and the deployed image tag. There's also a `GET /health` endpoint returning `{"status":"UP","version":"<tag>"}` - useful for liveness/readiness probes.

The controller (`HelloController.java`) loads quotes at startup via `@PostConstruct`, uses an `AtomicInteger` for the counter (resets on restart), and renders raw HTML via Java text blocks - no template engine.

The `IMAGE_TAG` environment variable is injected at deploy time so each page shows which Docker image version is running.

---

## Local Dev Setup

Uses **[Devbox](https://www.jetify.com/devbox)** for a reproducible, isolated environment (Nix-backed, nothing written to the system PATH).

```bash
devbox shell          # enter the environment
./mvnw spring-boot:run   # run the app
./mvnw test              # run all tests
pre-commit run --all-files  # run hooks manually
```

**Packages:** `jdk@17`, `maven`, `gradle`, `pre-commit`, `kubernetes-helm`, `kubectl`, `kubectx`

---

## Phase 1: K8s Cluster Setup

**Date:** 24 May 2026

**Minikube start command:**
```bash
minikube start --cpus=4 --memory=6144 --driver=docker
```

**kubectl get nodes output:**
```
NAME       STATUS   ROLES           AGE   VERSION
minikube   Ready    control-plane   15s   v1.35.1
```

**minikube status output:**
```
minikube
type: Control Plane
host: Running
kubelet: Running
apiserver: Running
kubeconfig: Configured
```

### Ingress Setup

Enabled minikube addons:
```bash
minikube addons enable ingress
minikube addons enable ingress-dns
```

**WSL2 note:** minikube IP (`192.168.49.2`) is not directly reachable from Windows. Run `minikube tunnel` in WSL to expose services:
```bash
minikube tunnel
```

Then in `C:\Windows\System32\drivers\etc\hosts` (Windows hosts file):
```
127.0.0.1 spring-app.test
```

App is then accessible at `http://spring-app.test` from Windows browser.

**Issues encountered:** None, surprisingly. I half-expected WSL2 driver issues but it came up clean on the first try. The tunnel setup for Windows access was the only non-obvious part.

---

## Phase 2: Harness Signup & Delegate Install

**Date:** 24 May 2026
**Harness account ID:** `77nMCFCRRVuhKDNfdQqWPA`
**Organization:** `default`
**Project:** `lab`

**Delegate name:** `minikube-delegate`
**Delegate namespace:** `harness-delegate-ng`

**kubectl get pods -n harness-delegate-ng output:**
```
NAME                                    READY   STATUS    RESTARTS   AGE
kubernetes-delegate-799dd45dc9-whxtx    1/1     Running   0          2m47s
```

**Issues encountered:** None. Applied the manifest, watched the pod come up, and the delegate showed as connected in the UI before I had even refreshed the page. Took about 2 minutes total.

---

## Phase 3: CI Pipeline (Java)

**Date:** 24 May 2026
**GitHub repo:** `https://github.com/pavan-karnati/spring-sample-app`
**Harness project:** Account `pavankalyan.in` → Org `default` → Project `lab`

**Connectors created:**
- GitHub connector name: `pavankarnatigh`
- Docker Hub connector name: configured via Harness Secrets (token-based)

**Pipelines in Project:**

- `spring-sample-app` - used to explore the Harness UI, browse prebuilt steps and get familiar with capabilities. Built the initial pipeline here, exported the YAML, then moved it to Git. The "Move to Git" feature was particularly useful for transitioning from inline UI editing to a proper Git workflow.

- `spring-sample-app-pr-build` ([`.harness/spring-sample-app-pr-build.yaml`](../.harness/spring-sample-app-pr-build.yaml)) - triggered on every PR open/reopen/sync via `trigger-pr-build.yaml`. Two stages:
  1. **Build** - runs tests with Harness Intelligence, packages the JAR, sanitizes the branch name into a Docker-safe tag (e.g. `feat-my-branch`), and pushes the image to Docker Hub (`kpkr7/springsampleapp`).
  2. **deploy-minikube** - Helm deploys to the `devminikube` environment (infrastructure: `minikube`, namespace: `dev`) using the branch-tagged image. Helm chart is fetched from the PR's source branch so infra changes are validated before merge.

- `pre-commit-checks` ([`.harness/pre-commit-pipeline.yaml`](../.harness/pre-commit-pipeline.yaml)) - triggered on every push to non-main branches via `trigger-push-precommit.yaml`. References the `precommitchecks` pipeline template (Version1). Runs `pre-commit run --all-files` to enforce hooks before code reaches review.

- `release` ([`.harness/release-pipeline.yaml`](../.harness/release-pipeline.yaml)) - triggered on merge to `main` via `trigger-main-release.yaml`. Two stages:
  1. **Semver and Release** - references the `semversvu` template (Version1) to compute the next semantic version, git-tag it, build the JAR, and push a versioned Docker image.
  2. **deploy-staging** - Helm deploys to the `stgminikube` environment (namespace: `stg`, ingress: `spring-app-stg.test`) using the semver tag from stage 1. Skipped automatically if the computed version is `SKIP` (no releasable changes). Rolls back on failure.

![Harness pipelines list showing all 4 pipelines](screenshots/Screenshot_11.png)

**Pipeline trigger:** GitHub webhook on push to `main` and on pull request creation

### Build Infrastructure - Harness Cloud


### First Successful CI Build

With Harness Cloud as the runner, the build passed end-to-end. All 10 tests ran and passed. Steps: Initialize → Clone codebase → Restore Cache From Harness → RunTestsWithIntelligence → Build → Save Cache to Harness.

- **Started:** 24/05/2026, 18:33:26
- **Duration:** 1m 56s
- **Tests:** Total: 10, Skipped: 0, Successful: 10, Failed: 0

![First successful CI build - Build #3, all 10 tests pass](screenshots/Screenshot_2.png)

### Second CI Run - Harness Intelligence Cache Hit

The second run showed Harness Test Intelligence in action: all 10 tests were skipped (unchanged since last run) and the build saved 33 seconds.

- **Started:** 24/05/2026, 18:47:01
- **Duration:** 1m 23s *(saved 33s with Harness Intelligence)*
- **Tests:** Total: 10, Skipped: 10, Successful: 0 (all cached)

![CI Build #4 - Harness Intelligence skips unchanged tests, saves 33s](screenshots/Screenshot_3.png)

Honestly, on a 10-test project the 33s saving looks like a party trick, but the underlying mechanism is the interesting part: Harness builds a call graph of which tests cover which code paths, so on a large monorepo running 2000+ tests most PRs would skip the vast majority. Cache Intelligence works the same way - it fingerprints the dependency tree and restores automatically. Both feel like the kind of thing you don't appreciate until your CI bill shows up.

### Prebuilt Steps Library

While building the pipeline I spent some time browsing the Harness step library and was genuinely impressed by how much is already there out of the box. Security scanning steps in particular stood out - Trivy, Aqua, Snyk, Grype - all available as first-class steps with no plugin wiring needed. I wanted to slot a Trivy image scan in right after the Docker push step so vulnerabilities get flagged before anything reaches the cluster, but didn't get to it within the lab timeframe. The fact that it would have been a drag-and-drop step rather than writing a custom shell script is the part worth calling out.

### GitHub OAuth Integration

The GitHub OAuth integration with Harness was a highlight. Once the connector was set up, Harness automatically registered webhook checks against the repo for each trigger - no manual configuration in GitHub needed. Every PR immediately showed the pipeline statuses (`precommitchecks-Precommit`, `springsampleappprbuild-Build`) as native GitHub checks, and the merge button stayed blocked until all passed. It felt seamless.

### PR Build with All Checks Green

Pull request #3 (`feat/improve-cicd-workflow`) showed all checks passing before merge:
- **precommitchecks-Precommit** - pre-commit hooks pipeline passed (Harness)
- **springsampleappprbuild-Build** - PR build pipeline passed (Harness)

![GitHub PR #3 with all 3 checks green and ready to merge](screenshots/Screenshot_5.png)

---

## Phase 4: Harness CD - Kubernetes Deploy with Helm

**Date:** 25-26 May 2026

The goal was to extend the CI pipelines with a CD stage to deploy the app to Kubernetes using Harness CD. I went with a Helm chart rather than raw manifests - it is a bit more setup upfront but managing two environments (dev/staging) without duplicating YAML was worth it. The `--set image.tag=` override pattern is simple and makes the promotion story obvious.

### Helm Chart Setup

I scaffolded the chart with `helm create` and then modified it for the Spring app:

```bash
helm create spring-sample-app
```

Main things I changed from the default scaffold (chart lives at [`charts/spring-sample-app/`](../charts/spring-sample-app/)):

- **Image:** `kpkr7/springsampleapp`, tag overridden at deploy time via `--set image.tag=<version>`
- **Service:** ClusterIP on port `8080` (matching the Spring app's embedded Tomcat)
- **Ingress:** nginx ingress enabled, default host `spring-app.test` for dev; staging overrides to `spring-app-stg.test` via Helm flag in the pipeline
- **Probes:** liveness and readiness both hitting `GET /` (the Spring app's main endpoint)
- **HPA:** enabled, 1-5 replicas, CPU target 80%
- **Resources:** requests 250m CPU / 256Mi RAM, limits 500m / 512Mi

### Services and Environments

Two Harness Services and two Environments were created to model a dev/staging promotion flow:

**Services** - both backed by the same Helm chart in the repo, different value overrides per environment:
- `spring-sample-dev` (Id: `springsampledev`) - dev service, Helm type
- `spring-sample-stg` (Id: `springsamplestg`) - staging service

![Harness CD Services - spring-sample-dev and spring-sample-stg](screenshots/cd1.png)

**Environments** - both typed Pre-Prod, both pointing at the local minikube cluster via the delegate:
- `dev-minikube` (Id: `devminikube`) - used by PR builds, namespace: `dev`
- `stg-minikube` (Id: `stgminikube`) - used by the release pipeline, namespace: `stg`

![Harness CD Environments - dev-minikube and stg-minikube](screenshots/cd2.png)

### CD Pipeline Failure - Missing Infrastructure Definition

The release pipeline (Build #6) ran successfully through the **Semver and Release** stage but failed at **deploy-staging - Infrastructure** step with:

> *Invalid request: Infrastructure definition minikube not found in environment stgminikube*

![CD release pipeline Build #6 failing - infrastructure definition not found](screenshots/Screenshot_10.png)

**Root cause:** I had created the environment but hadn't wired up an infrastructure definition inside it. Turns out Harness treats those as separate objects - the environment is just a label, and the infra definition is what tells it which cluster, connector, and namespace to actually use. Obvious in hindsight.

**Fix:** Created the infrastructure definition under Environments -> `stgminikube` -> Infrastructure Definitions, pointing at the Kubernetes connector with the delegate and namespace `stg`.

### Deployments Overview

After the infrastructure definition was in place, both pipelines ran end-to-end successfully. The deployments view shows 8 executions in the last 30 days across both pipelines and environments, all green except the one early failure.

![Harness CD Deployments - 8 executions, release and pr-build pipelines both green](screenshots/cd3.png)

### Successful PR Deploy to Dev (Helm)

The `spring-sample-app-pr-build` pipeline (Build #2) successfully deployed to `dev-minikube` via Helm on branch `feat/new-release-test`. Console output confirmed:

```
Release 'release-32f996' has been upgraded. Happy Helming!
STATUS: deployed  |  REVISION: 12  |  NAMESPACE: dev
```

The app was live at `http://spring-app.test` showing image tag `feat-new-release-test`.

![PR build pipeline - Helm deploy to dev green, app running at spring-app.test](screenshots/Screenshot_13.png)

### Successful Release Pipeline - Semver Tag v0.3.0

The `release` pipeline (Build #2) triggered on merge to `main` ran the Semver and Release stage in 1 minute (saving 35s with Harness Intelligence), computed `v0.3.0`, git-tagged it, and deployed to `stg-minikube` with the ingress overridden to `spring-app-stg.test`.

The app was live at `http://spring-app-stg.test` showing version `0.3.0`.

![Release pipeline - Semver stage green, v0.3.0 tagged, app running at spring-app-stg.test](screenshots/Screenshot_14.png)

### Product Usage

The Harness Product Usage dashboard showed all the avaialble features.

![Harness CD Product Usage - deployment frequency and feature adoption](screenshots/cd4.png)

---

## Phase 5 (Bonus): Pipeline Templates

**Date:** 25-26 May 2026

Two pipeline-level templates were created at **Project** scope to demonstrate reusability:

### Template 1: `precommit-checks` (Pipeline Template)

- **Template name:** `precommit-checks`
- **Identifier:** `precommitchecks`
- **Type:** Pipeline
- **Version:** `Version1` (STABLE)
- **Scope:** Project

The template wraps a full CI stage running `pre-commit run --all-files` inside a `python:3.12-slim` container. Runtime input: the codebase `build` branch expression.

![Harness precommit-checks pipeline template - Version1 STABLE with YAML](screenshots/Screenshot_7.png)

#### Refactoring the pre-commit pipeline to use the template

The `.harness/pre-commit-pipeline.yaml` was updated to reference the template instead of defining the CI stage inline. The diff (removed 30+ lines of inline stage YAML, replaced with a 5-line `template:` block):

![GitHub PR diff: pre-commit pipeline switched to template reference](screenshots/Screenshot_6.png)

### Template 2: `semversvu` (Release Pipeline Template)

The `release` pipeline was similarly refactored to reference a `semversvu` pipeline template (`Version1`). The part that caught me off guard: when I saved the pipeline, Harness automatically pushed the updated YAML to a branch and opened a PR to main - I didn't ask it to, it just did. That's the Git Experience in action. Once a pipeline is backed by a file in the repo, Harness treats config changes as code changes and handles the PR flow itself:
1. Updated the pipeline YAML
2. Pushed the change to branch `feat/improve-cicd-workflow`
3. Automatically opened a PR from that branch to `main`

![Harness saving release pipeline - updating, pushing branch, opening PR to main](screenshots/Screenshot_9.png)

### Pipeline Context Menu

The pipeline list's context menu gives one-click access to Run, View Pipeline, View Executions, Clone, Delete, and Edit Git Details - it shows how Harness treats pipelines as first-class Git objects.

![Harness pipeline context menu options](screenshots/Screenshot_4.png)

---

## Debugging Log

### Git credentials not available in custom container (Run step)

During development of a release step that ran `git fetch --tags` inside an `alpine/git` container, the command failed with:

> *fatal: could not read Username for 'https://github.com': No such device or address*

**Root cause:** Harness clones the repo during `cloneCodebase: true` and sets up credentials for its own clone step, but those credentials are not automatically propagated to a separate `Run` step using a different container image. The shared `/harness` volume is mounted, but the credential helper is not configured in the `alpine/git` image.

**Fix:** Harness injects `DRONE_NETRC_USERNAME` and `DRONE_NETRC_PASSWORD` environment variables into all CI steps. Configured the git remote URL explicitly in the script:
```bash
git remote set-url origin https://${DRONE_NETRC_USERNAME}:${DRONE_NETRC_PASSWORD}@github.com/pavan-karnati/spring-sample-app.git
git fetch --tags
```

![Copilot agent debugging the git credentials issue in CI container](screenshots/Screenshot_8.png)

### Audit trail: SYSTEM deleted PR Build Trigger

After restructuring the pipelines, the audit trail showed `SYSTEM` automatically deleted the `PR Build Trigger` (likely because the trigger's pipeline was renamed or its YAML changed incompatibly).

![Harness audit trail - SYSTEM deleted PR Build Trigger on 26 May 2026](screenshots/Screenshot_12.png)

**Fix:** Recreated the PR trigger manually on the `spring-sample-app-pr-build` pipeline targeting the `pavan-karnati/spring-sample-app` repository

---

## Issues & Resolutions Log

| Phase | Issue | Root Cause | Resolution |
|-------|-------|-----------|------------|
| 3 | CI Initialize failed - no eligible delegates | Pipeline set to use local k8s delegate that lacked the required selector | Switched build infrastructure to Harness Cloud (Linux/AMD64) |
| 3 | `git fetch --tags` failing in `alpine/git` Run step | Custom container doesn't inherit Harness git credential helper | Used `DRONE_NETRC_*` env vars to configure git remote URL before fetching |
| 4 | CD deploy-staging failed - infra definition not found | `minikube` infrastructure definition was never created inside `stgminikube` environment | Create infra definition in Harness UI under the correct environment |
| 5 | SYSTEM auto-deleted PR Build Trigger | Pipeline rename/YAML change broke the trigger reference | Recreated trigger manually on the renamed pipeline |

---

## What I'd Do Differently in Production

- **GitOps / drift detection:** Use Harness GitOps (Argo CD agent) so the cluster state is always reconciled from Git, rather than imperative `kubectl apply` via a pipeline step. Drift is detected and alerted automatically.
- **Namespace isolation:** I used `dev` and `stg` namespaces on the same minikube cluster, which is fine for a lab but in a real setup these would be separate clusters (or at least separate node pools) to avoid noisy-neighbour issues and to enforce real network policy.
- **Security scanning:** Add a Trivy image scan step right after the Docker push so vulnerabilities are caught before anything reaches the cluster. Harness has this as a prebuilt step - it would not have taken long to wire in, just ran out of time during the lab.
- **Secrets management:** Replace inline secret references with a secrets manager integration (HashiCorp Vault or AWS Secrets Manager) so secrets are never stored in Harness and rotation is centralised.
- **Multi-environment promotion gates:** Add manual approval steps between staging and production CD stages, with JIRA/ServiceNow ticket creation on approval request.
- **Canary / Blue-Green strategy:** Swap the Rolling strategy for Canary (10 % → 50 % → 100 %) so bad releases are caught with minimal blast radius. Harness makes this a one-field change in the deployment strategy block.
- **Test Intelligence baseline:** On a longer timeline, let Harness Test Intelligence build a richer call-graph baseline so the test-skipping savings grow beyond the 33 s seen in Build #4.
- **Org/Account-scoped templates:** Promote the `precommit-checks` and `semversvu` templates from Project scope to Org or Account scope so all teams can reuse them without duplication.
- **Notifications:** I didn't set up Slack notifications during the lab - the habit of checking the UI manually dies hard. In prod that's a must though. Pipeline Settings -> Notifications makes it a 2-minute job and means the on-call engineer finds out before they start polling.
