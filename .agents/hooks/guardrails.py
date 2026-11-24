#!/usr/bin/env python3
"""Antigravity PreToolUse guardrails for rebuild-android.

Enforces three hard project rules from AGENTS.md:
1. Never add AI attribution (Co-Authored-By / Generated with) to commits or PRs/issues
   (checks both inline command arguments and referenced --body-file / -F / --file files).
2. Never commit directly to `main` (tracks branch switches in execution order).
3. Never uninstall or clear com.rebuild data on a physical (non-emulator) device
   (checks each `adb` invocation independently, including inside shell/command wrappers).
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

ENV_ASSIGN_RE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*=(.*)$")
SHELL_NAMES = {"sh", "bash", "zsh", "dash", "ash", "ksh"}
MAX_RECURSION_DEPTH = 5


def allow() -> None:
    print(json.dumps({"decision": "allow"}))
    sys.exit(0)


def deny(reason: str) -> None:
    print(json.dumps({"decision": "deny", "reason": reason}))
    sys.exit(0)


def has_ai_attribution(text: str) -> bool:
    return any(p.search(text) for p in AI_ATTRIBUTION_PATTERNS)


def find_quoted_heredoc_delims(line: str) -> list[tuple[str, bool]]:
    """Find quoted heredoc openers (`<<'EOF'`, `<<"EOF"`, `<<\\EOF`, or `<<-...`) outside quotes and comments."""
    delims: list[tuple[str, bool]] = []
    i = 0
    n = len(line)
    in_single = False
    in_double = False

    while i < n:
        ch = line[i]
        if in_single:
            if ch == "'":
                in_single = False
            i += 1
            continue
        if in_double:
            if ch == "\\" and i + 1 < n:
                i += 2
                continue
            if ch == '"':
                in_double = False
            i += 1
            continue

        if ch == "\\" and i + 1 < n:
            i += 2
            continue
        if ch == "'":
            in_single = True
            i += 1
            continue
        if ch == '"':
            in_double = True
            i += 1
            continue
        if ch == "#" and (i == 0 or line[i - 1] in " \t;|&("):
            break

        if ch == "<" and i + 1 < n and line[i + 1] == "<":
            # Skip `<<<` (here-string).
            if i + 2 < n and line[i + 2] == "<":
                i += 3
                continue
            j = i + 2
            strip_tabs = False
            if j < n and line[j] == "-":
                strip_tabs = True
                j += 1
            while j < n and line[j] in " \t":
                j += 1
            if j < n and line[j] in ("'", '"'):
                quote = line[j]
                k = j + 1
                while k < n and line[k] != quote:
                    k += 1
                if k < n and k > j + 1:
                    delims.append((line[j + 1 : k], strip_tabs))
                    i = k + 1
                    continue
            elif j < n and line[j] == "\\":
                k = j + 1
                while k < n and line[k] not in " \t\r\n;|&()<>":
                    k += 1
                if k > j + 1:
                    delims.append((line[j + 1 : k], strip_tabs))
                    i = k
                    continue
            i = j
            continue

        i += 1

    return delims


def strip_quoted_heredocs(cmd: str) -> str:
    """Remove bodies of real quoted heredocs (`<<'EOF' ... EOF`) only when properly closed.

    Openers inside quotes or comments are ignored, and an unclosed heredoc never drops
    subsequent lines (fail-closed).
    """
    lines = cmd.splitlines(keepends=True)
    out: list[str] = []
    i = 0
    while i < len(lines):
        line = lines[i]
        out.append(line)
        delims = find_quoted_heredoc_delims(line)
        i += 1
        for delim, strip_tabs in delims:
            close_idx: int | None = None
            for k in range(i, len(lines)):
                candidate = lines[k].rstrip("\r\n")
                if strip_tabs:
                    candidate = candidate.lstrip("\t")
                if candidate == delim:
                    close_idx = k
                    break
            if close_idx is not None:
                i = close_idx + 1
    return "".join(out)


def split_top_level_commands(cmd: str) -> list[tuple[str, str, str]]:
    """Split a shell command string into `(preceding_op, raw_segment, following_op)` tuples.

    Respects quotes, escapes, comments, `$(...)`, and parenthesized subshells `(...)` so
    operators inside subshells are not flattened into the parent shell.
    """
    segments: list[tuple[str, str, str]] = []
    buf: list[str] = []
    prev_op = ""
    i = 0
    n = len(cmd)
    in_single = False
    in_double = False
    paren_depth = 0
    brace_depth = 0

    def flush(next_op: str) -> None:
        nonlocal prev_op
        text = "".join(buf).strip()
        buf.clear()
        if text:
            segments.append((prev_op, text, next_op))
        prev_op = next_op

    while i < n:
        ch = cmd[i]
        if in_single:
            buf.append(ch)
            if ch == "'":
                in_single = False
            i += 1
            continue
        if in_double:
            if ch == "\\" and i + 1 < n:
                buf.append(ch)
                buf.append(cmd[i + 1])
                i += 2
                continue
            buf.append(ch)
            if ch == '"':
                in_double = False
            i += 1
            continue

        if ch == "\\" and i + 1 < n:
            buf.append(ch)
            buf.append(cmd[i + 1])
            i += 2
            continue
        if ch == "'":
            in_single = True
            buf.append(ch)
            i += 1
            continue
        if ch == '"':
            in_double = True
            buf.append(ch)
            i += 1
            continue
        if ch == "#" and paren_depth == 0 and (not buf or buf[-1] in " \t\n;|&("):
            # Skip shell comment until newline.
            while i < n and cmd[i] != "\n":
                i += 1
            continue

        if ch == "(":
            paren_depth += 1
            buf.append(ch)
            i += 1
            continue
        if ch == ")" and paren_depth > 0:
            paren_depth -= 1
            buf.append(ch)
            i += 1
            continue

        if ch == "{" and paren_depth == 0 and (not buf or buf[-1] in " \t\n;|&"):
            if i + 1 == n or cmd[i + 1] in " \t\n":
                brace_depth += 1
                buf.append(ch)
                i += 1
                continue
        if ch == "}" and paren_depth == 0 and brace_depth > 0 and (not buf or buf[-1] in " \t\n;"):
            brace_depth -= 1
            buf.append(ch)
            i += 1
            continue

        if paren_depth == 0 and brace_depth == 0:
            if ch == "\n":
                flush("\n")
                i += 1
                continue
            if ch == ";":
                flush(";")
                i += 1
                continue
            if ch == "&":
                if i + 1 < n and cmd[i + 1] == "&":
                    flush("&&")
                    i += 2
                    continue
                flush("&")
                i += 1
                continue
            if ch == "|":
                if i + 1 < n and cmd[i + 1] == "|":
                    flush("||")
                    i += 2
                    continue
                flush("|")
                i += 1
                continue

        buf.append(ch)
        i += 1

    flush("")
    return segments


def tokenize_segment(seg_text: str) -> list[str]:
    """Tokenize one command segment while keeping unquoted colons/dots/slashes intact."""
    try:
        lexer = shlex.shlex(seg_text, posix=True)
        lexer.whitespace_split = True
        lexer.commenters = ""
        return list(lexer)
    except ValueError:
        return seg_text.split()


def is_wrapped_group(seg_text: str, open_ch: str, close_ch: str) -> str | None:
    """If seg_text starts with a balanced `( ... )` or `{ ... }` group (optionally followed by redirections), return its inner body."""
    s = seg_text.strip()
    if len(s) < 2 or s[0] != open_ch:
        return None
    depth = 0
    in_single = False
    in_double = False
    i = 0
    n = len(s)
    end_idx: int | None = None
    while i < n:
        ch = s[i]
        if in_single:
            if ch == "'":
                in_single = False
            i += 1
            continue
        if in_double:
            if ch == "\\" and i + 1 < n:
                i += 2
                continue
            if ch == '"':
                in_double = False
            i += 1
            continue
        if ch == "\\" and i + 1 < n:
            i += 2
            continue
        if ch == "'":
            in_single = True
            i += 1
            continue
        if ch == '"':
            in_double = True
            i += 1
            continue
        if ch == open_ch:
            depth += 1
        elif ch == close_ch:
            depth -= 1
            if depth == 0:
                end_idx = i
                break
        i += 1
    if end_idx is None or depth != 0:
        return None
    tail = s[end_idx + 1 :].strip()
    if tail and not re.match(r"^(?:\d*[<>]|&>)", tail):
        return None
    return s[1:end_idx].strip()


REDIR_OP_ONLY_RE = re.compile(r"^(?:\d*(?:>>?|<<-?|<>|<&|>&)|&>>?)$")
REDIR_WITH_TARGET_RE = re.compile(r"^(?:\d*(?:>>?|<<-?|<>|<&|>&)|&>>?).+$")


def strip_leading_redirections(tokens: list[str]) -> list[str]:
    """Strip leading shell redirections like `>/dev/null` or `2> /dev/null` before a command."""
    i = 0
    while i < len(tokens):
        tok = tokens[i]
        if REDIR_OP_ONLY_RE.match(tok):
            i += 2
            continue
        if REDIR_WITH_TARGET_RE.match(tok):
            i += 1
            continue
        break
    return tokens[i:]


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


def unwrap_command_prefixes(
    env: dict[str, str], tokens: list[str], cwd: str
) -> tuple[dict[str, str], list[str], str]:
    """Unwrap command prefix wrappers (`env`, `timeout`, `sudo`, `nohup`, `command`, `nice`, `time`, `exec`)."""
    merged_env = dict(env)
    cur = list(tokens)
    cur_cwd = cwd

    for _ in range(8):
        cur = strip_leading_redirections(cur)
        more_env, cur = strip_leading_env(cur)
        merged_env.update(more_env)
        cur = strip_leading_redirections(cur)
        if not cur:
            break

        exe = os.path.basename(cur[0])
        rest = cur[1:]

        if exe in {"command", "nohup", "exec", "time"}:
            i = 0
            while i < len(rest) and rest[i].startswith("-"):
                if rest[i] == "--":
                    i += 1
                    break
                i += 1
            cur = rest[i:]
            continue

        if exe == "env":
            i = 0
            while i < len(rest):
                tok = rest[i]
                if tok == "--":
                    i += 1
                    break
                if tok in {"-u", "--unset"} and i + 1 < len(rest):
                    merged_env.pop(rest[i + 1], None)
                    i += 2
                    continue
                if tok in {"-C", "--chdir"} and i + 1 < len(rest):
                    target = os.path.expanduser(rest[i + 1])
                    cur_cwd = target if os.path.isabs(target) else os.path.normpath(os.path.join(cur_cwd, target))
                    i += 2
                    continue
                if tok == "-i" or tok == "--ignore-environment":
                    merged_env.clear()
                    i += 1
                    continue
                if tok == "-S" and i + 1 < len(rest):
                    try:
                        expanded = shlex.split(rest[i + 1])
                    except ValueError:
                        expanded = rest[i + 1].split()
                    rest = expanded + rest[i + 2 :]
                    i = 0
                    continue
                if tok.startswith("-"):
                    i += 1
                    continue
                m = ENV_ASSIGN_RE.match(tok)
                if m:
                    merged_env[tok.split("=", 1)[0]] = m.group(1)
                    i += 1
                    continue
                break
            cur = rest[i:]
            continue

        if exe == "timeout":
            i = 0
            opts_with_arg = {"-k", "--kill-after", "-s", "--signal"}
            while i < len(rest):
                tok = rest[i]
                if tok == "--":
                    i += 1
                    break
                if tok in opts_with_arg and i + 1 < len(rest):
                    i += 2
                    continue
                if tok.startswith("-"):
                    i += 1
                    continue
                i += 1
                break
            cur = rest[i:]
            continue

        if exe == "nice":
            i = 0
            while i < len(rest) and rest[i].startswith("-"):
                if rest[i] == "--":
                    i += 1
                    break
                if rest[i] == "-n" and i + 1 < len(rest):
                    i += 2
                    continue
                i += 1
            cur = rest[i:]
            continue

        if exe == "sudo":
            i = 0
            opts_with_arg = {"-u", "-g", "-C", "-p", "-h", "-r", "-t", "-T", "-U", "-D"}
            while i < len(rest):
                tok = rest[i]
                if tok == "--":
                    i += 1
                    break
                if tok in opts_with_arg and i + 1 < len(rest):
                    i += 2
                    continue
                if tok.startswith("-"):
                    i += 1
                    continue
                m = ENV_ASSIGN_RE.match(tok)
                if m:
                    merged_env[tok.split("=", 1)[0]] = m.group(1)
                    i += 1
                    continue
                break
            cur = rest[i:]
            continue

        break

    return merged_env, cur, cur_cwd


def extract_nested_shell_command(tokens: list[str]) -> str | None:
    """If tokens invoke `sh -c '...'` / `bash -lc '...'` / `eval ...`, return the inner command string."""
    if not tokens:
        return None
    exe = os.path.basename(tokens[0])
    args = tokens[1:]

    if exe == "eval" and args:
        return " ".join(args)

    if exe not in SHELL_NAMES:
        return None

    i = 0
    while i < len(args):
        tok = args[i]
        if tok == "--":
            break
        if (
            tok in {"-o", "+o"}
            or (tok.startswith(("-", "+")) and not tok.startswith("--") and tok.endswith("o"))
        ) and i + 1 < len(args):
            i += 2
            continue
        if tok == "-c" or (tok.startswith("-") and not tok.startswith("--") and "c" in tok[1:]):
            if i + 1 < len(args):
                return args[i + 1]
            return None
        if tok.startswith("-") or tok.startswith("+"):
            i += 1
            continue
        break
    return None


def extract_command_substitutions(cmd: str) -> list[str]:
    """Extract inner commands from `$(...)` and backtick substitutions (outside single quotes and quoted heredocs)."""
    cmd = strip_quoted_heredocs(cmd)
    results: list[str] = []
    i = 0
    in_single = False
    n = len(cmd)
    while i < n:
        ch = cmd[i]
        if ch == "\\" and not in_single and i + 1 < n:
            i += 2
            continue
        if ch == "'":
            in_single = not in_single
            i += 1
            continue
        if not in_single and ch == "$" and i + 1 < n and cmd[i + 1] == "(":
            depth = 1
            j = i + 2
            while j < n and depth > 0:
                if cmd[j] == "\\" and j + 1 < n:
                    j += 2
                    continue
                if cmd[j] == "(":
                    depth += 1
                elif cmd[j] == ")":
                    depth -= 1
                j += 1
            if depth == 0:
                inner = cmd[i + 2 : j - 1].strip()
                if inner:
                    results.append(inner)
                i = j
                continue
        if not in_single and ch == "`":
            j = cmd.find("`", i + 1)
            if j != -1:
                inner = cmd[i + 1 : j].strip()
                if inner:
                    results.append(inner)
                i = j + 1
                continue
        i += 1
    return results


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


_REPO_ROOT_CACHE: dict[str, str] = {}


def git_repo_root(cwd: str) -> str:
    """Canonicalize any directory inside a Git repo to its top-level root path."""
    norm = os.path.realpath(cwd)
    if norm in _REPO_ROOT_CACHE:
        return _REPO_ROOT_CACHE[norm]
    try:
        res = subprocess.run(
            ["git", "-C", norm, "rev-parse", "--show-toplevel"],
            capture_output=True,
            text=True,
            timeout=2,
            check=False,
        )
        root = os.path.realpath(res.stdout.strip()) if res.returncode == 0 and res.stdout.strip() else norm
    except Exception:
        root = norm
    _REPO_ROOT_CACHE[norm] = root
    return root


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


def merge_child_branches(
    parent: dict[str, str], child: dict[str, str], *, allow_safe: bool
) -> None:
    """Propagate Git branch changes from a completed child shell/subshell.

    Git's checked-out branch lives on disk and is shared with the parent shell.
    Always propagate switches to `main` (fail-closed); only propagate switches away
    from `main` when `allow_safe` is True.
    """
    for repo, branch in child.items():
        if branch == "main":
            parent[repo] = "main"
        elif allow_safe:
            parent[repo] = branch


def inspect_command(
    cmd: str,
    cwd: str,
    inherited_env: dict[str, str],
    branch_by_repo: dict[str, str],
    depth: int = 0,
) -> tuple[str, dict[str, str], dict[str, str]]:
    if depth > MAX_RECURSION_DEPTH:
        if re.search(r"\b(uninstall|pm\s+clear)\b[^\n]*\bcom\.rebuild\b", cmd):
            deny(
                "Blocked by rebuild-guardrails: deeply nested destructive com.rebuild command "
                "could not be safely verified."
            )
        return cwd, inherited_env, branch_by_repo

    current_cwd = cwd
    session_env = dict(inherited_env)
    branches = dict(branch_by_repo)

    # Inspect any command substitutions (`$(...)` or backticks) in isolated env/cwd,
    # carrying back any switch to `main` because Git's HEAD is shared on disk.
    for sub in extract_command_substitutions(cmd):
        _, _, sub_branches = inspect_command(sub, current_cwd, dict(session_env), dict(branches), depth + 1)
        merge_child_branches(branches, sub_branches, allow_safe=False)

    cleaned = strip_quoted_heredocs(cmd)
    segments = split_top_level_commands(cleaned)

    for prev_op, seg_text, next_op in segments:
        # Subshells `( ... )` run in an isolated child process: env and cwd never leak,
        # but switches to `main` modify `.git/HEAD` on disk and must carry back.
        subshell_inner = is_wrapped_group(seg_text, "(", ")")
        if subshell_inner is not None:
            _, _, child_branches = inspect_command(
                subshell_inner, current_cwd, dict(session_env), dict(branches), depth + 1
            )
            merge_child_branches(branches, child_branches, allow_safe=False)
            continue

        # Brace groups `{ ...; }` run in the current shell unless part of a pipeline/background/||.
        brace_inner = is_wrapped_group(seg_text, "{", "}")
        if brace_inner is not None:
            if prev_op in {"|", "&", "||"} or next_op in {"|", "&"}:
                _, _, child_branches = inspect_command(
                    brace_inner, current_cwd, dict(session_env), dict(branches), depth + 1
                )
                merge_child_branches(branches, child_branches, allow_safe=False)
            else:
                current_cwd, session_env, branches = inspect_command(
                    brace_inner, current_cwd, session_env, branches, depth + 1
                )
            continue

        tokens = strip_leading_redirections(tokenize_segment(seg_text))
        if not tokens:
            continue

        # Commands in pipelines (`|`), backgrounded (`&`), or on the RHS of `||` run in a
        # subshell or conditionally; never let them grant safe state (`ANDROID_SERIAL=emulator-...`
        # or branch switch) to subsequent parent commands.
        can_propagate_safe_state = prev_op not in {"|", "&", "||"} and next_op not in {"|", "&"}

        seg_env, tokens = strip_leading_env(tokens)
        if not tokens:
            for k, v in seg_env.items():
                if k == "ANDROID_SERIAL" and v.startswith("emulator-") and not can_propagate_safe_state:
                    continue
                session_env[k] = v
            continue

        first_exe = os.path.basename(tokens[0])
        if first_exe == "export":
            for arg in tokens[1:]:
                m = ENV_ASSIGN_RE.match(arg)
                if m:
                    k, v = arg.split("=", 1)[0], m.group(1)
                    if k == "ANDROID_SERIAL" and v.startswith("emulator-") and not can_propagate_safe_state:
                        continue
                    session_env[k] = v
            continue
        if first_exe == "unset":
            if can_propagate_safe_state:
                for arg in tokens[1:]:
                    if not arg.startswith("-"):
                        session_env.pop(arg, None)
            continue

        combined_env = dict(session_env)
        combined_env.update(seg_env)

        effective_env, tokens, effective_cwd = unwrap_command_prefixes(
            combined_env, tokens, current_cwd
        )
        if not tokens:
            continue

        exe = os.path.basename(tokens[0])

        # Track `cd <dir>` only when unconditional in the current shell.
        if exe == "cd" and len(tokens) >= 2 and not tokens[1].startswith("-"):
            if can_propagate_safe_state:
                target = os.path.expanduser(tokens[1])
                current_cwd = target if os.path.isabs(target) else os.path.normpath(os.path.join(current_cwd, target))
            continue

        # Recursively inspect nested shell invocations (`sh -c`, `bash -lc`, `eval`, ...),
        # carrying back any switch to `main`.
        nested = extract_nested_shell_command(tokens)
        if nested is not None:
            _, _, child_branches = inspect_command(
                nested, effective_cwd, dict(effective_env), dict(branches), depth + 1
            )
            merge_child_branches(branches, child_branches, allow_safe=False)
            continue

        # 1. Check git commands (attribution + no commit on main) in execution order.
        git_subcmd, git_args, repo_cwd = parse_git_invocation(tokens, effective_cwd)
        if git_subcmd is not None:
            repo_key = git_repo_root(repo_cwd)
            if git_subcmd in {"switch", "checkout"}:
                switched = branch_switched_to(git_subcmd, git_args, repo_cwd)
                if switched is not None:
                    if switched == "main" or can_propagate_safe_state:
                        branches[repo_key] = switched
            elif git_subcmd == "commit" and "--help" not in git_args and "-h" not in git_args:
                seg_joined = " ".join(tokens)
                if has_ai_attribution(seg_joined) or (("<<" in cmd) and has_ai_attribution(cmd)):
                    deny(
                        "Blocked by rebuild-guardrails: never add AI co-author trailers or "
                        "'Generated with ...' attribution to commits (see AGENTS.md)."
                    )
                for msg_file in extract_option_values(git_args, {"-F"}, {"--file"}):
                    if has_ai_attribution(read_text_safely(msg_file, repo_cwd)):
                        deny(
                            f"Blocked by rebuild-guardrails: commit message file `{msg_file}` "
                            f"contains forbidden AI attribution (see AGENTS.md)."
                        )
                if repo_key not in branches:
                    branches[repo_key] = current_git_branch(repo_cwd)
                if branches[repo_key] == "main":
                    deny(
                        "Blocked by rebuild-guardrails: never commit directly to `main`. "
                        "Create a topic branch first (`git switch -c <branch>`)."
                    )

        # 1c. Check `gh pr|issue create|edit|comment` attribution (inline + `--body-file` / `-F`).
        if (
            exe == "gh"
            and len(tokens) >= 3
            and tokens[1] in {"pr", "issue"}
            and tokens[2] in {"create", "edit", "comment"}
        ):
            gh_args = tokens[3:]
            if has_ai_attribution(" ".join(tokens)) or (("<<" in cmd) and has_ai_attribution(cmd)):
                deny(
                    "Blocked by rebuild-guardrails: never add AI attribution to PRs or issues (see AGENTS.md)."
                )
            for body_file in extract_option_values(gh_args, {"-F"}, {"--body-file"}):
                if has_ai_attribution(read_text_safely(body_file, effective_cwd)):
                    deny(
                        f"Blocked by rebuild-guardrails: body file `{body_file}` contains "
                        f"forbidden AI attribution (see AGENTS.md)."
                    )

        # 3. Check each `adb` call independently for physical-device data wipes.
        if exe == "adb":
            check_adb_segment(effective_env, tokens)

    return current_cwd, session_env, branches


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

    inspect_command(cmd, cwd, {}, {}, depth=0)
    allow()


if __name__ == "__main__":
    main()
