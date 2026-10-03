#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
export HF_HOME="$PWD/.tools/huggingface"
export XDG_CACHE_HOME="$PWD/.tools/cache"
export TMPDIR="$PWD/.tools/tmp"
export HF_HUB_DISABLE_TELEMETRY=1
export TOKENIZERS_PARALLELISM=false
mkdir -p "$HF_HOME" "$XDG_CACHE_HOME" "$TMPDIR"
exec .tools/laya-venv/bin/python "$@"
