# ocfl-azure-test

## Build

```shell
mvn clean package
docker build -t ocfl-azure-test .
docker tag localhost/ocfl-azure-test pwinckles.azurecr.io/ocfl-azure-test
docket push pwinckles.azurecr.io/ocfl-azure-test
```

## Run

```shell
TEST_ROOT=/var/tmp/test java -jar target/ocfl-azure-test-1.0.0-SNAPSHOT-jar-with-dependencies.jar
```

## Azure

```shell
# Create
kubectl apply -f app.yaml
# Restart
kubectl rollout restart deployment/ocfl-test-app
# Delete
kubectl delete -f app.yaml
# List
kubectl get pods
# Logs
kubectl logs POD -f
# Login
kubectl exec POD -it -- /bin/bash
# Look for errors
cd /data
./rocfl -r ocfl-root validate -l warn > rocfl.log 2>&1 &
```