"""Captures d'écran pour la fiche Play Store, prises sur un émulateur avec l'APK démo.

Lancé par .github/workflows/screenshots.yml. Pilote l'appli avec adb et uiautomator
(recherche des éléments par texte ou description), puis enregistre les PNG dans screenshots/.
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "com.zynora.app.demo"
OUT = "screenshots"


def adb(*args, check=True):
    return subprocess.run(["adb", *args], check=check, capture_output=True, text=True).stdout


def dump():
    for _ in range(5):
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml", check=False)
        xml = adb("shell", "cat", "/sdcard/ui.xml", check=False)
        if xml.strip().startswith("<?xml"):
            return ET.fromstring(xml)
        time.sleep(1)
    raise RuntimeError("uiautomator dump impossible")


def find(pattern):
    rx = re.compile(pattern, re.IGNORECASE)
    for node in dump().iter("node"):
        label = (node.get("text") or "") + " " + (node.get("content-desc") or "")
        if rx.search(label.strip()):
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
            return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def tap(pattern, timeout=40):
    end = time.time() + timeout
    while time.time() < end:
        pos = find(pattern)
        if pos:
            adb("shell", "input", "tap", str(pos[0]), str(pos[1]))
            return True
        time.sleep(2)
    print(f"introuvable : {pattern}", flush=True)
    shot("debug-" + re.sub(r"[^a-z]+", "-", pattern.lower()).strip("-") + ".png", 0)
    return False


def shot(name, wait=6):
    time.sleep(wait)
    with open(os.path.join(OUT, name), "wb") as f:
        f.write(subprocess.run(["adb", "exec-out", "screencap", "-p"], check=True, capture_output=True).stdout)
    print("capture", name)


def main():
    os.makedirs(OUT, exist_ok=True)
    # Barre d'état propre : heure fixe, réseau et batterie pleins, sans notifications.
    # Pas de bulle « Viewing full screen » à l'ouverture du lecteur.
    adb("shell", "settings", "put", "secure", "immersive_mode_confirmations", "confirmed")
    adb("shell", "settings", "put", "global", "sysui_demo_allowed", "1")
    for extra in (
        ["-e", "command", "enter"],
        ["-e", "command", "clock", "-e", "hhmm", "2030"],
        ["-e", "command", "battery", "-e", "level", "100", "-e", "plugged", "false"],
        ["-e", "command", "network", "-e", "wifi", "show", "-e", "level", "4"],
        ["-e", "command", "network", "-e", "mobile", "show", "-e", "level", "4", "-e", "datatype", "none"],
        ["-e", "command", "notifications", "-e", "visible", "false"],
    ):
        adb("shell", "am", "broadcast", "-a", "com.android.systemui.demo", *extra, check=False)

    adb("shell", "am", "start", "-W", "-n", f"{PKG}/com.misterwhite.iptvsmart.MainActivity")
    time.sleep(8)
    tap(r"^J'ai compris$", timeout=15)
    if not tap(r"catalogue de démonstration"):
        sys.exit(1)

    shot("1-accueil.png", 20)

    tap(r"^Films$")
    shot("2-films.png", 10)

    tap(r"Sintel")
    shot("3-fiche-film.png", 10)

    tap(r"^Séries$", timeout=5) or (adb("shell", "input", "keyevent", "4"), tap(r"^Séries$"))
    shot("4-series.png", 10)

    tap(r"^Direct$")
    shot("5-direct.png", 10)

    tap(r"^Films$")
    time.sleep(4)
    tap(r"Tears of Steel")
    time.sleep(6)
    if tap(r"^(LECTURE|REPRENDRE)$"):
        shot("6-lecteur.png", 25)


if __name__ == "__main__":
    main()
