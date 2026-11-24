#!/usr/bin/env python3
"""Antigravity PreToolUse guardrails for rebuild-android.

Enforces three hard project rules from AGENTS.md:
1. Never add AI attribution (Co-Authored-By / Generated with) to commits or PRs/issues
   (checks both inline command arguments and referenced --body-file / -F / --file files).
2. Never commit directly to `main` (tracks branch switches in execution order).
3. Never uninstall or clear com.rebuild data on a physical (non-emulator) device
   (checks each `adb` invocation independently).
"""

import json
import os
import re
import shlex
import subprocess
import sys

AI_ATTRIBUTION_PATTERNS = (
    re.compile(
        r"Co-Authored-By\s*:[^\n]*(Claude|Anthropic|Antigravity|Gemini|OpenAI|Codex|ChatGPT|Copilot)",
        re.IGNORECASE,
    ),
    re.compile(
        r"Generated\s+with\s+(Claude|Antigravity|Gemini|AI)",
        re.IGNORECASE,
    ),
)

SEGMENT_SEPARATORS = {";", "&&", "||", "|", "&", "\n"}
ENV_ASSIGN_RE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*=(.*)$")


def allow() -> None:
    print(json.dumps({"decision": "allow"}))
    sys.exit(0)


def deny(reason: str) -> None:
    print(json.dumps({"decision": "deny", "reason": reason}))
    sys.exit(0)


def has_ai_attribution(text: str) -> bool:
    return any(p.search(text) for p in AI_ATTRIBUTION_PATTERNS)


def split_segments(cmd: str) -> list[list[str]]:
    """Split a shell command string into token lists per command segment."""
    try:
        lexer = shlex.shlex(cmd, posix=True, punctuation_chars=";&|\n")
        lexer.whitespace = " \t\r"
        lexer.whitespace_split = False
        tokens = list(lexer)
    except ValueError:
        # Fallback for unbalanced quotes/heredocs: split on common shell operators.
        raw_parts = re.split(r"(?:&&|\|\||[;\n|])", cmd)
        segments = []
        for part in raw_parts:
            part = part.strip()
            if part:
                segments.append(part.split())
        return segments

    segments: list[list[str]] = []
    current: list[str] = []
    for tok in tokens:
        if tok in SEGMENT_SEPARATORS or set(tok) <= set(";&|\n"):
            if current:
                segments.append(current)
                current = []
        else:
            current.append(tok)
    if current:
        segments.append(current)
    return segments


def strip_leading_env(tokens: list[str]) -> tuple[dict[str, str], list[str]]:
    """Separate leading VAR=VAL assignments from the command tokens."""
    env: dict[str, str] = {}
    idx = 0
    while idx < len(tokens):
        m = ENV_ASSIGN_RE.match(tokens[idx])
        if not m:
            break
        key = tokens[idx].split("=", 1)[0]
        env[key] = m.group(1)
        idx += 1
    return env, tokens[idx:]


def parse_git_invocation(tokens: list[str], cwd: str) -> tuple[str | None, list[str], str]:
    """Return (subcommand, subcommand_args, repo_cwd) for a git command segment."""
    if not tokens or os.path.basename(tokens[0]) != "git":
        return None, [], cwd

    repo_cwd = cwd
    i = 1
    opts_with_arg = {"-C", "-c", "--git-dir", "--work-tree", "--namespace", "--config-env"}
    while i < len(tokens):
        tok = tokens[i]
        if tok == "-C" and i + 1 < len(tokens):
            target = os.path.expanduser(tokens[i + 1])
            repo_cwd = target if os.path.isabs(target) else os.path.normpath(os.path.join(repo_cwd, target))
            i += 2
            continue
        if tok in opts_with_arg and i + 1 < len(tokens):
            i += 2
            continue
        if tok.startswith("-"):
            i += 1
            continue
        return tok, tokens[i + 1 :], repo_cwd
    return None, [], repo_cwd


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


def extract_option_values(args: list[str], short_flags: set[str], long_flags: set[str]) -> list[str]:
    """Extract values passed to any matching short or long CLI flags."""
    values: list[str] = []
    i = 0
    while i < len(args):
        tok = args[i]
        if tok == "--":
            break
        if tok in short_flags or tok in long_flags:
            if i + 1 < len(args):
                values.append(args[i + 1])
                i += 2
                continue
        else:
            matched = False
            for lf in long_flags:
                prefix = f"{lf}="
                if tok.startswith(prefix):
                    values.append(tok[len(prefix) :])
                    matched = True
                    break
            if not matched:
                for sf in short_flags:
                    if len(sf) == 2 and tok.startswith(sf) and len(tok) > 2:
                        values.append(tok[2:])
                        break
        i += 1
    return values


def read_text_safely(path_str: str, cwd: str, max_bytes: int = 262_144) -> str:
    if not path_str or path_str == "-":
        return ""
    expanded = os.path.expanduser(path_str)
    full_path = expanded if os.path.isabs(expanded) else os.path.join(cwd, expanded)
    try:
        with open(full_path, "r", encoding="utf-8", errors="ignore") as f:
            return f.read(max_bytes)
    except OSError:
        return ""


def branch_switched_to(subcmd: str, args: list[str], repo_cwd: str) -> str | None:
    """Return the target branch name if this git switch/checkout actually changes branches."""
    if "--help" in args or "-h" in args:
        return None

    if subcmd == "switch":
        created = extract_option_values(args, {"-c", "-C"}, {"--create", "--force-create"})
        if created:
            return created[0]
        if "--detach" in args or "-d" in args:
            return "__detached__"
        # First positional argument before `--`
        i = 0
        opts_with_arg = {"-c", "-C", "--create", "--force-create", "--orphan", "--conflict"}
        while i < len(args):
            tok = args[i]
            if tok == "--":
                break
            if tok in opts_with_arg:
                i += 2
                continue
            if tok.startswith("-"):
                i += 1
                continue
            return tok
        return None

    if subcmd == "checkout":
        created = extract_option_values(args, {"-b", "-B"}, set())
        if created:
            return created[0]
        if "--" in args or "-p" in args or "--patch" in args:
            # File restoration (`git checkout -- README.md`) or patch checkout does not switch branches.
            return None
        if "--detach" in args or "-d" in args:
            return "__detached__"
        opts_with_arg = {"-b", "-B", "--orphan", "--conflict", "--pathspec-from-file"}
        positionals: list[str] = []
        i = 0
        while i < len(args):
            tok = args[i]
            if tok in opts_with_arg:
                i += 2
                continue
            if tok.startswith("-"):
                i += 1
                continue
            positionals.append(tok)
            i += 1
        if len(positionals) == 1:
            candidate = positionals[0]
            # If the single positional exists as a path in the working tree, treat as file checkout.
            if not os.path.exists(os.path.join(repo_cwd, candidate)):
                return candidate
        return None

    return None


def check_adb_segment(env: dict[str, str], tokens: list[str]) -> None:
    """Deny uninstalling or wiping com.rebuild on a physical (non-emulator) device."""
    if not tokens or os.path.basename(tokens[0]) != "adb":
        return

    serial: str | None = env.get("ANDROID_SERIAL")
    force_emulator = False
    force_usb = False

    i = 1
    opts_with_arg = {"-s", "-t", "-H", "-P", "-L"}
    while i < len(tokens):
        tok = tokens[i]
        if tok == "-s" and i + 1 < len(tokens):
            serial = tokens[i + 1]
            i += 2
            continue
        if tok.startswith("-s") and len(tok) > 2:
            serial = tok[2:]
            i += 1
            continue
        if tok == "-e":
            force_emulator = True
            i += 1
            continue
        if tok == "-d":
            force_usb = True
            i += 1
            continue
        if tok in opts_with_arg and i + 1 < len(tokens):
            i += 2
            continue
        if tok.startswith("-"):
            i += 1
            continue
        break

    if i >= len(tokens):
        return

    subcmd = tokens[i]
    subargs = tokens[i + 1 :]
    joined = " ".join(subargs)

    is_destructive = False
    if subcmd == "uninstall" and re.search(r"(^|\s)com\.rebuild(\s|$)", joined):
        is_destructive = True
    elif subcmd == "shell" and re.search(
        r"\bpm\s+(clear|uninstall)\b[^\n]*\bcom\.rebuild\b", joined
    ):
        is_destructive = True

    if not is_destructive:
        return

    if force_usb:
        deny(
            "Blocked by rebuild-guardrails: refusing to uninstall or wipe com.rebuild "
            "on a USB physical device (`adb -d`). Export a backup first; never uninstall "
            "to work around an install error."
        )

    if force_emulator:
        return

    if serial is not None:
        if not serial.startswith("emulator-"):
            deny(
                f"Blocked by rebuild-guardrails: refusing to uninstall or wipe com.rebuild "
                f"on physical device `{serial}`. Export a backup first; never uninstall to "
                f"work around an install error."
            )
        return

    physical = connected_physical_devices()
    if physical:
        deny(
            f"Blocked by rebuild-guardrails: physical device(s) {physical} attached and "
            f"no `-s emulator-...` serial was specified. Refusing to uninstall or wipe com.rebuild."
        )


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
    current_cwd = payload.get("cwd") or "."

    segments = split_segments(cmd)

    # Track effective branch per repo directory as commands execute in order.
    branch_by_repo: dict[str, str] = {}

    for seg in segments:
        env, tokens = strip_leading_env(seg)
        if not tokens:
            continue

        exe = os.path.basename(tokens[0])

        # Track `cd <dir>` so relative paths and branch checks use the right working directory.
        if exe == "cd" and len(tokens) >= 2 and not tokens[1].startswith("-"):
            target = os.path.expanduser(tokens[1])
            current_cwd = target if os.path.isabs(target) else os.path.normpath(os.path.join(current_cwd, target))
            continue

        # 1. Check git commands (attribution + no commit on main) in execution order.
        git_subcmd, git_args, repo_cwd = parse_git_invocation(tokens, current_cwd)
        if git_subcmd is not None:
            if git_subcmd in {"switch", "checkout"}:
                switched = branch_switched_to(git_subcmd, git_args, repo_cwd)
                if switched is not None:
                    branch_by_repo[repo_cwd] = switched
            elif git_subcmd == "commit" and "--help" not in git_args and "-h" not in git_args:
                # 1a. Inline attribution check (also check full cmd for heredocs like `git commit -F - <<EOF`).
                seg_text = " ".join(tokens)
                if has_ai_attribution(seg_text) or (
                    ("<<" in cmd) and has_ai_attribution(cmd)
                ):
                    deny(
                        "Blocked by rebuild-guardrails: never add AI co-author trailers or "
                        "'Generated with ...' attribution to commits (see AGENTS.md)."
                    )
                # 1b. File-backed commit message attribution check (`-F` / `--file`).
                for msg_file in extract_option_values(git_args, {"-F"}, {"--file"}):
                    if has_ai_attribution(read_text_safely(msg_file, repo_cwd)):
                        deny(
                            f"Blocked by rebuild-guardrails: commit message file `{msg_file}` "
                            f"contains forbidden AI attribution (see AGENTS.md)."
                        )
                # 2. No direct commits to `main`.
                if repo_cwd not in branch_by_repo:
                    branch_by_repo[repo_cwd] = current_git_branch(repo_cwd)
                if branch_by_repo[repo_cwd] == "main":
                    deny(
                        "Blocked by rebuild-guardrails: never commit directly to `main`. "
                        "Create a topic branch first (`git switch -c <branch>`)."
                    )

        # 1c. Check `gh pr|issue create|edit|comment` attribution (inline + `--body-file` / `-F`).
        if exe == "gh" and len(tokens) >= 3 and tokens[1] in {"pr", "issue"} and tokens[2] in {"create", "edit", "comment"}:
            gh_args = tokens[3:]
            if has_ai_attribution(" ".join(tokens)) or (("<<" in cmd) and has_ai_attribution(cmd)):
                deny(
                    "Blocked by rebuild-guardrails: never add AI attribution to PRs or issues (see AGENTS.md)."
                )
            for body_file in extract_option_values(gh_args, {"-F"}, {"--body-file"}):
                if has_ai_attribution(read_text_safely(body_file, current_cwd)):
                    deny(
                        f"Blocked by rebuild-guardrails: body file `{body_file}` contains "
                        f"forbidden AI attribution (see AGENTS.md)."
                    )

        # 3. Check each `adb` call independently for physical-device data wipes.
        if exe == "adb":
            check_adb_segment(env, tokens)

    allow()


if __name__ == "__main__":
    main()
