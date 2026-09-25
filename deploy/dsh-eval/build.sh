#!/usr/bin/env bash
set -euo pipefail

dsh_source="${1:-/opt/deepseek-harness}"
image="${2:-agentforge/dsh-eval:99f6f02}"
expected_commit="99f6f02fecdb7dff40c3fbc9470f5907c29f74ca"
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

if [[ ! -d "$dsh_source/.git" ]]; then
  echo "DSH checkout not found: $dsh_source" >&2
  exit 1
fi

actual_commit="$(git -C "$dsh_source" rev-parse HEAD)"
if [[ "$actual_commit" != "$expected_commit" ]]; then
  echo "DSH source must be pinned to $expected_commit; found $actual_commit" >&2
  exit 1
fi

stage="$(mktemp -d "${TMPDIR:-/tmp}/agentforge-dsh.XXXXXXXX")"
cleanup() {
  case "$stage" in
    "${TMPDIR:-/tmp}"/agentforge-dsh.*) rm -rf -- "$stage" ;;
    *) echo "Refusing to remove unexpected staging path: $stage" >&2 ;;
  esac
}
trap cleanup EXIT

tar -C "$dsh_source" --exclude=.git --exclude=node_modules --exclude=.turbo --exclude=.cache -cf - . \
  | tar -C "$stage" -xf -
mkdir -p "$stage/eval-deploy"
cp "$script_dir/Dockerfile" "$script_dir/cordis.yml" "$script_dir/eval-tools.mjs" "$stage/eval-deploy/"

docker build \
  --build-arg "DSH_COMMIT=$expected_commit" \
  -f "$stage/eval-deploy/Dockerfile" \
  -t "$image" \
  "$stage"
