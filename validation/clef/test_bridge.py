import unittest
import json
import threading
import urllib.error
import urllib.request

from bridge import classify_request, make_server


class ProtocolTest(unittest.TestCase):
    def request(self, **changes):
        return dict(request_id="request-1", source="io.github.pathgao.housheng.fixture",
                    text="合成内容", **changes)

    def test_correlated_typed_result(self):
        result = classify_request(self.request(), lambda text: "filter")
        self.assertEqual(result["request_id"], "request-1")
        self.assertEqual(result["decision"], "filter")
        self.assertGreaterEqual(result["elapsed_ms"], 0)

    def test_invalid_input_never_reaches_model(self):
        def forbidden(text):
            self.fail("invalid input reached model")
        base = self.request()
        for patch in ({"source": "com.android.chrome"}, {"text": ""},
                      {"text": "x" * 4001}, {"text": None}, {"request_id": ""},
                      {"request_id": 3}, {"extra": True}):
            with self.subTest(patch=patch), self.assertRaises(ValueError):
                classify_request(dict(base, **patch), forbidden)

    def test_unknown_model_output_rejected(self):
        with self.assertRaises(ValueError):
            classify_request(self.request(), lambda text: "invented")

    def test_http_boundary_rejects_wrong_source_and_oversized_body(self):
        server = make_server(lambda text: "filter", port=0)
        worker = threading.Thread(target=server.serve_forever)
        worker.start()
        try:
            url = "http://127.0.0.1:%d/v1/classify" % server.server_port
            for body in (dict(self.request(), source="com.android.chrome"), {"text": "x" * 24001}):
                request = urllib.request.Request(url, data=json.dumps(body).encode(), headers={"Content-Type": "application/json"})
                with self.assertRaises(urllib.error.HTTPError) as caught:
                    urllib.request.urlopen(request, timeout=2)
                self.assertEqual(caught.exception.code, 400)
                caught.exception.close()
            request = urllib.request.Request(url, data=json.dumps(self.request()).encode(), headers={"Content-Type": "application/json"})
            with urllib.request.urlopen(request, timeout=2) as response:
                self.assertEqual(json.load(response)["decision"], "filter")
        finally:
            server.shutdown()
            server.server_close()
            worker.join()


if __name__ == "__main__":
    unittest.main()
