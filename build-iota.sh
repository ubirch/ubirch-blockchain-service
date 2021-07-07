#!/usr/bin/env bash
set -e

IOTA_HOME=iota.rs
IOTA_JAVA_BINDING=$IOTA_HOME/bindings/java/native
IOTA_JAVA_RELEASE=$IOTA_HOME/bindings/java/target/release

export JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk-amd64

if [ ! -d "$IOTA_HOME" ]; then
  echo "Installing config files in ${IOTA_HOME}..."
  git clone git@github.com:iotaledger/iota.rs.git $IOTA_HOME
fi

if [ ! -d "$IOTA_JAVA_RELEASE" ]; then
  echo "Compiling java bindings ${IOTA_JAVA_BINDING}... might take a while"
  cd $IOTA_JAVA_BINDING
  cargo build --release
fi
