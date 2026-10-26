import hashlib
import json
import re
import shutil
import subprocess
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

root = Path(__file__).parent
repo = Path("~/copilot-worktrees/rebuild/kidusm001-improved-couscous")
commit = subprocess.check_output(["git", "-C", str(repo), "rev-parse", "HEAD"], text=True).strip()
assert commit == "d93fac17253cdb7d40ee6e2daeac650713d84ff9"
assert not subprocess.check_output(["git", "-C", str(repo), "status", "--porcelain"], text=True).strip()
apk = root.parent / "delivery-0.1.3/rebuild-0.1.3-debug.apk"
apk_hash = hashlib.sha256(apk.read_bytes()).hexdigest()
assert apk_hash == "178e3308d27485948a73f0958531169719910cdabd8eaecd2d79b8c2bf7913fe"
theme_source = repo / "app/src/main/java/com/rebuild/ui/theme/Theme.kt"
theme = theme_source.read_text()
shutil.copy2(theme_source, root / "Theme.kt")
for document in ("PRODUCT.md", "DESIGN.md"):
    shutil.copy2(repo / document, root / document)
tokens = {}
for name in ("Light", "Dark"):
    block = re.search(rf"private val {name} = \w+ColorScheme\((.*?)\n\)", theme, re.S).group(1)
    tokens[name.lower()] = {key: f"#{value}" for key, value in
                           re.findall(r"(\w+) = Color\(0xFF([0-9A-F]{6})\)", block)}
(root / "color-tokens.json").write_text(json.dumps({
    "colorSpace": "sRGB", "alpha": "FF (opaque)",
    "source": str(theme_source), "sourceCommit": commit,
    "explicitTokens": tokens,
    "unlistedRoles": "Inherited from pinned Material 3 lightColorScheme/darkColorScheme defaults.",
    "selection": "System light/dark; no dynamic device palette or forced dark override.",
}, indent=2) + "\n")

pairs = [
    ("today", "Today, no active workout", 1.0),
    ("workout-rest", "Active Upper, previous results and running rest", 1.0),
    ("history-detail", "Read-first completed Upper workout", 1.0),
    ("history", "History workout list", 1.0),
    ("entry-200", "Active entry at 200% font scale", 2.0),
]
captures = []
events = [json.loads(line) for line in (root / "interactions.jsonl").read_text().splitlines()]
for screen, label, scale in pairs:
    for appearance in ("dark", "light"):
        name = f"{appearance}-{screen}"
        data = json.loads((root / f"{name}.json").read_text())
        assert float(data["fontScale"]) == scale
        assert data["night"] == f"Night mode: {'yes' if appearance == 'dark' else 'no'}"
        image = Image.open(root / f"{name}.png")
        assert image.size == (1080, 2400)
        assert data["density"] == 420
        captures.append({
            "id": name, "screen": label, "theme": appearance, "fontScale": scale,
            "screenshot": str(root / f"{name}.png"),
            "hierarchy": str(root / f"{name}.xml"),
            "configurationAndNodes": str(root / f"{name}.json"),
            "capturedAt": next(e["at"] for e in events if e["action"] == "capture" and e["name"] == name),
            "sha256": hashlib.sha256((root / f"{name}.png").read_bytes()).hexdigest(),
        })

def contact_sheet(filename, selected):
    width, height, margin, title = 324, 720, 18, 56
    sheet = Image.new("RGB", (width * 2 + margin * 3, (height + title + margin) * len(selected) + margin),
                      "#D9D9D9")
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default(size=15)
    for row, (screen, label, scale) in enumerate(selected):
        top = margin + row * (height + title + margin)
        for column, appearance in enumerate(("dark", "light")):
            left = margin + column * (width + margin)
            draw.text((left, top), f"{appearance.title()} | {screen} | {int(scale * 100)}%", fill="#222222", font=font)
            draw.text((left, top + 22), "Rebuild 0.1.3 / API 34", fill="#222222", font=font)
            with Image.open(root / f"{appearance}-{screen}.png") as image:
                sheet.paste(image.convert("RGB").resize((width, height), Image.Resampling.LANCZOS),
                            (left, top + title))
    sheet.save(root / filename)

contact_sheet("contact-sheet.png", pairs[:3])
contact_sheet("supplemental-contact-sheet.png", pairs[3:])
manifest = {
    "purpose": "Neutral current-app visual evidence. No aesthetic ratings or recommendations.",
    "sourceCommit": commit, "worktreeClean": True, "package": "com.rebuild",
    "versionName": "0.1.3", "versionCode": 4, "apkPath": str(apk), "apkSha256": apk_hash,
    "apkBytes": apk.stat().st_size,
    "device": {"api": 34, "serial": "emulator-5592",
               "fingerprint": (root / "device-fingerprint.txt").read_text().strip(),
               "pixels": [1080, 2400], "densityDpi": 420, "orientation": "portrait",
               "language": "English", "timezone": "device +03:00",
               "navigation": "gesture", "cores": 2, "memoryMiB": 2048},
    "fixture": {"path": str(root / "synthetic-baseline.json"), "syntheticOnly": True,
                "phase": "INTRO_TWO", "history": "Two partial Upper workouts, Sep 19 and Sep 26.",
                "previousChestPress": "42.5 kg x 10 @ 2 RIR; 42.5 kg x 9 @ 2 RIR",
                "active": "New Upper; one 42.5 kg x 10 working set logged through UI; optional RIR blank.",
                "rest": "180-second default auto-started by that working set.",
                "import": "Real Android Files restore with mandatory saved safety copy.",
                "plan": "Unedited 0.1.3 templates and planRevision 1, including prescribed Wednesday pulldown."},
    "captures": captures,
    "contactSheets": [str(root / "contact-sheet.png"), str(root / "supplemental-contact-sheet.png")],
    "themeTokens": str(root / "color-tokens.json"),
    "limitations": [
        "Native emulator screenshots, not physical Galaxy A71 or TalkBack speech evidence.",
        "One portrait viewport; keyboard is closed in all review captures.",
        "Normal and selected 200% states only; not an exhaustive responsive/state matrix.",
        "Timer seconds and status clock advance between paired captures; no composited app pixels.",
        "Notifications/exact alarms were not granted; actual Alerts off state is visible.",
        "History contains partial synthetic workouts and naturally scrolls beyond the captured viewport.",
        "Contact sheets are scaled copies; original full-resolution PNGs are authoritative.",
        "A boot System UI ANR dialog was dismissed; an invalid draft fixture was rejected before the valid import. Setup diagnostics are separate.",
        "No new test-suite run or aesthetic scoring was performed for this prep-only packet.",
    ],
    "implementation": "No repository edits. Awaiting parent council synthesis before 0.1.4 work.",
}
(root / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
diagnostics = root / "setup-diagnostics"
diagnostics.mkdir(exist_ok=True)
for path in root.glob("setup-*"):
    if path.is_file():
        shutil.move(path, diagnostics / path.name)
for path in root.glob("*-ime.txt"):
    shutil.move(path, diagnostics / path.name)
paths = sorted(p for p in root.rglob("*") if p.is_file() and p.name != "SHA256SUMS")
(root / "SHA256SUMS").write_text("".join(
    f"{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.relative_to(root)}\n" for p in paths))
print(f"Frozen {len(captures)} native screenshots, 2 contact sheets, {len(paths)} checksummed files.")
