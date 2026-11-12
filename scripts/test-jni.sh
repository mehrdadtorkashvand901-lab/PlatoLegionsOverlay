#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
cargo build --locked --release --lib
jni_classes="$(mktemp -d)"
trap 'rm -rf "$jni_classes"' EXIT
javac -d "$jni_classes" tests/jni/ir/legions/overlay/EngineBridge.java
java -cp "$jni_classes" ir.legions.overlay.EngineBridge "$PWD/target/release/liblegions_ai.so"
