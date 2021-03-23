#!/bin/bash
echo Should raise your Docker memory limit to at least 6 GB

mvn clean package -DskipTests -Pnative,nexus -Dquarkus.native.container-build=true -Dquarkus.native.native-image-xmx=5500m && \
# make build ... or ... & \
docker build . -t affinity-metadata && \
docker run --rm -p 8080:8080 affinity-metadata
