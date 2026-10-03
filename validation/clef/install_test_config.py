"""Send credentials over stdin to private app storage; never put them in argv."""
import json
import os
import subprocess
from client import ClefClient

client = ClefClient()
account = client.url.split("/accounts/", 1)[1].split("/", 1)[0]
adb = ".tools/android-sdk/platform-tools/adb"
args = [adb]
if os.environ.get("ANDROID_SERIAL"):
    args += ["-s", os.environ["ANDROID_SERIAL"]]
subprocess.run(args + ["shell", "run-as", "io.github.pathgao.housheng", "sh", "-c",
                      "'mkdir -p files; cat > files/clef-test.json; chmod 600 files/clef-test.json'"],
               input=json.dumps({"account": account, "token": client.token}).encode(), check=True)
print("Private test credentials installed without logging their values.")
