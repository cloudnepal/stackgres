#!/bin/bash

set -e

CONTAINER_BASE=$(buildah from "azul/zulu-openjdk-alpine:8-jre")

JAVA_OPTS="-Djava.net.preferIPv4Stack=true -XX:MaxRAMPercentage=85.0"
APP_OPTS="-Dquarkus.http.host=0.0.0.0 -Dquarkus.http.port=8080 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"

# Include binaries
buildah config --workingdir='/app/' "$CONTAINER_BASE"
buildah copy --chown nobody:nobody "$CONTAINER_BASE" 'operator/target/*-runner.jar' '/app/stackgres-operator.jar'
buildah copy --chown nobody:nobody "$CONTAINER_BASE" 'operator/target/lib/*' '/app/lib/'
buildah run "$CONTAINER_BASE" -- chmod 775 '/app'

## Run our server and expose the port
buildah config --cmd "java $JAVA_OPTS -jar /app/stackgres-operator.jar $APP_OPTS" "$CONTAINER_BASE"
buildah config --port 8080 "$CONTAINER_BASE"
buildah config --user nobody:nobody "$CONTAINER_BASE"

## Commit this container to an image name
buildah commit --squash "$CONTAINER_BASE" "${1:-"stackgres/operator:test-jvm"}"