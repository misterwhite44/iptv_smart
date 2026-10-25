"""Crée une release GitHub (tag v0.1.N) pour chaque version déjà sortie,
avec les notes de CHANGELOG.md. À lancer une seule fois via le workflow
« Backfill releases » ; les versions qui ont déjà leur release sont ignorées."""
import json
import re
import subprocess

REPO = "misterwhite44/iptv_smart"
# Version -> commit de main qui a produit l'APK.
COMMITS = {
    1: "a68c10b", 3: "b609d58", 4: "156c3c6", 5: "14fc84d", 6: "37b7cc7",
    8: "a2ab190", 9: "72bd513", 10: "c4ecbdc", 11: "a2f7aa6", 12: "6789083",
    15: "d9956aa", 18: "e0b06f6", 22: "6091b47", 24: "0cd43a2", 26: "b13e53d",
    28: "2456555", 30: "070fb61", 32: "fe3cc89", 34: "44102e0", 35: "960e45a",
}

text = open("CHANGELOG.md", encoding="utf-8").read()
sections = {
    int(n): (date, body.strip())
    for n, date, body in re.findall(
        r"^## 0\.1\.(\d+) — (.*?)\n(.*?)(?=^## |\Z)", text, re.S | re.M
    )
}
existing = set(
    subprocess.check_output(
        ["gh", "api", f"repos/{REPO}/releases?per_page=100", "--jq", ".[].tag_name"]
    ).decode().split()
)

for n in sorted(COMMITS):
    tag = f"v0.1.{n}"
    if tag in existing:
        print(f"{tag} existe déjà")
        continue
    date, notes = sections[n]
    sha = subprocess.check_output(["git", "rev-parse", COMMITS[n]]).decode().strip()
    body = (
        f"Zynora 0.1.{n} — {date}\n\n{notes}\n\n"
        f"Historique complet : [CHANGELOG.md](https://github.com/{REPO}/blob/main/CHANGELOG.md)"
    )
    payload = {
        "tag_name": tag,
        "target_commitish": sha,
        "name": f"Zynora 0.1.{n}",
        "body": body,
        "make_latest": "false",
    }
    subprocess.run(
        ["gh", "api", "-X", "POST", f"repos/{REPO}/releases", "--input", "-", "--jq", ".html_url"],
        input=json.dumps(payload).encode(),
        check=True,
    )
