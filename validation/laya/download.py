import json
from pathlib import Path
from huggingface_hub import snapshot_download

MODEL = "convaiinnovations/laya-multilingual"
REVISION = "e4e9ddf21a7b1903b7acffd8814ad4307bf63a67"

path = snapshot_download(
    MODEL, revision=REVISION, local_dir=".tools/laya-model",
    allow_patterns=["rl_agent_config.json", "model.safetensors", "encoder/*", "tokenizer/*"],
)
Path(".tools/laya-model-source.json").write_text(json.dumps({
    "model": MODEL, "revision": REVISION, "path": path,
}, indent=2) + "\n")
print(path)
