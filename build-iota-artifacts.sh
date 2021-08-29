#!/usr/bin/env bash
set -e

NAME=ubirch-blockchain-service
SRC_URL=https://github.com/ubirch/$NAME.git

NOW=$(date -u -Iminutes)
VERSION=$(git describe --tags --match 'v[0-9]*' --dirty='-dirty' --always)
REVISION=$(git rev-parse --short HEAD)$(if ! git diff --no-ext-diff --quiet --exit-code; then echo -dirty; fi)

#See other commit ids:
#https://github.com/iotaledger/iota.rs/commits/dev
IOTA_RS_COMMIT_ID=51ebef3a0b2ee5370ec66a271a325eab59194471
IOTA_RS_VERSION=${IOTA_RS_COMMIT_ID:0:7}

IMAGE_REPO=docker.io/ubirch/$NAME
IMAGE_TAG=v-$IOTA_RS_VERSION-$VERSION

docker build --pull -t "$IMAGE_REPO":"$IMAGE_TAG" \
    -f DockerfileBuildNativeIota \
		--build-arg="IOTA_RS_COMMIT_ID=$IOTA_RS_COMMIT_ID" \
		--label="org.opencontainers.image.title=$NAME" \
		--label="org.opencontainers.image.created=$NOW" \
		--label="org.opencontainers.image.source=$SRC_URL" \
		--label="org.opencontainers.image.version=$VERSION" \
		--label="org.opencontainers.image.revision=$REVISION" .

docker create -it --name iota_artifacts_temp "$IMAGE_REPO":"$IMAGE_TAG" /bin/bash
docker cp iota_artifacts_temp:/iota/src/java src/main
docker cp iota_artifacts_temp:/iota/lib/libiota_client.so lib/bindings/java/iota-release/libiota_client.so
docker rm iota_artifacts_temp
