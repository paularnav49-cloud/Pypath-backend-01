#!/usr/bin/env python3
"""
Validates the Practical tasks (app/src/main/assets/practical/tasks.json).

Standard library only. Run from anywhere:

    python content/validate_tasks.py
    python content/validate_tasks.py --runner path/to/pypath_runner.py   # also run in the real sandbox

What it checks
--------------
* tasks.json is valid JSON with the expected shape, task ids are unique, every
  ``afterSubLevel`` is a real sub-level id of the course (assets/course/*.json).
* Tasks that existed before Batch 2 (content/practical_tasks_baseline.json) and every
  task in any committed version of tasks.json (git history, when git is available) still
  exist with the same id, title, instructions, unlocking sub-level, concepts,
  requirements, example and starter code. Tasks may gain fields, never lose or change these.
* Every task has a hint. Every test has the required fields. checkMode is valid; contains
  tests have ``mustContain``; regex tests have a ``pattern`` that compiles.
* New tasks (not in the baseline) have a difficulty, 3-5 tests, at least one test whose
  description starts with "Edge case", and at least one hidden test when medium or hard.
* Every checked task has a reference solution in content/task_solutions.py and the solution
  imports nothing that the Practical sandbox blocks.
* Every solution is run in a separate Python process once per test, twice (determinism),
  with the test's input lines and a 5 second timeout. Its output (with the same input echo the
  app console shows) must pass the test, and must equal the test's ``expectedOutput`` after
  normalization, so the expected output shown to learners is always a real correct output.
* With --runner, each run is also repeated inside the real PyPath sandbox (pypath_runner.py)
  and must produce exactly the same result.

Exit code 0 = all good, 1 = problems found (each one is printed).
"""

import argparse
import ast
import json
import os
import re
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.dirname(HERE)
TASKS_REL = "app/src/main/assets/practical/tasks.json"
TASKS_PATH = os.path.join(PROJECT, TASKS_REL)
COURSE_DIR = os.path.join(PROJECT, "app/src/main/assets/course")
BASELINE_PATH = os.path.join(HERE, "practical_tasks_baseline.json")
DEFAULT_RUNNER = os.path.join(PROJECT, "app/src/main/python/pypath_runner.py")

TIMEOUT_SECONDS = 5
CHECK_MODES = ("exact", "contains", "regex")
DIFFICULTIES = ("easy", "medium", "hard")
FROZEN_FIELDS = ("title", "instructions", "afterSubLevel", "concepts", "requirements", "example", "starterCode")

# Mirrors BLOCKED_MODULES in app/src/main/python/pypath_runner.py. When that file is present
# its own list is read as well, so this copy can never be less strict than the app.
BLOCKED_MODULES = {
    "java", "android", "chaquopy", "_chaquopy", "com", "androidx",
    "socket", "_socket", "ssl", "_ssl", "select", "selectors", "asyncio",
    "http", "urllib", "ftplib", "smtplib", "poplib", "imaplib", "telnetlib", "socketserver",
    "xmlrpc", "webbrowser", "subprocess", "_posixsubprocess", "multiprocessing",
    "_multiprocessing", "concurrent", "ctypes", "_ctypes", "pty", "resource",
    "signal", "faulthandler",
    # Not blocked by the sandbox, but never allowed in a reference solution:
    "os.system", "threading", "_thread",
}


# ───────────────────────────── shared rules (mirrored in Kotlin) ─────────────────────────────

def normalize(text):
    """Same rules as OutputCheck.normalize in TaskChecks.kt:
    \\r\\n -> \\n, strip trailing spaces/tabs on every line, drop leading and trailing blank
    lines. Case-sensitive; nothing else changes."""
    text = text.replace("\r\n", "\n")
    lines = [line.rstrip(" \t") for line in text.split("\n")]
    while lines and lines[0] == "":
        lines.pop(0)
    while lines and lines[-1] == "":
        lines.pop()
    return "\n".join(lines)


def mode_of(task):
    return task.get("checkMode", "exact")


def test_passes(task, test, actual):
    """Same rules as OutputCheck.matches in TaskChecks.kt."""
    out = normalize(actual)
    mode = mode_of(task)
    if mode == "contains":
        needles = test.get("mustContain") or task.get("mustContain") or []
        return all(n in out for n in needles)
    if mode == "regex":
        pattern = test.get("pattern") or task.get("pattern")
        return re.search(pattern, out) is not None
    return out == normalize(test["expectedOutput"])


# ───────────────────────────── running a solution ─────────────────────────────

# Runs one program like the app's Check button does: input() prompts go to stdout and the
# answered line is echoed followed by a newline (exactly what the in-app console shows).
# Running out of input lines raises EOFError, so the run fails (the app stops such a run too).
_PLAIN_HARNESS = r'''
import builtins, json, sys
sys.dont_write_bytecode = True
code = open(sys.argv[1], encoding="utf-8").read()
lines = json.loads(open(sys.argv[2], encoding="utf-8").read())
def _input(prompt=""):
    prompt = str(prompt)
    sys.stdout.write(prompt)
    if not lines:
        raise EOFError("EOF when reading a line")
    line = lines.pop(0)
    sys.stdout.write(line + "\n")
    return line
builtins.input = _input
main = {"__name__": "__main__", "__builtins__": builtins}
exec(compile(code, "main.py", "exec"), main)
'''

# Same, but inside the real PyPath sandbox (pypath_runner.run) with its frame protocol.
_SANDBOX_HARNESS = r'''
import json, os, struct, sys, tempfile, threading
sys.dont_write_bytecode = True
sys.path.insert(0, os.path.dirname(os.path.abspath(sys.argv[3])))
import pypath_runner as R
code = open(sys.argv[1], encoding="utf-8").read()
lines = json.loads(open(sys.argv[2], encoding="utf-8").read())
out_r, out_w = os.pipe()
in_r, in_w = os.pipe()
transcript, errors = [], []
def reader():
    global in_w
    buf = b""
    while True:
        while len(buf) < 5:
            chunk = os.read(out_r, 65536)
            if not chunk:
                return
            buf += chunk
        n = struct.unpack(">I", buf[1:5])[0]
        while len(buf) < 5 + n:
            chunk = os.read(out_r, 65536)
            if not chunk:
                return
            buf += chunk
        kind, text, buf = buf[:1], buf[5:5 + n].decode("utf-8"), buf[5 + n:]
        if kind == b"O":
            transcript.append(text)
        elif kind == b"E":
            errors.append(text)
        elif kind == b"I":
            if lines:
                line = lines.pop(0)
                transcript.append(line + "\n")
                os.write(in_w, (line + "\n").encode("utf-8"))
            elif in_w is not None:
                os.close(in_w)
                in_w = None
t = threading.Thread(target=reader)
t.start()
with tempfile.TemporaryDirectory() as work:
    status = R.run(code, out_w, in_r, work)
os.close(out_w)
t.join()
sys.__stdout__.write(json.dumps({"status": status, "stdout": "".join(transcript), "stderr": "".join(errors)}))
'''


def run_program(code, stdin_lines, runner=None):
    """Returns (ok, transcript, error_text). ok is False on an exception, a non-zero exit or a timeout."""
    with tempfile.TemporaryDirectory() as tmp:
        code_file = os.path.join(tmp, "main_src.py")
        input_file = os.path.join(tmp, "stdin.json")
        work = os.path.join(tmp, "scratch")  # fresh scratch folder for every run, like the app
        os.mkdir(work)
        with open(code_file, "w", encoding="utf-8") as f:
            f.write(code)
        with open(input_file, "w", encoding="utf-8") as f:
            json.dump(list(stdin_lines), f)
        harness = _SANDBOX_HARNESS if runner else _PLAIN_HARNESS
        args = [sys.executable, "-B", "-c", harness, code_file, input_file] + ([runner] if runner else [])
        env = dict(os.environ, PYTHONHASHSEED="0", PYTHONIOENCODING="utf-8", PYTHONDONTWRITEBYTECODE="1")
        try:
            p = subprocess.run(args, cwd=work, capture_output=True, text=True, encoding="utf-8",
                               timeout=TIMEOUT_SECONDS, env=env)
        except subprocess.TimeoutExpired:
            return False, "", f"timed out after {TIMEOUT_SECONDS} s"
        if runner:
            try:
                data = json.loads(p.stdout)
            except ValueError:
                return False, "", "sandbox harness failed: " + p.stderr[-2000:]
            ok = data["status"] in ("ok", "exit:0")
            return ok, data["stdout"], data["stderr"] or ("" if ok else data["status"])
        return p.returncode == 0, p.stdout, p.stderr


# ───────────────────────────── validation ─────────────────────────────

class Problems:
    def __init__(self):
        self.items = []

    def add(self, where, message):
        self.items.append(f"{where}: {message}")


def course_sub_level_ids():
    with open(os.path.join(COURSE_DIR, "course.json"), encoding="utf-8") as f:
        course = json.load(f)
    ids = []
    for name in course["levelFiles"]:
        with open(os.path.join(COURSE_DIR, name), encoding="utf-8") as f:
            level = json.load(f)
        ids += [s["id"] for s in level.get("subLevels", [])]
    return ids


def runner_blocked_modules(runner_path):
    try:
        tree = ast.parse(open(runner_path, encoding="utf-8").read())
    except (OSError, SyntaxError):
        return set()
    for node in ast.walk(tree):
        if isinstance(node, ast.Assign) and any(getattr(t, "id", None) == "BLOCKED_MODULES" for t in node.targets):
            return {c.value for c in ast.walk(node.value) if isinstance(c, ast.Constant) and isinstance(c.value, str)}
    return set()


def imported_modules(code):
    names = set()
    for node in ast.walk(ast.parse(code)):
        if isinstance(node, ast.Import):
            names |= {a.name for a in node.names}
        elif isinstance(node, ast.ImportFrom) and node.module:
            names.add(node.module)
        elif isinstance(node, ast.Call) and getattr(node.func, "id", None) == "__import__":
            names.add("__import__")
    return names


def historical_task_lists():
    """Every committed version of tasks.json in this git repository (oldest first)."""
    try:
        top = subprocess.run(["git", "rev-parse", "--show-toplevel"], cwd=PROJECT, capture_output=True, text=True, check=True).stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return []
    rel = os.path.relpath(TASKS_PATH, top).replace(os.sep, "/")
    try:
        commits = subprocess.run(["git", "log", "--reverse", "--format=%H", "--", rel], cwd=top, capture_output=True, text=True, check=True).stdout.split()
    except (OSError, subprocess.CalledProcessError):
        return []
    versions = []
    for c in commits:
        p = subprocess.run(["git", "show", f"{c}:{rel}"], cwd=top, capture_output=True, text=True)
        if p.returncode == 0:
            try:
                versions.append((c[:8], json.loads(p.stdout)["tasks"]))
            except (ValueError, KeyError, TypeError):
                pass
    return versions


def check_frozen(where, old_tasks, by_id, problems):
    for old in old_tasks:
        new = by_id.get(old["id"])
        if new is None:
            problems.add(where, f"task '{old['id']}' was removed")
            continue
        for field in FROZEN_FIELDS:
            if field in old and new.get(field) != old[field]:
                problems.add(where, f"task '{old['id']}' changed its {field}")
        if "hint" in old and new.get("hint") != old["hint"]:
            problems.add(where, f"task '{old['id']}' changed its hint")


def validate_structure(catalog, sub_ids, baseline_ids, problems):
    if not isinstance(catalog, dict) or not isinstance(catalog.get("tasks"), list):
        problems.add("tasks.json", "must be an object with a 'tasks' list")
        return []
    tasks = catalog["tasks"]
    seen = set()
    for t in tasks:
        tid = t.get("id")
        where = f"task {tid!r}"
        if not isinstance(tid, str) or not tid:
            problems.add(where, "missing id")
            continue
        if tid in seen:
            problems.add(where, "duplicate id")
        seen.add(tid)
        for field in ("title", "instructions"):
            if not isinstance(t.get(field), str) or not t[field].strip():
                problems.add(where, f"missing {field}")
        if not isinstance(t.get("hint"), str) or not t["hint"].strip():
            problems.add(where, "missing hint")
        after = t.get("afterSubLevel")
        if after is not None and after not in sub_ids:
            problems.add(where, f"afterSubLevel '{after}' is not a sub-level of the course")
        if tid != "playground" and after is None:
            problems.add(where, "only the playground may have no afterSubLevel")
        diff = t.get("difficulty")
        if diff is not None and diff not in DIFFICULTIES:
            problems.add(where, f"difficulty must be one of {DIFFICULTIES}")
        mode = mode_of(t)
        if mode not in CHECK_MODES:
            problems.add(where, f"checkMode must be one of {CHECK_MODES}")
        tests = t.get("tests", [])
        if not isinstance(tests, list):
            problems.add(where, "tests must be a list")
            continue
        is_new = tid not in baseline_ids
        if is_new:
            if diff is None:
                problems.add(where, "new tasks need a difficulty")
            if not 3 <= len(tests) <= 5:
                problems.add(where, f"new tasks need 3-5 tests (has {len(tests)})")
            if not any(str(x.get("description", "")).startswith("Edge case") for x in tests):
                problems.add(where, "new tasks need at least one test described as 'Edge case ...'")
            if diff in ("medium", "hard") and not any(x.get("hidden") for x in tests):
                problems.add(where, "medium and hard tasks need at least one hidden test")
        for i, x in enumerate(tests, 1):
            w = f"{where} test {i}"
            if not isinstance(x, dict):
                problems.add(w, "must be an object")
                continue
            if not isinstance(x.get("expectedOutput"), str):
                problems.add(w, "missing expectedOutput")
            stdin = x.get("stdin", [])
            if not isinstance(stdin, list) or not all(isinstance(s, str) for s in stdin):
                problems.add(w, "stdin must be a list of strings")
            elif any("\n" in s or "\r" in s for s in stdin):
                problems.add(w, "stdin lines must not contain line breaks")
            if "hidden" in x and not isinstance(x["hidden"], bool):
                problems.add(w, "hidden must be true or false")
            if not x.get("hidden") and not str(x.get("description", "")).strip():
                problems.add(w, "visible tests need a description")
            if mode == "contains" and not (x.get("mustContain") or t.get("mustContain")):
                problems.add(w, "contains mode needs mustContain")
            if mode == "regex":
                pattern = x.get("pattern") or t.get("pattern")
                if not pattern:
                    problems.add(w, "regex mode needs a pattern")
                else:
                    try:
                        re.compile(pattern)
                    except re.error as e:
                        problems.add(w, f"pattern does not compile: {e}")
    return tasks


def validate_solutions(tasks, solutions, blocked, runners, problems):
    checked = {t["id"]: t for t in tasks if t.get("tests")}
    for tid in solutions:
        if tid not in checked:
            problems.add(f"solution {tid!r}", "has no checked task with this id")
    total_runs = 0
    for tid, t in checked.items():
        code = solutions.get(tid)
        where = f"task {tid!r}"
        if code is None:
            problems.add(where, "has tests but no reference solution in task_solutions.py")
            continue
        try:
            mods = imported_modules(code)
        except SyntaxError as e:
            problems.add(where, f"solution has a syntax error: {e}")
            continue
        for m in sorted(mods):
            if m == "__import__" or m.split(".")[0] in blocked or m in blocked:
                problems.add(where, f"solution imports blocked module '{m}'")
        for i, test in enumerate(t["tests"], 1):
            w = f"{where} test {i}"
            stdin = test.get("stdin", [])
            results = []
            for runner in runners:
                for _ in range(2):
                    results.append((runner, run_program(code, stdin, runner)))
                    total_runs += 1
            first = results[0][1]
            ok, out, err = first
            if not ok:
                problems.add(w, f"reference solution failed: {err.strip()[-500:]}")
                continue
            for runner, other in results[1:]:
                if other == first:
                    continue
                if runner and not other[0]:
                    problems.add(w, f"reference solution fails in the real sandbox: {other[2].strip()[-500:]}")
                elif runner:
                    problems.add(w, f"the real sandbox gave {normalize(other[1])!r} instead of {normalize(out)!r}")
                else:
                    problems.add(w, f"not deterministic: a second run gave {normalize(other[1])!r} instead of {normalize(out)!r}")
                break
            if not test_passes(t, test, out):
                problems.add(w, f"reference output does not pass the test:\n----- got -----\n{normalize(out)}\n---------------")
            if normalize(out) != normalize(test.get("expectedOutput", "")):
                problems.add(w, f"expectedOutput differs from the reference output:\n----- expected -----\n"
                             f"{normalize(test.get('expectedOutput', ''))}\n----- reference -----\n{normalize(out)}\n--------------------")
    return total_runs


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--runner", help="path to pypath_runner.py: also run every solution in the real sandbox")
    ap.add_argument("--no-run", action="store_true", help="only check the JSON, do not run solutions")
    args = ap.parse_args(argv)

    problems = Problems()
    try:
        with open(TASKS_PATH, encoding="utf-8") as f:
            catalog = json.load(f)
    except (OSError, ValueError) as e:
        print(f"FAIL: cannot read {TASKS_REL}: {e}")
        return 1
    try:
        sub_ids = course_sub_level_ids()
    except (OSError, ValueError, KeyError) as e:
        print(f"FAIL: cannot read the course data in {COURSE_DIR}: {e}")
        return 1

    with open(BASELINE_PATH, encoding="utf-8") as f:
        baseline = json.load(f)["tasks"]
    baseline_ids = {t["id"] for t in baseline}

    tasks = validate_structure(catalog, set(sub_ids), baseline_ids, problems)
    by_id = {t.get("id"): t for t in tasks}
    check_frozen("baseline", baseline, by_id, problems)
    history = historical_task_lists()
    for commit, old in history:
        check_frozen(f"git {commit}", old, by_id, problems)

    sys.path.insert(0, HERE)
    from task_solutions import SOLUTIONS  # noqa: E402

    runner = args.runner or (DEFAULT_RUNNER if os.path.exists(DEFAULT_RUNNER) else None)
    if runner and not os.path.exists(runner):
        problems.add("--runner", f"{runner} does not exist")
        runner = None
    blocked = set(BLOCKED_MODULES) | (runner_blocked_modules(runner) if runner else set())
    runs = 0
    if not args.no_run:
        runs = validate_solutions(tasks, SOLUTIONS, blocked, [None] + ([runner] if runner else []), problems)

    checked = [t for t in tasks if t.get("tests")]
    print(f"Tasks: {len(tasks)} ({len(checked)} checked, {sum(len(t['tests']) for t in checked)} tests)")
    print(f"Sub-levels in course: {len(sub_ids)}; git versions compared: {len(history)}")
    print(f"Solution runs: {runs}" + (f" (plain Python and the real sandbox: {runner})" if runner else " (plain Python; real sandbox not found)"))
    if problems.items:
        print(f"\nFAIL: {len(problems.items)} problem(s)")
        for p in problems.items:
            print(" - " + p)
        return 1
    print("OK: all tasks are valid and every reference solution passes its tests")
    return 0


if __name__ == "__main__":
    sys.exit(main())
