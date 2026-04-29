# PI-4 Microservices Platform

This repository contains a containerized microservices platform with automated quality checks, Docker image publication, and Kubernetes deployment support.

The project is organized around a small service ecosystem:

- `payment`
- `milestone`
- `microservice_user`
- `eureka`
- `api-gateway`
- `web`

The implementation was completed with a DevOps-oriented workflow:

- unit testing on business services
- JaCoCo coverage reporting
- SonarCloud static analysis
- GitHub Actions CI
- Docker Hub image publication
- Kubernetes deployment on Docker Desktop

## Architecture

The current runtime topology is:

- `web`: frontend entry point
- `api-gateway`: routing layer
- `eureka`: service discovery
- `microservice-user`: user management
- `payment`: payment workflow
- `milestone`: milestone and contract workflow
- `mysql`: shared database service for the local academic deployment

## Repository Structure

```text
.
├── .github/workflows/              # GitHub Actions CI pipeline
├── api-gateway/                    # Gateway service
├── eureka/                         # Eureka discovery server
├── k8s/                            # Kubernetes manifests
├── microservice_user/              # User microservice
├── milestone/                      # Milestone microservice
├── payment/                        # Payment microservice
├── web/                            # Frontend
├── docker-compose.yml              # Local container orchestration
└── RUN_CLIENT.md                   # Simple client run guide
```

## DevOps Workflow

The project pipeline is defined in:

- [.github/workflows/payment-milestone-ci.yml](.github/workflows/payment-milestone-ci.yml)

### CI stages

1. `build`
   - builds `payment` and `milestone`
2. `test`
   - runs unit tests for `payment` and `milestone`
3. `sonarcloud`
   - runs JaCoCo-based coverage analysis
   - publishes quality analysis to SonarCloud
4. `docker-image`
   - builds and pushes Docker images to Docker Hub

### Published Docker images

The pipeline publishes the following images:

- `mehditoumi/pi-payment`
- `mehditoumi/pi-milestone`
- `mehditoumi/pi-eureka`
- `mehditoumi/pi-user`
- `mehditoumi/pi-gateway`
- `mehditoumi/pi-web`

## Quality and Testing

The quality workflow focuses on `payment` and `milestone`, which are the main business services targeted for testing and refactoring.

### Testing stack

- `JUnit 5`
- `Mockito`
- `JaCoCo`
- `SonarCloud`

### Coverage strategy

Coverage measurement is centered on business logic. Low-value framework and wiring classes are excluded from Sonar coverage metrics, such as:

- bootstrap classes
- configuration classes
- DTOs
- repository interfaces
- security wiring
- client / feign interfaces

This keeps the reported coverage focused on service behavior rather than infrastructure boilerplate.

## Local Execution

There are two supported ways to run the project.

### Option 1: Docker Compose

This is the fastest local execution path.

```powershell
docker compose up --build
```

Useful commands:

```powershell
docker compose ps
docker compose logs --tail=100
docker compose down
```

### Option 2: Kubernetes on Docker Desktop

This is the academic deployment path used for Kubernetes validation.

#### Start Kubernetes

- enable Kubernetes in Docker Desktop
- use the `docker-desktop` context

```powershell
kubectl config use-context docker-desktop
kubectl get nodes
kubectl apply -k .\k8s
kubectl get pods -n pi
kubectl get svc -n pi
```

To stop the deployment:

```powershell
kubectl delete -k .\k8s
```

## Kubernetes Notes

The manifests are located in:

- [k8s](k8s)

The deployment is intentionally simple and local-first:

- one namespace: `pi`
- one MySQL deployment for local validation
- backend services configured with Docker profile
- startup sequencing handled with `initContainers`
- readiness and startup probes for the main business services

This approach keeps the deployment lightweight and appropriate for academic demonstration while still showing real orchestration behavior.

## Client Run Guide

For a simplified usage guide, see:

- [RUN_CLIENT.md](RUN_CLIENT.md)

This file is designed for someone who only needs to clone the project and run it with minimal setup decisions.

## Main Deliverables

This repository demonstrates:

- microservice packaging with Docker
- CI automation with GitHub Actions
- code-quality analysis with SonarCloud
- test coverage reporting with JaCoCo
- Docker Hub artifact publication
- Kubernetes deployment using Docker Desktop

## Recommended Demo Flow

For presentation or evaluation, the clearest sequence is:

1. show the GitHub Actions pipeline
2. show SonarCloud analysis and coverage
3. show Docker Hub published images
4. show Kubernetes pods and services
5. open the exposed application entry points

Suggested commands:

```powershell
kubectl get pods -n pi
kubectl get svc -n pi
kubectl logs deployment/api-gateway -n pi --tail=50
kubectl logs deployment/microservice-user -n pi --tail=50
```

## Future Improvements

Potential next steps:

- Prometheus and Grafana monitoring
- registry version pinning instead of `latest`
- persistent volumes for MySQL
- deployment automation from CI to Kubernetes
- broader test coverage across the remaining services

## Authoring Note

The current repository state prioritizes:

- fast reproducible local execution
- visible CI/CD evidence
- measurable test coverage improvements
- a simple but defensible Kubernetes deployment path // test 

That makes it suitable for technical demonstration, academic evaluation, and incremental extension.
