#!/usr/bin/env python3
"""Antigravity PreToolUse guardrails for rebuild-android.

Enforces three hard project rules from AGENTS.md:
1. Never add AI attribution (Co-Authored-By / Generated with) to commits or PRs/issues.
2. Never commit directly to `main`.
3. Never uninstall or clear com.rebuild data on a physical (non-emulator) device.
"""

import json
import re
import subprocess
import sys


def allow() -> None:
    print(json.dumps({"decision": "allow"}))
    sys.exit(0)


def deny(reason: str) -> None:
    print(json.dumps({"decision": "deny", "reason": reason}))
    sys.exit(0)


def current_git_branch(cwd: str) -> str:
    try:
        res = subprocess.run(
            ["git", "-C", cwd, "symbolic-ref", "--short", "HEAD"],
            capture_output=True,
            text=True,
            timeout=2,
            check=False,
        )
        return res.stdout.strip()
    except Exception:
        return ""


def connected_physical_devices() -> list[str]:
    try:
        res = subprocess.run(
            ["adb", "devices"],
            capture_output=True,
            text=True,
            timeout=2,
            check=False,
        )
        physical = []
        for line in res.stdout.splitlines()[1:]:
            parts = line.strip().split()
            if len(parts) >= 2 and parts[1] == "device" and not parts[0].startswith("emulator-"):
                physical.append(parts[0])
        return physical
    except Exception:
        return []


def main() -> None:
    try:
        payload = json.load(sys.stdin)
    except Exception:
        allow()

    tool_call = payload.get("toolCall") or {}
    if tool_call.get("name") != "run_command":
        allow()

    args = tool_call.get("args") or {}
    cmd = args.get("CommandLine") or ""
    cwd = payload.get("cwd") or "."

    # 1. Block AI attribution trailers in git commits / gh pr / gh issue commands.
    is_commit_or_gh = bool(
        re.search(r"\bgit\b[^\n]*\bcommit\b", cmd)
        or re.search(r"\bgh\s+(pr|issue)\s+(create|edit|comment)\b", cmd)
    )
    if is_commit_or_gh:
        if re.search(
            r"Co-Authored-By\s*:[^\n]*(Claude|Anthropic|Antigravity|Gemini|OpenAI|Codex|ChatGPT|Copilot)",
            cmd,
            re.IGNORECASE,
        ) or re.search(
            r"Generated\s+with\s+(Claude|Antigravity|Gemini|AI)",
            cmd,
            re.IGNORECASE,
        ):
            deny(
                "Blocked by rebuild-guardrails: never add AI co-author trailers or "
                "'Generated with ...' attribution to commits, PRs, or issues (see AGENTS.md)."
            )

    # 2. Block committing directly to `main` (unless the command first creates/switches branch).
    if re.search(r"\bgit\b(?![^\n]*--help)[^\n]*\bcommit\b", cmd):
        switches_branch_first = bool(
            re.search(r"\bgit\b[^\n]*(switch\s+-c|checkout\s+-b|switch\s+(?!main\b)|checkout\s+(?!main\b))", cmd)
        )
        if not switches_branch_first and current_git_branch(cwd) == "main":
            deny(
                "Blocked by rebuild-guardrails: never commit directly to `main`. "
                "Create a topic branch first (`git switch -c <branch>`)."
            )

    # 3. Block uninstalling or clearing com.rebuild on a physical (non-emulator) device.
    is_destructive_adb = bool(
        re.search(r"\badb\b[^\n]*\b(uninstall|pm\s+clear)\b[^\n]*\bcom\.rebuild\b", cmd)
    )
    if is_destructive_adb:
        serial_match = re.search(r"\badb\s+-s\s+(\S+)", cmd)
        if serial_match:
            serial = serial_match.group(1)
            if not serial.startswith("emulator-"):
                deny(
                    f"Blocked by rebuild-guardrails: refusing to uninstall or wipe com.rebuild "
                    f"on physical device `{serial}`. Export a backup first; never uninstall to "
                    f"work around an install error."
                )
        else:
            physical = connected_physical_devices()
            if physical:
                deny(
                    f"Blocked by rebuild-guardrails: physical device(s) {physical} attached and "
                    f"no `-s emulator-...` serial was specified. Refusing to uninstall or wipe com.rebuild."
                )

    allow()


if __name__ == "__main__":
    main()
