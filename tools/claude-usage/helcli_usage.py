#!/usr/bin/env python3
"""Publishes your Claude plan limits (5-hour and weekly %) so HelCLI can show them on your phone.

Claude Code gives its status line command your plan's rate limits (claude.ai Pro, Max and Ultra):
https://code.claude.com/docs/en/statusline . This script is that status line command. Each time
Claude Code runs it, it copies the numbers into a secret GitHub gist, which HelCLI reads.

Only the percentages and reset times are published: no prompts, code, tokens or account details.

Setup, once (needs the GitHub CLI, signed in: gh auth login):
    python3 helcli_usage.py --setup

That creates the secret gist and prints the two things to do next: a line for
~/.claude/settings.json, and a command for the phone.

Already have a status line? Keep it, and let this script run it after publishing:
    python3 helcli_usage.py --then "your existing status line command"
"""

import json
import os
import shutil
import subprocess
import sys
import tempfile
import time

FILE_NAME = "claude-usage.json"
CONFIG_DIR = os.path.join(os.path.expanduser("~"), ".config", "helcli")
CONFIG = os.path.join(CONFIG_DIR, "usage.json")
STATE = os.path.join(CONFIG_DIR, "usage-last.json")

MIN_INTERVAL = 60        # never publish more often than once a minute
REFRESH_INTERVAL = 900   # publish at least every 15 minutes while Claude Code is in use, so "updated" stays fresh


def load(path, default):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError):
        return default


def save(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(data, f)
    os.replace(tmp, path)


def gh():
    path = shutil.which("gh")
    if not path:
        sys.exit("The GitHub CLI (gh) is needed: https://cli.github.com, then run: gh auth login")
    return path


# ------------------------------------------------------------------ setup

def setup():
    gh_path = gh()
    if subprocess.run([gh_path, "auth", "status"], capture_output=True).returncode != 0:
        sys.exit("Sign in to GitHub first: gh auth login")

    existing = load(CONFIG, {}).get("gist")
    if existing:
        print("Already set up, publishing to gist " + existing)
        print_next_steps(existing)
        return

    with tempfile.TemporaryDirectory() as d:
        path = os.path.join(d, FILE_NAME)
        with open(path, "w", encoding="utf-8") as f:
            json.dump({"updated_at": int(time.time()), "rate_limits": {}}, f)
        out = subprocess.run(
            [gh_path, "gist", "create", "--desc", "HelCLI: Claude plan usage (5-hour and weekly %)", path],
            capture_output=True, text=True)
    if out.returncode != 0:
        sys.exit("Couldn't create the gist: " + out.stderr.strip())

    url = out.stdout.strip().splitlines()[-1].strip()   # https://gist.github.com/<user>/<id>
    parts = url.rstrip("/").split("/")
    if len(parts) < 2 or "gist.github.com" not in url:
        sys.exit("Unexpected answer from gh: " + out.stdout)
    gist = parts[-2] + "/" + parts[-1]

    save(CONFIG, {"gist": gist})
    print("Created a secret gist: " + url)
    print_next_steps(gist)


def print_next_steps(gist):
    script = os.path.abspath(__file__)
    python = "python3" if os.name != "nt" else "python"
    command = '%s "%s"' % (python, script)
    print()
    print("1. Make it Claude Code's status line. In ~/.claude/settings.json add:")
    print()
    print('   "statusLine": {"type": "command", "command": %s}' % json.dumps(command))
    print()
    print("   (Already have a status line? Use this command instead, with yours after --then:")
    print('    %s --then "your status line command")' % command)
    print()
    print("2. On the phone, in HelCLI:")
    print()
    print("   usage -plan " + gist)
    print("   usage -show")
    print()
    print("The numbers update whenever Claude Code answers you.")


# ------------------------------------------------------------------ status line

def status_line(stdin_text, then):
    try:
        data = json.loads(stdin_text) if stdin_text.strip() else {}
    except ValueError:
        data = {}

    limits = data.get("rate_limits") or {}
    publish(limits)

    if then:
        # hand the same input to the user's own status line and show its output instead of ours
        result = subprocess.run(then, shell=True, input=stdin_text, capture_output=True, text=True)
        sys.stdout.write(result.stdout)
        return

    parts = []
    if "five_hour" in limits:
        parts.append("5h %d%%" % round(limits["five_hour"].get("used_percentage", 0)))
    if "seven_day" in limits:
        parts.append("week %d%%" % round(limits["seven_day"].get("used_percentage", 0)))
    print(" · ".join(parts))


def publish(limits):
    """Uploads the numbers when they changed (at most once a minute), or every 15 minutes."""
    gist = load(CONFIG, {}).get("gist")
    if not gist:
        return

    keep = {}
    for window in ("five_hour", "seven_day"):
        w = limits.get(window)
        if isinstance(w, dict) and "used_percentage" in w:
            keep[window] = {"used_percentage": w.get("used_percentage"), "resets_at": w.get("resets_at")}
    if not keep:
        return  # not a plan subscriber, or Claude Code hasn't answered yet in this session

    now = int(time.time())
    last = load(STATE, {})
    since = now - last.get("published_at", 0)
    changed = last.get("rate_limits") != keep
    if since < MIN_INTERVAL or (not changed and since < REFRESH_INTERVAL):
        return

    save(STATE, {"published_at": now, "rate_limits": keep})

    path = os.path.join(CONFIG_DIR, FILE_NAME)
    save(path, {"updated_at": now, "rate_limits": keep})

    # upload in the background, so the status line never waits for the network
    kwargs = {"stdin": subprocess.DEVNULL, "stdout": subprocess.DEVNULL, "stderr": subprocess.DEVNULL}
    if os.name == "nt":
        kwargs["creationflags"] = 0x00000008 | 0x00000200  # DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP
    else:
        kwargs["start_new_session"] = True
    gh_path = shutil.which("gh")
    if gh_path:
        subprocess.Popen([gh_path, "gist", "edit", gist.split("/")[-1], "--filename", FILE_NAME, path], **kwargs)


def main(argv):
    if "--setup" in argv:
        setup()
        return
    if "-h" in argv or "--help" in argv:
        print(__doc__)
        return
    then = None
    if "--then" in argv:
        i = argv.index("--then")
        then = " ".join(argv[i + 1:]) or None
    status_line(sys.stdin.read(), then)


if __name__ == "__main__":
    main(sys.argv[1:])
