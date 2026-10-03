"""Bounded Clef REST calls. Credentials remain on the computer."""
import json
import math
import re
import urllib.request
from pathlib import Path

MODEL = "@cf/cloudflare/clef-flash"
LABELS = {"keep", "filter", "uncertain"}
ROOT = Path(__file__).resolve().parents[2]


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


class ClefClient:
    def __init__(self, config=ROOT / ".tools/clef.env", opener=None):
        values = {}
        for line in Path(config).read_text().splitlines():
            if line.strip() and not line.lstrip().startswith("#"):
                key, value = line.split("=", 1)
                values[key.strip()] = value.strip().strip("\"'")
        account = values.get("CLOUDFLARE_ACCOUNT_ID", "")
        token = values.get("CLOUDFLARE_API_TOKEN", "")
        if not re.fullmatch(r"[a-fA-F0-9]{32}", account) or not token or not token.isascii() or any(c.isspace() for c in token):
            raise ValueError("missing or invalid Cloudflare credentials")
        if values.get("CLOUDFLARE_MODEL", MODEL) != MODEL:
            raise ValueError("this validation uses clef-flash")
        self.url = f"https://api.cloudflare.com/client/v4/accounts/{account}/ai/run/{MODEL}"
        self.token = token
        self.opener = opener or urllib.request.build_opener(NoRedirect())
        self.questions = json.loads((ROOT / "app/src/debug/assets/clef-questions.json").read_text())

    def predict(self, text):
        if not isinstance(text, str) or not text.strip() or len(text) > 4000:
            raise ValueError("only bounded nonempty text is accepted")
        body = json.dumps({"model": "clef-flash", "state": text, "questions": self.questions}).encode()
        request = urllib.request.Request(self.url, data=body, headers={
            "Authorization": "Bearer " + self.token, "Content-Type": "application/json"})
        with self.opener.open(request, timeout=2.5) as response:
            if response.status != 200:
                raise ValueError("unexpected API status")
            raw = response.read(65537)
        if len(raw) > 65536:
            raise ValueError("oversized API response")
        payload = json.loads(raw)
        if payload.get("success") is not True:
            raise ValueError("API reported failure")
        result = payload["result"]
        if result["model"] != "clef-flash":
            raise ValueError("unexpected model")
        answer = result["answers"]["decision"]
        probabilities = answer["probabilities"]
        if answer["choice"] not in LABELS or set(probabilities) != LABELS:
            raise ValueError("invalid decision schema")
        if any(type(v) not in (int, float) or not math.isfinite(v) or not 0 <= v <= 1 for v in probabilities.values()):
            raise ValueError("invalid probabilities")
        if abs(sum(probabilities.values()) - 1) > .002:
            raise ValueError("probabilities do not sum to one")
        if probabilities[answer["choice"]] != max(probabilities.values()):
            raise ValueError("choice does not match probabilities")
        if result.get("usage", {}).get("truncated"):
            raise ValueError("truncated input")
        return answer, result.get("usage", {})
