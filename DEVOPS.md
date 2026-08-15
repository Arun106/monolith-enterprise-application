# Snowman Enterprise CI/CD and Kubernetes Design

## Outcome

This repository now has a cloud-neutral delivery system for the legacy Snowman
Java monolith:

```text
pull request
  -> Java 8 compile and 58 unit tests
  -> application and Liquibase image builds
  -> Compose and Kubernetes schema validation

master or v* tag
  -> repeat tests
  -> publish immutable application and migration images to GHCR
  -> publish a vulnerability report to GitHub code scanning

approved deployment
  -> select an immutable sha-* image
  -> apply the chosen Kustomize overlay
  -> run Liquibase as a one-time Kubernetes Job
  -> verify the application rollout or request rollback
```

Production deployment is approval-driven. Every accepted change continuously
produces a deployable release; a reviewer promotes that exact release through the
`Deploy to Kubernetes` workflow.

## Application architecture

Snowman is an employee-management backend built as a hexagonal monolith:

- Java 7-compatible source, built and run with a maintained Java 8 distribution;
- Spring Framework 4.3 and embedded Jetty on port 8090;
- REST endpoints;
- MySQL through JDBC and Hibernate;
- ActiveMQ through JMS;
- Liquibase database changesets;
- Maven Shade producing `target/Snowman.jar`.

MySQL and ActiveMQ are dependencies, not parts of the application image. Production
should normally use separately operated or managed services.

## Build corrections

The original build was unsafe for CI/CD:

1. Spring, Jetty, and Jackson used open-ended Maven ranges, so the same source
   could resolve differently over time.
2. Liquibase ran during `process-resources`, meaning an ordinary build attempted
   to change a database.
3. Several unit tests were incomplete or contradicted their application behavior.
4. Database and broker endpoints were fixed in a classpath properties file.
5. `/health` returned HTTP 200 even when the database was down.
6. Raw JDBC ignored runtime configuration, and Hibernate relied on
   case-insensitive table names and sequence generation not present in MySQL.

The implementation pins those ranges, separates migration from packaging, repairs
the 58-test baseline, supports environment configuration, aligns the legacy ORM
with the Liquibase schema, and returns HTTP 503 when database readiness is down.

Migration is now an explicit action:

```bash
mvn liquibase:update
```

Kubernetes uses a dedicated migration image and Job instead.

## Version baseline

| Component | Version |
|---|---:|
| Java build/runtime | Eclipse Temurin 8u492 |
| Maven container | 3.9.13 |
| Source/target bytecode | Java 7 |
| Spring Framework | 4.3.30.RELEASE |
| Jetty | 9.4.57.v20241219 |
| Jackson Databind | 2.15.4 |
| MySQL Connector/J | 8.0.33 |
| MySQL local/dev service | 8.0.46 |
| ActiveMQ local/dev service | 5.19.2 |
| Liquibase migration image | 4.33.0 |
| Kubernetes validation target | 1.36 |
| Kubeconform | 0.7.0 |

Java 8 is a compatibility bridge, not the long-term target. A separate application
modernization project should move to a supported Java LTS and Spring generation.

## Repository layout

```text
.github/workflows/
  ci.yml                    tests, images, and manifest validation
  release.yml               GHCR publishing and vulnerability reporting
  deploy.yml                approved Kubernetes promotion

Dockerfile                  non-root application image
Dockerfile.migration        Liquibase changeset image
compose.yaml                local full stack

k8s/
  base/                     portable application resources
  jobs/                     one-time migration Job
  overlays/dev/             demo MySQL, ActiveMQ, and demo credentials
  overlays/production/      ingress, policy, and external endpoints
```

## Local usage

### Maven

Use Java 8:

```bash
mvn --batch-mode --no-transfer-progress clean verify
java -jar target/Snowman.jar
```

The running application requires reachable MySQL and ActiveMQ services.

### Docker Compose

```bash
cp .env.example .env
docker compose up --build
curl --fail http://localhost:8090/health
```

Compose waits for MySQL, applies Liquibase migrations, then starts the application.

```bash
docker compose down
```

`docker compose down --volumes` also deletes local trial data.

## Runtime configuration

| Variable | Purpose |
|---|---|
| `PORT` | Jetty port, default 8090 |
| `SNOWMAN_JDBC_DRIVER` | JDBC driver |
| `SNOWMAN_JDBC_URL` | MySQL URL |
| `SNOWMAN_JDBC_USERNAME` | Database user |
| `SNOWMAN_JDBC_PASSWORD` | Database password |
| `SNOWMAN_JMS_BROKER_URL` | ActiveMQ OpenWire URL |
| `SNOWMAN_HIBERNATE_DIALECT` | Optional dialect override |
| `SNOWMAN_HIBERNATE_DDL_AUTO` | Set to `validate` after migration |

Explicit Java `-D` properties remain higher priority than environment variables.

## Kubernetes design

The base contains:

- a restricted `snowman` Namespace;
- a ServiceAccount without an automatically mounted API token;
- ConfigMap and Secret references;
- a two-replica zero-downtime rolling Deployment;
- startup, liveness, and readiness probes;
- non-root execution, read-only filesystem, dropped capabilities, and seccomp;
- resource requests/limits and temporary storage limits;
- a ClusterIP Service, PodDisruptionBudget, and HorizontalPodAutoscaler;
- topology spreading across nodes.

Liveness checks only the Jetty listener. Readiness uses `/health`, which checks
MySQL. A database outage removes the pod from traffic without causing a restart
loop.

### Development overlay

The dev overlay includes demo MySQL and ActiveMQ workloads and demo credentials.
It is for a disposable cluster only:

```bash
kubectl apply -k k8s/overlays/dev
kubectl delete job snowman-database-migration \
  --namespace snowman --ignore-not-found
kubectl apply -k k8s/jobs
kubectl wait --namespace snowman \
  --for=condition=complete job/snowman-database-migration --timeout=5m
kubectl rollout status --namespace snowman deployment/snowman --timeout=5m
```

### Production overlay

Before production, replace:

- `mysql.example.internal`;
- `activemq.example.internal`;
- `snowman.example.com`;
- the ingress class, TLS secret, and ingress namespace when platform values differ.

Create `snowman-secrets` through the enterprise secret manager or external-secret
controller. A manual non-production example is:

```bash
kubectl create namespace snowman
kubectl create secret generic snowman-secrets \
  --namespace snowman \
  --from-literal=jdbc-username='<database-user>' \
  --from-literal=jdbc-password='<database-password>'
```

Do not commit a rendered Secret. A mature setup should use a separate,
more-privileged migration identity instead of giving schema permissions to the
application account.

## GitHub configuration

1. Enable Actions and package publishing.
2. Make both GHCR packages public, or configure a Kubernetes image pull secret.
3. Protect `master` and require all three CI jobs.
4. Create GitHub Environments named `dev` and `production`.
5. Add an environment-scoped `KUBE_CONFIG` secret containing kubeconfig text.
6. Configure required reviewers for production.
7. Use a namespace-scoped Kubernetes identity, not cluster-admin.
8. The obsolete Travis configuration has been removed; GitHub Actions is the
   repository's CI/CD system.

Publishing creates application and migration images with matching tags:

- `sha-<full-commit-sha>`;
- branch name;
- `latest` on the default branch;
- semantic version for a `v1.2.3` tag.

Always deploy the immutable `sha-...` tag.

## Jenkins CI/CD

`Jenkinsfile` provides the equivalent local delivery path on the Jenkins `PVM1`
agent. The pipeline:

1. checks out the requested branch;
2. builds and runs the unit tests with Java 8 in a pinned Maven container;
3. publishes JUnit and JaCoCo results;
4. analyzes `snowman-enterprise-monolith` with the configured
   `naukri-sonarqube` Jenkins installation and enforces its quality gate;
5. runs OWASP Dependency-Check and a blocking SonarQube quality gate;
6. validates Compose and all Kubernetes manifests;
7. builds immutable application and Liquibase images and scans the application
   image with Trivy;
8. publishes the versioned images to ACR and deploys them to AKS;
9. runs the Liquibase Job, verifies the AKS rollout, and executes an in-cluster
   health check.

Deployment runs only for the `master` branch. `DEPLOY_TARGET` selects `aks` or
`none`. AKS mode requires the ACR, AKS, tenant, subscription, and Jenkins credential
parameters declared by the pipeline. Failed AKS deployments request an application
rollback. Jenkins retains the executable JAR, coverage, OWASP and Trivy reports,
rendered manifests, test results, and fingerprints.

The checked-in defaults target subscription
`ffcf8f61-5974-487d-95d9-9adf380c6233`, resource group `Ar-RG`, AKS cluster
`Ar-AKS`, and the existing `chunkhoundacr20260802.azurecr.io` registry credential.
Change the ACR parameters at build time if a Snowman-specific registry is created.

Required Jenkins configuration:

- an online agent labeled `PVM1` with Docker, Maven, Java 17, Azure CLI, and
  kubectl (the pipeline installs a pinned kubectl client when absent);
- Docker access for the Jenkins agent user;
- a SonarQube server named `naukri-sonarqube`, including its token and a webhook
  to `<jenkins-url>/sonarqube-webhook/` for quality-gate completion;
- for fast OWASP Dependency-Check updates, a Jenkins secret-text credential
  containing an NVD API key. Set its ID in `NVD_API_CREDENTIALS_ID`; leaving the
  parameter empty uses the rate-limited public NVD feed.

SonarQube is hosted on PVM1 (`4.154.168.83`) and is exposed to processes on
that agent as `http://127.0.0.1:9000`. Because the scanner also runs on PVM1,
this private loopback address is preferred over exposing SonarQube publicly.
The scanner reads the dedicated masked Jenkins secret
`sonarqube-snowman-token`.

The quality-gate stage polls the analysis task from PVM1. This is intentional:
`127.0.0.1:9000` identifies PVM1's SonarQube only from the agent, while the same
address on the Jenkins controller identifies a different network namespace.

The `SECURITY_GATE_MODE` build parameter defaults to `report-only` because the
legacy Java 7 dependency baseline contains known vulnerabilities. OWASP and
Trivy reports are still generated and archived. Select `strict` to fail the
pipeline when OWASP finds CVSS 9+ dependencies or Trivy finds a fixable critical
container vulnerability.

The job enables the Jenkins GitHub push trigger. Register a GitHub webhook with
content type `application/json` and payload URL:

```text
https://<public-jenkins-host>/github-webhook/
```

`localhost:8080` is not reachable from GitHub. A development tunnel can provide
the public host temporarily; production should use a stable HTTPS Jenkins URL.

The application itself remains compiled and tested with Java 8. Java 17 on the
agent is used only to run the current Sonar scanner.

## Build-quality and test implementation

The Maven lifecycle now separates tests by purpose:

- Surefire runs `*Test` and `*UTest` unit tests and explicitly excludes `*ITest`;
- Failsafe runs `*ITest` during `integration-test` and checks the result during
  `verify`;
- JaCoCo combines the executions into `target/site/jacoco/jacoco.xml` and enforces
  a 40% line-coverage floor, just below the verified 43.3% baseline;
- Maven Enforcer requires Java 8 for the application lifecycle and Maven 3.6.3 or
  newer.

The initial additions cover client REST delegation and failures, cache clearing,
database-health success and failure paths, application port/resource resolution,
and a real JDBC health integration test using an isolated in-memory H2 database.
The H2 test validates the health-check contract without using production secrets;
the AKS migration Job remains the authoritative MySQL/Liquibase validation.

Run the same lifecycle used by CI with:

```bash
docker run --rm \
  --user "$(id -u):$(id -g)" \
  --env HOME=/tmp/jenkins-user \
  --env MAVEN_CONFIG=/tmp/jenkins-user/.m2 \
  --volume "$PWD:/workspace" \
  --volume "$HOME/.m2:/tmp/jenkins-user/.m2" \
  --workdir /workspace \
  maven:3.9.13-eclipse-temurin-8-noble \
  mvn --batch-mode --no-transfer-progress \
    -Dmaven.repo.local=/tmp/jenkins-user/.m2/repository \
    clean verify
```

Important generated evidence:

- `target/surefire-reports/` — unit-test results;
- `target/failsafe-reports/` — integration-test results;
- `target/site/jacoco/jacoco.xml` — SonarQube coverage input;
- `target/Snowman.jar` — executable application artifact.

Verified on the pinned Java 8 toolchain on 2026-08-14:

| Check | Result |
|---|---:|
| Surefire unit tests | 71 passed |
| Failsafe integration tests | 2 passed |
| Test failures/errors | 0 |
| JaCoCo line coverage | 428/989 lines (43.3%) |
| Coverage gate | 40% |
| Executable JAR | 55,674,675 bytes |

The implementation was validated by resolving the effective POM, running
`clean verify` in the pinned Java 8 Maven image, checking both XML report sets for
failures and errors, confirming the JaCoCo counters and coverage gate, verifying
`target/Snowman.jar`, running `git diff --check`, and validating `Jenkinsfile`
against the live Jenkins declarative linter.

## Deployment and rollback

1. Confirm CI passed.
2. Confirm both images were published.
3. Run `Deploy to Kubernetes`.
4. Choose `dev` or `production`.
5. enter the immutable SHA image tag.
6. Approve the environment gate.
7. Observe the migration and rollout.

The workflow prints migration logs on failure. It requests a Deployment rollback
when rollout verification fails.

Database schema cannot be automatically rolled back safely merely because an
application rollout fails. Use expand/migrate/contract changes:

1. add backward-compatible schema;
2. deploy compatible code;
3. migrate data;
4. remove old schema in a later release.

Useful diagnostics:

```bash
kubectl get pods --namespace snowman
kubectl logs deployment/snowman --namespace snowman
kubectl logs job/snowman-database-migration --namespace snowman
kubectl rollout history deployment/snowman --namespace snowman
kubectl rollout undo deployment/snowman --namespace snowman
```

## Security and known debt

- Pull requests have read-only permissions.
- Publishing alone receives `packages: write`.
- Production uses an environment approval and scoped kubeconfig.
- Containers run without root or Linux capabilities.
- Kubernetes API tokens are not mounted.
- Trivy publishes HIGH/CRITICAL findings to GitHub code scanning.

The scanner starts in visibility-only mode because the legacy dependency tree has
known debt. Establish an accepted baseline, then fail CI for new or policy-breaking
findings.

The Maven Shade build also reports duplicate Hibernate/JPA/dom4j classes. Delivery
automation cannot make unsupported libraries safe. Recommended next work is to
generate an SBOM, remove unused database drivers, align Hibernate/JPA, upgrade Java
and Spring, protect management endpoints, improve password handling, add MySQL and
ActiveMQ integration tests, and add metrics and structured logs.
