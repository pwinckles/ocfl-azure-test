# ocfl-azure-test

## Build

```shell
mvn clean package
docker build -t ocfl-azure-test .
```

## Run

```shell
TEST_ROOT=/var/tmp/test java -jar target/ocfl-azure-test-1.0.0-SNAPSHOT-jar-with-dependencies.jar
```

## Azure

```shell
kubectl apply -f app.yaml
kubectl delete -f app.yaml
kubectl get pods
kubectl logs POD -f
kubectl exec POD -it -- /bin/bash

./rocfl -r ocfl-root validate -l warn > rocfl.log 2>&1 &
```