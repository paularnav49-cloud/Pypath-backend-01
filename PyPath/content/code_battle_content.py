#!/usr/bin/env python3
"""
Premium Code Battle content: timed Python challenges with hidden test cases.

Writes one bundled, offline asset file (nothing is downloaded at run time):

    app/src/main/assets/premium/battle.json

and validates it. Standard library only:

    python content/code_battle_content.py              # write + validate (plain Python)
    python content/code_battle_content.py --runner app/src/main/python/pypath_runner.py
                                                       # also run every check inside the real PyPath sandbox
    python content/code_battle_content.py --check      # validate only; fails if the asset is out of date

What is validated
-----------------
* 5 Easy, 5 Medium and 5 Hard challenges with unique ids starting with "premium-cb-", and time
  limits that grow with difficulty (Easy < Medium < Hard).
* Every challenge has 2 visible example tests and at least 3 hidden tests, including an edge case.
* The reference solution imports nothing the sandbox blocks and passes every test: it is run twice
  in plain Python (determinism) and, with --runner, once more inside the real PyPath sandbox.
  expectedOutput is always the real output of the reference solution.
* The examples shown to learners are generated from the same runs, so they are always real.
* Negative checks (the app must never report a pass that didn't happen):
    - the starter code fails at least one test;
    - a typical wrong solution ("mistake") passes at least one test but fails at least one hidden
      test, so hidden tests really matter;
    - a syntax error and a runtime error fail the checked tests, and an endless loop is stopped by
      the time limit and fails (checked on the first challenge of each difficulty).

The reference and wrong solutions stay in this script. They are not shipped in the app.
"""

import argparse
import json
import os
import sys

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import validate_tasks as vt  # noqa: E402  (shared run + check rules, mirrored in TaskChecks.kt)

PROJECT = os.path.dirname(HERE)
OUT_PATH = os.path.join(PROJECT, "app/src/main/assets/premium/battle.json")

# PremiumIds.BATTLE_PREFIX in Kotlin: results are stored in the Code Battle file only, never in
# course progress, Practical task counts or the Project Library.
PREFIX = "premium-cb-"
MINUTES = {"easy": 5, "medium": 10, "hard": 15}
EXAMPLE_TESTS = 2

# Each test: (stdin lines, description). The first EXAMPLE_TESTS are visible examples, the rest hidden.
CHALLENGES = [
    # ───────────────────────────── Easy ─────────────────────────────
    {
        "id": "rectangle", "difficulty": "easy", "title": "Rectangle maths",
        "concepts": ["input()", "int()", "arithmetic"],
        "description": "Read the width and the height of a rectangle (whole numbers, one per line). Print its area and its perimeter.",
        "requirements": ["Read two lines with input()", "Print Area: and the area", "Print Perimeter: and the perimeter"],
        "hint": "Area is width * height. Perimeter is 2 * (width + height).",
        "starter": "width = int(input())\nheight = int(input())\n\n# Print the area and the perimeter\n",
        "solution": "width = int(input())\nheight = int(input())\nprint(\"Area:\", width * height)\nprint(\"Perimeter:\", 2 * (width + height))\n",
        "mistake": "width = int(input())\nheight = int(input())\nprint(\"Area:\", width * height)\nprint(\"Perimeter:\", 4 * width)\n",
        "tests": [(["3", "4"], "3 by 4"), (["5", "5"], "A square"), (["1", "1"], "Edge case: 1 by 1"),
                  (["10", "2"], None), (["7", "3"], None), (["0", "9"], "Edge case: width 0")],
    },
    {
        "id": "even-odd", "difficulty": "easy", "title": "Even or odd",
        "concepts": ["%", "if / else"],
        "description": "Read one whole number. Print Even if it is even, otherwise print Odd.",
        "requirements": ["Read one number with input()", "Print exactly Even or Odd"],
        "hint": "A number is even when number % 2 == 0. This also works for 0 and negative numbers.",
        "starter": "number = int(input())\n\n# Print Even or Odd\n",
        "solution": "number = int(input())\nif number % 2 == 0:\n    print(\"Even\")\nelse:\n    print(\"Odd\")\n",
        "mistake": "number = int(input())\nif number % 2 == 1:\n    print(\"Odd\")\nelif number > 0:\n    print(\"Even\")\n",
        "tests": [(["4"], "4 is even"), (["7"], "7 is odd"), (["0"], "Edge case: zero"),
                  (["-3"], None), (["-8"], None), (["1001"], None)],
    },
    {
        "id": "sum-to-n", "difficulty": "easy", "title": "Sum from 1 to n",
        "concepts": ["for loops", "range()"],
        "description": "Read a whole number n (0 or more). Print the sum of all numbers from 1 to n.",
        "requirements": ["Read n with input()", "Use a loop to add the numbers", "Print only the total"],
        "hint": "Start with total = 0 and add each number of range(1, n + 1). When n is 0 the loop never runs.",
        "starter": "n = int(input())\ntotal = 0\n\n# Add the numbers 1 to n, then print the total\n",
        "solution": "n = int(input())\ntotal = 0\nfor i in range(1, n + 1):\n    total += i\nprint(total)\n",
        "mistake": "n = int(input())\ntotal = 0\nfor i in range(1, n):\n    total += i\nprint(total)\n",
        "tests": [(["5"], "1 + 2 + 3 + 4 + 5"), (["10"], "Up to 10"), (["0"], "Edge case: n is 0"),
                  (["1"], "Edge case: n is 1"), (["3"], None), (["100"], None)],
    },
    {
        "id": "grade", "difficulty": "easy", "title": "Mark to grade",
        "concepts": ["if / elif / else", "comparisons"],
        "description": "Read a mark from 0 to 100. Print the grade: A for 90 or more, B for 75 or more, C for 50 or more, otherwise F.",
        "requirements": ["Read one number with input()", "Print one letter: A, B, C or F"],
        "hint": "Check the highest grade first: if mark >= 90, then elif mark >= 75, and so on.",
        "starter": "mark = int(input())\n\n# Print A, B, C or F\n",
        "solution": "mark = int(input())\nif mark >= 90:\n    print(\"A\")\nelif mark >= 75:\n    print(\"B\")\nelif mark >= 50:\n    print(\"C\")\nelse:\n    print(\"F\")\n",
        "mistake": "mark = int(input())\nif mark > 90:\n    print(\"A\")\nelif mark > 75:\n    print(\"B\")\nelif mark > 50:\n    print(\"C\")\nelse:\n    print(\"F\")\n",
        "tests": [(["95"], "95 is an A"), (["60"], "60 is a C"), (["90"], "Edge case: exactly 90"),
                  (["75"], "Edge case: exactly 75"), (["50"], None), (["12"], None)],
    },
    {
        "id": "countdown", "difficulty": "easy", "title": "Rocket countdown",
        "concepts": ["while loops"],
        "description": "Read a whole number n (1 or more). Count down from n to 1, one number per line, then print Liftoff!",
        "requirements": ["Read n with input()", "Print n, n-1, ... 1 on separate lines", "Finish with Liftoff!"],
        "hint": "while n > 0: print(n) and then n -= 1. Print Liftoff! after the loop, not inside it.",
        "starter": "n = int(input())\n\n# Count down, then print Liftoff!\n",
        "solution": "n = int(input())\nwhile n > 0:\n    print(n)\n    n -= 1\nprint(\"Liftoff!\")\n",
        "mistake": "n = int(input())\nwhile n > 0:\n    print(n)\n    n -= 2\nprint(\"Liftoff!\")\n",
        "tests": [(["3"], "From 3"), (["1"], "Edge case: from 1"), (["5"], None), (["2"], None), (["10"], None)],
    },
    # ───────────────────────────── Medium ─────────────────────────────
    {
        "id": "vowels", "difficulty": "medium", "title": "Vowel counter",
        "concepts": ["strings", "for loops", "lower()"],
        "description": "Read a line of text. Print how many vowels (a, e, i, o, u) it contains. Capital letters count too.",
        "requirements": ["Read one line with input()", "Count a, e, i, o and u in upper or lower case", "Print only the number"],
        "hint": "Loop over text.lower() and add 1 when the letter is in \"aeiou\".",
        "starter": "text = input()\ncount = 0\n\n# Count the vowels, then print count\n",
        "solution": "text = input()\ncount = 0\nfor ch in text.lower():\n    if ch in \"aeiou\":\n        count += 1\nprint(count)\n",
        "mistake": "text = input()\ncount = 0\nfor ch in text:\n    if ch in \"aeiou\":\n        count += 1\nprint(count)\n",
        "tests": [(["hello world"], "hello world"), (["python"], "Only one vowel"), (["AEIOU"], "Edge case: capital vowels"),
                  (["rhythm"], "Edge case: no vowels"), (["Programming Is Fun"], None), ([""], "Edge case: empty line")],
    },
    {
        "id": "fizzbuzz", "difficulty": "medium", "title": "FizzBuzz",
        "concepts": ["for loops", "%", "if / elif"],
        "description": "Read n. For every number from 1 to n print Fizz if it divides by 3, Buzz if it divides by 5, FizzBuzz if it divides by both, otherwise the number.",
        "requirements": ["Read n with input()", "One line per number from 1 to n", "Check FizzBuzz (3 and 5) first"],
        "hint": "Test i % 15 == 0 (or i % 3 == 0 and i % 5 == 0) before the separate 3 and 5 checks.",
        "starter": "n = int(input())\n\nfor i in range(1, n + 1):\n    # Print Fizz, Buzz, FizzBuzz or the number\n    pass\n",
        "solution": "n = int(input())\nfor i in range(1, n + 1):\n    if i % 3 == 0 and i % 5 == 0:\n        print(\"FizzBuzz\")\n    elif i % 3 == 0:\n        print(\"Fizz\")\n    elif i % 5 == 0:\n        print(\"Buzz\")\n    else:\n        print(i)\n",
        "mistake": "n = int(input())\nfor i in range(1, n + 1):\n    if i % 3 == 0:\n        print(\"Fizz\")\n    elif i % 5 == 0:\n        print(\"Buzz\")\n    elif i % 15 == 0:\n        print(\"FizzBuzz\")\n    else:\n        print(i)\n",
        "tests": [(["5"], "Up to 5"), (["3"], "Up to 3"), (["1"], "Edge case: just 1"), (["15"], None), (["20"], None)],
    },
    {
        "id": "min-max", "difficulty": "medium", "title": "Largest and smallest",
        "concepts": ["lists", "loops", "max() / min()"],
        "description": "Read how many numbers there are, then read each number on its own line. Print the largest and the smallest.",
        "requirements": ["Read the count first, then that many numbers", "Print Largest: and the largest number", "Print Smallest: and the smallest number"],
        "hint": "Collect the numbers in a list with append(), then use max() and min(). Numbers can be negative.",
        "starter": "count = int(input())\nnumbers = []\n\n# Read count numbers, then print Largest: and Smallest:\n",
        "solution": "count = int(input())\nnumbers = []\nfor _ in range(count):\n    numbers.append(int(input()))\nprint(\"Largest:\", max(numbers))\nprint(\"Smallest:\", min(numbers))\n",
        "mistake": "count = int(input())\nlargest = 0\nsmallest = 100\nfor _ in range(count):\n    x = int(input())\n    if x > largest:\n        largest = x\n    if x < smallest:\n        smallest = x\nprint(\"Largest:\", largest)\nprint(\"Smallest:\", smallest)\n",
        "tests": [(["3", "4", "9", "2"], "Three numbers"), (["4", "10", "20", "5", "15"], "Four numbers"),
                  (["1", "42"], "Edge case: one number"), (["3", "-5", "-1", "-9"], "Edge case: all negative"),
                  (["5", "150", "200", "175", "160", "190"], None)],
    },
    {
        "id": "palindrome", "difficulty": "medium", "title": "Palindrome check",
        "concepts": ["strings", "slicing", "replace()"],
        "description": "Read a word or phrase. Ignore capital letters and spaces. Print Yes if it reads the same backwards, otherwise No.",
        "requirements": ["Read one line with input()", "Ignore case and spaces", "Print exactly Yes or No"],
        "hint": "Clean it first: text.lower().replace(\" \", \"\"). Then compare it with cleaned[::-1].",
        "starter": "text = input()\n\n# Print Yes or No\n",
        "solution": "text = input()\ncleaned = text.lower().replace(\" \", \"\")\nif cleaned == cleaned[::-1]:\n    print(\"Yes\")\nelse:\n    print(\"No\")\n",
        "mistake": "text = input()\nif text == text[::-1]:\n    print(\"Yes\")\nelse:\n    print(\"No\")\n",
        "tests": [(["level"], "level"), (["python"], "python"), (["Racecar"], "Edge case: a capital letter"),
                  (["never odd or even"], "Spaces are ignored"), (["a"], "Edge case: one letter"), (["Step on no petx"], None)],
    },
    {
        "id": "times-table", "difficulty": "medium", "title": "Times table",
        "concepts": ["for loops", "f-strings"],
        "description": "Read a number n and a length k. Print the times table of n from 1 to k, one line each, in the form n x i = result.",
        "requirements": ["Read n, then k, with input()", "Print k lines like 3 x 2 = 6", "Print nothing when k is 0"],
        "hint": "for i in range(1, k + 1): print(f\"{n} x {i} = {n * i}\")",
        "starter": "n = int(input())\nk = int(input())\n\n# Print the times table\n",
        "solution": "n = int(input())\nk = int(input())\nfor i in range(1, k + 1):\n    print(f\"{n} x {i} = {n * i}\")\n",
        "mistake": "n = int(input())\nk = int(input())\nfor i in range(1, 11):\n    print(f\"{n} x {i} = {n * i}\")\n",
        "tests": [(["3", "4"], "3, four lines"), (["7", "2"], "7, two lines"), (["5", "10"], None),
                  (["9", "0"], "Edge case: k is 0"), (["12", "1"], None)],
    },
    # ───────────────────────────── Hard ─────────────────────────────
    {
        "id": "word-count", "difficulty": "hard", "title": "Word frequency",
        "concepts": ["split()", "dictionaries", "loops"],
        "description": "Read a sentence. Ignoring capital letters, print every different word with how many times it appears, in the order the words first appear.",
        "requirements": ["Read one line with input()", "Treat words in any case as the same word", "Print word: count, one line per word"],
        "hint": "Dictionaries keep the order keys were added. counts[w] = counts.get(w, 0) + 1 for every word of text.lower().split().",
        "starter": "text = input()\ncounts = {}\n\n# Count every word, then print word: count lines\n",
        "solution": "text = input()\ncounts = {}\nfor w in text.lower().split():\n    counts[w] = counts.get(w, 0) + 1\nfor w, n in counts.items():\n    print(f\"{w}: {n}\")\n",
        "mistake": "text = input()\ncounts = {}\nfor w in text.split():\n    counts[w] = counts.get(w, 0) + 1\nfor w, n in counts.items():\n    print(f\"{w}: {n}\")\n",
        "tests": [(["the cat and the hat"], "the appears twice"), (["red blue red green blue red"], "Three colours"),
                  (["Hi hi HI"], "Edge case: same word, different case"), (["one"], "Edge case: one word"),
                  (["To be or not To Be"], None), (["a b c a b a"], None)],
    },
    {
        "id": "primes", "difficulty": "hard", "title": "Prime numbers",
        "concepts": ["nested loops", "functions", "break"],
        "description": "Read n. Print every prime number from 2 to n on one line, separated by single spaces. If there are none, print None.",
        "requirements": ["Read n with input()", "A prime has exactly two divisors: 1 and itself", "Print None when n is less than 2"],
        "hint": "Write is_prime(x): return False if any d in range(2, x) divides x. Collect primes in a list and print \" \".join(...) of their str() values.",
        "starter": "n = int(input())\n\ndef is_prime(x):\n    # Return True when x is prime\n    pass\n\n# Print the primes from 2 to n, or None\n",
        "solution": "n = int(input())\n\ndef is_prime(x):\n    if x < 2:\n        return False\n    for d in range(2, x):\n        if x % d == 0:\n            return False\n    return True\n\nprimes = [str(x) for x in range(2, n + 1) if is_prime(x)]\nprint(\" \".join(primes) if primes else \"None\")\n",
        "mistake": "n = int(input())\n\ndef is_prime(x):\n    for d in range(2, x):\n        if x % d == 0:\n            return False\n    return True\n\nprimes = [str(x) for x in range(1, n + 1) if is_prime(x)]\nprint(\" \".join(primes) if primes else \"None\")\n",
        "tests": [(["10"], "Up to 10"), (["20"], "Up to 20"), (["1"], "Edge case: no primes"), (["2"], "Edge case: just 2"),
                  (["30"], None), (["0"], None)],
    },
    {
        "id": "second-largest", "difficulty": "hard", "title": "Second largest",
        "concepts": ["lists", "set()", "sorted()"],
        "description": "Read whole numbers on one line, separated by spaces. Print the second largest different number. If there isn't one, print None.",
        "requirements": ["Read one line and split it into numbers", "Repeated numbers count once", "Print None when fewer than two different numbers"],
        "hint": "values = sorted(set(numbers)). If len(values) < 2 print None, otherwise print values[-2].",
        "starter": "numbers = [int(x) for x in input().split()]\n\n# Print the second largest different number, or None\n",
        "solution": "numbers = [int(x) for x in input().split()]\nvalues = sorted(set(numbers))\nif len(values) < 2:\n    print(\"None\")\nelse:\n    print(values[-2])\n",
        "mistake": "numbers = [int(x) for x in input().split()]\nnumbers.sort()\nif len(numbers) < 2:\n    print(\"None\")\nelse:\n    print(numbers[-2])\n",
        "tests": [(["4 9 2 7"], "Four numbers"), (["10 5"], "Two numbers"), (["8 8 3"], "Edge case: the largest appears twice"),
                  (["6 6 6"], "Edge case: all the same"), (["-4 -1 -7"], None), (["1 2 3 4 5 5 5"], None)],
    },
    {
        "id": "caesar", "difficulty": "hard", "title": "Caesar cipher",
        "concepts": ["ord() / chr()", "strings", "%"],
        "description": "Read a message and a shift (0 to 25). Move every letter forward by the shift, wrapping from z back to a. Keep capital letters capital and leave other characters unchanged.",
        "requirements": ["Read the message, then the shift", "Shift a-z and A-Z, wrapping around", "Spaces, digits and punctuation stay the same"],
        "hint": "For a lower-case letter: chr((ord(ch) - ord(\"a\") + shift) % 26 + ord(\"a\")). Do the same with \"A\" for capitals.",
        "starter": "message = input()\nshift = int(input())\nresult = \"\"\n\n# Build the shifted message in result, then print it\n",
        "solution": "message = input()\nshift = int(input())\nresult = \"\"\nfor ch in message:\n    if \"a\" <= ch <= \"z\":\n        result += chr((ord(ch) - ord(\"a\") + shift) % 26 + ord(\"a\"))\n    elif \"A\" <= ch <= \"Z\":\n        result += chr((ord(ch) - ord(\"A\") + shift) % 26 + ord(\"A\"))\n    else:\n        result += ch\nprint(result)\n",
        "mistake": "message = input()\nshift = int(input())\nresult = \"\"\nfor ch in message:\n    if ch.isalpha():\n        result += chr(ord(ch) + shift)\n    else:\n        result += ch\nprint(result)\n",
        "tests": [(["abc", "1"], "Shift by 1"), (["Hello, World!", "3"], "Capitals and punctuation"), (["xyz", "3"], "Edge case: wrap from z to a"),
                  (["Python 3", "0"], "Edge case: shift 0"), (["Zebra", "2"], None), (["attack at dawn", "13"], None)],
    },
    {
        "id": "compress", "difficulty": "hard", "title": "Letter compression",
        "concepts": ["while loops", "indexes", "strings"],
        "description": "Read a word. Replace each run of the same letter with the letter followed by how many times it repeats in a row. For example aaabcc becomes a3b1c2.",
        "requirements": ["Read one word with input()", "Count letters that repeat next to each other", "Print the compressed word"],
        "hint": "Walk through the word with an index. Count how far the same letter continues, add letter + str(count), then jump past the run.",
        "starter": "word = input()\nresult = \"\"\n\n# Build the compressed word in result, then print it\n",
        "solution": "word = input()\nresult = \"\"\ni = 0\nwhile i < len(word):\n    j = i\n    while j < len(word) and word[j] == word[i]:\n        j += 1\n    result += word[i] + str(j - i)\n    i = j\nprint(result)\n",
        "mistake": "word = input()\nresult = \"\"\nfor ch in word:\n    if ch not in result:\n        result += ch + str(word.count(ch))\nprint(result)\n",
        "tests": [(["aaabcc"], "aaabcc"), (["hello"], "hello"), (["z"], "Edge case: one letter"),
                  (["aabbaa"], "Edge case: a letter comes back later"), (["mississippi"], None), (["xxxxxxxxxx"], None)],
    },
]

# Code that must fail every test, whatever the challenge (the app must never report these as passed).
BROKEN = {
    "syntax error": "print(\"unfinished\"\n",
    "runtime error": "x = int(input())\nprint(10 / 0)\n",
    "endless loop": "while True:\n    pass\n",
}


def _no_echo_output(code, stdin_lines):
    """The program's own output (no echoed input), used for the example boxes shown to learners."""
    import subprocess
    import tempfile
    harness = (
        "import builtins, json, sys\n"
        "code = open(sys.argv[1], encoding='utf-8').read()\n"
        "lines = json.loads(open(sys.argv[2], encoding='utf-8').read())\n"
        "def _input(prompt=''):\n"
        "    sys.stdout.write(str(prompt))\n"
        "    return lines.pop(0)\n"
        "builtins.input = _input\n"
        "exec(compile(code, 'main.py', 'exec'), {'__name__': '__main__', '__builtins__': builtins})\n"
    )
    with tempfile.TemporaryDirectory() as tmp:
        cf, sf = os.path.join(tmp, "c.py"), os.path.join(tmp, "s.json")
        with open(cf, "w", encoding="utf-8") as f:
            f.write(code)
        with open(sf, "w", encoding="utf-8") as f:
            json.dump(list(stdin_lines), f)
        p = subprocess.run([sys.executable, "-B", "-c", harness, cf, sf], capture_output=True, text=True,
                           encoding="utf-8", timeout=vt.TIMEOUT_SECONDS, cwd=tmp)
        return p.returncode == 0, vt.normalize(p.stdout)


class Problems:
    def __init__(self):
        self.items = []

    def add(self, where, msg):
        self.items.append(f"{where}: {msg}")


def build(problems, runner):
    out = []
    counters = {"easy": 0, "medium": 0, "hard": 0}
    for ch in CHALLENGES:
        cid = PREFIX + ch["id"]
        d = ch["difficulty"]
        counters[d] += 1
        tests, examples = [], []
        for i, (stdin, desc) in enumerate(ch["tests"]):
            ok, transcript, err = vt.run_program(ch["solution"], stdin)
            if not ok:
                problems.add(cid, f"reference solution failed for {stdin}: {err.strip()[-300:]}")
                continue
            hidden = i >= EXAMPLE_TESTS
            test = {"stdin": stdin, "expectedOutput": vt.normalize(transcript)}
            if desc:
                test["description"] = desc
            if hidden:
                test["hidden"] = True
            tests.append(test)
            if not hidden:
                ok2, plain = _no_echo_output(ch["solution"], stdin)
                if not ok2:
                    problems.add(cid, f"example output failed for {stdin}")
                examples.append({"input": "\n".join(stdin), "output": plain})
        out.append({
            "id": cid, "difficulty": d, "order": counters[d], "title": ch["title"],
            "minutes": MINUTES[d], "concepts": ch["concepts"], "description": ch["description"],
            "requirements": ch["requirements"], "examples": examples, "hint": ch["hint"],
            "starterCode": ch["starter"], "tests": tests,
        })
    return out


def validate(battle, problems, runner):
    blocked = set(vt.BLOCKED_MODULES)
    if runner:
        blocked |= vt.runner_blocked_modules(runner)
    by_id = {PREFIX + c["id"]: c for c in CHALLENGES}
    ids = set()
    per = {"easy": 0, "medium": 0, "hard": 0}
    if not MINUTES["easy"] < MINUTES["medium"] < MINUTES["hard"]:
        problems.add("minutes", "time limits must grow with difficulty")
    for ch in battle:
        where = ch["id"]
        src = by_id[where]
        if where in ids:
            problems.add(where, "duplicate id")
        ids.add(where)
        if not where.startswith(PREFIX):
            problems.add(where, f"id must start with {PREFIX}")
        if ch["difficulty"] not in per:
            problems.add(where, "bad difficulty")
            continue
        per[ch["difficulty"]] += 1
        task = {"checkMode": "exact"}
        visible = [x for x in ch["tests"] if not x.get("hidden")]
        hidden = [x for x in ch["tests"] if x.get("hidden")]
        if len(visible) != EXAMPLE_TESTS or len(ch["examples"]) != EXAMPLE_TESTS:
            problems.add(where, f"needs exactly {EXAMPLE_TESTS} visible example tests")
        if len(hidden) < 3:
            problems.add(where, "needs at least 3 hidden tests")
        if not any((x.get("description") or "").startswith("Edge case") for x in ch["tests"]):
            problems.add(where, "needs an 'Edge case' test")
        if not ch.get("hint") or not ch.get("requirements") or not ch.get("description"):
            problems.add(where, "missing description, requirements or hint")
        bad = vt.imported_modules(src["solution"]) & blocked
        if bad:
            problems.add(where, f"solution imports blocked modules {sorted(bad)}")
        outs = set()
        for x in ch["tests"]:
            results = [vt.run_program(src["solution"], x["stdin"]) for _ in range(2)]
            if runner:
                results.append(vt.run_program(src["solution"], x["stdin"], runner))
            texts = set()
            for ok, transcript, err in results:
                if not ok:
                    problems.add(where, f"solution failed for {x['stdin']}: {err.strip()[-300:]}")
                    continue
                if not vt.test_passes(task, x, transcript):
                    problems.add(where, f"solution does not pass its own test {x['stdin']}")
                texts.add(vt.normalize(transcript))
            if len(texts) > 1:
                problems.add(where, f"non-deterministic output for {x['stdin']}")
            outs.add(vt.normalize(x["expectedOutput"]))
        if len(outs) < len(ch["tests"]):
            problems.add(where, "two tests have the same expected output")

        def passes(code, x, use_runner=None):
            ok, transcript, _ = vt.run_program(code, x["stdin"], use_runner)
            return ok and vt.test_passes(task, x, transcript)

        if all(passes(ch["starterCode"], x) for x in ch["tests"]):
            problems.add(where, "starter code already passes every test")
        mistake = src["mistake"]
        rr = runner or None
        if not any(passes(mistake, x, rr) for x in ch["tests"]):
            problems.add(where, "the typical mistake should pass at least one test")
        if all(passes(mistake, x, rr) for x in hidden):
            problems.add(where, "the typical mistake passes every hidden test (hidden tests too weak)")
        for name, code in BROKEN.items():
            if name == "endless loop" and ch["order"] != 1:
                continue
            for x in (ch["tests"][:1] if name == "endless loop" else ch["tests"][:2] + ch["tests"][-1:]):
                if passes(code, x, rr):
                    problems.add(where, f"{name} code passed a test")
    for d, n in per.items():
        if n != 5:
            problems.add("catalog", f"needs 5 {d} challenges, has {n}")


def dump(obj):
    return json.dumps(obj, indent=1, ensure_ascii=False) + "\n"


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--runner", help="path to pypath_runner.py: also run solutions in the real sandbox")
    ap.add_argument("--check", action="store_true", help="validate only; fail if the asset is out of date")
    args = ap.parse_args(argv)
    problems = Problems()
    challenges = build(problems, args.runner)
    validate(challenges, problems, args.runner)
    doc = {"schemaVersion": 1, "challenges": challenges}
    text = dump(doc)
    if problems.items:
        for p in problems.items:
            print("PROBLEM", p)
        return 1
    if args.check:
        if not os.path.exists(OUT_PATH) or open(OUT_PATH, encoding="utf-8").read() != text:
            print("OUT OF DATE", os.path.relpath(OUT_PATH, PROJECT))
            return 1
    else:
        os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)
        with open(OUT_PATH, "w", encoding="utf-8") as f:
            f.write(text)
    nt = sum(len(c["tests"]) for c in challenges)
    nh = sum(1 for c in challenges for x in c["tests"] if x.get("hidden"))
    print(f"OK: {len(challenges)} challenges ({nt} tests, {nh} hidden)"
          + (" [sandbox checked]" if args.runner else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
