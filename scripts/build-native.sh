#!/usr/bin/env bash
set -euo pipefail
# Requires Rust target aarch64-linux-android and the pinned Android NDK.
repo_dir="$(cd "$(dirname "$0")/.." && pwd)"
ndk_dir="${ANDROID_NDK_HOME:-${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}/ndk/27.2.12479018}"
clang_dir="$ndk_dir/toolchains/llvm/prebuilt/linux-x86_64/bin"
test -x "$clang_dir/aarch64-linux-android33-clang"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$clang_dir/aarch64-linux-android33-clang"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_RUSTFLAGS='-C link-arg=-Wl,-z,max-page-size=16384'
cd "$repo_dir"
cargo build --locked --release --lib --target aarch64-linux-android
mkdir -p app/src/main/jniLibs/arm64-v8a
cp target/aarch64-linux-android/release/liblegions_ai.so app/src/main/jniLibs/arm64-v8a/
"$clang_dir/llvm-strip" --strip-unneeded app/src/main/jniLibs/arm64-v8a/liblegions_ai.so
