import io
import json
import tempfile
import unittest
from pathlib import Path
from client import ClefClient


class Response(io.BytesIO):
    status = 200


class Opener:
    def __init__(self, payload):
        self.payload = payload
        self.requests = []

    def open(self, request, timeout):
        self.requests.append(request)
        return Response(json.dumps(self.payload).encode())


class ClientTest(unittest.TestCase):
    def client(self, payload):
        temporary = tempfile.TemporaryDirectory(dir=Path(__file__).parent)
        self.addCleanup(temporary.cleanup)
        config = Path(temporary.name) / "credentials.env"
        config.write_text("CLOUDFLARE_ACCOUNT_ID=" + "a" * 32 + "\nCLOUDFLARE_API_TOKEN=test-token\n")
        self.last_opener = Opener(payload)
        return ClefClient(config, self.last_opener)

    def payload(self):
        return {"success": True, "result": {"model": "clef-flash", "answers": {
            "decision": {"choice": "filter", "probabilities": {"keep": .1, "filter": .8, "uncertain": .1}}},
            "usage": {"input_tokens": 10}}}

    def test_real_request_schema_and_typed_result(self):
        client = self.client(self.payload())
        answer, usage = client.predict("合成内容")
        request = self.last_opener.requests[0]
        self.assertEqual(json.loads(request.data)["state"], "合成内容")
        self.assertEqual(json.loads(request.data)["model"], "clef-flash")
        self.assertEqual(request.get_header("Authorization"), "Bearer test-token")
        self.assertEqual(answer["choice"], "filter")
        self.assertEqual(usage["input_tokens"], 10)

    def test_invalid_results_are_never_filter(self):
        for mutate in (
            lambda p: p.update(success=False),
            lambda p: p["result"].update(model="other"),
            lambda p: p["result"]["usage"].update(truncated=True),
            lambda p: p["result"]["answers"]["decision"].update(choice="keep"),
            lambda p: p["result"]["answers"]["decision"]["probabilities"].update(filter=float("nan")),
            lambda p: p["result"]["answers"]["decision"]["probabilities"].update(filter=.3),
        ):
            payload = self.payload()
            mutate(payload)
            with self.subTest(payload=payload), self.assertRaises(ValueError):
                self.client(payload).predict("合成内容")

    def test_empty_or_oversized_text_never_reaches_api(self):
        client = self.client(self.payload())
        for text in ("", " ", "x" * 4001, None):
            with self.assertRaises(ValueError):
                client.predict(text)
        self.assertEqual(self.last_opener.requests, [])


if __name__ == "__main__":
    unittest.main()
