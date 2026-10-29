#!/usr/bin/env bash
set -euo pipefail

out_dir=${1:?usage: build-android.sh <jniLibs-output-dir> [go-binary]}
go_bin=${2:-${GO_BINARY:-go}}
ndk_or_sdk_dir=${3:-${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}}
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

if ! command -v "$go_bin" >/dev/null 2>&1; then
  echo "Go 1.24.13 is required to build the Android Plus bridge (set GO_BINARY or -PgoBinary)." >&2
  exit 1
fi
go_version="$("$go_bin" env GOVERSION)"
if [[ "$go_version" != "go1.24.13" ]]; then
  echo "Expected Go 1.24.13, found $go_version." >&2
  exit 1
fi

mkdir -p "$out_dir"
cd "$script_dir"
export GOCACHE="${GOCACHE:-${TMPDIR:-/tmp}/om-ytdl-go-cache}"
export GOPROXY="${GOPROXY:-https://proxy.golang.org,direct}"

ndk_dir="$ndk_or_sdk_dir"
if [[ -n "$ndk_dir" && -d "$ndk_dir/ndk" ]]; then
  ndk_dir="$(find "$ndk_dir/ndk" -mindepth 1 -maxdepth 1 -type d -print | sort -V | tail -n 1)"
fi
if [[ -z "$ndk_dir" || ! -d "$ndk_dir/toolchains/llvm/prebuilt/linux-x86_64/bin" ]]; then
  echo "An installed Android NDK is required to build the Plus native bridge." >&2
  exit 1
fi
ndk_bin="$ndk_dir/toolchains/llvm/prebuilt/linux-x86_64/bin"
api_level=26

build_abi() {
  local abi="$1" arch="$2" arm="$3" cgo="$4" cc="$5"
  local dest="$out_dir/$abi"
  local -a env_args=(CGO_ENABLED="$cgo" GOOS=android GOARCH="$arch")
  if [[ -n "$arm" ]]; then
    env_args+=(GOARM="$arm")
  fi
  if [[ -n "$cc" ]]; then
    env_args+=(CC="$cc")
  fi
  mkdir -p "$dest"
  echo "Building ytdl-go bridge for $abi"
  env "${env_args[@]}" "$go_bin" build -trimpath -buildvcs=false -ldflags="-s -w" \
    -o "$dest/libytdlbridge.so" .
}

build_abi arm64-v8a arm64 "" 1 "$ndk_bin/aarch64-linux-android${api_level}-clang"
build_abi x86_64 amd64 "" 1 "$ndk_bin/x86_64-linux-android${api_level}-clang"
build_abi armeabi-v7a arm 7 1 "$ndk_bin/armv7a-linux-androideabi${api_level}-clang"
build_abi x86 386 "" 1 "$ndk_bin/i686-linux-android${api_level}-clang"
