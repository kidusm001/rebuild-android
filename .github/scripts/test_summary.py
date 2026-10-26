"""Writes a Markdown summary of JUnit XML results for the GitHub Actions job page.

usage: test_summary.py TITLE DIRECTORY   (searches DIRECTORY recursively for TEST-*.xml)
"""
import pathlib
import sys
import xml.etree.ElementTree as ET

title, root = sys.argv[1], pathlib.Path(sys.argv[2])
totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
failed = []
for path in sorted(root.rglob("TEST-*.xml")):
    suite = ET.parse(path).getroot()
    for key in totals:
        totals[key] += int(suite.get(key, 0))
    for case in suite.iter("testcase"):
        if case.find("failure") is not None or case.find("error") is not None:
            failed.append(f"{case.get('classname', '').rsplit('.', 1)[-1]}.{case.get('name')}")

if totals["tests"] == 0:
    print(f"### {title}\n\nNo results found.")
    sys.exit(0)
passed = totals["tests"] - totals["failures"] - totals["errors"] - totals["skipped"]
mark = "✅" if not failed else "❌"
print(f"### {mark} {title}\n")
print(f"{passed} passed, {totals['failures'] + totals['errors']} failed, {totals['skipped']} skipped.\n")
for name in failed[:50]:
    print(f"- `{name}`")
