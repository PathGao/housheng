"""Run frozen synthetic cases against real local weights; never operates a phone."""
import argparse
import hashlib
import json
import math
import platform
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
    from client import ClefClient, MODEL
    parser = argparse.ArgumentParser()
    parser.add_argument("--split", choices=("dev", "eval"), default="dev")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--repeats", type=int, default=1)
    args = parser.parse_args()
    if args.repeats < 1 or args.output.exists():
        parser.error("positive repeats and a new output path are required")
    cases = json.loads((HERE / "cases.json").read_text())
    selected = [r for r in cases if r["split"] == args.split]
    client = ClefClient()
    payload = {
        "started_at": datetime.now(timezone.utc).isoformat(), "synthetic_only": True,
        "split": args.split, "repeats": args.repeats, "model": MODEL,
        "dataset_sha256": digest(HERE / "cases.json"),
        "questions_sha256": digest(HERE.parents[1] / "app/src/online/assets/clef-questions.json"),
        "python": platform.python_version(), "platform": platform.platform(), "rows": [],
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    try:
        for repeat in range(args.repeats):
            for case in selected:
                started = time.perf_counter()
                answer, usage = client.predict(case["text"])
                elapsed = (time.perf_counter() - started) * 1000
                row = dict(case, repeat=repeat, predicted=answer["choice"],
                           probabilities=answer["probabilities"], usage=usage,
                           answer_probability=answer["probabilities"][answer["choice"]],
                           elapsed_ms=round(elapsed, 3), correct=case["expected"] == answer["choice"])
                payload["rows"].append(row)
                print(json.dumps({k: row[k] for k in ("id", "expected", "predicted", "elapsed_ms")}), flush=True)
    except Exception as error:
        payload["error_type"] = type(error).__name__
        raise
    finally:
        payload["completed_at"] = datetime.now(timezone.utc).isoformat()
        if payload["rows"]:
            payload["summary"] = summarize(payload["rows"])
        args.output.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(payload["summary"], ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
