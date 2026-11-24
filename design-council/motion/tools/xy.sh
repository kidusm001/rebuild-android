#!/usr/bin/env bash
# xy.sh <handle>: centre "x y" of the first node matching (tag, exact text, desc, substring), like ui.sh.
for a in 1 2 3; do adb shell rm -f /sdcard/rv.xml >/dev/null 2>&1; adb shell uiautomator dump /sdcard/rv.xml 2>&1 | grep -q "dumped to" && break; sleep 0.7; done
adb exec-out cat /sdcard/rv.xml | python3 -c '
import re, sys
q = sys.argv[1]; nodes = []
for m in re.finditer(r"<node [^>]*>", sys.stdin.read()):
    n = m.group(0); get = lambda k: (re.search(k + r"=\"([^\"]*)\"", n) or [None, ""])[1]
    rid, text, desc = get("resource-id"), get("text"), get("content-desc")
    b = list(map(int, re.findall(r"-?\d+", get("bounds"))))
    if len(b) == 4 and b[2] > b[0] and b[3] > b[1] and b[0] >= 0 and b[1] >= 0: nodes.append((rid, text, desc, b))
for test in (lambda r, t, d: r == q or r.endswith(":id/" + q), lambda r, t, d: t == q, lambda r, t, d: d == q, lambda r, t, d: q in t or q in d):
    for r, t, d, b in nodes:
        if test(r, t, d): print((b[0] + b[2]) // 2, (b[1] + b[3]) // 2); sys.exit()
' "$1"
