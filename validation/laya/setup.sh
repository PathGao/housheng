#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
export UV_CACHE_DIR="$PWD/.tools/uv-cache"
export UV_PYTHON_INSTALL_DIR="$PWD/.tools/python"
export TMPDIR="$PWD/.tools/tmp"
mkdir -p "$UV_CACHE_DIR" "$UV_PYTHON_INSTALL_DIR" "$TMPDIR"
commit=fa9a2a7070b1789912a49ae24603bbfb1a78b001
if [ ! -d .tools/laya-source ]; then
    git clone --depth 1 https://github.com/NandhaKishorM/laya.git .tools/laya-source
    git -C .tools/laya-source fetch --depth 1 origin "$commit"
    git -C .tools/laya-source checkout --detach "$commit"
fi
test "$(git -C .tools/laya-source rev-parse HEAD)" = "$commit"
test -z "$(git -C .tools/laya-source status --porcelain)"
if [ ! -d .tools/laya-venv ]; then
    uv venv --python 3.12.14 .tools/laya-venv
fi
uv pip sync --python .tools/laya-venv/bin/python validation/laya/requirements.lock
uv pip install --no-deps --python .tools/laya-venv/bin/python .tools/laya-source
validation/laya/python.sh validation/laya/download.py
