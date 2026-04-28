# Run Guide

This project can be started in two simple ways:

1. `Docker Compose` for the fastest local run
2. `Kubernetes` with Docker Desktop for the academic deployment demo

## 1. Prerequisites

Install:

- `Docker Desktop`
- `Git`
- `kubectl` only if you want the Kubernetes version

Recommended:

- In Docker Desktop, allocate enough RAM for the containers
- Make sure Docker Desktop is running before starting

## 2. Clone the project

```powershell
git clone <YOUR_REPOSITORY_URL>
cd mehdi
```

## 3. Fastest start: Docker Compose

This is the easiest way to run the full stack locally.

### Start

```powershell
docker compose up --build
```

### Start in background

```powershell
docker compose up --build -d
```

### Check running containers

```powershell
docker compose ps
```

### Stop

```powershell
docker compose down
```

### Stop and remove volumes

```powershell
docker compose down -v
```

## 4. Kubernetes start

Use this only if you want to demonstrate Kubernetes deployment.

### Step 1: Enable Kubernetes in Docker Desktop

- Open `Docker Desktop`
- Go to `Settings`
- Open `Kubernetes`
- Enable Kubernetes
- Wait until the cluster is ready

### Step 2: Verify cluster

```powershell
kubectl config use-context docker-desktop
kubectl get nodes
```

You should see one node in `Ready` state.

### Step 3: Deploy the project

```powershell
kubectl apply -k .\k8s
```

### Step 4: Check pods

```powershell
kubectl get pods -n pi
```

### Step 5: Check services

```powershell
kubectl get svc -n pi
```

### Step 6: Restart the application deployments if needed

```powershell
kubectl rollout restart deployment microservice-user milestone payment api-gateway web -n pi
```

### Step 7: Watch pod startup live

```powershell
kubectl get pods -n pi -w
```

### Delete the Kubernetes deployment

```powershell
kubectl delete -k .\k8s
```

## 5. URLs

### Docker Compose

- Web: `http://localhost:4200`
- Eureka: `http://localhost:8761`
- Gateway: `http://localhost:8089`

### Kubernetes

Check service IPs:

```powershell
kubectl get svc -n pi
```

Usually:

- Web: `http://localhost`
- Gateway: `http://localhost:8089`

If Docker Desktop exposes another external IP, use the `EXTERNAL-IP` shown by `kubectl get svc -n pi`.

## 6. Useful commands

### Docker Compose logs

```powershell
docker compose logs --tail=100
```

### Kubernetes logs

```powershell
kubectl logs deployment/api-gateway -n pi --tail=100
kubectl logs deployment/microservice-user -n pi --tail=100
kubectl logs deployment/milestone -n pi --tail=100
kubectl logs deployment/payment -n pi --tail=100
```

### Describe a pod

```powershell
kubectl describe pod <POD_NAME> -n pi
```

## 7. Notes

- On first Kubernetes startup, some services may restart once if MySQL is not ready yet.
- This is handled by Kubernetes and is normal in this setup.
- The manifests already include startup waiting for MySQL and Eureka for the main backend services.

## 8. Recommended demo flow

For a simple demonstration:

1. Start Docker Desktop
2. Run:

```powershell
kubectl config use-context docker-desktop
kubectl apply -k .\k8s
kubectl get pods -n pi
kubectl get svc -n pi
```

3. Open the web or gateway URL
4. Show pod status and service list
