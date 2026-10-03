"""Loopback-only validation bridge. No phone content is stored."""
import argparse
import json
import os
import re
import time
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

FIXTURE = "io.github.pathgao.housheng.fixture"
LABELS = {"keep", "filter", "uncertain"}

def classify_request(body, predict):
    if not isinstance(body, dict) or set(body) != {"request_id", "source", "text"}:
        raise ValueError("invalid fields")
    request_id, text = body["request_id"], body["text"]
    if not isinstance(request_id, str) or not re.fullmatch(r"[A-Za-z0-9-]{1,64}", request_id):
        raise ValueError("invalid request id")
    if body["source"] != FIXTURE or not isinstance(text, str) or not 1 <= len(text.strip()) <= 4000 or len(text) > 4000:
        raise ValueError("only bounded fixture text is accepted")
    started = time.perf_counter()
    decision = predict(text)
    if decision not in LABELS:
        raise ValueError("invalid model decision")
    return {"request_id": request_id, "decision": decision,
            "model": "laya-multilingual", "elapsed_ms": round((time.perf_counter() - started) * 1000, 3)}


def make_server(predict, port=18765):
    class Handler(BaseHTTPRequestHandler):
        def setup(self):
            super().setup()
            self.connection.settimeout(2)

        def log_message(self, *_):
            pass

        def respond(self, status, body):
            raw = json.dumps(body, ensure_ascii=False).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(raw)))
            self.send_header("Connection", "close")
            self.end_headers()
            try:
                self.wfile.write(raw)
            except (BrokenPipeError, ConnectionResetError):
                pass

        def do_GET(self):
            if self.path == "/health":
                self.respond(200, {"model": "laya-multilingual", "ready": True})
            else:
                self.respond(404, {"error": "not_found"})

        def do_POST(self):
            if self.path != "/v1/classify":
                self.respond(404, {"error": "not_found"})
                return
            try:
                length = int(self.headers.get("Content-Length", "0"))
                if not 0 < length <= 24000 or self.headers.get("Transfer-Encoding"):
                    raise ValueError("invalid body length")
                body = json.loads(self.rfile.read(length))
                result = classify_request(body, predict)
            except (ValueError, UnicodeError):
                self.respond(400, {"error": "invalid_request_or_result"})
                return
            except Exception as error:
                print(json.dumps({"error_type": type(error).__name__}), flush=True)
                self.respond(503, {"error": "model_unavailable"})
                return
            self.respond(200, result)
            print(json.dumps({k: result[k] for k in ("request_id", "decision", "elapsed_ms")}), flush=True)

    return HTTPServer(("127.0.0.1", port), Handler)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--device", choices=("mps", "cpu"), default="mps")
    parser.add_argument("--port", type=int, default=18765)
    args = parser.parse_args()
    os.environ["HF_HUB_OFFLINE"] = "1"
    os.environ["TRANSFORMERS_OFFLINE"] = "1"
    import laya
    agent = laya.load(".tools/laya-model", device=args.device)
    questions = json.loads((Path(__file__).parent / "questions.json").read_text())

    def predict(text):
        response = agent.predict(text, questions, lang="zh", max_len=1024)
        return "uncertain" if response["usage"]["truncated"] else response["answers"]["decision"]["choice"]

    for _ in range(5):
        predict("合成内容，用于预热模型。")
    server = make_server(predict, args.port)
    print(json.dumps({"ready": True, "address": server.server_address, "device": str(agent.device)}), flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
