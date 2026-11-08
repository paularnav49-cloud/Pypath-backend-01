#!/usr/bin/env python3
"""
Premium Phase 2 content: Premium Project Library and Advanced Quiz Arena.

Writes two bundled, offline asset files (nothing is downloaded at run time):

    app/src/main/assets/premium/projects.json   advanced projects, real-world projects, challenges
    app/src/main/assets/premium/arena.json      difficult question sets and mock test definitions

and validates them. Standard library only:

    python content/premium_content.py              # write + validate (plain Python)
    python content/premium_content.py --runner app/src/main/python/pypath_runner.py
                                                    # also run every check inside the real PyPath sandbox
    python content/premium_content.py --check      # validate only; fails if the assets are out of date

What is validated
-----------------
* Ids are unique; every challenge belongs to an existing project and vice versa.
* Challenges follow the Practical task rules (content/validate_tasks.py): valid check mode, 3-5
  tests, an "Edge case" test, a hidden test for medium/hard, a hint, and a reference solution that
  imports nothing the sandbox blocks. Every solution is run once per test (twice, for determinism)
  and must pass the test with output equal to the test's expectedOutput.
* Every project's reference solution runs with its sample inputs; the transcript becomes the
  project's sample run, so the example shown to learners is always a real output.
* Every difficult question with code is executed. For MCQs exactly one option equals the real
  output (or the name of the exception raised); for "predict" questions the answer equals the
  real output.

The reference solutions stay in this script. They are not shipped in the app.
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
OUT_DIR = os.path.join(PROJECT, "app/src/main/assets/premium")
PROJECTS_PATH = os.path.join(OUT_DIR, "projects.json")
ARENA_PATH = os.path.join(OUT_DIR, "arena.json")

# Every Premium challenge id starts with this prefix (PremiumIds.CHALLENGE_PREFIX in Kotlin), so
# its completion is stored in the Premium study store and never in the course progress.
CHALLENGE_PREFIX = "premium-ch-"


# ═══════════════════════════════ 1. Projects ═══════════════════════════════

def step(title, text):
    return {"title": title, "text": text}


PROJECTS = [
    # ── Advanced Python Projects (beginner → advanced) ──
    {
        "id": "adv-patterns", "collection": "advanced", "difficulty": "easy",
        "title": "Number Pattern Studio", "fileName": "patterns.py", "recommendedAfter": "l3s4",
        "summary": "Print number triangles and squares from a size the user types.",
        "concepts": ["for loops", "range()", "nested loops", "end="],
        "objectives": [
            "Use a loop inside a loop to build rows and columns",
            "Control where print() ends a line with end=",
            "Turn one number from input() into a whole pattern",
        ],
        "steps": [
            step("Read the size", "Ask for a size with input() and convert it with int()."),
            step("One loop per row", "Write a for loop over range(1, size + 1). Each pass prints one row."),
            step("Numbers in a row", "Inside it, loop again from 1 to the row number and print each number with end=\" \"."),
            step("Finish the row", "After the inner loop, call print() on its own to start the next line."),
            step("Try other sizes", "Run it with 1, 3 and 6. A size of 1 should print a single 1."),
        ],
        "starterCode": "# Number Pattern Studio\nsize = int(input(\"Size: \"))\n\n# Print one row per number from 1 to size\n",
        "sampleInputs": ["4"],
        "solution": (
            "size = int(input(\"Size: \"))\n"
            "for row in range(1, size + 1):\n"
            "    for n in range(1, row + 1):\n"
            "        print(n, end=\" \")\n"
            "    print()\n"
        ),
    },
    {
        "id": "adv-words", "collection": "advanced", "difficulty": "easy",
        "title": "Word Statistics", "fileName": "word_stats.py", "recommendedAfter": "l6s4",
        "summary": "Analyse a sentence: count its words, find the longest one and the most common one.",
        "concepts": ["split()", "lower()", "dictionaries", ".get()"],
        "objectives": [
            "Split text into a list of words",
            "Count things with a dictionary and .get()",
            "Find a maximum while looping",
        ],
        "steps": [
            step("Read and split", "Read a sentence, make it lower case and split() it into words."),
            step("Count the words", "len() of the list is the word count."),
            step("Longest word", "Loop over the words and keep the longest seen so far (the first one wins a tie)."),
            step("Most common word", "Count every word in a dictionary with counts[w] = counts.get(w, 0) + 1, then loop to find the biggest count."),
            step("Print a report", "Print Words, Longest and Most common, each on its own line."),
        ],
        "starterCode": "# Word Statistics\ntext = input(\"Sentence: \")\nwords = text.lower().split()\n\n",
        "sampleInputs": ["the cat and the hat"],
        "solution": (
            "text = input(\"Sentence: \")\n"
            "words = text.lower().split()\n"
            "longest = \"\"\n"
            "counts = {}\n"
            "for w in words:\n"
            "    if len(w) > len(longest):\n"
            "        longest = w\n"
            "    counts[w] = counts.get(w, 0) + 1\n"
            "best = \"\"\n"
            "for w in words:\n"
            "    if best == \"\" or counts[w] > counts[best]:\n"
            "        best = w\n"
            "print(\"Words:\", len(words))\n"
            "print(\"Longest:\", longest)\n"
            "print(\"Most common:\", best)\n"
        ),
    },
    {
        "id": "adv-gradebook", "collection": "advanced", "difficulty": "medium",
        "title": "Class Gradebook", "fileName": "gradebook.py", "recommendedAfter": "l6s2",
        "summary": "Store marks for several students, then print each average and grade with functions.",
        "concepts": ["functions", "return", "lists", "f-strings"],
        "objectives": [
            "Split a program into small functions that return values",
            "Keep related data together in a list",
            "Format numbers neatly with :.1f",
        ],
        "steps": [
            step("A function for the average", "Write average(marks) that returns sum(marks) / len(marks)."),
            step("A function for the grade", "Write grade(avg): A from 90, B from 75, C from 50, otherwise F."),
            step("Read the students", "Ask how many students, then for each one read a name and three marks."),
            step("Print a report line", "For each student print the name, the average with :.1f and the grade."),
            step("Class average", "At the end, print the average of all students' averages."),
        ],
        "starterCode": (
            "# Class Gradebook\n"
            "def average(marks):\n"
            "    # return the average of the list\n"
            "    pass\n\n"
            "def grade(avg):\n"
            "    # return \"A\", \"B\", \"C\" or \"F\"\n"
            "    pass\n\n"
        ),
        "sampleInputs": ["2", "Asha", "90", "95", "88", "Ravi", "60", "72", "55"],
        "solution": (
            "def average(marks):\n"
            "    return sum(marks) / len(marks)\n\n"
            "def grade(avg):\n"
            "    if avg >= 90:\n"
            "        return \"A\"\n"
            "    elif avg >= 75:\n"
            "        return \"B\"\n"
            "    elif avg >= 50:\n"
            "        return \"C\"\n"
            "    return \"F\"\n\n"
            "count = int(input(\"Students: \"))\n"
            "averages = []\n"
            "for i in range(count):\n"
            "    name = input(\"Name: \")\n"
            "    marks = []\n"
            "    for j in range(3):\n"
            "        marks.append(int(input(\"Mark: \")))\n"
            "    avg = average(marks)\n"
            "    averages.append(avg)\n"
            "    print(f\"{name}: {avg:.1f} ({grade(avg)})\")\n"
            "print(f\"Class average: {average(averages):.1f}\")\n"
        ),
    },
    {
        "id": "adv-temperature", "collection": "advanced", "difficulty": "medium",
        "title": "Temperature Log Analyser", "fileName": "temperatures.py", "recommendedAfter": "l8s4",
        "summary": "Collect temperature readings until the user types done, skipping anything that isn't a number.",
        "concepts": ["while loops", "try / except", "float()", "min() and max()"],
        "objectives": [
            "Keep reading input in a loop until a stop word",
            "Survive bad input with try / except ValueError",
            "Summarise a list with min(), max() and an average",
        ],
        "steps": [
            step("Loop until done", "Use while True and break when the user types done."),
            step("Convert safely", "Wrap float(text) in try. In except ValueError print a short message and continue."),
            step("Store good readings", "Append every valid number to a list."),
            step("Handle no data", "If the list is empty at the end, print No readings and stop."),
            step("Print the summary", "Print the count, lowest, highest and the average with :.1f."),
        ],
        "starterCode": "# Temperature Log Analyser\nreadings = []\n\nwhile True:\n    text = input(\"Reading: \")\n    # stop on \"done\", skip bad numbers\n    break\n",
        "sampleInputs": ["21.5", "hot", "19", "24", "done"],
        "solution": (
            "readings = []\n"
            "while True:\n"
            "    text = input(\"Reading: \")\n"
            "    if text == \"done\":\n"
            "        break\n"
            "    try:\n"
            "        readings.append(float(text))\n"
            "    except ValueError:\n"
            "        print(\"Not a number, skipped\")\n"
            "if len(readings) == 0:\n"
            "    print(\"No readings\")\n"
            "else:\n"
            "    print(\"Readings:\", len(readings))\n"
            "    print(\"Lowest:\", min(readings))\n"
            "    print(\"Highest:\", max(readings))\n"
            "    print(f\"Average: {sum(readings) / len(readings):.1f}\")\n"
        ),
    },
    {
        "id": "adv-todo-file", "collection": "advanced", "difficulty": "medium",
        "title": "To-do List Saved to a File", "fileName": "todo.py", "recommendedAfter": "l10s3",
        "summary": "Add tasks, save them to todo.txt and read them back, even when the file doesn't exist yet.",
        "concepts": ["with open()", "\"a\" and \"r\" modes", "FileNotFoundError", "strip()"],
        "objectives": [
            "Append lines to a text file",
            "Read a file line by line and clean each line",
            "Handle a missing file without crashing",
        ],
        "steps": [
            step("Show saved tasks", "Write show_tasks(): open todo.txt in a try block and print each line with strip(). In except FileNotFoundError print No tasks yet."),
            step("Add tasks", "Ask for tasks in a loop. Stop on an empty answer."),
            step("Append each task", "Open todo.txt with \"a\" and write the task followed by \\n."),
            step("Show the list again", "Call show_tasks() once more so the new tasks appear."),
            step("Run it twice", "Run the program again: the tasks from the first run are still in the file (in this session)."),
        ],
        "starterCode": "# To-do List Saved to a File\ndef show_tasks():\n    pass\n\nshow_tasks()\n",
        "sampleInputs": ["Revise loops", "Finish project", ""],
        "solution": (
            "def show_tasks():\n"
            "    try:\n"
            "        with open(\"todo.txt\") as f:\n"
            "            for line in f:\n"
            "                print(\"-\", line.strip())\n"
            "    except FileNotFoundError:\n"
            "        print(\"No tasks yet\")\n\n"
            "show_tasks()\n"
            "while True:\n"
            "    task = input(\"New task (Enter to stop): \")\n"
            "    if task == \"\":\n"
            "        break\n"
            "    with open(\"todo.txt\", \"a\") as f:\n"
            "        f.write(task + \"\\n\")\n"
            "print(\"Your tasks:\")\n"
            "show_tasks()\n"
        ),
    },
    {
        "id": "adv-library", "collection": "advanced", "difficulty": "hard",
        "title": "Library System with Classes", "fileName": "library.py", "recommendedAfter": "l11s5",
        "summary": "Model books as objects, lend and return them, and print the library with __str__.",
        "concepts": ["classes", "__init__", "methods", "__str__", "lists of objects"],
        "objectives": [
            "Design a class with attributes and methods",
            "Keep many objects in a list and search it",
            "Give objects a readable print form with __str__",
        ],
        "steps": [
            step("The Book class", "Give Book a title, an author and available = True in __init__."),
            step("Lend and return", "lend() sets available to False and returns True, or returns False if it was already lent. give_back() sets it to True."),
            step("Printing a book", "__str__ returns e.g. \"Dune by Herbert (available)\" or \"(on loan)\"."),
            step("The library", "Make a list of three Book objects and a find(title) function that returns the matching book or None."),
            step("Try it", "Lend one book twice, return it, and print every book after each change."),
        ],
        "starterCode": (
            "# Library System with Classes\n"
            "class Book:\n"
            "    def __init__(self, title, author):\n"
            "        self.title = title\n"
            "        self.author = author\n\n"
        ),
        "sampleInputs": [],
        "solution": (
            "class Book:\n"
            "    def __init__(self, title, author):\n"
            "        self.title = title\n"
            "        self.author = author\n"
            "        self.available = True\n\n"
            "    def lend(self):\n"
            "        if not self.available:\n"
            "            return False\n"
            "        self.available = False\n"
            "        return True\n\n"
            "    def give_back(self):\n"
            "        self.available = True\n\n"
            "    def __str__(self):\n"
            "        state = \"available\" if self.available else \"on loan\"\n"
            "        return f\"{self.title} by {self.author} ({state})\"\n\n"
            "books = [Book(\"Dune\", \"Herbert\"), Book(\"Matilda\", \"Dahl\"), Book(\"Wings of Fire\", \"Kalam\")]\n\n"
            "def find(title):\n"
            "    for b in books:\n"
            "        if b.title == title:\n"
            "            return b\n"
            "    return None\n\n"
            "book = find(\"Matilda\")\n"
            "print(\"Lend:\", book.lend())\n"
            "print(\"Lend again:\", book.lend())\n"
            "for b in books:\n"
            "    print(b)\n"
            "book.give_back()\n"
            "print(book)\n"
        ),
    },
    # ── Real-World Project Collection ──
    {
        "id": "rw-calculator", "collection": "realworld", "difficulty": "easy",
        "title": "Calculator", "fileName": "calculator.py", "recommendedAfter": "l8s2",
        "summary": "A two-number calculator for +, -, * and / that never crashes on division by zero.",
        "concepts": ["float()", "if / elif", "try / except", "f-strings"],
        "objectives": [
            "Choose an operation with if / elif",
            "Show a friendly message instead of a ZeroDivisionError",
            "Print results with two decimal places",
        ],
        "steps": [
            step("Read the numbers", "Read the first number, the operator and the second number. Convert numbers with float()."),
            step("Pick the operation", "Use if / elif for +, -, * and /."),
            step("Protect division", "If the second number is 0 and the operator is /, print Cannot divide by zero."),
            step("Unknown operator", "Anything else prints Unknown operator."),
            step("Show the result", "Print Result: with :.2f, e.g. Result: 3.50."),
        ],
        "starterCode": "# Calculator\na = float(input(\"First number: \"))\nop = input(\"Operator (+ - * /): \")\nb = float(input(\"Second number: \"))\n\n",
        "sampleInputs": ["7", "/", "2"],
        "solution": None,  # same program as the challenge solution (filled in below)
        "solutionFromChallenge": "premium-ch-calculator",
    },
    {
        "id": "rw-expenses", "collection": "realworld", "difficulty": "medium",
        "title": "Expense Tracker", "fileName": "expenses.py", "recommendedAfter": "l6s4",
        "summary": "Record expenses by category and print a total for each category and overall.",
        "concepts": ["dictionaries", "split()", "int()", "loops"],
        "objectives": [
            "Parse one line of input into two values",
            "Add up amounts per category with a dictionary",
            "Print a tidy summary",
        ],
        "steps": [
            step("How many?", "Ask how many expenses there are."),
            step("Read each line", "Each expense is typed as category amount, for example food 120. Use split()."),
            step("Add per category", "totals[cat] = totals.get(cat, 0) + amount keeps a running total."),
            step("Print each category", "Loop over totals.items() and print category: amount in the order they first appeared."),
            step("Grand total", "Print Total: with the sum of all amounts."),
        ],
        "starterCode": "# Expense Tracker\ntotals = {}\ncount = int(input(\"How many expenses? \"))\n\n",
        "sampleInputs": ["3", "food 120", "bus 30", "food 50"],
        "solution": None,
        "solutionFromChallenge": "premium-ch-expenses",
    },
    {
        "id": "rw-quiz-app", "collection": "realworld", "difficulty": "medium",
        "title": "Quiz Application", "fileName": "quiz_app.py", "recommendedAfter": "l6s3",
        "summary": "Ask questions stored in a list of dictionaries, accept answers in any case and show the score.",
        "concepts": ["lists of dictionaries", "lower() and strip()", "counters", "f-strings"],
        "objectives": [
            "Keep quiz data separate from the quiz logic",
            "Compare answers fairly with strip() and lower()",
            "Report a score and a short message",
        ],
        "steps": [
            step("Store the questions", "Make a list of dictionaries, each with a \"q\" and an \"a\" key."),
            step("Ask each question", "Loop over the list and ask each question with input()."),
            step("Mark the answer", "Compare answer.strip().lower() with the stored answer. Print Correct! or the right answer."),
            step("Keep score", "Add 1 to score for every correct answer."),
            step("Final message", "Print Score: 2/3 and Well done! when every answer was right."),
        ],
        "starterCode": (
            "# Quiz Application\n"
            "questions = [\n"
            "    {\"q\": \"Capital of France? \", \"a\": \"paris\"},\n"
            "    {\"q\": \"5 + 7 = \", \"a\": \"12\"},\n"
            "    {\"q\": \"Opposite of hot? \", \"a\": \"cold\"},\n"
            "]\n"
            "score = 0\n\n"
        ),
        "sampleInputs": ["Paris", "11", " COLD "],
        "solution": None,
        "solutionFromChallenge": "premium-ch-quiz-app",
    },
    {
        "id": "rw-password", "collection": "realworld", "difficulty": "medium",
        "title": "Password Generator and Checker", "fileName": "passwords.py", "recommendedAfter": "l9s2",
        "summary": "Generate random passwords with the random module, then rate how strong a password is.",
        "concepts": ["import random", "random.choice()", "loops over strings", "counters"],
        "objectives": [
            "Build a string one random character at a time",
            "Check a string for digits, capitals and small letters",
            "Turn several checks into a single rating",
        ],
        "steps": [
            step("Pick a character set", "Make one string with letters and digits, e.g. letters = \"abc...XYZ0123456789\"."),
            step("Generate", "Ask for a length and add random.choice(letters) to an empty string that many times."),
            step("Check strength", "For the password to check, count which rules it meets: 8 or more characters, a digit, a capital, a small letter."),
            step("Rate it", "4 rules: Strong. 3 rules: Medium. Fewer: Weak."),
            step("Try both", "Generate one password, then check a weak one like abc."),
        ],
        "starterCode": "# Password Generator and Checker\nimport random\n\nletters = \"abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789\"\n\n",
        "sampleInputs": ["abc"],
        "solution": (
            "import random\n\n"
            "letters = \"abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789\"\n"
            "random.seed(7)\n"
            "password = \"\"\n"
            "for i in range(10):\n"
            "    password += random.choice(letters)\n"
            "print(\"Generated:\", len(password), \"characters\")\n\n"
            "def strength(pw):\n"
            "    rules = 0\n"
            "    if len(pw) >= 8:\n"
            "        rules += 1\n"
            "    digit = False\n"
            "    upper = False\n"
            "    lower = False\n"
            "    for ch in pw:\n"
            "        if ch in \"0123456789\":\n"
            "            digit = True\n"
            "        elif ch.upper() == ch:\n"
            "            upper = True\n"
            "        else:\n"
            "            lower = True\n"
            "    for ok in [digit, upper, lower]:\n"
            "        if ok:\n"
            "            rules += 1\n"
            "    if rules == 4:\n"
            "        return \"Strong\"\n"
            "    elif rules == 3:\n"
            "        return \"Medium\"\n"
            "    return \"Weak\"\n\n"
            "print(strength(password))\n"
            "print(strength(input(\"Check a password: \")))\n"
        ),
        # The generated password is random, so the sample run only shows its length and rating.
        "sampleNote": "The generated password is different on every run (random.seed(7) was used for this example).",
    },
    {
        "id": "rw-contacts", "collection": "realworld", "difficulty": "hard",
        "title": "Contact Book", "fileName": "contacts.py", "recommendedAfter": "l6s4",
        "summary": "A small data-management tool: add, find and delete contacts with simple commands.",
        "concepts": ["dictionaries", "split()", "while loops", "if / elif"],
        "objectives": [
            "Read commands in a loop until end",
            "Add, look up and remove dictionary entries",
            "Give clear messages for missing contacts",
        ],
        "steps": [
            step("Command loop", "Read a command in a while True loop and split() it into words. end stops the loop."),
            step("add name phone", "Store the phone number under the name."),
            step("find name", "Print the number, or Not found."),
            step("delete name", "Remove the contact with pop(name), or print Not found."),
            step("Summary", "After end, print Contacts: and how many are saved."),
        ],
        "starterCode": "# Contact Book\ncontacts = {}\n\nwhile True:\n    parts = input(\"> \").split()\n    if parts[0] == \"end\":\n        break\n",
        "sampleInputs": ["add Asha 98765", "add Ravi 91234", "find Ravi", "delete Asha", "find Asha", "end"],
        "solution": None,
        "solutionFromChallenge": "premium-ch-contacts",
    },
]


# ═══════════════════════════════ 2. Challenges ═══════════════════════════════
# Practical-task format (see PracticalTask in PracticalTasks.kt). Each one belongs to a project.

def t(stdin, expected, description=None, hidden=False, pattern=None, must=None):
    d = {"stdin": stdin, "expectedOutput": expected}
    if description:
        d["description"] = description
    if hidden:
        d["hidden"] = True
    if pattern:
        d["pattern"] = pattern
    if must:
        d["mustContain"] = must
    return d


CHALLENGES = [
    {
        "id": "premium-ch-triangle", "projectId": "adv-patterns", "title": "Number triangle",
        "difficulty": "easy", "concepts": ["nested loops", "end="],
        "instructions": "Read a size n and print a triangle: row 1 is 1, row 2 is 1 2, up to row n. Numbers are separated by single spaces.",
        "requirements": ["Ask with input(\"Size: \")", "Print n rows", "Row r shows the numbers 1 to r"],
        "example": "Size: 3\n1\n1 2\n1 2 3",
        "hint": "print(n, end=\" \") keeps numbers on one line. Trailing spaces don't matter.",
        "starterCode": "size = int(input(\"Size: \"))\n",
        "checkMode": "exact",
        "solution": "size = int(input(\"Size: \"))\nfor row in range(1, size + 1):\n    for n in range(1, row + 1):\n        print(n, end=\" \")\n    print()\n",
        "tests": [
            ([ "3" ], "Size 3", False),
            ([ "5" ], "Size 5", False),
            ([ "1" ], "Edge case: size 1 prints a single 1", False),
            ([ "7" ], None, True),
        ],
    },
    {
        "id": "premium-ch-word-stats", "projectId": "adv-words", "title": "Sentence report",
        "difficulty": "medium", "concepts": ["split()", "dictionaries", ".get()"],
        "instructions": "Read a sentence, make it lower case and print three lines: the number of words, the longest word (first one if tied) and the most common word (first one to reach the top count).",
        "requirements": [
            "Ask with input(\"Sentence: \")",
            "Print Words: n",
            "Print Longest: word",
            "Print Most common: word",
        ],
        "example": "Sentence: the cat and the hat\nWords: 5\nLongest: the\nMost common: the",
        "hint": "Loop once to count with counts[w] = counts.get(w, 0) + 1, then loop again over the words in order to pick the first word with the highest count.",
        "starterCode": "text = input(\"Sentence: \")\nwords = text.lower().split()\n",
        "checkMode": "exact",
        "solution": None,  # = project adv-words
        "solutionFromProject": "adv-words",
        "tests": [
            (["the cat and the hat"], "A short sentence", False),
            (["Python is fun and python is easy"], "Capital letters count as the same word", False),
            (["hello"], "Edge case: a single word", False),
            (["a bb a ccc bb bb"], None, True),
        ],
    },
    {
        "id": "premium-ch-grades", "projectId": "adv-gradebook", "title": "Average and grade",
        "difficulty": "medium", "concepts": ["functions", "return", ":.1f"],
        "instructions": "Read how many marks there are, then each mark. Print the average with one decimal place and the grade: A from 90, B from 75, C from 50, otherwise F.",
        "requirements": [
            "Ask with input(\"Marks: \") for the count and input(\"Mark: \") for each mark",
            "Print Average: with :.1f",
            "Print Grade: and the letter",
        ],
        "example": "Marks: 3\nMark: 90\nMark: 80\nMark: 70\nAverage: 80.0\nGrade: B",
        "hint": "A grade(avg) function with if / elif / elif / else that returns a letter keeps it tidy. Check the biggest threshold first.",
        "starterCode": "def grade(avg):\n    pass\n\ncount = int(input(\"Marks: \"))\n",
        "checkMode": "exact",
        "solution": (
            "def grade(avg):\n"
            "    if avg >= 90:\n"
            "        return \"A\"\n"
            "    elif avg >= 75:\n"
            "        return \"B\"\n"
            "    elif avg >= 50:\n"
            "        return \"C\"\n"
            "    return \"F\"\n\n"
            "count = int(input(\"Marks: \"))\n"
            "total = 0\n"
            "for i in range(count):\n"
            "    total += int(input(\"Mark: \"))\n"
            "avg = total / count\n"
            "print(f\"Average: {avg:.1f}\")\n"
            "print(\"Grade:\", grade(avg))\n"
        ),
        "tests": [
            (["3", "90", "80", "70"], "Three marks, grade B", False),
            (["2", "95", "91"], "Grade A", False),
            (["2", "75", "74"], "Edge case: 74.5 is just below a B", False),
            (["1", "50"], "Edge case: exactly 50 is a C", False),
            (["4", "10", "20", "30", "41"], None, True),
        ],
    },
    {
        "id": "premium-ch-readings", "projectId": "adv-temperature", "title": "Safe readings",
        "difficulty": "medium", "concepts": ["while True", "try / except", "float()"],
        "instructions": "Read values until the user types done. Skip anything that isn't a number by printing Skipped: followed by the text. At the end print the count and the total with one decimal place, or No readings if there were none.",
        "requirements": [
            "Ask with input(\"Reading: \")",
            "Print Skipped: text for bad values",
            "Print Count: n and Total: with :.1f, or No readings",
        ],
        "example": "Reading: 2.5\nReading: abc\nSkipped: abc\nReading: 4\nReading: done\nCount: 2\nTotal: 6.5",
        "hint": "Put only float(text) inside try. except ValueError: print the message, then continue.",
        "starterCode": "readings = []\nwhile True:\n    text = input(\"Reading: \")\n    break\n",
        "checkMode": "exact",
        "solution": (
            "readings = []\n"
            "while True:\n"
            "    text = input(\"Reading: \")\n"
            "    if text == \"done\":\n"
            "        break\n"
            "    try:\n"
            "        readings.append(float(text))\n"
            "    except ValueError:\n"
            "        print(\"Skipped:\", text)\n"
            "if len(readings) == 0:\n"
            "    print(\"No readings\")\n"
            "else:\n"
            "    print(\"Count:\", len(readings))\n"
            "    print(f\"Total: {sum(readings):.1f}\")\n"
        ),
        "tests": [
            (["2.5", "abc", "4", "done"], "One bad value is skipped", False),
            (["10", "20", "30", "done"], "Only numbers", False),
            (["done"], "Edge case: no readings at all", False),
            (["x", "y", "done"], "Edge case: every value is bad", False),
            (["-3", "1.25", "seven", "0", "done"], None, True),
        ],
    },
    {
        "id": "premium-ch-account", "projectId": "adv-library", "title": "Bank account object",
        "difficulty": "hard", "concepts": ["classes", "methods", "split()"],
        "instructions": "Write an Account class with a balance and deposit / withdraw methods. Read commands until end: deposit 50 adds money, withdraw 30 takes it out, but a withdrawal bigger than the balance prints Not enough money and changes nothing. After end print Balance: and the amount.",
        "requirements": [
            "Use a class with a balance attribute starting at 0",
            "Read commands with input(\"> \")",
            "Print Not enough money for a refused withdrawal",
            "Print Balance: n after end",
        ],
        "example": "> deposit 50\n> withdraw 80\nNot enough money\n> withdraw 20\n> end\nBalance: 30",
        "hint": "parts = input(\"> \").split() gives [\"deposit\", \"50\"]. Convert parts[1] with int(). withdraw() can return False when it refuses.",
        "starterCode": "class Account:\n    def __init__(self):\n        self.balance = 0\n\naccount = Account()\n",
        "checkMode": "exact",
        "solution": (
            "class Account:\n"
            "    def __init__(self):\n"
            "        self.balance = 0\n\n"
            "    def deposit(self, amount):\n"
            "        self.balance += amount\n\n"
            "    def withdraw(self, amount):\n"
            "        if amount > self.balance:\n"
            "            return False\n"
            "        self.balance -= amount\n"
            "        return True\n\n"
            "account = Account()\n"
            "while True:\n"
            "    parts = input(\"> \").split()\n"
            "    if parts[0] == \"end\":\n"
            "        break\n"
            "    amount = int(parts[1])\n"
            "    if parts[0] == \"deposit\":\n"
            "        account.deposit(amount)\n"
            "    elif parts[0] == \"withdraw\":\n"
            "        if not account.withdraw(amount):\n"
            "            print(\"Not enough money\")\n"
            "print(\"Balance:\", account.balance)\n"
        ),
        "tests": [
            (["deposit 50", "withdraw 80", "withdraw 20", "end"], "A refused and an accepted withdrawal", False),
            (["deposit 100", "deposit 25", "end"], "Two deposits", False),
            (["end"], "Edge case: no commands, balance stays 0", False),
            (["deposit 10", "withdraw 10", "withdraw 1", "end"], "Edge case: emptying the account exactly", False),
            (["withdraw 5", "deposit 7", "withdraw 7", "deposit 3", "end"], None, True),
        ],
    },
    {
        "id": "premium-ch-calculator", "projectId": "rw-calculator", "title": "Safe calculator",
        "difficulty": "easy", "concepts": ["if / elif", "float()", ":.2f"],
        "instructions": "Read two numbers and an operator (+, -, * or /). Print Result: with two decimal places. Division by zero prints Cannot divide by zero; any other operator prints Unknown operator.",
        "requirements": [
            "Ask with input(\"First number: \"), input(\"Operator (+ - * /): \") and input(\"Second number: \")",
            "Print Result: with :.2f",
            "Handle division by zero and unknown operators",
        ],
        "example": "First number: 7\nOperator (+ - * /): /\nSecond number: 2\nResult: 3.50",
        "hint": "Check op == \"/\" and b == 0 before dividing. f\"Result: {r:.2f}\" gives two decimal places.",
        "starterCode": "a = float(input(\"First number: \"))\nop = input(\"Operator (+ - * /): \")\nb = float(input(\"Second number: \"))\n",
        "checkMode": "exact",
        "solution": (
            "a = float(input(\"First number: \"))\n"
            "op = input(\"Operator (+ - * /): \")\n"
            "b = float(input(\"Second number: \"))\n"
            "if op == \"+\":\n"
            "    print(f\"Result: {a + b:.2f}\")\n"
            "elif op == \"-\":\n"
            "    print(f\"Result: {a - b:.2f}\")\n"
            "elif op == \"*\":\n"
            "    print(f\"Result: {a * b:.2f}\")\n"
            "elif op == \"/\":\n"
            "    if b == 0:\n"
            "        print(\"Cannot divide by zero\")\n"
            "    else:\n"
            "        print(f\"Result: {a / b:.2f}\")\n"
            "else:\n"
            "    print(\"Unknown operator\")\n"
        ),
        "tests": [
            (["7", "/", "2"], "Division", False),
            (["3", "*", "4.5"], "Multiplication with a decimal", False),
            (["5", "/", "0"], "Edge case: division by zero", False),
            (["5", "%", "2"], "Edge case: unknown operator", False),
            (["-2.5", "-", "1"], None, True),
        ],
    },
    {
        "id": "premium-ch-expenses", "projectId": "rw-expenses", "title": "Category totals",
        "difficulty": "medium", "concepts": ["dictionaries", "split()", ".items()"],
        "instructions": "Read how many expenses there are, then each one as category amount (e.g. food 120). Print each category's total in the order the categories first appeared, then the overall total.",
        "requirements": [
            "Ask with input(\"How many expenses? \") and input(\"Expense: \")",
            "Print category: total for each category",
            "Print Total: n last",
        ],
        "example": "How many expenses? 3\nExpense: food 120\nExpense: bus 30\nExpense: food 50\nfood: 170\nbus: 30\nTotal: 200",
        "hint": "Dictionaries remember the order keys were added, so looping over totals.items() prints categories in first-seen order.",
        "starterCode": "totals = {}\ncount = int(input(\"How many expenses? \"))\n",
        "checkMode": "exact",
        "solution": (
            "totals = {}\n"
            "count = int(input(\"How many expenses? \"))\n"
            "for i in range(count):\n"
            "    parts = input(\"Expense: \").split()\n"
            "    cat = parts[0]\n"
            "    amount = int(parts[1])\n"
            "    totals[cat] = totals.get(cat, 0) + amount\n"
            "for cat, amount in totals.items():\n"
            "    print(f\"{cat}: {amount}\")\n"
            "print(\"Total:\", sum(totals.values()))\n"
        ),
        "tests": [
            (["3", "food 120", "bus 30", "food 50"], "Two categories", False),
            (["2", "books 300", "books 45"], "One category twice", False),
            (["0"], "Edge case: no expenses gives Total: 0", False),
            (["4", "tea 10", "bus 20", "tea 15", "gift 5"], None, True),
        ],
    },
    {
        "id": "premium-ch-quiz-app", "projectId": "rw-quiz-app", "title": "Quiz scorer",
        "difficulty": "medium", "concepts": ["lists of dictionaries", "lower()", "strip()"],
        "instructions": "Ask the three questions from the starter code in order. Answers count as correct whatever their capital letters or extra spaces. Print Correct! or Answer: and the right answer after each question, then Score: n/3, and Well done! when all three are right.",
        "requirements": [
            "Use each question text as the input() prompt",
            "Compare with answer.strip().lower()",
            "Print Correct! or Answer: x",
            "Print Score: n/3 (and Well done! for 3/3)",
        ],
        "example": "Capital of France? Paris\nCorrect!\n5 + 7 = 11\nAnswer: 12\nOpposite of hot?  COLD \nCorrect!\nScore: 2/3",
        "hint": "Loop with for item in questions: then use item[\"q\"] and item[\"a\"].",
        "starterCode": (
            "questions = [\n"
            "    {\"q\": \"Capital of France? \", \"a\": \"paris\"},\n"
            "    {\"q\": \"5 + 7 = \", \"a\": \"12\"},\n"
            "    {\"q\": \"Opposite of hot? \", \"a\": \"cold\"},\n"
            "]\n"
            "score = 0\n"
        ),
        "checkMode": "exact",
        "solution": (
            "questions = [\n"
            "    {\"q\": \"Capital of France? \", \"a\": \"paris\"},\n"
            "    {\"q\": \"5 + 7 = \", \"a\": \"12\"},\n"
            "    {\"q\": \"Opposite of hot? \", \"a\": \"cold\"},\n"
            "]\n"
            "score = 0\n"
            "for item in questions:\n"
            "    answer = input(item[\"q\"])\n"
            "    if answer.strip().lower() == item[\"a\"]:\n"
            "        print(\"Correct!\")\n"
            "        score += 1\n"
            "    else:\n"
            "        print(\"Answer:\", item[\"a\"])\n"
            "print(f\"Score: {score}/{len(questions)}\")\n"
            "if score == len(questions):\n"
            "    print(\"Well done!\")\n"
        ),
        "tests": [
            (["Paris", "11", " COLD "], "Two right, one wrong", False),
            (["paris", "12", "cold"], "All right shows Well done!", False),
            (["", "", ""], "Edge case: empty answers score 0", False),
            (["PARIS ", " 12", "warm"], None, True),
        ],
    },
    {
        "id": "premium-ch-strength", "projectId": "rw-password", "title": "Password strength",
        "difficulty": "medium", "concepts": ["loops over strings", "counters", "if / elif"],
        "instructions": "Read a password and rate it. Count the rules it meets: at least 8 characters, contains a digit, contains a capital letter, contains a small letter. 4 rules: Strong. 3 rules: Medium. Otherwise: Weak.",
        "requirements": ["Ask with input(\"Password: \")", "Print exactly Strong, Medium or Weak"],
        "example": "Password: Secret123\nStrong",
        "hint": "Loop over the characters and set three True/False flags. ch in \"0123456789\" checks for a digit; ch.upper() == ch and ch.lower() != ch means a capital.",
        "starterCode": "password = input(\"Password: \")\n",
        "checkMode": "regex",
        "solution": (
            "password = input(\"Password: \")\n"
            "digit = False\n"
            "upper = False\n"
            "lower = False\n"
            "for ch in password:\n"
            "    if ch in \"0123456789\":\n"
            "        digit = True\n"
            "    elif ch.upper() == ch and ch.lower() != ch:\n"
            "        upper = True\n"
            "    elif ch.lower() == ch and ch.upper() != ch:\n"
            "        lower = True\n"
            "rules = 0\n"
            "for ok in [len(password) >= 8, digit, upper, lower]:\n"
            "    if ok:\n"
            "        rules += 1\n"
            "if rules == 4:\n"
            "    print(\"Strong\")\n"
            "elif rules == 3:\n"
            "    print(\"Medium\")\n"
            "else:\n"
            "    print(\"Weak\")\n"
        ),
        # Prompt wording is free; the rating must be alone on the last line.
        "patterns": {"Strong": r"(?:\A|\n)Strong\Z", "Medium": r"(?:\A|\n)Medium\Z", "Weak": r"(?:\A|\n)Weak\Z"},
        "tests": [
            (["Secret123"], "All four rules: Strong", False),
            (["secret123"], "No capital letter: Medium", False),
            (["abc"], "Edge case: short and simple is Weak", False),
            (["Ab1"], "Edge case: three kinds of character but too short is Medium", False),
            (["!!!!!!!!"], None, True),
        ],
    },
    {
        "id": "premium-ch-contacts", "projectId": "rw-contacts", "title": "Contact commands",
        "difficulty": "hard", "concepts": ["dictionaries", "while True", "split()"],
        "instructions": "Read commands until end. add name phone saves a contact, find name prints the phone number or Not found, delete name removes it or prints Not found. After end print Contacts: and the number saved.",
        "requirements": [
            "Read commands with input(\"> \")",
            "Support add, find and delete",
            "Print Not found for missing names",
            "Print Contacts: n after end",
        ],
        "example": "> add Asha 98765\n> find Asha\n98765\n> delete Ravi\nNot found\n> end\nContacts: 1",
        "hint": "if name in contacts: checks before find or delete. contacts.pop(name) removes an entry.",
        "starterCode": "contacts = {}\n\nwhile True:\n    parts = input(\"> \").split()\n    if parts[0] == \"end\":\n        break\n",
        "checkMode": "exact",
        "solution": (
            "contacts = {}\n\n"
            "while True:\n"
            "    parts = input(\"> \").split()\n"
            "    if parts[0] == \"end\":\n"
            "        break\n"
            "    name = parts[1]\n"
            "    if parts[0] == \"add\":\n"
            "        contacts[name] = parts[2]\n"
            "    elif parts[0] == \"find\":\n"
            "        if name in contacts:\n"
            "            print(contacts[name])\n"
            "        else:\n"
            "            print(\"Not found\")\n"
            "    elif parts[0] == \"delete\":\n"
            "        if name in contacts:\n"
            "            contacts.pop(name)\n"
            "        else:\n"
            "            print(\"Not found\")\n"
            "print(\"Contacts:\", len(contacts))\n"
        ),
        "tests": [
            (["add Asha 98765", "find Asha", "delete Ravi", "end"], "Add, find and a missing delete", False),
            (["add Asha 1", "add Ravi 2", "delete Asha", "find Asha", "end"], "Deleted contacts are gone", False),
            (["end"], "Edge case: an empty contact book", False),
            (["add Meera 5", "add Meera 6", "find Meera", "end"], "Edge case: adding the same name again replaces the number", False),
            (["add A 1", "add B 2", "add C 3", "delete B", "find B", "find C", "end"], None, True),
        ],
    },
]


# ═══════════════════════════════ 3. Difficult questions ═══════════════════════════════
# Only concepts the course teaches before each set's "afterSubLevel". Every question with code is
# executed by validate_questions(). "verify": "output" compares stdout, "error" the exception name.

def mcq(code, options, correct, explanation, prompt="What does this code print?", verify="output", hint=None):
    q = {"type": "mcq", "prompt": prompt, "code": code, "options": options, "correctIndex": correct,
         "explanation": explanation, "verify": verify}
    if hint:
        q["hint"] = hint
    return q


def predict(code, answer, explanation, prompt="Type exactly what this code prints."):
    return {"type": "predict", "prompt": prompt, "code": code, "answer": answer, "explanation": explanation,
            "verify": "output"}


ERR = "What happens when this code runs?"

HARD_SETS = [
    {
        "id": "basics", "title": "Values and conditions", "afterSubLevel": "l2s3",
        "description": "Types, operators and which branch really runs.",
        "questions": [
            mcq('x = "5"\ny = 2\nprint(x * y)', ["10", "55", "TypeError", "52"], 1,
                "x is the text \"5\". Multiplying text by a whole number repeats it, so \"5\" * 2 is \"55\"."),
            predict("a = 7\nb = 2\nprint(a // b)\nprint(a / b)", "3\n3.5",
                    "// divides and drops the decimal part (3). / always gives a float (3.5)."),
            mcq('score = 75\nif score > 50:\n    print("pass")\nelif score > 70:\n    print("good")\nelse:\n    print("fail")',
                ["pass", "good", "pass\ngood", "fail"], 0,
                "Python checks the conditions from the top and runs only the first true branch. 75 > 50 is true, so the elif is never checked."),
            mcq('print(10 == 10.0)\nprint("10" == 10)', ["True\nTrue", "True\nFalse", "False\nFalse", "False\nTrue"], 1,
                "10 and 10.0 are equal numbers. \"10\" is text, and text is never equal to a number."),
            predict('temp = 30\nif temp > 25:\n    print("hot")\nif temp > 20:\n    print("warm")\nelse:\n    print("cold")', "hot\nwarm",
                    "These are two separate if statements, so both are checked. 30 > 25 prints hot, and 30 > 20 prints warm."),
            mcq('age = 15\nprint("Age: " + age)', ["Age: 15", "Age:15", "TypeError", "NameError"], 2,
                "+ can't join text and a number. Python raises a TypeError. Use str(age) or an f-string.", prompt=ERR, verify="error"),
        ],
    },
    {
        "id": "loops", "title": "Loops", "afterSubLevel": "l3s4",
        "description": "Counting carefully: range steps, break, continue and nested loops.",
        "questions": [
            predict("total = 0\nfor n in range(1, 10, 3):\n    total += n\nprint(total)", "12",
                    "range(1, 10, 3) gives 1, 4 and 7 (it stops before 10). 1 + 4 + 7 = 12."),
            mcq("for i in range(3):\n    if i == 1:\n        continue\n    print(i)", ["0\n1\n2", "0\n2", "0", "1"], 1,
                "continue skips the rest of the loop body for that pass only. i == 1 is skipped; 0 and 2 are printed."),
            mcq("n = 10\ncount = 0\nwhile n > 1:\n    n = n // 2\n    count += 1\nprint(count)", ["2", "3", "4", "5"], 1,
                "n goes 10 → 5 → 2 → 1. The loop body ran three times, then n > 1 is false."),
            predict('for i in range(1, 4):\n    for j in range(i):\n        print("*", end="")\n    print()', "*\n**\n***",
                    "The inner loop runs i times, printing stars on the same line. print() then ends the row."),
            mcq('for n in range(5):\n    if n == 3:\n        break\n    print(n, end=" ")\nprint("done")',
                ["0 1 2 done", "0 1 2 3 done", "0 1 2", "done"], 0,
                "break stops the loop as soon as n is 3, before 3 is printed. The line after the loop still runs."),
            mcq("x = 0\nwhile x < 5:\n    x += 2\nprint(x)", ["4", "5", "6", "8"], 2,
                "x goes 0 → 2 → 4 → 6. The check happens before each pass, so 4 < 5 lets one more += 2 run."),
        ],
    },
    {
        "id": "functions", "title": "Functions and scope", "afterSubLevel": "l4s5",
        "description": "Parameters, return values, defaults and local versus global variables.",
        "questions": [
            mcq("def double(x):\n    x = x * 2\n    print(x)\n\nx = 5\ndouble(x)\nprint(x)", ["10\n10", "10\n5", "5\n5", "5\n10"], 1,
                "The x inside double is a local copy. Changing it doesn't change the global x, which is still 5."),
            mcq('def describe(name, age=10, city="Pune"):\n    return f"{name} {age} {city}"\n\nprint(describe("Meera", city="Delhi"))',
                ["Meera 10 Delhi", "Meera Delhi Pune", "Meera 10 Pune", "TypeError"], 0,
                "city= is a keyword argument, so only city changes. age keeps its default of 10."),
            mcq("def add(a, b):\n    print(a + b)\n\nresult = add(2, 3)\nprint(result)", ["5\n5", "5\nNone", "None", "5"], 1,
                "add prints 5 but has no return, so it returns None. result is None."),
            predict("count = 0\n\ndef bump():\n    global count\n    count += 1\n\nbump()\nbump()\nprint(count)", "2",
                    "global count lets the function change the global variable. Two calls add 1 twice."),
            mcq("total = 10\n\ndef change():\n    total = total + 1\n\nchange()\nprint(total)", ["11", "10", "UnboundLocalError", "NameError"], 2,
                "Assigning to total inside the function makes it local, so total + 1 reads a local that has no value yet. Python raises UnboundLocalError.",
                prompt=ERR, verify="error"),
            mcq('def check(n):\n    if n > 0:\n        return "positive"\n    print("not positive")\n    return "done"\n\nprint(check(5))',
                ["positive", "positive\nnot positive", "not positive\ndone", "positive\ndone"], 0,
                "return ends the function immediately, so the lines after it never run when n is 5."),
        ],
    },
    {
        "id": "lists", "title": "Lists", "afterSubLevel": "l5s5",
        "description": "Slices, negative indexes, list methods and comprehensions.",
        "questions": [
            mcq("nums = [10, 20, 30, 40, 50]\nprint(nums[1:4])\nprint(nums[-2])",
                ["[20, 30, 40, 50]\n40", "[20, 30, 40]\n40", "[10, 20, 30, 40]\n50", "[20, 30, 40]\n30"], 1,
                "A slice stops before its end index, so [1:4] is items 1, 2 and 3. [-2] is the second item from the end."),
            mcq("nums = [3, 1, 2]\nnums.sort()\nnums.append(0)\nprint(nums)",
                ["[0, 1, 2, 3]", "[1, 2, 3, 0]", "[3, 1, 2, 0]", "[0, 3, 1, 2]"], 1,
                "sort() sorts once, when it is called. append(0) then adds 0 at the end; the list is not sorted again."),
            predict("squares = [n * n for n in range(1, 6) if n % 2 == 1]\nprint(squares)", "[1, 9, 25]",
                    "The if keeps only odd n (1, 3, 5), and each is squared."),
            mcq('items = ["pen", "book", "bag"]\nitems.insert(1, "cap")\nitems.remove("bag")\nprint(items)',
                ["['pen', 'cap', 'book']", "['cap', 'pen', 'book']", "['pen', 'book', 'cap']", "['pen', 'cap', 'book', 'bag']"], 0,
                "insert(1, ...) puts cap at position 1, pushing book along. remove(\"bag\") deletes bag."),
            mcq("nums = [4, 8, 15]\ntotal = 0\nfor n in nums:\n    total += n\nprint(total / len(nums))", ["9", "9.0", "27", "3.0"], 1,
                "The total is 27 and there are 3 items. / always gives a float, so the answer is 9.0."),
            mcq("nums = [1, 2, 3]\nprint(nums[3])", ["3", "None", "IndexError", "[3]"], 2,
                "Indexes start at 0, so a 3-item list has indexes 0, 1 and 2. nums[3] raises IndexError.", prompt=ERR, verify="error"),
        ],
    },
    {
        "id": "strings-dicts", "title": "Strings and dictionaries", "afterSubLevel": "l6s4",
        "description": "Chained string methods, formatting, counting with dictionaries.",
        "questions": [
            mcq('text = "  Hello World  "\nprint(text.strip().lower().replace("o", "0"))',
                ["hell0 w0rld", "Hell0 W0rld", "  hell0 w0rld  ", "hello world"], 0,
                "The methods run left to right: strip spaces, make lower case, then replace every o with 0."),
            predict('words = "red,green,blue".split(",")\nprint(len(words))\nprint("-".join(words))', "3\nred-green-blue",
                    "split(\",\") makes a list of 3 words. \"-\".join puts a dash between them."),
            mcq('stock = {"apple": 5, "pear": 0}\nprint(stock.get("mango", 0) + stock["apple"])', ["5", "0", "KeyError", "None"], 0,
                ".get() returns the default 0 for a missing key instead of an error. 0 + 5 = 5."),
            mcq('price = 7.5\nprint(f"Total: {price * 2:.2f}")', ["Total: 15.00", "Total: 15.0", "Total: 15", "Total: 7.52"], 0,
                "The expression inside {} is worked out first (15.0), then :.2f shows exactly two decimal places."),
            mcq('counts = {}\nfor ch in "banana":\n    counts[ch] = counts.get(ch, 0) + 1\nprint(counts)',
                ["{'b': 1, 'a': 3, 'n': 2}", "{'a': 3, 'b': 1, 'n': 2}", "{'b': 1, 'a': 1, 'n': 1}", "{'banana': 1}"], 0,
                "Each letter's count goes up by 1. Dictionaries keep keys in the order they were first added: b, a, n."),
            mcq('ages = {"Asha": 15}\nprint(ages["Ravi"])', ["None", "0", "KeyError", "Ravi"], 2,
                "Square brackets need the key to exist. \"Ravi\" isn't a key, so Python raises KeyError. .get() would return None.",
                prompt=ERR, verify="error"),
        ],
    },
    {
        "id": "advanced", "title": "Errors, files and classes", "afterSubLevel": "l11s5",
        "description": "try / except / else / finally, reading files and how objects behave.",
        "questions": [
            mcq('try:\n    n = int("12a")\n    print("number")\nexcept ValueError:\n    print("bad")\nfinally:\n    print("end")',
                ["number\nend", "bad\nend", "bad", "number"], 1,
                "int(\"12a\") fails, so the rest of try is skipped and except runs. finally always runs."),
            mcq('def safe_div(a, b):\n    try:\n        return a / b\n    except ZeroDivisionError:\n        return "no"\n\nprint(safe_div(6, 3), safe_div(1, 0))',
                ["2.0 no", "2 no", "2.0 ZeroDivisionError", "no no"], 0,
                "6 / 3 is 2.0 (a float). 1 / 0 raises ZeroDivisionError, which is caught and \"no\" is returned."),
            mcq('try:\n    x = 5\nexcept ValueError:\n    print("error")\nelse:\n    print("ok")', ["ok", "error", "error\nok", "Nothing is printed"], 0,
                "else runs only when the try block finished without an exception. x = 5 can't fail."),
            predict("class Counter:\n    def __init__(self):\n        self.value = 0\n\n    def add(self, n=1):\n        self.value += n\n\nc = Counter()\nc.add()\nc.add(5)\nprint(c.value)", "6",
                    "add() uses the default 1 the first time, then 5. The object remembers value between calls: 0 + 1 + 5."),
            mcq('class Pet:\n    def __init__(self, name):\n        self.name = name\n\n    def __str__(self):\n        return "Pet: " + self.name\n\np = Pet("Milo")\nprint(p)',
                ["Pet: Milo", "Milo", "<__main__.Pet object>", "Pet"], 0,
                "print() uses __str__ when a class defines it, so it prints the returned text."),
            mcq('class Box:\n    def __init__(self):\n        self.items = []\n\na = Box()\nb = Box()\na.items.append("pen")\nprint(len(a.items), len(b.items))',
                ["1 1", "1 0", "0 0", "0 1"], 1,
                "__init__ runs for each object, so a and b each get their own new list. Only a's list gets the pen."),
            mcq('with open("notes.txt", "w") as f:\n    f.write("one\\n")\nwith open("notes.txt", "a") as f:\n    f.write("two\\n")\ncount = 0\nwith open("notes.txt") as f:\n    for line in f:\n        count += 1\nprint(count)',
                ["1", "2", "3", "0"], 1,
                "\"w\" writes a fresh file with one line, \"a\" adds a second line to the end. The loop counts 2 lines."),
        ],
    },
]

# Full-length mock tests: questions are drawn from the bundled course quizzes of these levels
# (a fixed selection per test, so a mock is the same every time; see ArenaEngine.buildMock).
MOCKS = [
    {"id": "mock-core", "title": "Mock test 1: Core Python", "description": "Levels 1 to 6: basics, conditions, loops, functions, lists, strings and dictionaries.",
     "levels": [1, 2, 3, 4, 5, 6], "perLevel": 5, "minutes": 30, "seed": 101},
    {"id": "mock-full", "title": "Mock test 2: Full course", "description": "Every level with questions, including the advanced levels 8 to 11.",
     "levels": [1, 2, 3, 4, 5, 6, 8, 9, 10, 11], "perLevel": 3, "minutes": 30, "seed": 202},
    {"id": "mock-final", "title": "Mock test 3: Final exam", "description": "A longer exam across every level. Plan for 45 minutes.",
     "levels": [1, 2, 3, 4, 5, 6, 8, 9, 10, 11], "perLevel": 4, "minutes": 45, "seed": 303},
]

TIMED = {"questionCount": 10, "minuteOptions": [3, 5, 10], "defaultMinutes": 5}


# ═══════════════════════════════ build + validate ═══════════════════════════════

class Problems(vt.Problems):
    pass


def build_challenges(problems, runner):
    projects = {p["id"]: p for p in PROJECTS}
    out = []
    for ch in CHALLENGES:
        sol = ch.get("solution")
        if sol is None and ch.get("solutionFromProject"):
            sol = projects[ch["solutionFromProject"]]["solution"]
        ch["_solution"] = sol
        task = {k: ch[k] for k in ("id", "title", "difficulty", "concepts", "instructions", "requirements",
                                    "example", "hint", "starterCode", "checkMode")}
        task["projectId"] = ch["projectId"]
        tests = []
        for stdin, desc, hidden in ch["tests"]:
            ok, transcript, err = vt.run_program(sol, stdin)
            if not ok:
                problems.add(ch["id"], f"reference solution failed for {stdin}: {err.strip()[-300:]}")
                continue
            expected = vt.normalize(transcript)
            test = t(stdin, expected, desc, hidden)
            if ch["checkMode"] == "regex":
                last = expected.split("\n")[-1]
                test["pattern"] = ch["patterns"][last]
            tests.append(test)
        task["tests"] = tests
        out.append(task)
    return out


def validate_challenges(tasks, problems, runner):
    blocked = set(vt.BLOCKED_MODULES)
    if runner:
        blocked |= vt.runner_blocked_modules(runner)
    ids = set()
    project_ids = {p["id"] for p in PROJECTS}
    by_id = {c["id"]: c for c in CHALLENGES}
    for task in tasks:
        where = task["id"]
        if task["id"] in ids:
            problems.add(where, "duplicate id")
        ids.add(task["id"])
        if not task["id"].startswith(CHALLENGE_PREFIX):
            problems.add(where, f"id must start with {CHALLENGE_PREFIX}")
        if task["projectId"] not in project_ids:
            problems.add(where, "unknown projectId")
        if task["checkMode"] not in vt.CHECK_MODES:
            problems.add(where, "bad checkMode")
        if task["difficulty"] not in vt.DIFFICULTIES:
            problems.add(where, "bad difficulty")
        if not task.get("hint"):
            problems.add(where, "missing hint")
        n = len(task["tests"])
        if not 3 <= n <= 5:
            problems.add(where, f"needs 3-5 tests, has {n}")
        if not any((x.get("description") or "").startswith("Edge case") for x in task["tests"]):
            problems.add(where, "needs an 'Edge case' test")
        if task["difficulty"] in ("medium", "hard") and not any(x.get("hidden") for x in task["tests"]):
            problems.add(where, "medium/hard needs a hidden test")
        sol = by_id[task["id"]]["_solution"]
        bad = vt.imported_modules(sol) & blocked
        if bad:
            problems.add(where, f"solution imports blocked modules {sorted(bad)}")
        outs = set()
        for x in task["tests"]:
            # Run twice in plain Python (determinism) and once in the sandbox when given.
            results = [vt.run_program(sol, x["stdin"]) for _ in range(2)]
            if runner:
                results.append(vt.run_program(sol, x["stdin"], runner))
            texts = set()
            for ok, transcript, err in results:
                if not ok:
                    problems.add(where, f"solution failed for {x['stdin']}: {err.strip()[-300:]}")
                    continue
                if not vt.test_passes(task, x, transcript):
                    problems.add(where, f"solution does not pass its own test {x['stdin']}")
                if vt.normalize(transcript) != vt.normalize(x["expectedOutput"]):
                    problems.add(where, f"expectedOutput differs from the real output for {x['stdin']}")
                texts.add(vt.normalize(transcript))
            if len(texts) > 1:
                problems.add(where, f"non-deterministic output for {x['stdin']}")
            outs.add(vt.normalize(x["expectedOutput"]))
        if len(outs) < 2:
            problems.add(where, "tests should not all have the same output")
        # The starter code must not already pass the challenge.
        starter_pass = all(
            (lambda r: r[0] and vt.test_passes(task, x, r[1]))(vt.run_program(task["starterCode"], x["stdin"]))
            for x in task["tests"]
        )
        if starter_pass:
            problems.add(where, "starter code already passes every test")


def build_projects(problems, runner):
    challenges = {c["id"]: c for c in CHALLENGES}
    out = []
    ch_by_project = {}
    for c in CHALLENGES:
        ch_by_project.setdefault(c["projectId"], []).append(c["id"])
    seen = set()
    for i, p in enumerate(PROJECTS):
        where = p["id"]
        if p["id"] in seen:
            problems.add(where, "duplicate project id")
        seen.add(p["id"])
        sol = p.get("solution") or challenges[p["solutionFromChallenge"]]["solution"]
        ok, transcript, err = vt.run_program(sol, p["sampleInputs"])
        if runner:
            ok2, transcript2, err2 = vt.run_program(sol, p["sampleInputs"], runner)
            if not ok2 or vt.normalize(transcript2) != vt.normalize(transcript):
                problems.add(where, f"sandbox run differs: {err2.strip()[-300:]}")
        if not ok:
            problems.add(where, f"reference solution failed: {err.strip()[-300:]}")
        bad = vt.imported_modules(sol) & set(vt.BLOCKED_MODULES)
        if bad:
            problems.add(where, f"solution imports blocked modules {sorted(bad)}")
        okS, _, errS = vt.run_program(p["starterCode"], p["sampleInputs"] + [""] * 3)
        # Starter code only has to be valid Python (it may stop early or wait for input).
        try:
            compile(p["starterCode"], "starter.py", "exec")
        except SyntaxError as e:
            problems.add(where, f"starter code has a syntax error: {e}")
        if p["collection"] not in ("advanced", "realworld"):
            problems.add(where, "bad collection")
        if p["difficulty"] not in vt.DIFFICULTIES:
            problems.add(where, "bad difficulty")
        if not 3 <= len(p["objectives"]) <= 5 or not 3 <= len(p["steps"]) <= 6:
            problems.add(where, "needs 3-5 objectives and 3-6 steps")
        entry = {k: p[k] for k in ("id", "collection", "difficulty", "title", "summary", "concepts",
                                    "objectives", "steps", "starterCode", "fileName", "recommendedAfter")}
        entry["order"] = i + 1
        entry["sampleRun"] = vt.normalize(transcript)
        if p.get("sampleNote"):
            entry["sampleNote"] = p["sampleNote"]
        entry["challengeIds"] = ch_by_project.get(p["id"], [])
        out.append(entry)
    return out


def _run_code(code):
    """Runs a question's code. Returns (stdout, exception name or None)."""
    import io
    import contextlib
    import tempfile
    buf = io.StringIO()
    old = os.getcwd()
    with tempfile.TemporaryDirectory() as tmp:
        os.chdir(tmp)
        try:
            with contextlib.redirect_stdout(buf):
                exec(compile(code, "main.py", "exec"), {"__name__": "__main__"})
            err = None
        except Exception as e:  # noqa: BLE001
            err = type(e).__name__
        finally:
            os.chdir(old)
    return buf.getvalue(), err


def build_questions(problems):
    sets = []
    ids = set()
    for s in HARD_SETS:
        qs = []
        for n, q in enumerate(s["questions"], start=1):
            qid = f"hard-{s['id']}-{n}"
            where = qid
            if qid in ids:
                problems.add(where, "duplicate id")
            ids.add(qid)
            out, err = _run_code(q["code"])
            actual = vt.normalize(out)
            if q["verify"] == "error":
                if err is None:
                    problems.add(where, "expected an exception but the code ran")
                actual = err or ""
            elif err is not None:
                problems.add(where, f"code raised {err}")
            if q["type"] == "mcq":
                opts = [vt.normalize(o) for o in q["options"]]
                if len(opts) != 4 or len(set(opts)) != 4:
                    problems.add(where, "needs 4 different options")
                if opts[q["correctIndex"]] != actual:
                    problems.add(where, f"correct option {opts[q['correctIndex']]!r} != real result {actual!r}")
                if sum(1 for o in opts if o == actual) != 1:
                    problems.add(where, "the real result must match exactly one option")
            else:
                if vt.normalize(q["answer"]) != actual:
                    problems.add(where, f"answer {q['answer']!r} != real output {actual!r}")
            if len(q["explanation"]) < 30:
                problems.add(where, "explanation too short")
            item = {"id": qid, "type": q["type"], "prompt": q["prompt"], "code": q["code"], "explanation": q["explanation"]}
            if q["type"] == "mcq":
                item["options"] = q["options"]
                item["correctIndex"] = q["correctIndex"]
            else:
                item["answer"] = q["answer"]
            if q.get("hint"):
                item["hint"] = q["hint"]
            qs.append(item)
        sets.append({"id": s["id"], "title": s["title"], "description": s["description"],
                     "afterSubLevel": s["afterSubLevel"], "questions": qs})
    return sets


def course_question_counts():
    course_dir = vt.COURSE_DIR
    index = json.load(open(os.path.join(course_dir, "course.json"), encoding="utf-8"))
    counts, sub_ids = {}, set()
    for f in index["levelFiles"]:
        lvl = json.load(open(os.path.join(course_dir, f), encoding="utf-8"))
        n = 0
        for s in lvl["subLevels"]:
            sub_ids.add(s["id"])
            n += len((s.get("quiz") or {}).get("questions", []))
        counts[lvl["number"]] = n
    return counts, sub_ids


def validate_arena(arena, problems):
    counts, sub_ids = course_question_counts()
    for s in arena["hardSets"]:
        if s["afterSubLevel"] not in sub_ids:
            problems.add(s["id"], "unknown afterSubLevel")
    for p in PROJECTS:
        if p["recommendedAfter"] not in sub_ids:
            problems.add(p["id"], "unknown recommendedAfter")
    for m in arena["mocks"]:
        for lv in m["levels"]:
            if counts.get(lv, 0) < m["perLevel"]:
                problems.add(m["id"], f"level {lv} has fewer than {m['perLevel']} questions")
    for lv, n in counts.items():
        if 0 < n < arena["timed"]["questionCount"]:
            problems.add("timed", f"level {lv} has only {n} questions")


def dump(obj):
    return json.dumps(obj, indent=1, ensure_ascii=False) + "\n"


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--runner", help="path to pypath_runner.py: also run solutions in the real sandbox")
    ap.add_argument("--check", action="store_true", help="validate only; fail if the assets are out of date")
    args = ap.parse_args(argv)
    problems = Problems()
    challenges = build_challenges(problems, args.runner)
    validate_challenges(challenges, problems, args.runner)
    projects = build_projects(problems, args.runner)
    hard = build_questions(problems)
    arena = {"schemaVersion": 1, "timed": TIMED, "hardSets": hard, "mocks": MOCKS}
    validate_arena(arena, problems)
    projects_doc = {"schemaVersion": 1, "projects": projects, "challenges": challenges}
    texts = {PROJECTS_PATH: dump(projects_doc), ARENA_PATH: dump(arena)}
    if problems.items:
        for p in problems.items:
            print("PROBLEM", p)
        return 1
    if args.check:
        stale = [p for p, txt in texts.items() if not os.path.exists(p) or open(p, encoding="utf-8").read() != txt]
        for p in stale:
            print("OUT OF DATE", os.path.relpath(p, PROJECT))
        if stale:
            return 1
    else:
        os.makedirs(OUT_DIR, exist_ok=True)
        for p, txt in texts.items():
            with open(p, "w", encoding="utf-8") as f:
                f.write(txt)
    nq = sum(len(s["questions"]) for s in hard)
    print(f"OK: {len(projects)} projects, {len(challenges)} challenges "
          f"({sum(len(c['tests']) for c in challenges)} tests), {len(hard)} difficult sets ({nq} questions), "
          f"{len(MOCKS)} mock tests" + (" [sandbox checked]" if args.runner else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
