"""Run frozen synthetic cases against real local weights; never operates a phone."""
import argparse
import hashlib
import importlib.metadata
import json
import math
import platform
import resource
import statistics
import time
from datetime import datetime, timezone
from pathlib import Path

HERE = Path(__file__).resolve().parent
LABELS = ("keep", "filter", "uncertain")


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def percentile(values, p):
    return sorted(values)[max(0, math.ceil(len(values) * p) - 1)]


def sync(device):
    if device.type == "mps":
        import torch
        torch.mps.synchronize()


def summarize(rows):
    latencies = [r["elapsed_ms"] for r in rows]
    matrix = {label: dict.fromkeys(LABELS, 0) for label in LABELS}
    for row in rows:
        matrix[row["expected"]][row["predicted"]] += 1
    return {
        "count": len(rows), "accuracy": sum(r["correct"] for r in rows) / len(rows),
        "confusion": matrix,
        "false_filter_count": sum(r["predicted"] == "filter" and r["expected"] != "filter" for r in rows),
        "non_filter_count": sum(r["expected"] != "filter" for r in rows),
        "filter_recall": matrix["filter"]["filter"] / max(1, sum(matrix["filter"].values())),
        "median_ms": statistics.median(latencies), "p95_ms": percentile(latencies, .95),
        "max_ms": max(latencies), "over_500ms": sum(t > 500 for t in latencies),
        "wrong_at_probability_0_9": [r["id"] for r in rows if not r["correct"] and r["answer_probability"] >= .9],
    }


def main():
    import laya
    import torch
    from laya.common import build_head, render_options

    started_at = datetime.now(timezone.utc).isoformat()
    parser = argparse.ArgumentParser()
    parser.add_argument("--device", choices=("mps", "cpu"), default="mps")
    parser.add_argument("--split", choices=("dev", "eval"), default="dev")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--questions", type=Path, default=HERE / "questions.json")
    parser.add_argument("--repeats", type=int, default=1)
    args = parser.parse_args()
    if args.repeats < 1:
        parser.error("repeats must be positive")
    if args.output.exists():
        parser.error("output exists; preserve earlier evidence")
    cases = json.loads((HERE / "cases.json").read_text())
    questions = json.loads(args.questions.read_text())
    selected = [r for r in cases if r["split"] == args.split]
    assert len({r["id"] for r in cases}) == len(cases)
    assert all(r["expected"] in LABELS for r in cases)
    if args.device == "mps" and not torch.backends.mps.is_available():
        raise RuntimeError("MPS unavailable; do not label CPU results as MPS")
    started = time.perf_counter()
    agent = laya.load(".tools/laya-model", device=args.device)
    sync(agent.device)
    load_ms = (time.perf_counter() - started) * 1000
    print(json.dumps({"loaded_ms": load_ms, "device": str(agent.device)}, ensure_ascii=False), flush=True)
    internal = agent._to_internal(questions["decision"])
    option_lengths = [len(agent.tok.encode(" " + option, add_special_tokens=False))
                      for option in render_options(internal)]
    instruction_length = len(agent.tok.encode(internal["t"] + " question: " + internal["ins"],
                                            add_special_tokens=False))
    head_limit = agent.cfg["head_max_len"]
    audit = {"instruction_tokens": instruction_length, "option_tokens": option_lengths,
             "head_tokens": len(build_head(agent.tok, internal, head_limit)[0]),
             "head_limit": head_limit}
    if max(option_lengths) > 48 or instruction_length + sum(n + 1 for n in option_lengths) > head_limit:
        raise ValueError("Question would be truncated; shorten it before evaluation: " + str(audit))

    def predict(text):
        sync(agent.device)
        start = time.perf_counter()
        response = agent.predict(text, questions, lang="zh", max_len=1024)
        sync(agent.device)
        elapsed = (time.perf_counter() - start) * 1000
        answer = response["answers"]["decision"]
        assert answer["choice"] in LABELS
        probabilities = answer["probabilities"]
        assert set(probabilities) == set(LABELS)
        assert all(math.isfinite(v) and 0 <= v <= 1 for v in probabilities.values())
        assert abs(sum(probabilities.values()) - 1) < .002
        assert not response["usage"]["truncated"], response["usage"]
        return answer, elapsed

    warmup = [predict("今天教大家把衣服叠整齐，步骤简单，慢慢练习。")[1] for _ in range(5)]
    rows = []
    for repeat in range(args.repeats):
        for case in selected:
            answer, elapsed = predict(case["text"])
            row = dict(case, repeat=repeat, predicted=answer["choice"],
                       probabilities=answer["probabilities"],
                       answer_probability=answer["probabilities"][answer["choice"]],
                       elapsed_ms=round(elapsed, 3), correct=case["expected"] == answer["choice"])
            rows.append(row)
            print(json.dumps({k: row[k] for k in ("id", "expected", "predicted", "elapsed_ms", "answer_probability")}), flush=True)
    summary = summarize(rows)
    payload = {
        "started_at": started_at, "completed_at": datetime.now(timezone.utc).isoformat(),
        "question_token_audit": audit,
        "synthetic_only": True, "split": args.split, "repeats": args.repeats,
        "dataset_sha256": digest(HERE / "cases.json"), "questions_sha256": digest(args.questions),
        "model_source": json.loads(Path(".tools/laya-model-source.json").read_text()),
        "code_commit": "fa9a2a7070b1789912a49ae24603bbfb1a78b001",
        "python": platform.python_version(), "platform": platform.platform(),
        "versions": {name: importlib.metadata.version(name) for name in ("laya", "torch", "transformers", "numpy")},
        "device": str(agent.device), "load_ms": load_ms, "warmup_ms": warmup,
        "max_rss_mib": resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1024**2,
        "mps_driver_mib": torch.mps.driver_allocated_memory() / 1024**2 if agent.device.type == "mps" else None,
        "summary": summary, "rows": rows,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
