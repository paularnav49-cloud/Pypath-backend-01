"""Authoring script for PyPath course content.

Edit the data below and run:
    python content/build_course.py
It writes the course into app/src/main/assets/course/:
    course.json          course index (id, title, list of level files)
    levels/l1.json ...   one file per level (with its bonus project and cheat sheet)
    glossary.json        searchable Python terms
Level projects, cheat sheets and the glossary live in content/course_extras.py.
Batch 1 additions:
    content/mcq_feedback.py      hint + per-option feedback for every existing Level 1-6 MCQ
    content/advanced_levels.py   Levels 8-11 (Advanced): errors, modules, files, classes
    content/existing_course_baseline.json
                                 frozen copy of the pre-Batch-1 ids, lessons and questions;
                                 validation fails if any of them change
The app loads these at runtime. Adding a level or sub-level here requires NO Kotlin changes.

    python content/build_course.py           validate, then write the JSON files
    python content/build_course.py --check   validate and fail if the committed JSON is out of date

When it runs, the script also validates the content (see VALIDATION at the bottom):
ids, MCQ shape, answer-position balance, variety rules, and it executes every code
example with a known output to make sure the output shown to learners is correct.
Use Python 3.11 (the version bundled in the app by Chaquopy).
"""
import contextlib, hashlib, io, json, os, pathlib, random, re, subprocess, sys, tempfile, textwrap

# Batch 1: never write __pycache__ files (pypath_runner.py is imported read-only for validation).
sys.dont_write_bytecode = True

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import course_extras  # noqa: E402

def code(src, output=None, explain=(), caption=None, inputs=None, full_error=False):
    b = {"type": "code", "code": textwrap.dedent(src).strip("\n")}
    # Validation-only (Batch 1): the output shows the whole traceback, not just its last line.
    if full_error: b["_full_error"] = True
    if output is not None: b["output"] = textwrap.dedent(output).strip("\n")
    if explain: b["explanation"] = list(explain)
    if caption: b["caption"] = caption
    # Validation-only: simulated keyboard input for examples that call input(). Not written to JSON.
    if inputs is not None: b["_inputs"] = list(inputs)
    return b

def text(t): return {"type": "text", "text": " ".join(t.split())}
def bullets(*items): return {"type": "bullets", "items": list(items)}
def tip(t, title="Tip"): return {"type": "tip", "title": title, "text": " ".join(t.split())}
def mistake(t): return tip(t, title="Common mistake")
def key(t): return {"type": "keypoint", "text": " ".join(t.split())}
def page(title, *blocks): return {"title": title, "blocks": list(blocks)}

def q(qid, prompt, options, correct, explanation, code_=None):
    assert len(options) == 4
    d = {"id": qid, "prompt": prompt, "options": options, "correctIndex": correct, "explanation": explanation}
    if code_: d["code"] = textwrap.dedent(code_).strip("\n")
    return d

def sub(sid, code_, title, summary, minutes, pages, questions, pass_percent=60):
    return {"id": sid, "code": code_, "title": title, "summary": summary, "estimatedMinutes": minutes,
            "steps": ["learn", "quiz"], "lesson": {"pages": pages},
            "quiz": {"questions": questions, "passPercent": pass_percent}}

def outline(sid, code_, title, summary):
    return {"id": sid, "code": code_, "title": title, "summary": summary, "steps": ["learn", "quiz"]}

# ─────────────── Helpers for Levels 3+ (balanced answer positions) ───────────────

def m(prompt, right, wrongs, explanation, code_=None, check=None, hint=None, fb=None):
    """One MCQ written as: the right answer + three plausible wrong answers.

    The final position of the right answer is assigned by `balanced()` so that the
    correct option is spread evenly over A-D in every quiz.
    check (validation only, not written to JSON):
      "out"        run code_ and require its printed output == right
      "err:Name"   run code_ and require it to raise the exception Name
    hint (Batch 1): one-sentence nudge shown behind the Hint button.
    fb   (Batch 1): (feedback for the right answer, [feedback for each wrong answer, same order as wrongs]).
         balanced() moves the feedback together with its option, so optionFeedback stays aligned.
    """
    assert len(wrongs) == 3, prompt
    d = {"prompt": prompt, "_right": right, "_wrongs": list(wrongs), "explanation": explanation}
    if hint is not None: d["_hint"] = hint
    if fb is not None:
        assert len(fb) == 2 and len(fb[1]) == 3, prompt
        d["_fb"] = (fb[0], list(fb[1]))
    if code_: d["code"] = textwrap.dedent(code_).strip("\n")
    if check: d["_check"] = check
    return d

def positions(seed, n):
    """Deterministic, balanced correct-answer positions: counts differ by at most 1 and
    the same position never appears more than twice in a row."""
    rnd = random.Random(int(hashlib.sha256(seed.encode()).hexdigest()[:8], 16))
    base = ([0, 1, 2, 3] * ((n + 3) // 4))[:n]
    while True:
        rnd.shuffle(base)
        if all(not (base[i] == base[i - 1] == base[i - 2]) for i in range(2, n)):
            return list(base)

def balanced(prefix, items):
    out = []
    for i, (it, pos) in enumerate(zip(items, positions(prefix, len(items)))):
        opts = list(it["_wrongs"])
        opts.insert(pos, it["_right"])
        d = {"id": f"{prefix}q{i + 1}", "prompt": it["prompt"], "options": opts, "correctIndex": pos,
             "explanation": it["explanation"]}
        if "code" in it: d["code"] = it["code"]
        if "_check" in it: d["_check"] = it["_check"]
        if "_hint" in it: d["hint"] = it["_hint"]
        if "_fb" in it:
            fbs = list(it["_fb"][1])
            fbs.insert(pos, it["_fb"][0])
            d["optionFeedback"] = fbs
        out.append(d)
    return out

def lesson(sid, code_, title, summary, minutes, pages, questions, pass_percent=60):
    return sub(sid, code_, title, summary, minutes, pages, balanced(sid, questions), pass_percent)

# ───────────────────────────── LEVEL 1 ─────────────────────────────
level1 = {
  "id": "l1", "number": 1, "title": "Python Basics",
  "description": "Meet Python, store values in variables, explore data types and talk to the user.",
  "subLevels": [
    sub("l1s1", "1.1", "What is Python?", "Your first look at Python and your very first line of code.", 4, [
        page("Meet Python",
             text("""Python is a programming language — a way to give instructions to a computer
                  using words that are close to plain English."""),
             bullets("Easy to read and write", "Free and works on every computer",
                     "Used by Google, Netflix, NASA and millions of developers"),
             key("A program is just a list of instructions the computer follows from top to bottom.")),
        page("Your first program",
             text("The classic first program prints a message on the screen."),
             code('print("Hello, World!")', output="Hello, World!", explain=[
                 "print is a built-in function that shows something on the screen.",
                 "The text inside the quotes is what gets shown.",
                 "The parentheses ( ) hold what you want to print."])),
        page("Printing more",
             text("You can call print as many times as you like. Each call starts on a new line."),
             code('''
                 print("I am learning Python")
                 print(2 + 3)
             ''', output='''
                 I am learning Python
                 5
             ''', explain=["Text needs quotes.", "Numbers and maths don't — Python works out 2 + 3 for you."]),
             tip("Python reads your code line by line, starting at the top.")),
        page("Comments",
             text("Lines starting with # are comments. Python ignores them — they are notes for humans."),
             code('''
                 # This line is a comment
                 print("Comments are ignored")  # notes can go here too
             ''', output="Comments are ignored", explain=["Use comments to explain why your code does something."])),
    ], [
        q("l1s1q1", "What does print() do in Python?",
          ["Runs the program again", "Shows something on the screen", "Turns text into a comment", "Ends the program"], 1,
          "print() shows whatever you put inside the parentheses on the screen. It does not restart or end the program, and only # makes a comment."),
        q("l1s1q2", "What will this code display?", ["2 + 3", "\"5\"", "5", "Error"], 2,
          "Without quotes, 2 + 3 is maths, so Python works it out and prints 5. print shows the value without quotes, so it is not \"5\", and you would only see 2 + 3 if it were inside quotes.", code_="print(2 + 3)"),
        q("l1s1q3", "Which symbol starts a comment in Python?", ["//", "#", "--", "/*"], 1,
          "In Python, everything after # on a line is a comment that Python ignores. //, -- and /* are used in other languages, not in Python."),
        q("l1s1q4", "In what order does Python run your lines of code?",
          ["Random order", "Bottom to top", "Top to bottom", "Shortest line first"], 2,
          "Python reads your code line by line, starting at the top, so the first line runs first. It never picks lines at random or starts from the bottom."),
    ]),
    sub("l1s2", "1.2", "Variables", "Give names to values so you can reuse them.", 5, [
        page("What is a variable?",
             text("""A variable is a named box that stores a value. You create one with the
                  equals sign ="""),
             code('''
                 name = "Asha"
                 age = 21
                 print(name)
                 print(age)
             ''', output='''
                 Asha
                 21
             ''', explain=["name stores the text \"Asha\".", "age stores the number 21.",
                           "print(name) shows the value inside the box, not the word name."])),
        page("Changing a value",
             text("Variables can change. The newest value replaces the old one."),
             code('''
                 score = 10
                 score = 15
                 print(score)
             ''', output="15", explain=["The second line overwrites the first value.", "Only the latest value is kept."]),
             key("= means \"store this value\", not \"is equal to\".")),
        page("Naming rules",
             bullets("Use letters, numbers and underscores: user_name, level2",
                     "Can't start with a number: 2name is not allowed",
                     "No spaces: use first_name, not first name",
                     "Names are case-sensitive: Age and age are different"),
             tip("Pick names that describe the value, like total_price instead of x.")),
        page("Using variables together",
             code('''
                 apples = 4
                 oranges = 3
                 total = apples + oranges
                 print(total)
             ''', output="7", explain=["Python looks up the values of apples and oranges.",
                                       "It adds them and stores the result in total."])),
    ], [
        q("l1s2q1", "Which line correctly creates a variable?",
          ["city = \"Pune\"", "\"Pune\" = city", "city \"Pune\"", "var city = \"Pune\""], 0,
          "The variable name goes on the left, then a single =, then the value. Putting the value on the left is backwards, leaving out = stores nothing, and Python does not use the word var."),
        q("l1s2q2", "What does this code print?", ["10", "15", "25", "score"], 1,
          "The second assignment replaces 10 with 15.", code_="score = 10\nscore = 15\nprint(score)"),
        q("l1s2q3", "Which is a valid variable name?", ["2fast", "my name", "user_age", "class-1"], 2,
          "Names can use letters, digits and underscores but can't start with a digit or contain spaces/hyphens."),
        q("l1s2q4", "What is printed?", ["a + b", "34", "7", "Error"], 2,
          "a is 3 and b is 4, so a + b is 7. print shows the values, not the words a + b, and numbers are added, so the answer is not 34.", code_="a = 3\nb = 4\nprint(a + b)"),
    ]),
    sub("l1s3", "1.3", "Data Types", "Text, whole numbers, decimals and True/False.", 5, [
        page("Every value has a type",
             text("Python keeps track of what kind of value something is. The four you'll use most:"),
             bullets("str — text, like \"hello\"", "int — whole numbers, like 42",
                     "float — decimal numbers, like 3.14", "bool — True or False")),
        page("Seeing the type",
             text("Use type() to ask Python what type a value is."),
             code('''
                 print(type("hi"))
                 print(type(7))
                 print(type(2.5))
                 print(type(True))
             ''', output='''
                 <class 'str'>
                 <class 'int'>
                 <class 'float'>
                 <class 'bool'>
             ''', explain=["type() returns the kind of value.", "Don't worry about the word class yet."])),
        page("Why types matter",
             text("The same symbol can behave differently depending on the type."),
             code('''
                 print(2 + 3)
                 print("2" + "3")
             ''', output='''
                 5
                 23
             ''', explain=["With numbers, + adds.", "With text, + joins strings together."]),
             key("\"5\" (text) and 5 (number) are different things in Python.")),
        page("Converting types",
             code('''
                 age_text = "20"
                 age = int(age_text)
                 print(age + 1)
             ''', output="21", explain=["int() turns the text \"20\" into the number 20.",
                                        "Now maths works on it. str() and float() convert too."])),
    ], [
        q("l1s3q1", "What is the type of 3.14?", ["int", "str", "float", "bool"], 2,
          "3.14 has a decimal point, so it is a float. int is only for whole numbers like 42, and a str would need quotes."),
        q("l1s3q2", "What does this code print?", ["5", "23", "2 + 3", "Error"], 1,
          "Both values are strings, so + joins them into \"23\".", code_='print("2" + "3")'),
        q("l1s3q3", "Which value is a bool?", ["\"True\"", "True", "1.0", "\"yes\""], 1,
          "True without quotes is a bool. \"True\" and \"yes\" have quotes, so they are text (str), and 1.0 is a float."),
        q("l1s3q4", "Which function converts \"42\" into a whole number?", ["str()", "float()", "type()", "int()"], 3,
          "int() turns text like \"42\" into the whole number 42. float() gives a decimal number, str() makes text, and type() only tells you the type."),
    ]),
    sub("l1s4", "1.4", "Input and Output", "Ask the user a question and respond to them.", 6, [
        page("Getting input",
             text("input() pauses the program and waits for the user to type something."),
             code('''
                 name = input("What is your name? ")
                 print("Hello,", name)
             ''', output='''
                 What is your name? Ravi
                 Hello, Ravi
             ''', explain=["The text inside input() is the question shown to the user.",
                           "Whatever they type is stored in name.",
                           "print can show several things separated by commas."])),
        page("Input is always text",
             text("Even if the user types a number, input() gives you a string."),
             code('''
                 age = input("Age: ")
                 print(type(age))
             ''', output='''
                 Age: 18
                 <class 'str'>
             '''),
             key("Convert input with int() or float() before doing maths.")),
        page("Doing maths with input",
             code('''
                 age = int(input("Age: "))
                 print("Next year you will be", age + 1)
             ''', output='''
                 Age: 18
                 Next year you will be 19
             ''', explain=["input() reads the text \"18\".", "int() turns it into the number 18.",
                           "Now age + 1 works."])),
        page("Formatting output",
             text("f-strings let you place variables directly inside text. Put f before the quotes and wrap variables in { }."),
             code('''
                 name = "Meera"
                 level = 1
                 print(f"{name} is on level {level}")
             ''', output="Meera is on level 1"),
             tip("f-strings are the cleanest way to mix text and values.")),
    ], [
        q("l1s4q1", "What does input() return?", ["A number", "A string", "A bool", "Nothing"], 1,
          "input() always returns what the user typed as a string."),
        q("l1s4q2", "How do you turn the user's answer into a whole number?",
          ["int(input())", "input(int())", "number(input())", "input().int"], 0,
          "input() gives the answer as text, and wrapping it in int() turns that text into a whole number. input(int()) puts int() inside the question instead, and Python has no number() function or .int."),
        q("l1s4q3", "What does this print?", ["{x} apples", "x apples", "5 apples", "f5 apples"], 2,
          "In an f-string, {x} is replaced with the value of x.", code_='x = 5\nprint(f"{x} apples")'),
        q("l1s4q4", "What is shown to the user by input(\"Age: \")?",
          ["Nothing", "The word input", "Age: ", "An error"], 2,
          "The text inside input() is the question shown to the user before they type, so they see Age: . It is not an error, and Python shows your text, not the word input."),
    ]),
  ],
}

# ───────────────────────────── LEVEL 2 ─────────────────────────────
level2 = {
  "id": "l2", "number": 2, "title": "Conditions",
  "description": "Teach your programs to make decisions with if, else and elif.",
  "subLevels": [
    sub("l2s1", "2.1", "Comparisons", "Compare values and get True or False.", 4, [
        page("Asking questions",
             text("Comparison operators compare two values. The answer is always True or False."),
             bullets("==  equal to", "!=  not equal to", ">  greater than", "<  less than",
                     ">=  greater or equal", "<=  less or equal")),
        page("Try them",
             code('''
                 print(5 > 3)
                 print(4 == 5)
                 print("a" != "b")
             ''', output='''
                 True
                 False
                 True
             ''', explain=["5 is greater than 3, so True.", "4 is not equal to 5, so False."]),
             key("== compares. A single = stores a value.")),
    ], [
        q("l2s1q1", "What does 7 >= 7 give?", ["True", "False", "7", "Error"], 0,
          ">= is True when the left side is greater than OR equal to the right."),
        q("l2s1q2", "Which operator checks if two values are equal?", ["=", "==", "!=", "=>"], 1,
          "== compares values; = assigns a value to a variable."),
        q("l2s1q3", "What is printed?", ["True", "10", "False", "Error"], 2,
          "10 is not less than 3, so the comparison is False.", code_="print(10 < 3)"),
    ]),
    sub("l2s2", "2.2", "if and else", "Run code only when a condition is True.", 5, [
        page("The if statement",
             code('''
                 age = 20
                 if age >= 18:
                     print("You can vote")
             ''', output="You can vote", explain=["The condition age >= 18 is True.",
                                                  "So the indented line underneath runs.",
                                                  "Notice the colon : at the end of the if line."])),
        page("Indentation matters",
             text("Python uses indentation (4 spaces) to know which lines belong to the if."),
             tip("Forgetting the colon or the indentation is the most common beginner error.", title="Watch out")),
        page("Adding else",
             code('''
                 temp = 15
                 if temp > 25:
                     print("It's hot")
                 else:
                     print("It's cool")
             ''', output="It's cool", explain=["15 > 25 is False, so the if block is skipped.",
                                               "The else block runs instead."])),
    ], [
        q("l2s2q1", "What must end an if line?", ["A colon :", "A semicolon ;", "A period .", "Nothing"], 0,
          "Every if line, and every else line, ends with a colon :. Leaving it out is an error, and Python does not use ; or . here."),
        q("l2s2q2", "What is printed?", ["Big", "Small", "Big and Small", "Nothing"], 1,
          "3 > 10 is False, so the else branch runs.",
          code_='n = 3\nif n > 10:\n    print("Big")\nelse:\n    print("Small")'),
        q("l2s2q3", "How does Python know which lines belong to an if?",
          ["Curly braces { }", "The word end", "Indentation", "Line numbers"], 2,
          "Python groups the lines of an if by indentation, usually 4 spaces. It does not use curly braces, an end word or line numbers for this."),
    ]),
    sub("l2s3", "2.3", "elif", "Check several conditions in order.", 5, [
        page("More than two choices",
             text("elif (short for else if) lets you test another condition when the first one is False."),
             code('''
                 marks = 72
                 if marks >= 90:
                     print("Grade A")
                 elif marks >= 60:
                     print("Grade B")
                 else:
                     print("Keep practising")
             ''', output="Grade B", explain=["72 >= 90 is False, so Python moves on.",
                                             "72 >= 60 is True — Grade B is printed.",
                                             "Once a branch runs, the rest are skipped."])),
        page("Order matters",
             key("Python checks conditions from top to bottom and runs only the first one that is True."),
             tip("Put the most specific condition first.")),
    ], [
        q("l2s3q1", "What does elif mean?", ["else if", "end if", "exit if", "either if"], 0,
          "elif is short for else if: when the condition above is False, Python tries this one. It does not end or exit the if."),
        q("l2s3q2", "What is printed?", ["High", "Medium", "Low", "High and Medium"], 1,
          "50 > 80 is False; 50 > 30 is True, so Medium prints and the rest are skipped.",
          code_='x = 50\nif x > 80:\n    print("High")\nelif x > 30:\n    print("Medium")\nelse:\n    print("Low")'),
        q("l2s3q3", "In one if / elif / else chain, how many of the branches run?",
          ["Every branch that is True", "Two", "Exactly one", "None of them"], 2,
          "Python checks the conditions from top to bottom and runs only the first branch that is True, or the else if none are. Later branches are skipped even if their condition is also True."),
    ]),
  ],
}

# ───────────────────────────── LEVEL 3 ─────────────────────────────
level3 = {
  "id": "l3", "number": 3, "title": "Loops",
  "description": "Repeat actions with for and while loops, control them with break and continue, and draw patterns.",
  "subLevels": [
    lesson("l3s1", "3.1", "for loops", "Repeat code a set number of times with for and range().", 6, [
        page("Why loops?",
             text("""Imagine printing "Hello" five times. You could write print five times,
                  but that gets boring fast, and what if you needed it 500 times?"""),
             text("A loop tells Python to repeat some lines for you."),
             bullets("Less typing, fewer mistakes", "Change one number to repeat more or fewer times",
                     "Loops are used in almost every real program"),
             key("A loop repeats the indented lines underneath it.")),
        page("Your first for loop",
             code('''
                 for i in range(5):
                     print("Hello")
             ''', output='''
                 Hello
                 Hello
                 Hello
                 Hello
                 Hello
             ''', explain=["range(5) means \"do this 5 times\".",
                           "i is the loop variable. Python gives it a new value on each repeat.",
                           "The line ends with a colon : and the repeated line is indented, just like if."])),
        page("Counting with range()",
             text("The loop variable holds the current number. range() can start and stop where you choose."),
             code('''
                 for n in range(1, 6):
                     print(n)
             ''', output='''
                 1
                 2
                 3
                 4
                 5
             ''', explain=["range(1, 6) starts at 1 and stops before 6.",
                           "So n is 1, then 2, 3, 4 and finally 5."]),
             code('''
                 for n in range(0, 10, 2):
                     print(n)
             ''', output='''
                 0
                 2
                 4
                 6
                 8
             ''', explain=["The third number is the step: jump by 2 each time.",
                           "10 is never printed because range stops before the end number."])),
        page("A times table",
             text("You can use the loop variable in calculations. The * symbol multiplies."),
             code('''
                 num = 3
                 for i in range(1, 6):
                     print(f"{num} x {i} = {num * i}")
             ''', output='''
                 3 x 1 = 3
                 3 x 2 = 6
                 3 x 3 = 9
                 3 x 4 = 12
                 3 x 5 = 15
             ''', explain=["num stays 3 the whole time.", "i changes: 1, 2, 3, 4, 5.",
                           "The f-string works out num * i on every repeat."]),
             tip("Change num to 7 and you get the 7 times table. One change, five new lines.")),
        page("Adding up with a loop",
             text("""A very common pattern: start a total at 0 and add to it on every repeat.
                  total += n is a short way to write total = total + n."""),
             code('''
                 total = 0
                 for n in range(1, 5):
                     total += n
                 print(total)
             ''', output="10", explain=["total starts at 0.", "The loop adds 1, then 2, then 3, then 4.",
                                        "print(total) is not indented, so it runs once, after the loop."]),
             mistake("""range(1, 5) gives 1, 2, 3, 4 — it stops BEFORE 5. If you want to include 5,
                     write range(1, 6).""")),
    ], [
        m("What does this code print?", "0\n3\n6", ["0\n3\n6\n9", "3\n6\n9", "0\n1\n2"],
          "range(0, 9, 3) starts at 0 and jumps by 3: 0, 3, 6. 9 is the stop number and range always stops before it, so 9 is not printed. The third number is the step, so it does not count 0, 1, 2.",
          code_="for n in range(0, 9, 3):\n    print(n)", check="out"),
        m("Which numbers does range(2, 6) produce?", "2, 3, 4, 5", ["2, 3, 4, 5, 6", "3, 4, 5, 6", "Only 2 and 6"],
          "range(start, stop) includes the start but stops before the stop number, so 6 is not included."),
        m("Find the bug in this loop.", "The colon : is missing after range(3)",
          ["range needs square brackets", "The loop variable must be called n", "print must not be indented"],
          "Every for line ends with a colon, exactly like an if line. The name i is fine, and the body must be indented.",
          code_="for i in range(3)\n    print(i)"),
        m("Fill in the blank so the loop prints the numbers 1 to 10.", "1, 11", ["1, 10", "0, 10", "10"],
          "The stop number is never included, so to reach 10 you stop at 11. range(1, 10) would stop at 9.",
          code_="for n in range(____):\n    print(n)"),
        m("What does this code print?", "6", ["10", "3", "0"],
          "total becomes 1, then 3, then 6. range(1, 4) gives 1, 2, 3 — it does not include 4, so the answer is not 10.",
          code_="total = 0\nfor n in range(1, 4):\n    total += n\nprint(total)", check="out"),
    ]),
    lesson("l3s2", "3.2", "while loops", "Keep repeating while a condition is True.", 6, [
        page("Repeat while something is true",
             text("""A while loop keeps going as long as its condition is True. It checks the
                  condition before every repeat."""),
             code('''
                 count = 1
                 while count <= 3:
                     print(count)
                     count += 1
             ''', output='''
                 1
                 2
                 3
             ''', explain=["count starts at 1.", "Each repeat prints count and then adds 1 to it.",
                           "When count becomes 4, count <= 3 is False and the loop stops."])),
        page("A countdown",
             text("You can count down too. n -= 1 is short for n = n - 1."),
             code('''
                 n = 3
                 while n > 0:
                     print(n)
                     n -= 1
                 print("Lift off!")
             ''', output='''
                 3
                 2
                 1
                 Lift off!
             ''', explain=["The loop runs while n is bigger than 0.", "n goes 3, 2, 1, then 0 — and the loop stops.",
                           "The last print is not indented, so it runs after the loop."])),
        page("When you don't know how many times",
             text("""Use while when you can't say in advance how many repeats you need. Here we
                  double some savings until they reach 1000."""),
             code('''
                 savings = 100
                 years = 0
                 while savings < 1000:
                     savings = savings * 2
                     years += 1
                 print(f"{years} years, savings: {savings}")
             ''', output="4 years, savings: 1600",
                 explain=["100 becomes 200, 400, 800 and then 1600.", "years counts how many times the loop ran."]),
             text("A while loop is also great for asking again until the answer is right:"),
             code('''
                 password = ""
                 while password != "python":
                     password = input("Password: ")
                 print("Welcome!")
             ''', output='''
                 Password: cat
                 Password: python
                 Welcome!
             ''', inputs=["cat", "python"],
                 explain=["The loop keeps asking while the answer is not \"python\".",
                          "When the user types python, the condition is False and the loop ends."])),
        page("Infinite loops",
             text("If the condition never becomes False, the loop runs forever. This is called an infinite loop."),
             mistake("""Forgetting to change the variable, for example leaving out count += 1, makes the
                     condition stay True forever. In the Practical tab, press Stop if this happens."""),
             bullets("Use for when you know how many repeats you need",
                     "Use while when you repeat until something changes"),
             key("Every while loop needs something inside it that eventually makes the condition False.")),
    ], [
        m("What does this code print?", "1\n2\n3", ["1\n2\n3\n4", "0\n1\n2\n3", "4"],
          "x is printed while it is less than 4: 1, 2, 3. When x becomes 4 the condition is False, so 4 is never printed.",
          code_="x = 1\nwhile x < 4:\n    print(x)\n    x += 1", check="out"),
        m("When does a while loop stop?", "When its condition becomes False",
          ["After exactly 10 repeats", "When the condition becomes True", "When it reaches the last line of the file"],
          "A while loop repeats as long as the condition is True and stops as soon as it is False. There is no built-in repeat limit."),
        m("What is wrong with this code?", "n never changes, so the loop runs forever",
          ["while loops need range()", "The condition should use =", "print can't be inside a while loop"],
          "n stays 5, so n > 0 is always True. Adding n -= 1 inside the loop fixes it. range() is only used with for.",
          code_="n = 5\nwhile n > 0:\n    print(n)"),
        m("Which line, placed inside the loop, makes this countdown finish?", "n -= 1", ["n += 1", "n = 3", "n == n - 1"],
          "n -= 1 makes n smaller each time until n > 0 is False. n += 1 counts up, so the loop would never end.",
          code_="n = 3\nwhile n > 0:\n    print(n)\n    ____"),
        m("What does this code print?", "1", ["4", "3", "7"],
          "n goes 10, 7, 4, 1. At 1 the condition n > 3 is False, so the loop stops and prints 1. It does not stop at 4 because 4 > 3 is still True.",
          code_="n = 10\nwhile n > 3:\n    n -= 3\nprint(n)", check="out"),
        m("Which statement is true?", "A while loop may run zero times if its condition starts False",
          ["A while loop always runs at least once", "while loops can only count upwards",
           "You must know the number of repeats before using while"],
          "The condition is checked before the first repeat, so a False condition means the body never runs."),
    ]),
    lesson("l3s3", "3.3", "break and continue", "Stop a loop early or skip a single repeat.", 5, [
        page("Stopping early with break",
             text("break jumps out of the loop straight away, even if there are repeats left."),
             code('''
                 for n in range(1, 10):
                     if n == 4:
                         break
                     print(n)
                 print("Done")
             ''', output='''
                 1
                 2
                 3
                 Done
             ''', explain=["The loop would normally run up to 9.", "When n is 4, break ends the loop before print(n).",
                           "Python carries on with the line after the loop."])),
        page("Skipping with continue",
             text("""continue skips the rest of the current repeat and moves to the next one.
                  The % symbol gives the remainder of a division: 7 % 2 is 1, 8 % 2 is 0.
                  So n % 2 == 0 means n is even."""),
             code('''
                 for n in range(1, 7):
                     if n % 2 == 0:
                         continue
                     print(n)
             ''', output='''
                 1
                 3
                 5
             ''', explain=["For even numbers, continue skips print(n).", "The loop does not stop — it just moves on."]),
             key("break = leave the loop. continue = skip to the next repeat.")),
        page("break in a while True loop",
             text("""while True: repeats forever, so you use break to get out. This is handy when the
                  stopping point is decided in the middle of the loop."""),
             code('''
                 total = 0
                 while True:
                     total += 5
                     if total >= 20:
                         break
                 print(total)
             ''', output="20", explain=["total grows 5, 10, 15, 20.", "When it reaches 20, break ends the loop."]),
             mistake("""A while True loop with no break inside runs forever. Also make sure break is
                     indented inside the if, otherwise the loop stops on the very first repeat.""")),
    ], [
        m("What does this code print?", "1\n2", ["1\n2\n3", "3", "1\n2\n4\n5"],
          "When n is 3, break ends the loop before print runs, so only 1 and 2 are shown. break does not skip just one number — it stops the loop.",
          code_="for n in range(1, 6):\n    if n == 3:\n        break\n    print(n)", check="out"),
        m("What does this code print?", "1\n3\n4", ["1\n2\n3\n4", "1", "3\n4"],
          "continue skips only the repeat where n is 2. The loop keeps going, so 3 and 4 are still printed. Stopping after 1 is what break would do.",
          code_="for n in range(1, 5):\n    if n == 2:\n        continue\n    print(n)", check="out"),
        m("What does continue do?", "Skips the rest of this repeat and moves on to the next one",
          ["Stops the loop completely", "Restarts the loop from the very first number", "Pauses until Enter is pressed"],
          "continue only skips the current repeat. Stopping the loop completely is what break does."),
        m("What does this code print?", "1", ["2", "2.33", "21"],
          "% gives the remainder: 7 divided by 3 is 2 with 1 left over. 2 is how many times 3 fits, not the remainder.",
          code_="print(7 % 3)", check="out"),
    ]),
    lesson("l3s4", "3.4", "Nested loops and patterns", "Put a loop inside a loop to make grids and shapes.", 8, [
        page("A loop inside a loop",
             text("""A nested loop is a loop inside another loop. For every single repeat of the outer
                  loop, the inner loop runs all the way through."""),
             code('''
                 for row in range(2):
                     for col in range(3):
                         print(row, col)
             ''', output='''
                 0 0
                 0 1
                 0 2
                 1 0
                 1 1
                 1 2
             ''', explain=["The outer loop runs 2 times (row 0 and row 1).",
                           "Each time, the inner loop runs 3 times (col 0, 1, 2).",
                           "2 x 3 = 6 lines in total."])),
        page("Staying on one line",
             text("""print normally moves to a new line. Add end=" " to print a space instead.
                  An empty print() just moves to the next line."""),
             code('''
                 for i in range(3):
                     print(i, end=" ")
                 print()
                 print("next line")
             ''', output='''
                 0 1 2
                 next line
             ''', explain=["end=\" \" keeps all three numbers on the same line.",
                           "print() on its own finishes that line."])),
        page("Repeating text with *",
             text("Multiplying a string by a number repeats it."),
             code('''
                 print("*" * 4)
                 print("ab" * 3)
             ''', output='''
                 ****
                 ababab
             '''),
             tip("This only works with a whole number: \"ab\" * 3 is fine, \"ab\" * \"3\" is an error.")),
        page("Drawing a triangle",
             code('''
                 for row in range(1, 5):
                     print("*" * row)
             ''', output='''
                 *
                 **
                 ***
                 ****
             ''', explain=["row goes 1, 2, 3, 4.", "Each line prints that many stars."]),
             text("The same shape with numbers, using a real nested loop:"),
             code('''
                 for row in range(1, 4):
                     for col in range(row):
                         print(col + 1, end=" ")
                     print()
             ''', output='''
                 1
                 1 2
                 1 2 3
             ''', explain=["The inner loop runs row times.", "print() after the inner loop starts the next line."])),
        page("A multiplication grid",
             code('''
                 for a in range(1, 4):
                     for b in range(1, 4):
                         print(a * b, end=" ")
                     print()
             ''', output='''
                 1 2 3
                 2 4 6
                 3 6 9
             ''', explain=["a picks the row, b picks the column.", "Each cell is a * b."]),
             key("Total repeats of the inner body = outer repeats x inner repeats.")),
        page("Indentation decides everything",
             text("""With nested loops, the indentation of each line decides which loop it belongs to.
                  Move a line left or right and the output changes completely."""),
             code('''
                 for row in range(3):
                     for col in range(3):
                         print("*", end="")
                 print()
             ''', output="*********",
                 explain=["print() is not indented, so it runs only once, after both loops.",
                          "All 9 stars end up on one line."]),
             mistake("""To get 3 rows of ***, the final print() must be indented one level, inside the
                     outer loop but outside the inner loop.""")),
    ], [
        m("How many times is hi printed?", "12", ["7", "4", "3"],
          "The inner loop runs 4 times for each of the 3 outer repeats: 3 x 4 = 12. Adding them (7) is a common mix-up.",
          code_='for a in range(3):\n    for b in range(4):\n        print("hi")'),
        m("What does this code print?", "#\n##\n###", ["###\n##\n#", "#\n#\n#", "###"],
          "r goes 1, 2, 3 and each line prints r hashes, so the triangle grows downward.",
          code_='for r in range(1, 4):\n    print("#" * r)', check="out"),
        m('What does end=" " do in print(x, end=" ")?', "Prints a space instead of moving to a new line",
          ["Ends the program after printing", "Adds a blank line after the value", "Prints the word end"],
          "end sets what is printed after the value. By default it's a new line; end=\" \" swaps it for a space."),
        m("What does this code print?", "ababab", ["ab3", "ab ab ab", "Error"],
          "A string times a whole number repeats the string, with no spaces added.",
          code_='print("ab" * 3)', check="out"),
        m("This should draw 3 rows of ***, but prints all stars on one line. Why?",
          "print() is not indented inside the outer loop",
          ['end="" is not allowed in a loop', "The inner loop must use a different range", "range(3) should be range(4)"],
          "print() runs only once, after both loops. Indent it inside the outer loop so each row ends with a new line.",
          code_='for row in range(3):\n    for col in range(3):\n        print("*", end="")\nprint()'),
        m("What does this code print?", "0\n1\n1\n2", ["0\n1\n2\n3", "0\n2", "1\n2\n2\n3"],
          "The pairs are (0,0), (0,1), (1,0), (1,1), and their sums are 0, 1, 1, 2.",
          code_="for i in range(2):\n    for j in range(2):\n        print(i + j)", check="out"),
        m("To print 1 2 3 on one line, what goes in the blank? print(n, ____)", 'end=" "',
          ['space=" "', '" "', "line=False"],
          'end=" " replaces the new line with a space. Writing just " " prints a space as a second value but still moves to a new line every time, and print has no space or line setting.',
          code_="for n in range(1, 4):\n    print(n, ____)"),
        m("Which statement about nested loops is true?",
          "The inner loop runs completely for every repeat of the outer loop",
          ["The outer loop finishes before the inner loop starts", "Both loops always run the same number of times",
           "The inner loop can't use the outer loop's variable"],
          "For each outer repeat, the inner loop starts again and runs to the end. The inner loop can use the outer variable, as in range(row)."),
    ]),
  ],
}

# ───────────────────────────── LEVEL 4 ─────────────────────────────
level4 = {
  "id": "l4", "number": 4, "title": "Functions",
  "description": "Package code into reusable building blocks with parameters, return values and scope.",
  "subLevels": [
    lesson("l4s1", "4.1", "Defining functions", "Write your own reusable commands with def.", 5, [
        page("What is a function?",
             text("""You already use functions: print(), input(), int() and range(). A function is a
                  named block of code that does one job. Now you will write your own."""),
             bullets("Write the code once, use it many times", "Give a clear name to a piece of work",
                     "Keep long programs tidy and easier to fix"),
             key("A function is a reusable, named set of instructions.")),
        page("Defining and calling",
             code('''
                 def greet():
                     print("Hello!")
                     print("Welcome to PyPath")

                 greet()
                 greet()
             ''', output='''
                 Hello!
                 Welcome to PyPath
                 Hello!
                 Welcome to PyPath
             ''', explain=["def starts a function definition. greet is its name.",
                           "The empty ( ) and the colon : are required.",
                           "The indented lines are the function's body.",
                           "greet() calls the function — each call runs the body once."])),
        page("Define first, then call",
             text("""Python reads your file from top to bottom. Defining a function does not run it —
                  it only remembers it. The body runs when you call it."""),
             code('''
                 say_hi()

                 def say_hi():
                     print("Hi")
             ''', output="NameError: name 'say_hi' is not defined",
                 explain=["The call on line 1 happens before Python has read the def.",
                          "Python stops with a NameError."]),
             mistake("Calling a function above its def. Always put the def first and the call below it.")),
    ], [
        m("Which word starts a function definition?", "def", ["function", "func", "define"],
          "Python uses def. Other languages use words like function, which is why it's a common guess."),
        m("What does this code print?", "B\nA", ["A\nB", "B", "A"],
          "The def is only remembered, not run. print(\"B\") runs first, then show() prints A.",
          code_='def show():\n    print("A")\n\nprint("B")\nshow()', check="out"),
        m("Find the bug.", "The colon : is missing at the end of the def line",
          ["Function names must be written in capitals", "print must not be indented", "def lines need a semicolon"],
          "Like if and for, a def line ends with a colon. Lowercase names and an indented body are correct.",
          code_='def hello()\n    print("hi")'),
        m("What does this code print?", "beep\nbeep\nbeep", ["beep", "beep()\nbeep()\nbeep()", "Nothing, the function is never called"],
          "beep() is called three times and each call prints beep once.",
          code_='def beep():\n    print("beep")\n\nbeep()\nbeep()\nbeep()', check="out"),
        m("What happens when Python reads a def block?", "It remembers the function but does not run its body yet",
          ["It runs the body immediately", "It prints the function's name", "It deletes the function after one call"],
          "The body only runs when the function is called. That's why you can define a function and use it many times later."),
    ]),
    lesson("l4s2", "4.2", "Parameters", "Pass values into a function so it can work with them.", 5, [
        page("Passing information in",
             text("""A parameter is a variable in the brackets of the def line. When you call the function,
                  the value you pass is stored in it."""),
             code('''
                 def greet(name):
                     print(f"Hello, {name}!")

                 greet("Asha")
                 greet("Ravi")
             ''', output='''
                 Hello, Asha!
                 Hello, Ravi!
             ''', explain=["name is the parameter.", "In the first call name is \"Asha\", in the second it's \"Ravi\"."])),
        page("More than one parameter",
             text("Separate parameters with commas. Values are matched in order."),
             code('''
                 def add(a, b):
                     print(a + b)

                 add(2, 3)
                 add(10, 5)
             ''', output='''
                 5
                 15
             ''', explain=["In add(2, 3), a is 2 and b is 3.", "In add(10, 5), a is 10 and b is 5."])),
        page("Parameters vs arguments",
             bullets("Parameter: the name in the def line, like pet",
                     "Argument: the actual value you pass in the call, like \"Tom\"",
                     "Arguments are matched to parameters by position"),
             code('''
                 def describe(pet, age):
                     print(f"{pet} is {age} years old")

                 describe("Tom", 3)
                 describe(3, "Tom")
             ''', output='''
                 Tom is 3 years old
                 3 is Tom years old
             ''', explain=["Swapping the arguments swaps the values.", "Python doesn't know what you meant — order matters."])),
        page("Missing arguments",
             code('''
                 def square(n):
                     print(n * n)

                 square()
             ''', output="TypeError: square() missing 1 required positional argument: 'n'",
                 explain=["square needs one value for n.", "Calling it with empty brackets is an error."]),
             mistake("Calling a function with fewer (or more) arguments than it has parameters."),
             key("Each parameter needs a value when the function is called.")),
    ], [
        m("What does this code print?", "Hi!", ["word!", "Hi", "Error"],
          "word holds \"Hi\", and + joins it with \"!\". The name word is not printed — its value is.",
          code_='def shout(word):\n    print(word + "!")\n\nshout("Hi")', check="out"),
        m("In def area(width, height):, what are width and height?", "Parameters",
          ["Arguments", "Function names", "Function calls"],
          "The names in the def line are parameters. Arguments are the values you pass when calling, like area(3, 4). The function's name is area, and area(3, 4) is a call."),
        m("What does this code print?", "-7", ["7", "3 - 10", "Error"],
          "a is 3 and b is 10 because arguments are matched in order, so a - b is -7.",
          code_="def minus(a, b):\n    print(a - b)\n\nminus(3, 10)", check="out"),
    ]),
    lesson("l4s3", "4.3", "Return values", "Send a result back from a function with return.", 7, [
        page("Sending a value back",
             text("return hands a value back to the code that called the function. You can store it in a variable."),
             code('''
                 def double(n):
                     return n * 2

                 result = double(4)
                 print(result)
             ''', output="8", explain=["double(4) works out 4 * 2.", "return sends 8 back.",
                                       "result stores the returned value."])),
        page("Using returned values directly",
             code('''
                 def square(n):
                     return n * n

                 print(square(3) + square(4))
             ''', output="25", explain=["square(3) returns 9 and square(4) returns 16.",
                                        "You can use them in maths just like numbers: 9 + 16 = 25."])),
        page("print vs return",
             text("print shows a value on the screen. return gives it back to your code. They are not the same."),
             code('''
                 def shown(n):
                     print(n * 2)

                 def given(n):
                     return n * 2

                 a = shown(5)
                 b = given(5)
                 print(a)
                 print(b)
             ''', output='''
                 10
                 None
                 10
             ''', explain=["shown(5) prints 10 but returns nothing.",
                           "A function without return gives back None, Python's \"no value\".",
                           "given(5) returns 10, so b holds 10."]),
             key("If you need to use the result later, return it.")),
        page("return ends the function",
             code('''
                 def check_age(age):
                     if age >= 18:
                         return "Adult"
                     return "Minor"

                 print(check_age(20))
                 print(check_age(12))
             ''', output='''
                 Adult
                 Minor
             ''', explain=["For 20, the first return runs and the function ends immediately.",
                           "For 12, the if is skipped and the second return runs."])),
        page("Forgetting return",
             code('''
                 def total(a, b):
                     a + b

                 print(total(2, 3))
             ''', output="None", explain=["a + b is worked out, then thrown away.", "Nothing is returned, so the call gives None."]),
             mistake("Writing the calculation but forgetting the word return. The function then gives back None."),
             tip("Lines after a return inside the same block never run.")),
    ], [
        m("What does this code print?", "12", ["7", "444", "None"],
          "x * 3 with x = 4 is 12, and return sends it back to print. 7 would be 4 + 3, which is not what * does.",
          code_="def triple(x):\n    return x * 3\n\nprint(triple(4))", check="out"),
        m("What does this code print?", "3\nNone", ["3\n3", "None", "3"],
          "f prints 3 but has no return, so y is None. Both lines appear: first 3 from inside f, then None.",
          code_="def f(x):\n    print(x)\n\ny = f(3)\nprint(y)", check="out"),
        m("What does return do?", "Sends a value back to the code that called it and ends the function",
          ["Prints the value on the screen for the user", "Runs the function again from the very top",
           "Saves the value so every function can use it"],
          "return gives a value back to the code that called the function, and the function stops there. Showing it on screen is print's job, and return does not repeat the function."),
        m("This prints None instead of 10. What's the problem?", "The function never returns result",
          ["w and h must be named width and height", "print can't call a function", "result must be printed inside the function"],
          "result is calculated but not returned, so the call gives None. Adding return result fixes it.",
          code_="def area(w, h):\n    result = w * h\n\nprint(area(2, 5))"),
        m("What does this code print?", "negative", ["positive", "negative\npositive", "None"],
          "-2 < 0 is True, so the first return runs and the function ends. The second return is never reached.",
          code_='def sign(n):\n    if n < 0:\n        return "negative"\n    return "positive"\n\nprint(sign(-2))', check="out"),
        m("Fill in the blank so print(add(1, 2)) shows 3.", "return", ["print", "give", "result ="],
          "return a + b sends 3 back to print. Using print inside would show 3 but then print(add(1, 2)) would also show None.",
          code_="def add(a, b):\n    ____ a + b"),
    ]),
    lesson("l4s4", "4.4", "Default and keyword arguments", "Give parameters default values and pass arguments by name.", 4, [
        page("Default values",
             text("""A parameter can have a default value. If the caller leaves it out, the default is used."""),
             code('''
                 def greet(name, greeting="Hello"):
                     print(f"{greeting}, {name}!")

                 greet("Asha")
                 greet("Ravi", "Welcome")
             ''', output='''
                 Hello, Asha!
                 Welcome, Ravi!
             ''', explain=["greeting=\"Hello\" sets a default.", "The first call doesn't pass a greeting, so \"Hello\" is used.",
                           "The second call passes \"Welcome\", which replaces the default."]),
             mistake("""Parameters with defaults must come after the ones without. def greet(greeting="Hello", name):
                     is a SyntaxError.""")),
        page("Keyword arguments",
             text("""You can pass an argument by naming its parameter: name=value. Then the order
                  doesn't matter, and you can skip defaults you don't want to change."""),
             code('''
                 def order(item, size="medium", sugar=1):
                     print(f"{size} {item}, sugar: {sugar}")

                 order("tea")
                 order("coffee", sugar=0)
                 order(size="large", item="juice")
             ''', output='''
                 medium tea, sugar: 1
                 medium coffee, sugar: 0
                 large juice, sugar: 1
             ''', explain=["order(\"tea\") uses both defaults.", "sugar=0 changes only sugar; size keeps its default.",
                           "With keywords, item and size can be given in any order."]),
             key("Defaults make arguments optional. Keywords make calls easier to read.")),
    ], [
        m("What does this code print?", "haha", ["ha", "hahaha", "Error: times is missing"],
          "times wasn't passed, so its default 2 is used and \"ha\" * 2 is \"haha\". No error, because the parameter has a default.",
          code_='def repeat(word, times=2):\n    print(word * times)\n\nrepeat("ha")', check="out"),
        m("What does this code print?", "Mia-9", ["9-Mia", "age-name", "Error"],
          "Keyword arguments are matched by name, not position, so name is \"Mia\" and age is 9.",
          code_='def info(name, age):\n    print(f"{name}-{age}")\n\ninfo(age=9, name="Mia")', check="out"),
        m("Which function definition causes a SyntaxError?", "def f(a=1, b):",
          ["def f(a, b=1):", "def f(a=1, b=2):", "def f(a, b):"],
          "A parameter without a default can't come after one with a default. The other three are all valid."),
        m("When is a default value used?", "When the caller doesn't pass a value for that parameter",
          ["Always, even when the caller passes a different value", "Only when the function has no return line",
           "Only when the call uses keyword arguments"],
          "A passed value always replaces the default. The default only fills in when nothing is given, with or without keyword arguments."),
    ]),
    lesson("l4s5", "4.5", "Scope", "Learn where variables live: inside or outside a function.", 7, [
        page("Local variables",
             text("""A variable created inside a function is local: it only exists while the function
                  runs. Outside the function, it's gone."""),
             code('''
                 def make_tea():
                     cups = 2
                     print(f"Making {cups} cups")

                 make_tea()
                 print(cups)
             ''', output='''
                 Making 2 cups
                 NameError: name 'cups' is not defined
             ''', explain=["cups is created inside make_tea.", "When the function ends, cups disappears.",
                           "The last line can't find it, so Python raises a NameError."])),
        page("Reading global variables",
             text("A variable created outside all functions is global. Functions can read it."),
             code('''
                 shop = "PyCafe"

                 def welcome():
                     print(f"Welcome to {shop}")

                 welcome()
             ''', output="Welcome to PyCafe", explain=["shop is defined at the top level.",
                                                     "welcome() can read it without any extra work."])),
        page("Same name, different box",
             text("""If you assign to a name inside a function, Python makes a new local variable —
                  even if a global has the same name. The global is not changed."""),
             code('''
                 x = 10

                 def change():
                     x = 99
                     print(f"inside: {x}")

                 change()
                 print(f"outside: {x}")
             ''', output='''
                 inside: 99
                 outside: 10
             ''', explain=["x = 99 creates a local x inside change().", "The global x is still 10."]),
             key("Assigning inside a function creates a local variable.")),
        page("Pass in, return out",
             text("""The clean way for a function to change a value: take it as a parameter and return the
                  new value. The caller decides where to store it."""),
             code('''
                 score = 10

                 def add_bonus(points):
                     return points + 5

                 score = add_bonus(score)
                 print(score)
             ''', output="15", explain=["score is passed in as points.", "The function returns 15.",
                                        "The caller stores it back in score."]),
             mistake("""Expecting total = total + n inside a function to change a global total. Python treats
                     total as local and raises an error. Pass it in and return it instead."""),
             bullets("Local: made inside a function, lives only during the call",
                     "Global: made outside, readable everywhere",
                     "Each call gets fresh local variables")),
    ], [
        m("What happens when this runs?", "A NameError", ["It prints 5", "It prints None", "A TypeError"],
          "y is local to f and disappears when f ends, so print(y) can't find it and Python raises a NameError. Calling f() first doesn't make y available outside, so it does not print 5.",
          code_="def f():\n    y = 5\n\nf()\nprint(y)", check="err:NameError"),
        m("What does this code print?", "1", ["NameError", "None", "0"],
          "Functions can read global variables, so show() prints the global n.",
          code_="n = 1\n\ndef show():\n    print(n)\n\nshow()", check="out"),
        m("What does this code print?", "3", ["7", "None", "NameError"],
          "a = 7 inside set_a creates a new local a. The global a is untouched and is still 3.",
          code_="a = 3\n\ndef set_a():\n    a = 7\n\nset_a()\nprint(a)", check="out"),
        m("What is a local variable?", "A variable made inside a function that exists only during the call",
          ["A variable that every function in the file can change", "A variable created at the top of the file, outside functions",
           "A variable whose value can never be changed"],
          "Local means \"belongs to this function call\". A variable at the top of the file, outside all functions, is global."),
        m("Which statement is true?", "Each call of a function gets fresh local variables",
          ["Local variables keep their values between calls", "A function can't read variables from outside",
           "Parameters are global variables"],
          "Locals are created again on every call and thrown away at the end. Functions can read globals, and parameters are local."),
        m("Calling add(5) raises an error. What's the cleanest fix?",
          "Take total as a parameter and return total + n",
          ["Call add(5) twice so that total exists first", "Delete the line total = 0 at the top of the file",
           "Add print(total) as the first line inside add"],
          "Assigning total inside the function makes it local, so total + n has nothing to read yet. Passing total in and returning the new value (total = add(total, 5)) avoids the problem. Calling twice, deleting the global or printing it first still leaves total local, so the error stays.",
          code_="total = 0\n\ndef add(n):\n    total = total + n\n\nadd(5)"),
        m("What does this code print?", "1", ["2", "0", "None"],
          "c is a fresh local variable on every call: it starts at 0 and becomes 1. The first call's c doesn't carry over, so it's not 2.",
          code_="def count():\n    c = 0\n    c += 1\n    return c\n\ncount()\nprint(count())", check="out"),
    ]),
  ],
}

# ───────────────────────────── LEVEL 5 ─────────────────────────────
level5 = {
  "id": "l5", "number": 5, "title": "Lists",
  "description": "Store many values in one variable, pick them out, change them and loop over them.",
  "subLevels": [
    lesson("l5s1", "5.1", "Creating lists", "Square brackets, items and len().", 4, [
        page("Many values, one variable",
             text("""A list stores several values in order, inside square brackets [ ] and separated by commas."""),
             code('''
                 fruits = ["apple", "banana", "mango"]
                 print(fruits)
             ''', output="['apple', 'banana', 'mango']",
                 explain=["fruits is one variable holding three strings.",
                          "Printing a list shows the brackets and quotes too."])),
        page("Lists can hold anything",
             text("A list can hold numbers, text, True/False — or be empty. len() tells you how many items it has."),
             code('''
                 marks = [72, 95, 48]
                 empty = []
                 mixed = ["Asha", 21, True]
                 print(len(marks))
                 print(len(empty))
                 print(mixed)
             ''', output='''
                 3
                 0
                 ['Asha', 21, True]
             ''', explain=["marks has 3 items.", "[] is an empty list with 0 items.",
                           "One list can mix different types."])),
        page("Is it in the list?",
             text("The word in checks whether a value is somewhere in a list. The answer is True or False."),
             code('''
                 colours = ["red", "green", "blue"]
                 print("green" in colours)
                 print("pink" in colours)
             ''', output='''
                 True
                 False
             '''),
             mistake("""Forgetting a comma. ["a" "b"] is a list with ONE item, "ab", because Python joins
                     strings that sit next to each other."""),
             key("Lists keep items in the order you put them in.")),
    ], [
        m("Which line creates a list?", "nums = [1, 2, 3]", ['nums = "1, 2, 3"', 'nums = "[1, 2, 3]"', "nums == [1, 2, 3]"],
          "A list is written with square brackets and commas. Anything inside quotes is one string, even if it contains brackets, and == compares values instead of storing one."),
        m("What does this code print?", "3", ["27", "15", "2"],
          "len() counts items, not their total. There are 3 numbers in the list.",
          code_="nums = [4, 8, 15]\nprint(len(nums))", check="out"),
        m("What does this code print?", "False", ["True", "cow", "Error"],
          '"cow" is not one of the items, so in gives False. It\'s not an error to check for a missing value.',
          code_='pets = ["cat", "dog"]\nprint("cow" in pets)', check="out"),
        m("This list has 2 items, not 3. Why?", 'A comma is missing between "Asha" and "Ravi"',
          ["Lists can't hold names", "Lists need round brackets", "Strings in lists need single quotes"],
          'Without the comma Python joins the two strings into "AshaRavi". Both single and double quotes work in lists.',
          code_='names = ["Asha" "Ravi", "Meera"]'),
    ]),
    lesson("l5s2", "5.2", "Indexing and slicing", "Get, change and cut out items by position.", 6, [
        page("Positions start at 0",
             text("Every item has a position number called an index. The first index is 0, not 1."),
             code('''
                 letters = ["a", "b", "c", "d"]
                 print(letters[0])
                 print(letters[2])
             ''', output='''
                 a
                 c
             ''', explain=["letters[0] is the first item.", "letters[2] is the third item."])),
        page("Counting from the end",
             text("Negative indexes count from the end: -1 is the last item, -2 the one before it."),
             code('''
                 letters = ["a", "b", "c", "d"]
                 print(letters[-1])
                 print(letters[-2])
             ''', output='''
                 d
                 c
             '''),
             tip("letters[-1] always gives the last item, however long the list is.")),
        page("Changing an item",
             code('''
                 scores = [10, 20, 30]
                 scores[1] = 25
                 print(scores)
             ''', output="[10, 25, 30]", explain=["scores[1] is the second item.", "Assigning to it replaces 20 with 25."]),
             key("You can read AND change list items by index.")),
        page("Slicing",
             text("""A slice list[start:stop] gives a new list from start up to, but not including, stop.
                  Leave out start to begin at 0; leave out stop to go to the end."""),
             code('''
                 nums = [10, 20, 30, 40, 50]
                 print(nums[1:3])
                 print(nums[:2])
                 print(nums[3:])
             ''', output='''
                 [20, 30]
                 [10, 20]
                 [40, 50]
             ''', explain=["nums[1:3] takes index 1 and 2.", "nums[:2] takes the first two.",
                           "nums[3:] takes everything from index 3 to the end."])),
        page("Index out of range",
             code('''
                 days = ["Mon", "Tue", "Wed"]
                 print(days[3])
             ''', output="IndexError: list index out of range",
                 explain=["The list has 3 items, so the indexes are 0, 1 and 2.", "There is no index 3."]),
             mistake("Using the length as an index. The last index is always len(list) - 1.")),
    ], [
        m("What does this code print?", "green", ["red", "blue", "Error"],
          "Indexes start at 0, so x[1] is the second item. red would be x[0].",
          code_='x = ["red", "green", "blue"]\nprint(x[1])', check="out"),
        m("What does this code print?", "8", ["5", "7", "Error"],
          "-1 means the last item. Negative indexes are allowed, so there is no error.",
          code_="x = [5, 6, 7, 8]\nprint(x[-1])", check="out"),
        m("What does this code print?", "[2, 3, 4]", ["[1, 2, 3, 4]", "[2, 3, 4, 5]", "[1, 4]"],
          "x[1:4] starts at index 1 (the value 2) and stops before index 4, so the 5 at index 4 is left out.",
          code_="x = [1, 2, 3, 4, 5]\nprint(x[1:4])", check="out"),
        m("What happens when this runs?", "An IndexError",
          ["It prints book", "It prints pen", "A NameError"],
          "Two items means indexes 0 and 1, so items[2] goes past the end and Python raises an IndexError. book is items[1], the last item, and items does exist, so it is not a NameError.",
          code_='items = ["pen", "book"]\nprint(items[2])', check="err:IndexError"),
        m("Fill in the blank to get the first three items: nums[____]", ":3", ["1:3", "0:2", ":2"],
          "A slice stops before the stop index, so :3 gives indexes 0, 1, 2. 1:3 would skip the first item.",
          code_="nums = [4, 7, 1, 9, 3]\nfirst_three = nums[____]"),
        m("What does this code print?", "[9, 2, 3]", ["[1, 2, 3]", "[9, 1, 2, 3]", "[1, 9, 3]"],
          "a[0] = 9 replaces the first item. It doesn't insert a new one, so the list still has 3 items.",
          code_="a = [1, 2, 3]\na[0] = 9\nprint(a)", check="out"),
    ]),
    lesson("l5s3", "5.3", "List methods", "append, insert, remove, pop, sort — plus max, min and sum.", 8, [
        page("Adding with append()",
             text("A method is a function that belongs to a value. You call it with a dot: list.method()."),
             code('''
                 cart = ["milk"]
                 cart.append("bread")
                 cart.append("eggs")
                 print(cart)
             ''', output="['milk', 'bread', 'eggs']", explain=["append() adds one item to the end of the list.",
                                                          "The list itself is changed."])),
        page("Adding at a position with insert()",
             code('''
                 queue = ["Asha", "Ravi"]
                 queue.insert(0, "Meera")
                 print(queue)
             ''', output="['Meera', 'Asha', 'Ravi']", explain=["insert(index, value) puts the value at that index.",
                                                          "Everything after it moves one place along."])),
        page("Removing items",
             code('''
                 tasks = ["wash", "cook", "study"]
                 tasks.remove("cook")
                 print(tasks)
                 last = tasks.pop()
                 print(last)
                 print(tasks)
             ''', output='''
                 ['wash', 'study']
                 study
                 ['wash']
             ''', explain=["remove() deletes the first item equal to the value you give.",
                           "pop() removes the last item AND gives it back, so you can store it."])),
        page("Sorting",
             code('''
                 marks = [72, 95, 48, 66]
                 marks.sort()
                 print(marks)
                 marks.sort(reverse=True)
                 print(marks)
             ''', output='''
                 [48, 66, 72, 95]
                 [95, 72, 66, 48]
             ''', explain=["sort() puts the items in order, smallest first.", "reverse=True sorts largest first."])),
        page("max, min, sum and len",
             text("These built-in functions work on a whole list of numbers. Together they make quick statistics."),
             code('''
                 marks = [72, 95, 48, 66, 81]
                 marks.append(73)
                 print(max(marks))
                 print(min(marks))
                 print(sum(marks) / len(marks))
             ''', output='''
                 95
                 48
                 72.5
             ''', explain=["max() gives the biggest value, min() the smallest.",
                           "sum() adds everything up: 435.", "Dividing by len() (6 marks) gives the average."])),
        page("Methods change the list",
             text("""Methods like append() and sort() change the list itself. They don't give back a new list —
                  they give back None."""),
             code('''
                 nums = [3, 1, 2]
                 nums = nums.sort()
                 print(nums)
             ''', output="None", explain=["nums.sort() sorts the list and returns None.",
                                          "nums = ... then replaces the sorted list with None."]),
             mistake("Writing nums = nums.sort(). Just call nums.sort() on its own line."),
             bullets("append(x) — add to the end", "insert(i, x) — add at index i", "remove(x) — delete the first x",
                     "pop() — remove and return the last item", "sort() — put in order", "count(x) — how many times x appears")),
    ], [
        m("What does this code print?", "[1, 2, 3]", ["[3, 1, 2]", "[1, 2]", "[3]"],
          "append() adds the value to the end of the list and keeps the items already there. It does not put 3 at the start or replace the list.",
          code_="a = [1, 2]\na.append(3)\nprint(a)", check="out"),
        m("What does this code print?", "[2, 5, 9]", ["[9, 5, 2]", "[5, 2, 9]", "None"],
          "sort() orders the list smallest first. Here the list is printed after sorting, so it isn't None.",
          code_="n = [5, 2, 9]\nn.sort()\nprint(n)", check="out"),
        m("What does pop() do?", "Removes the last item and returns it",
          ["Removes the first item and returns it", "Adds a new item at the end of the list", "Removes the last item and returns None"],
          "pop() takes the last item and hands it back, so you can store it. It doesn't take the first item, and unlike append() and sort() it does not give back None."),
        m("Why does this print None?", "sort() changes the list itself and returns None",
          ["Lists of numbers can't be sorted with sort()", "print can't show a list after sorting", "sort() only works if reverse=True is given"],
          "x = x.sort() stores sort()'s return value, which is None. Numbers sort fine and reverse=True is optional — just call x.sort() on its own line.",
          code_="x = [4, 1, 3]\nx = x.sort()\nprint(x)"),
        m("What does this code print?", "['a', 'b', 'c']", ["['b', 'c', 'a']", "['a', 'c']", "['b', 'a', 'c']"],
          "insert(0, \"a\") puts \"a\" at index 0, the front, and the other items move one place along. It does not add to the end like append(), and it does not replace \"b\".",
          code_='letters = ["b", "c"]\nletters.insert(0, "a")\nprint(letters)', check="out"),
    ]),
    lesson("l5s4", "5.4", "Looping over lists", "Visit every item with a for loop.", 5, [
        page("for item in list",
             text("A for loop can walk through a list directly. The loop variable takes each item in turn."),
             code('''
                 fruits = ["apple", "banana", "mango"]
                 for fruit in fruits:
                     print(f"I like {fruit}")
             ''', output='''
                 I like apple
                 I like banana
                 I like mango
             '''),
             code('''
                 prices = [40, 25, 60]
                 total = 0
                 for price in prices:
                     total += price
                 print(total)
             ''', output="125", explain=["price is 40, then 25, then 60.", "The same add-up pattern you used with range()."])),
        page("Building a new list",
             text("Combine a loop, an if and append() to pick out the items you want."),
             code('''
                 marks = [35, 80, 52, 91]
                 passed = []
                 for m in marks:
                     if m >= 50:
                         passed.append(m)
                 print(passed)
             ''', output="[80, 52, 91]", explain=["passed starts empty.", "Only marks of 50 or more are added."]),
             mistake("""Writing for m in range(marks). range() needs a number, not a list — loop over the
                     list itself with for m in marks."""),
             key("for x in my_list visits every item, in order.")),
    ], [
        m("What does this code print?", "x\ny", ["['x', 'y']", "xy", "0\n1"],
          "The loop prints each item on its own line. It loops over the items, not their index numbers.",
          code_='for c in ["x", "y"]:\n    print(c)', check="out"),
        m("What does this code print?", "12", ["6", "246", "3"],
          "The numbers are added: 2 + 4 + 6 = 12. They are numbers, not strings, so they aren't joined into 246.",
          code_="total = 0\nfor n in [2, 4, 6]:\n    total += n\nprint(total)", check="out"),
        m("Which is a correct way to loop over the list names?", "for name in names:",
          ["for name in range(names):", "for names[] in name:", "for name of names:"],
          "Loop over the list directly. range() needs a number, so range(names) is a TypeError.",
          code_='names = ["Asha", "Ravi"]\n____\n    print(name)'),
        m("What does this code print?", "[5, 8]", ["[1]", "[1, 5, 8]", "2"],
          "Only numbers greater than 4 are appended. The new list is printed, not how many items it has.",
          code_="nums = [1, 5, 8]\nbig = []\nfor n in nums:\n    if n > 4:\n        big.append(n)\nprint(big)", check="out"),
    ]),
    lesson("l5s5", "5.5", "List comprehensions", "Build a new list in one line (the simple way).", 8, [
        page("A shortcut for building lists",
             text("Here is the loop-and-append pattern you already know:"),
             code('''
                 squares = []
                 for n in range(1, 5):
                     squares.append(n * n)
                 print(squares)
             ''', output="[1, 4, 9, 16]"),
             text("A list comprehension does the same thing in one line:"),
             code('''
                 squares = [n * n for n in range(1, 5)]
                 print(squares)
             ''', output="[1, 4, 9, 16]", explain=["Read it as: \"n * n for each n in range(1, 5)\".",
                                                   "The result is a brand-new list."])),
        page("Reading a comprehension",
             bullets("[ what_to_keep  for  name  in  source ]",
                     "what_to_keep — the value that goes into the new list",
                     "for name in source — the same as a normal for loop"),
             code('''
                 names = ["asha", "ravi"]
                 loud = [name + "!" for name in names]
                 print(loud)
             ''', output="['asha!', 'ravi!']")),
        page("Adding a filter",
             text("Put an if at the end to keep only some items."),
             code('''
                 marks = [35, 80, 52, 91]
                 passed = [m for m in marks if m >= 50]
                 print(passed)
             ''', output="[80, 52, 91]", explain=["Same result as the loop in 5.4, in one line.",
                                                  "Only items where the if is True are kept."]),
             key("A list comprehension always makes a new list. The original list is not changed.")),
        page("Keep it simple",
             code('''
                 prices = [100, 250, 80]
                 with_tax = [p + 10 for p in prices]
                 print(with_tax)
                 print(prices)
             ''', output='''
                 [110, 260, 90]
                 [100, 250, 80]
             '''),
             mistake("""Putting the parts in the wrong order, like [for p in prices p + 10]. The value
                     always comes first, then for."""),
             tip("If a comprehension gets hard to read, use a normal loop. Both are correct.")),
    ], [
        m("What does this code print?", "[0, 2, 4]", ["[2, 4, 6]", "[0, 1, 2]", "[0, 2, 4, 6]"],
          "range(3) gives 0, 1, 2, and each is doubled. It starts at 0, so the first value is 0, not 2.",
          code_="print([n * 2 for n in range(3)])", check="out"),
        m("What does this code print?", "[3, 4]", ["[1, 2]", "[2, 3, 4]", "[False, False, True, True]"],
          "Only items where x > 2 is True are kept, and the items themselves go in the list, not True/False.",
          code_="print([x for x in [1, 2, 3, 4] if x > 2])", check="out"),
        m('Which loop does [w + "s" for w in words] replace?',
          'A for loop that appends w + "s" to a new list',
          ['A for loop that prints w + "s" for each word', "A while loop that removes each w from words",
           'A for loop that adds "s" to the words list itself'],
          "A comprehension builds a brand-new list, like a loop that appends to an empty list. It doesn't print anything and doesn't change the original words list."),
        m("Find the bug.", "The value must come before for, with no colon",
          ["Comprehensions need round brackets ( )", "nums must be replaced by a range()", "The colon should be a semicolon ;"],
          "A comprehension is written [value for name in source], so the fix is [n * 2 for n in nums] with no colon. Square brackets are correct, and any list can be the source.",
          code_="doubled = [for n in nums: n * 2]"),
        m("What does this code print?", "['hihi', 'yoyo']", ["['hi', 'yo', 'hi', 'yo']", "hihiyoyo", "['hiyo']"],
          "Each word is joined with itself, giving one new item per word in a list.",
          code_='words = ["hi", "yo"]\nprint([w + w for w in words])', check="out"),
        m("Fill in the blank to keep only even numbers.", "if n % 2 == 0", ["if n % 2 == 1", "while n % 2 == 0", "for n % 2 == 0"],
          "A filter is an if at the end. n % 2 == 1 would keep the odd numbers instead, and while or for can't be used as a filter.",
          code_="evens = [n for n in nums ____]"),
        m("Which statement is true?", "A list comprehension always creates a new list",
          ["A list comprehension changes the original list", "A list comprehension can only use range()",
           "A list comprehension prints each item"],
          "The source list stays the same. You can loop over any list, not just range()."),
        m("What does this code print?", "[10, 30]", ["[10, 20, 30]", "[20]", "[1, 3]"],
          "2 is skipped by the filter, and the kept numbers are multiplied by 10.",
          code_="nums = [1, 2, 3]\nresult = [n * 10 for n in nums if n != 2]\nprint(result)", check="out"),
    ]),
  ],
}

# ───────────────────────────── LEVEL 6 ─────────────────────────────
level6 = {
  "id": "l6", "number": 6, "title": "Strings and Dictionaries",
  "description": "Work with text using string methods and f-strings, and store labelled data in dictionaries.",
  "subLevels": [
    lesson("l6s1", "6.1", "String methods", "Change case, clean, search, split and join text.", 6, [
        page("Strings have methods too",
             text("Like lists, strings have methods you call with a dot. upper() and lower() change the case."),
             code('''
                 name = "Asha"
                 print(name.upper())
                 print(name.lower())
                 print(name)
             ''', output='''
                 ASHA
                 asha
                 Asha
             ''', explain=["upper() gives back a new string in capitals.",
                           "The original name is not changed — strings can't be changed in place."]),
             key("String methods return a NEW string. Store it if you want to keep it.")),
        page("Cleaning text with strip()",
             text("strip() removes spaces from the start and end. Very useful for user input."),
             code('''
                 answer = "  yes  "
                 print(answer.strip())
                 print(len(answer))
                 print(len(answer.strip()))
             ''', output='''
                 yes
                 7
                 3
             ''', explain=["len() works on strings too and counts characters, including spaces.",
                           "After strip() only the 3 letters are left."])),
        page("Finding and replacing",
             code('''
                 text = "I like tea"
                 print(text.replace("tea", "coffee"))
                 print(text.startswith("I"))
                 print(text.count("e"))
             ''', output='''
                 I like coffee
                 True
                 2
             ''', explain=["replace(old, new) swaps every match.", "startswith() answers True or False.",
                           "count() counts how many times the text appears."])),
        page("Splitting and joining",
             text("split() breaks a string into a list. join() glues a list of strings back together."),
             code('''
                 line = "red,green,blue"
                 colours = line.split(",")
                 print(colours)
                 print(" & ".join(colours))
             ''', output='''
                 ['red', 'green', 'blue']
                 red & green & blue
             ''', explain=["split(\",\") cuts the text at every comma.",
                           "\" & \".join(...) puts \" & \" between the items."])),
        page("Strings are like lists of letters",
             text("You can index a string and loop over its letters, just like a list."),
             code('''
                 word = "python"
                 print(word[0])
                 print(word[-1])
                 print(len(word))
                 for letter in "hi":
                     print(letter)
             ''', output='''
                 p
                 n
                 6
                 h
                 i
             '''),
             mistake("""Calling name.upper() on its own line and expecting name to change. Write
                     name = name.upper() to keep the result.""")),
    ], [
        m("What does this code print?", "HELLO", ["Hello", "hello", "HELLO()"],
          "upper() turns every letter into a capital, not just the first one.",
          code_='print("hello".upper())', check="out"),
        m("What does this code print?", "2", ["6", "4", "hi"],
          "strip() removes the spaces at both ends, leaving \"hi\", which has 2 characters. len() gives a number, not the text.",
          code_='s = "  hi  "\nprint(len(s.strip()))', check="out"),
        m("What does this code print?", "['a', 'b', 'c']", ["['a-b-c']", "abc", "['a', '-', 'b', '-', 'c']"],
          "split(\"-\") cuts at each dash and throws the dashes away, giving a list of three strings.",
          code_='print("a-b-c".split("-"))', check="out"),
        m("Why does this still print ravi?", "upper() returns a new string, and the result was never stored",
          ["upper() needs an argument", "print can't show capital letters", "Strings must use single quotes for upper()"],
          "Strings never change in place. Writing name = name.upper() keeps the capital version.",
          code_='name = "ravi"\nname.upper()\nprint(name)'),
        m('Fill in the blank so the result is "a-b".', "join", ["split", "replace", "append"],
          "join() glues list items together with the string in front of the dot between them. split() does the opposite.",
          code_='result = "-".____(["a", "b"])'),
        m("What does this code print?", "3", ["2", "1", "6"],
          "b-a-n-a-n-a has three a's. 6 is the length of the whole word.",
          code_='w = "banana"\nprint(w.count("a"))', check="out"),
    ]),
    lesson("l6s2", "6.2", "f-strings and formatting", "Round numbers and line up columns neatly.", 5, [
        page("f-strings can do maths",
             text("You met f-strings in 1.4. Anything inside { } is worked out first — even a calculation."),
             code('''
                 item = "pen"
                 price = 12
                 qty = 3
                 print(f"{qty} x {item} = {price * qty}")
             ''', output="3 x pen = 36")),
        page("Decimal places",
             text("Add :.2f after a value to show exactly 2 decimal places. The number is rounded."),
             code('''
                 average = 72.456
                 print(f"{average:.2f}")
                 print(f"{average:.1f}")
                 print(f"{2 / 3:.3f}")
             ''', output='''
                 72.46
                 72.5
                 0.667
             ''', explain=[":.2f means \"float with 2 decimals\".", "The value itself is not changed — only how it is shown."])),
        page("Lining up columns",
             text("""Add :<8 to left-align a value in 8 spaces, or :>6 to right-align it in 6. This makes
                  neat tables."""),
             code('''
                 print(f"{'Item':<8}{'Price':>6}")
                 print(f"{'Tea':<8}{15:>6}")
                 print(f"{'Samosa':<8}{20:>6}")
             ''', output='''
                 Item     Price
                 Tea         15
                 Samosa      20
             ''', explain=["< means left-align, > means right-align.", "The number is the width in characters.",
                           "Inside a double-quoted f-string, use single quotes for text like 'Item'."]),
             mistake("""Forgetting the f. print("Hi {name}") prints the braces literally: Hi {name}."""),
             key("f\"{value:.2f}\" rounds. f\"{value:>6}\" lines things up.")),
    ], [
        m("What does this code print?", "3.14", ["3.15", "3.14159", "3.1"],
          ":.2f rounds to 2 decimal places. The third decimal is 1, so it rounds down to 3.14.",
          code_='x = 3.14159\nprint(f"{x:.2f}")', check="out"),
        m("What does this code print?", "Hi {n}", ["Hi Ali", "Hi n", "Error"],
          "Without the f in front of the quotes it's a normal string, so the braces are printed as they are.",
          code_='n = "Ali"\nprint("Hi {n}")', check="out"),
        m('What does :>5 do in f"{x:>5}"?', "Right-aligns x in a space 5 characters wide",
          ["Keeps only values greater than 5", "Shows x with 5 decimal places", "Repeats x five times"],
          "> means right-align and 5 is the width. Decimal places use a dot, like :.5f."),
        m("What does this code print?", "4 + 5 = 9", ["4 + 5 = 45", "{a} + {b} = {a + b}", "a + b = 9"],
          "Each { } is replaced by its value, and {a + b} is calculated as 9 because a and b are numbers.",
          code_='a = 4\nb = 5\nprint(f"{a} + {b} = {a + b}")', check="out"),
        m("Fill in the blank to show 2 decimal places.", ":.2f", [".2", ":2", ":2f"],
          ":.2f means 2 decimal places, so 12.3456 shows as 12.35. Without the dot, :2 only sets a width of 2 and shows 12.3456 unchanged, and :2f shows 12.345600, not 2 decimals. .2 without the colon is an error.",
          code_='total = 12.3456\nprint(f"{total____}")'),
    ]),
    lesson("l6s3", "6.3", "Dictionaries", "Store values under labels called keys.", 8, [
        page("Labels instead of positions",
             text("""A dictionary stores pairs of key: value inside curly braces { }. You look values up by
                  their key instead of by a position number."""),
             code('''
                 student = {"name": "Asha", "age": 21, "city": "Pune"}
                 print(student["name"])
                 print(student["age"])
             ''', output='''
                 Asha
                 21
             ''', explain=["\"name\", \"age\" and \"city\" are the keys.", "student[\"name\"] gives the value stored under \"name\"."])),
        page("Adding and changing",
             code('''
                 stock = {"pens": 10}
                 stock["books"] = 4
                 stock["pens"] = 8
                 print(stock)
             ''', output="{'pens': 8, 'books': 4}", explain=["Assigning to a new key adds it.",
                                                         "Assigning to an existing key replaces its value."]),
             key("Keys are unique. One key, one value.")),
        page("Safe lookups with get()",
             text("get() looks up a key but doesn't crash if it's missing. You can give a fallback value."),
             code('''
                 prices = {"tea": 15, "coffee": 25}
                 print(prices.get("tea"))
                 print(prices.get("juice"))
                 print(prices.get("juice", 0))
             ''', output='''
                 15
                 None
                 0
             ''', explain=["\"juice\" isn't a key, so get() returns None.", "get(\"juice\", 0) returns 0 instead."])),
        page("Checking and removing",
             code('''
                 ages = {"Asha": 21, "Ravi": 19}
                 print("Ravi" in ages)
                 removed = ages.pop("Ravi")
                 print(removed)
                 print(len(ages))
             ''', output='''
                 True
                 19
                 1
             ''', explain=["in checks the keys.", "pop(key) removes the pair and gives back its value.",
                           "len() counts the pairs."])),
        page("Counting with a dictionary",
             text("A classic use: count how often each thing appears."),
             code('''
                 votes = ["tea", "coffee", "tea", "tea"]
                 counts = {}
                 for v in votes:
                     counts[v] = counts.get(v, 0) + 1
                 print(counts)
             ''', output="{'tea': 3, 'coffee': 1}", explain=["counts.get(v, 0) is the count so far (0 the first time).",
                                                        "Adding 1 and storing it updates the count."])),
        page("Missing keys",
             code('''
                 marks = {"maths": 90}
                 print(marks["science"])
             ''', output="KeyError: 'science'", explain=["Square brackets need the key to exist.",
                                                       "Python stops with a KeyError."]),
             mistake("Using d[key] for a key that might be missing. Check with in first, or use d.get(key, default)."),
             tip("Keys are case-sensitive: \"Tea\" and \"tea\" are different keys.")),
    ], [
        m("What does this code print?", "2", ["1", "b", "Error"],
          "d[\"b\"] looks up the value stored under the key \"b\", which is 2 — not the key itself.",
          code_='d = {"a": 1, "b": 2}\nprint(d["b"])', check="out"),
        m("What does this code print?", "{'x': 7}", ["{'x': 5, 'x': 7}", "{'x': 5}", "7"],
          "Keys are unique, so assigning to \"x\" again replaces the old value.",
          code_='d = {"x": 5}\nd["x"] = 7\nprint(d)', check="out"),
        m("What does this code print?", "0", ["None", "10", "KeyError"],
          "\"milk\" is missing, so get() returns the fallback 0. Without a fallback it would return None.",
          code_='d = {"tea": 10}\nprint(d.get("milk", 0))', check="out"),
        m("In a dictionary, what is a key?", "The label you use to look up a value",
          ["The position number of a value", "The last value added", "A password that locks the dictionary"],
          "Dictionaries are looked up by key, not by position like lists."),
        m("Why does this raise a KeyError?", 'Keys are case-sensitive: "Pen" is not "pen"',
          ["Dictionaries can only store numbers", 'You must write d("Pen")', "print can't show dictionary values"],
          "The only key is \"pen\" in lowercase. Lookups use square brackets, and values can be any type.",
          code_='d = {"pen": 5}\nprint(d["Pen"])'),
        m("What does this code print?", "2", ["1", "3", "{'a': 1, 'b': 2}"],
          "Adding \"b\" makes two key-value pairs, and len() counts pairs.",
          code_='d = {"a": 1}\nd["b"] = 2\nprint(len(d))', check="out"),
        m("Fill in the blank to count words safely.", "get", ["count", "append", "values"],
          "get(word, 0) returns the current count, or 0 if the word is new, and then 1 is added. Dictionaries have no count() or append() method, and values() takes no word.",
          code_="counts[word] = counts.____(word, 0) + 1"),
        m("Which statement is true?", "Each key appears only once in a dictionary",
          ["All values must be the same type", "Keys are numbered from 0", "A dictionary can't be changed after it's created"],
          "Assigning to an existing key replaces its value. Values can be any type, and dictionaries can be changed."),
    ]),
    lesson("l6s4", "6.4", "Looping over dictionaries", "Visit keys, values and pairs with for loops.", 6, [
        page("Looping over keys",
             text("A for loop over a dictionary gives you its keys, in the order they were added."),
             code('''
                 prices = {"tea": 15, "coffee": 25, "juice": 30}
                 for item in prices:
                     print(item)
             ''', output='''
                 tea
                 coffee
                 juice
             ''')),
        page("keys(), values() and items()",
             text("""values() gives just the values. items() gives each key together with its value, so you can
                  use two loop variables. list() turns the result into a normal list for printing."""),
             code('''
                 prices = {"tea": 15, "coffee": 25, "juice": 30}
                 print(list(prices.keys()))
                 print(list(prices.values()))
                 for item, price in prices.items():
                     print(f"{item} costs {price}")
             ''', output='''
                 ['tea', 'coffee', 'juice']
                 [15, 25, 30]
                 tea costs 15
                 coffee costs 25
                 juice costs 30
             ''', explain=["for item, price in ... takes each pair apart into two variables.",
                           "This is the most common way to loop over a dictionary."])),
        page("Totals and best values",
             code('''
                 sales = {"Mon": 120, "Tue": 90, "Wed": 150}
                 total = 0
                 best_day = ""
                 best = 0
                 for day, amount in sales.items():
                     total += amount
                     if amount > best:
                         best = amount
                         best_day = day
                 print(f"Total: {total}")
                 print(f"Best day: {best_day} ({best})")
             ''', output='''
                 Total: 360
                 Best day: Wed (150)
             ''', explain=["total adds every amount.", "best remembers the biggest amount seen so far, and best_day its key."]),
             key("sum(d.values()), max(d.values()) and len(d) also work on dictionaries.")),
        page("Forgetting items()",
             code('''
                 ages = {"Asha": 21}
                 for name, age in ages:
                     print(name, age)
             ''', output="ValueError: too many values to unpack (expected 2)",
                 explain=["Looping over ages gives only the keys, like \"Asha\".",
                          "Python can't split one string into name and age."]),
             mistake("Using two loop variables without .items(). Write for name, age in ages.items():"),
             bullets("for k in d — keys", "for v in d.values() — values", "for k, v in d.items() — both")),
    ], [
        m("What does this code print?", "a\nb", ["1\n2", "a 1\nb 2", "a: 1\nb: 2"],
          "Looping over a dictionary gives its keys. To get values you need .values() or .items().",
          code_='d = {"a": 1, "b": 2}\nfor k in d:\n    print(k)', check="out"),
        m("What does this code print?", "x 3\ny 4", ["x\ny", "3\n4", "{'x': 3}\n{'y': 4}"],
          "items() gives each key with its value and the two variables take the pair apart, so print(k, v) shows the key and the value with a space. Only k would give x and y.",
          code_='d = {"x": 3, "y": 4}\nfor k, v in d.items():\n    print(k, v)', check="out"),
        m("What does this code print?", "7", ["ab", "2", "Error"],
          "values() gives 2 and 5, and sum() adds them. Keys are not added.",
          code_='d = {"a": 2, "b": 5}\nprint(sum(d.values()))', check="out"),
        m("What does .items() give you?", "Each key together with its value, as pairs",
          ["Only the values, one at a time", "Only the keys, in the order added", "The number of key-value pairs"],
          "items() gives key-value pairs. Only values is .values(), and the count is len(d)."),
        m("This raises a ValueError. How do you fix it?", "Loop over marks.items() instead of marks",
          ["Use marks.keys() with two variables", "Rename mark to marks", "Put the loop inside a function"],
          "Looping over marks gives only keys, so there's nothing to put in mark. items() gives the pairs.",
          code_='marks = {"Asha": 90, "Ravi": 75}\nfor name, mark in marks:\n    print(name, mark)'),
        m("Fill in the blank to print only the values.", "values", ["keys", "items", "get"],
          "values() gives just the values. items() would give pairs, and keys() the labels.",
          code_="for v in prices.____():\n    print(v)"),
    ]),
  ],
}

# ───────────────────────────── LEVEL 7 (FINAL PROJECT) ─────────────────────────────
PROJECT_CODE = '''
# Simple Expense Tracker
budget = 2000
expenses = []

def add_expense(name, amount, category="Other"):
    expense = {"name": name, "amount": amount, "category": category}
    expenses.append(expense)

def total_spent():
    total = 0
    for expense in expenses:
        total += expense["amount"]
    return total

def totals_by_category():
    totals = {}
    for expense in expenses:
        category = expense["category"]
        totals[category] = totals.get(category, 0) + expense["amount"]
    return totals

add_expense("Lunch", 120, "Food")
add_expense("Bus pass", 300, "Travel")
add_expense("Notebook", 80)
add_expense("Dinner", 250, "Food")
add_expense("Movie", 200, "Fun")

print("=== Expense Report ===")
for expense in expenses:
    print(f"{expense['name']:<10} Rs {expense['amount']:>4}")

print("--- By category ---")
for category, amount in totals_by_category().items():
    print(f"{category}: Rs {amount}")

spent = total_spent()
big = [e["name"] for e in expenses if e["amount"] >= 200]
print(f"Total spent: Rs {spent}")
print(f"Big expenses: {', '.join(big)}")
if spent > budget:
    print("Over budget!")
else:
    print(f"Left to spend: Rs {budget - spent}")
'''.strip("\n").split("\n")

PROJECT_OUTPUT = '''
=== Expense Report ===
Lunch      Rs  120
Bus pass   Rs  300
Notebook   Rs   80
Dinner     Rs  250
Movie      Rs  200
--- By category ---
Food: Rs 370
Travel: Rs 300
Other: Rs 80
Fun: Rs 200
Total spent: Rs 950
Big expenses: Bus pass, Dinner, Movie
Left to spend: Rs 1050
'''.strip("\n")

def stage(n, title, instruction, lines, prompt, right, wrongs, explanation, why_wrong, code_options=True, code_=None):
    """One build stage. lines = 1-based line numbers of PROJECT_CODE this stage reveals.
    why_wrong = one reason per wrong option, in the same order as wrongs."""
    assert len(wrongs) == 3 and len(why_wrong) == 3, title
    return {"n": n, "title": title, "instruction": instruction, "lines": lines, "prompt": prompt, "right": right,
            "wrongs": wrongs, "explanation": explanation, "why_wrong": why_wrong, "code_options": code_options,
            "code": code_}

def build_stages(prefix, raw):
    out = []
    for s, pos in zip(raw, positions(prefix, len(raw))):
        opts = list(s["wrongs"]); opts.insert(pos, s["right"])
        feedback = list(s["why_wrong"]); feedback.insert(pos, s["explanation"])
        question = {"id": f"{prefix}st{s['n']}q", "prompt": s["prompt"], "options": opts, "correctIndex": pos,
                    "explanation": s["explanation"]}
        if s["code"]: question["code"] = s["code"]
        out.append({"id": f"{prefix}st{s['n']}", "title": s["title"], "instruction": s["instruction"],
                    "revealsLines": s["lines"], "optionsAreCode": s["code_options"], "question": question,
                    "optionFeedback": feedback})
    return out

project_stages = [
    stage(1, "Set up the data",
          "Every tracker needs a budget and somewhere to keep the expenses. The budget is ready; now create an empty list for the expenses.",
          [1, 2, 3, 4], "Which line creates an empty list to collect expenses?",
          "expenses = []", ['expenses = ""', "expenses = {}", "expenses = [0]"],
          "[] is an empty list. Lists keep items in order and let you append() new ones (5.1).",
          ["That's an empty string. Strings have no append() method.",
           "{} is an empty dictionary. We want an ordered list we can append() to.",
           "[0] is not empty: it already holds the number 0, which isn't an expense."]),
    stage(2, "Define add_expense",
          "Write the first line of a function that takes a name, an amount and a category. Most things are \"Other\", so make that the default category.",
          [5], "Which def line is correct?",
          'def add_expense(name, amount, category="Other"):',
          ['def add_expense(category="Other", name, amount):', "def add_expense(name, amount, category):",
           'add_expense(name, amount, category="Other")'],
          "Parameters with defaults go last, so category=\"Other\" comes after name and amount (4.4).",
          ["A parameter with a default can't come before ones without. This is a SyntaxError.",
           "This works, but with no default every call must pass a category. Notebook won't have one.",
           "Without def this is a function call, not a definition, and it ends with a colon."]),
    stage(3, "Describe one expense",
          "Inside the function, bundle the three values into one dictionary so each expense keeps its own name, amount and category.",
          [6], "Which line builds the expense dictionary?",
          'expense = {"name": name, "amount": amount, "category": category}',
          ['expense = ["name", "amount", "category"]', 'expense = {name: "name", amount: "amount", category: "category"}',
           'expense = {"name" = name, "amount" = amount, "category" = category}'],
          "Keys are the labels in quotes, values are the parameters. Later we can read expense[\"amount\"] (6.3).",
          ["This list holds three words, not the actual values that were passed in.",
           "Keys and values are swapped: the key would be \"Lunch\" and the value the word \"name\".",
           "Dictionaries use a colon between key and value, not =. This is a SyntaxError."]),
    stage(4, "Save the expense",
          "Add the new dictionary to the end of the expenses list, so every call to add_expense() remembers one more expense.",
          [7, 8], "Which line stores the expense?",
          "expenses.append(expense)", ["expenses = expense", "expense.append(expenses)", "expenses + expense"],
          "append() adds one item to the end of the list (5.3). The function reads the global list and changes it in place.",
          ["This replaces the whole list with one dictionary. It also makes expenses a local variable.",
           "Backwards: dictionaries don't have append(). The list is the one that grows.",
           "+ can't add a dictionary to a list, and the result isn't stored anywhere."]),
    stage(5, "Start total_spent",
          "Now a function that adds up every amount. It needs a running total that starts at zero, then a loop over all expenses.",
          [9, 10, 11], "Which start of the function is correct?",
          "def total_spent():\n    total = 0\n    for expense in expenses:",
          ["def total_spent():\n    for expense in expenses:\n        total = 0",
           "def total_spent():\n    total = 0\n    while expense in expenses:",
           "def total_spent():\n    total = 0\n    for expense in range(expenses):"],
          "total starts at 0 once, before the loop, and for visits every expense in the list (5.4).",
          ["total would be reset to 0 on every repeat, so it could never add up.",
           "while needs a condition that changes; expense doesn't exist yet, so this is a NameError.",
           "range() needs a number, not a list. Loop over the list itself."]),
    stage(6, "Add up and return",
          "Finish total_spent: add each expense's amount to total, and after the loop send the total back.",
          [12, 13, 14], "Which two lines finish the function?",
          '        total += expense["amount"]\n    return total',
          ['        total += expense["amount"]\n        return total', "        total += expense\n    return total",
           '        total += expense["amount"]\n    print(total)'],
          "Add only the amount inside the loop, then return once the loop has finished (4.3).",
          ["return is inside the loop, so the function ends after the first expense and returns 120.",
           "expense is a whole dictionary. You can't add a dictionary to a number, so this is a TypeError.",
           "print shows the total but returns None, so spent = total_spent() would be None."]),
    stage(7, "Totals per category",
          "Next, a function that works out how much was spent in each category. Start it with the right empty container for name-to-total pairs.",
          [15, 16, 17, 18], "Which line should start the totals?",
          "totals = {}", ["totals = []", "totals = 0", 'totals = ""'],
          "A dictionary maps each category name to its total, like {\"Food\": 370} (6.3).",
          ["A list has positions, not labels, so you couldn't look up totals[\"Food\"].",
           "One number can only hold one total, not one per category.",
           "A string can't store numbers under labels."]),
    stage(8, "Count with get()",
          "For each expense, add its amount to the running total for its category. The first time a category appears it has no total yet.",
          [19, 20, 21], "Which line updates the category total safely?",
          'totals[category] = totals.get(category, 0) + expense["amount"]',
          ['totals[category] = totals[category] + expense["amount"]', 'totals.get(category) + expense["amount"]',
           'totals[category] = expense["amount"]'],
          "get(category, 0) gives 0 for a new category, then the amount is added and stored (6.3 counting pattern).",
          ["The first time a category appears it isn't a key yet, so this is a KeyError.",
           "get() without a default returns None for a new category, and the result isn't stored.",
           "This overwrites instead of adding: Food would end up as 250, not 370."]),
    stage(9, "Record the expenses",
          "Time to use add_expense(). Lunch, Bus pass, Dinner and Movie each have a category. The notebook doesn't, so it should go into \"Other\".",
          [22, 23, 24, 25, 26, 27], "Which call adds the notebook using the default category?",
          'add_expense("Notebook", 80)', ['add_expense("Notebook")', 'add_expense(80, "Notebook")',
                                          'add_expense("Notebook", 80, category)'],
          "Leaving out the category makes Python use the default \"Other\" (4.4).",
          ["amount has no default, so leaving it out is a TypeError.",
           "Arguments are matched in order, so name would be 80 and amount \"Notebook\".",
           "category isn't a variable outside the function, so this is a NameError."]),
    stage(10, "Print the report",
          "Print a heading, then one neat line per expense: the name left-aligned in 10 spaces and the amount right-aligned in 4.",
          [28, 29, 30, 31], "Which line prints each expense neatly?",
          "    print(f\"{expense['name']:<10} Rs {expense['amount']:>4}\")",
          ["    print(f\"{expense['name']:>10} Rs {expense['amount']:<4}\")",
           "    print(\"{expense['name']:<10} Rs {expense['amount']:>4}\")",
           "    print(f\"{expense['name']:.10} Rs {expense['amount']:.4}\")"],
          ":<10 left-aligns the name in 10 spaces and :>4 right-aligns the amount (6.2).",
          ["The alignments are swapped: names would be pushed right and amounts left.",
           "Without the f, the braces are printed as plain text.",
           ":.10 cuts text to 10 characters, and :.4 on a whole number is an error. It doesn't align anything."]),
    stage(11, "Show category totals",
          "Print a second heading, then loop over the dictionary returned by totals_by_category(), taking each category and its amount.",
          [32, 33, 34, 35], "Which for line gives both the category and the amount?",
          "for category, amount in totals_by_category().items():",
          ["for category, amount in totals_by_category():", "for category, amount in totals_by_category.items():",
           "for category in totals_by_category().values():"],
          "Call the function with (), then .items() gives key-value pairs to unpack (6.4).",
          ["Looping over a dictionary gives only keys, so unpacking two values is a ValueError.",
           "The () are missing, so this asks the function itself for items(): an AttributeError.",
           "values() gives only the amounts, so the category names are lost."]),
    stage(12, "Total and big expenses",
          "Store the total, then build a list of the NAMES of expenses of 200 or more, and print both. join() turns the names into one line.",
          [36, 37, 38, 39], "Which line builds the list of big expense names?",
          'big = [e["name"] for e in expenses if e["amount"] >= 200]',
          ['big = [e["name"] for e in expenses if e >= 200]', 'big = [for e in expenses e["name"] if e["amount"] >= 200]',
           'big = [e for e in expenses if e["amount"] >= 200]'],
          "Value first, then for, then the filter: a simple list comprehension (5.5).",
          ["e is a dictionary, and you can't compare a dictionary with 200. This is a TypeError.",
           "The value must come before for, so this is a SyntaxError.",
           "This keeps whole dictionaries, so join() would fail because it needs strings."]),
    stage(13, "Check the budget",
          "Finally, compare what was spent with the budget and tell the user how it went.",
          [40, 41, 42, 43], "What should the program do here?",
          'If spent is more than budget, print "Over budget!", otherwise print how much is left',
          ["Use break to stop the program when spent is more than budget",
           "Set spent back to 0 so the budget is never exceeded", "Print the budget without comparing it to spent"],
          "An if/else picks one of two messages (2.2). 2000 - 950 leaves Rs 1050.",
          ["break only works inside a loop, and stopping wouldn't tell the user anything.",
           "That throws away the real total and hides the problem.",
           "The user wants to know how spending compares with the budget, not just the budget."],
          code_options=False),
]

level7 = {
  "id": "l7", "number": 7, "title": "Final Project",
  "description": "Put everything together and build a Simple Expense Tracker, one line at a time.",
  "subLevels": [{
    "id": "l7s1", "code": "7.1", "title": "Simple Expense Tracker",
    "summary": "A guided build of a real program that uses everything from Levels 1-6.",
    "estimatedMinutes": 20, "type": "project", "steps": ["project"],
    "briefing": {
      "title": "Build a Simple Expense Tracker",
      "text": [
        "You'll build a program that records what you spend, adds it up, groups it by category and checks it against a budget.",
        "You build it one piece at a time. At each step you choose the right line of code, and it appears in the program.",
        "This program does not use input(). To track your own spending, change the add_expense(...) lines and run it again in the Practical tab.",
      ],
      "why": [
        "Knowing where your money goes is the first step to saving",
        "Real apps such as banking and shopping apps do the same: store records, total them, group them",
        "It combines functions, lists, dictionaries, loops and f-strings in one useful program",
      ],
      "concepts": [
        {"subLevelId": "l1s2", "label": "Variables"},
        {"subLevelId": "l2s2", "label": "if and else"},
        {"subLevelId": "l4s3", "label": "Return values"},
        {"subLevelId": "l4s4", "label": "Default arguments"},
        {"subLevelId": "l5s3", "label": "append()"},
        {"subLevelId": "l5s4", "label": "Looping over lists"},
        {"subLevelId": "l5s5", "label": "List comprehensions"},
        {"subLevelId": "l6s1", "label": "join()"},
        {"subLevelId": "l6s2", "label": "f-string alignment"},
        {"subLevelId": "l6s3", "label": "Dictionaries and get()"},
        {"subLevelId": "l6s4", "label": "items()"},
      ],
      "sampleOutput": PROJECT_OUTPUT,
      "usesInput": False,
      "fileName": "expense_tracker.py",
    },
    "finalCode": PROJECT_CODE,
    "stages": build_stages("l7s1", project_stages),
    "walkthrough": [
      "Lines 2-3 create the budget and an empty list. Each expense will be a dictionary inside that list.",
      "add_expense() builds a dictionary from its parameters and appends it. category defaults to \"Other\".",
      "total_spent() starts at 0, adds every amount in a loop and returns the result.",
      "totals_by_category() uses get(category, 0) so a new category starts at 0, then adds to it.",
      "The five add_expense(...) calls fill the list. Notebook has no category, so it uses \"Other\".",
      "The report loops over the list and uses :<10 and :>4 to line the columns up.",
      "items() gives each category with its total. The list comprehension picks the names of expenses of 200 or more.",
      "Finally if/else compares spent with budget: 950 is under 2000, so Rs 1050 is left.",
    ],
  }],
}

LEVELS = [level1, level2, level3, level4, level5, level6, level7]
for _lvl in LEVELS[2:]:
    _lvl["available"] = True

# ─────────── Batch 1: hints + option feedback for the existing MCQs, and Levels 8-11 ───────────
# Existing questions only GAIN two fields (hint, optionFeedback); nothing else about them changes.
import mcq_feedback  # noqa: E402
import advanced_levels  # noqa: E402

EXISTING_LEVEL_COUNT = len(LEVELS)  # Levels 1-7 as they were before Batch 1
_unused_feedback = dict(mcq_feedback.FEEDBACK)
for _lvl in LEVELS:
    for _s in _lvl["subLevels"]:
        for _q in _s.get("quiz", {}).get("questions", []):
            _f = _unused_feedback.pop(_q["id"], None)
            assert _f is not None, f"{_q['id']}: no entry in content/mcq_feedback.py"
            _q["hint"] = _f["hint"]
            _q["optionFeedback"] = list(_f["optionFeedback"])
assert not _unused_feedback, f"feedback for unknown questions: {sorted(_unused_feedback)}"

_helpers = dict(code=code, text=text, bullets=bullets, tip=tip, mistake=mistake, key=key, page=page, m=m, lesson=lesson)
ADVANCED_LEVELS = advanced_levels.make_levels(_helpers, first_number=EXISTING_LEVEL_COUNT + 1)
LEVELS += ADVANCED_LEVELS

# ─────────── Batch 3 Part 2: "predict", "fill" and "order" questions ───────────
# APPENDED at the end of existing quizzes with the next free ids; existing questions are untouched.
# Questions without a "type" are MCQs (the app's default), so nothing else in the JSON changes.
import new_question_types  # noqa: E402

NEW_TYPES = ("predict", "fill", "order")
_by_sub = {_s["id"]: _s for _lvl in LEVELS for _s in _lvl["subLevels"]}
for _sid, _new in new_question_types.NEW_QUESTIONS.items():
    _qs = _by_sub[_sid]["quiz"]["questions"]
    for _nq in _new:
        _qs.append({"id": f"{_sid}q{len(_qs) + 1}", **_nq})

# ─────────── v0.4.0 extras: level projects, cheat sheets, glossary ───────────
# Added as new fields; the existing Level 1-2 lessons and questions are not changed.
BY_LEVEL = {lvl["id"]: lvl for lvl in LEVELS}
for _p in course_extras.make_projects(stage):
    _lvl = BY_LEVEL[_p["level"]]
    _pid = f"{_lvl['id']}p1"
    _briefing = {
        "title": _p["briefing_title"], "text": _p["text"], "why": _p["why"],
        "concepts": [{"subLevelId": sid, "label": label} for sid, label in _p["concepts"]],
        "sampleOutput": _p["output"], "usesInput": _p["inputs"] is not None,
        "fileName": _p["file"],
    }
    if _p["inputs"] is not None:
        _briefing["sampleInputs"] = _p["inputs"]
    _lvl["projects"] = [{
        "id": _pid, "code": f"{_lvl['number']}.P", "title": _p["title"], "summary": _p["summary"],
        "estimatedMinutes": _p["minutes"], "type": "project", "steps": ["project"],
        "briefing": _briefing, "finalCode": _p["code"], "stages": build_stages(_pid, _p["stages"]),
        "walkthrough": _p["walkthrough"],
    }]
for _lid, _sheet in list(course_extras.CHEAT_SHEETS.items()) + list(advanced_levels.CHEAT_SHEETS.items()):
    BY_LEVEL[_lid]["cheatSheet"] = _sheet
GLOSSARY = course_extras.GLOSSARY + advanced_levels.GLOSSARY


def level_number_of(sub_level_id):
    """'l3s2' -> 3, 'l10s1' -> 10 (Batch 1: level numbers can have two digits)."""
    mt = re.fullmatch(r"l(\d+)(?:s|p)\d+", sub_level_id)
    assert mt, f"bad sub-level id {sub_level_id}"
    return int(mt.group(1))

course = {
  "id": "python-beginner", "title": "Python for Beginners", "schemaVersion": 2,
  "levelFiles": [f"levels/{lvl['id']}.json" for lvl in LEVELS],
  "glossaryFile": "glossary.json",
}

# ───────────────────────────── VALIDATION ─────────────────────────────

class _Inputs:
    """Fake keyboard for examples that call input(): echoes the prompt and the typed value."""
    def __init__(self, values, out): self.values, self.out = list(values), out
    def __call__(self, prompt=""):
        value = self.values.pop(0)
        self.out.write(f"{prompt}{value}\n")
        return value

def run_python(src, inputs=None):
    """Run src like a fresh main.py. Returns (stdout, exception or None).

    Batch 1: every run happens inside a new, empty temporary folder (like the app's Practical
    scratch folder), so file examples start with no files and never touch the repository."""
    out = io.StringIO()
    env = {"__name__": "__main__"}
    if inputs is not None: env["input"] = _Inputs(inputs, out)
    err = None
    here = os.getcwd()
    with tempfile.TemporaryDirectory() as scratch, contextlib.redirect_stdout(out):
        os.chdir(scratch)
        try:
            exec(compile(src, "main.py", "exec"), env)
        except Exception as e:  # noqa: BLE001 - examples deliberately raise errors
            err = e
        finally:
            os.chdir(here)
    return out.getvalue(), err


# ── Batch 1: run Level 8+ examples in the app's real Practical sandbox (read-only use of pypath_runner.py) ──
# pypath_runner.py is NOT modified; it is only imported so the outputs shown in the advanced lessons
# are exactly what a learner sees in the Practical tab (same tracebacks, same file rules). It is used
# only on Python 3.11 (the version the app bundles). Without it, validation falls back to run_python.
def _load_sandbox():
    if sys.version_info[:2] != (3, 11):
        return None
    here = pathlib.Path(__file__).resolve().parent
    candidates = [os.environ.get("PYPATH_RUNNER", ""), str(here.parent / "app/src/main/python/pypath_runner.py")]
    for c in candidates:
        if c and pathlib.Path(c).is_file():
            import importlib.util
            spec = importlib.util.spec_from_file_location("pypath_runner_readonly", c)
            mod = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(mod)
            return mod
    return None

SANDBOX = _load_sandbox()

def sandbox_run(src, inputs=None):
    """Runs src through pypath_runner.run() in a fresh scratch folder.
    Returns (stdout with typed input echoed like the course shows it, stderr, status)."""
    import struct, threading
    out_r, out_w = os.pipe()
    in_r, in_w = os.pipe()
    values = list(inputs or [])
    os.write(in_w, "".join(v + "\n" for v in values).encode())
    os.close(in_w)
    raw = []
    reader = threading.Thread(target=lambda: raw.extend(iter(lambda: os.read(out_r, 65536), b"")))
    reader.start()
    try:
        with tempfile.TemporaryDirectory() as scratch:
            status = SANDBOX.run(src, out_w, in_r, scratch)
    finally:
        os.close(out_w)
        reader.join()
        os.close(out_r)
        os.close(in_r)
    buf, i, stdout, stderr = b"".join(raw), 0, [], []
    while i < len(buf):
        kind, size = buf[i:i + 1], struct.unpack(">I", buf[i + 1:i + 5])[0]
        payload = buf[i + 5:i + 5 + size].decode("utf-8")
        i += 5 + size
        if kind == b"O": stdout.append(payload)
        elif kind == b"E": stderr.append(payload)
        elif kind == b"I": stdout.append(values.pop(0) + "\n")  # the learner's typed answer
    return "".join(stdout), "".join(stderr), status

def run_like_app(src, inputs=None):
    """(stdout, full error text or "", last error line or "") using the sandbox when available."""
    if SANDBOX is not None:
        out, err, _ = sandbox_run(src, inputs)
        lines = [ln for ln in err.strip("\n").split("\n") if ln.strip()]
        return out, err, (lines[-1] if lines else "")
    out, e = run_python(src, inputs)
    last = "" if e is None else (f"{type(e).__name__}: {e}" if str(e) else type(e).__name__)
    return out, last, last

def check_advanced_code_block(where, b):
    expected = b.get("output")
    if expected is None: return
    out, full, last = run_like_app(b["code"], b.get("_inputs"))
    if b.get("_full_error") and SANDBOX is None:
        # Without the sandbox the traceback layout can't be reproduced; check the printed part and last line.
        exp = norm(expected).split("\n")
        assert exp[-1] == last, f"{where}: error line {last!r}, shown {exp[-1]!r}"
        assert norm(expected).startswith(norm(out)), f"{where}: output mismatch\n{out}"
        return
    got = out + (full if b.get("_full_error") else last)
    if b.get("_full_error"):
        # The ^/~ marker lines Python 3.11 adds depend on details like a final new line in the editor,
        # so lessons leave them out (and say so); compare everything else exactly.
        got = "\n".join(ln for ln in got.split("\n") if not _is_marker_line(ln))
        assert not any(_is_marker_line(ln) for ln in expected.split("\n")), f"{where}: leave out ^ marker lines"
    assert norm(got) == norm(expected), f"{where}: output mismatch\n--- expected\n{expected}\n--- got\n{got}"

def _is_marker_line(line):
    return bool(line.strip()) and set(line.strip()) <= {"^", "~"}

def check_advanced_question(where, qd):
    chk = qd.get("_check")
    if not chk: return
    right = qd["options"][qd["correctIndex"]]
    out, full, last = run_like_app(qd["code"])
    if chk == "out":
        assert not last, f"{where}: unexpected {last}"
        assert norm(out) == norm(right), f"{where}: prints {out!r}, correct option is {right!r}"
    elif chk.startswith("err:"):
        assert last.split(":")[0] == chk[4:], f"{where}: expected {chk[4:]}, got {last!r}"
    else:
        raise AssertionError(f"{where}: unknown check {chk}")

def norm(s): return "\n".join(line.rstrip() for line in s.strip("\n").split("\n"))

def check_code_block(where, b):
    src, expected = b["code"], b.get("output")
    if expected is None: return
    if "input(" in src and "_inputs" not in b:
        return  # Level 1-2 legacy examples with typed input: shown as a sample session.
    out, err = run_python(src, b.get("_inputs"))
    got = out + (f"{type(err).__name__}: {err}" if err else "")
    assert norm(got) == norm(expected), f"{where}: output mismatch\n--- expected\n{expected}\n--- got\n{got}"

def check_question(where, qd):
    chk = qd.get("_check")
    if not chk: return
    right = qd["options"][qd["correctIndex"]]
    out, err = run_python(qd["code"])
    if chk == "out":
        assert err is None, f"{where}: unexpected {err!r}"
        assert norm(out) == norm(right), f"{where}: prints {out!r}, correct option is {right!r}"
    elif chk.startswith("err:"):
        assert type(err).__name__ == chk[4:], f"{where}: expected {chk[4:]}, got {err!r}"
    else:
        raise AssertionError(f"{where}: unknown check {chk}")

def no_repeating_pattern(seq):
    triples = [tuple(seq[i:i + 3]) for i in range(len(seq) - 2)]
    return len(triples) == len(set(triples))

def check_project(proj, unique, by_id, level_number, stage_range):
    """Shared rules for every guided project (final and bonus)."""
    pid = proj["id"]
    final, stages = proj["finalCode"], proj["stages"]
    lo, hi = stage_range
    assert lo <= len(stages) <= hi, f"{pid}: {len(stages)} stages"
    rebuilt = [None] * len(final)
    for st in stages:
        unique(st["id"]); unique(st["question"]["id"])
        qd = st["question"]
        assert len(qd["options"]) == 4 and len(set(qd["options"])) == 4 and 0 <= qd["correctIndex"] <= 3, st["id"]
        assert len(st["optionFeedback"]) == 4, st["id"]
        assert st["revealsLines"], st["id"]
        for n in st["revealsLines"]:
            assert 1 <= n <= len(final), f"{st['id']} line {n}"
            assert rebuilt[n - 1] is None, f"{pid}: line {n} revealed twice"
            rebuilt[n - 1] = final[n - 1]
        # The correct option for code stages must match the revealed code (ignoring indentation).
        if st["optionsAreCode"]:
            right = [ln.strip() for ln in qd["options"][qd["correctIndex"]].split("\n")]
            revealed = [final[n - 1].strip() for n in st["revealsLines"]]
            assert all(r in revealed for r in right), f"{st['id']}: correct option not in revealed lines"
    assert rebuilt == final, f"{pid}: revealing every stage must rebuild finalCode exactly"
    b = proj["briefing"]
    inputs = b.get("sampleInputs")
    out, err = run_python("\n".join(final) + "\n", inputs)
    assert err is None, f"{pid}: {err!r}"
    assert norm(out) == norm(b["sampleOutput"]), f"{pid} output mismatch:\n{out}"
    assert ("input(" in "\n".join(final)) == b["usesInput"], pid
    assert b["usesInput"] == (inputs is not None), pid
    assert b.get("fileName", "").endswith(".py"), pid
    for c in b["concepts"]:
        assert c["subLevelId"] in by_id, c
        assert level_number_of(c["subLevelId"]) <= level_number, f"{pid} uses {c['subLevelId']} from a later level"
    pos = [st["question"]["correctIndex"] for st in stages]
    assert max(pos.count(k) for k in range(4)) - min(pos.count(k) for k in range(4)) <= 1, f"{pid} {pos}"
    assert all(not (pos[k] == pos[k - 1] == pos[k - 2]) for k in range(2, len(pos))), f"{pid} {pos}"


def validate():
    ids = set()
    def unique(i):
        assert i not in ids, f"duplicate id {i}"
        ids.add(i)

    sub_counts = []
    new_pages, new_mcqs = [], []
    by_id = {}
    hints_seen = {}
    for lvl_index, lvl in enumerate(LEVELS):
        advanced = lvl_index >= EXISTING_LEVEL_COUNT
        unique(lvl["id"])
        assert lvl.get("available", True), f"{lvl['id']} must be available"
        sub_counts.append(len(lvl["subLevels"]))
        for i, s in enumerate(lvl["subLevels"]):
            unique(s["id"])
            assert s["id"] == f"{lvl['id']}s{i + 1}", f"bad sub-level id {s['id']}"
            by_id[s["id"]] = s
            if s.get("type") == "project":
                continue
            pages, qs = s["lesson"]["pages"], s["quiz"]["questions"]
            for p_i, p in enumerate(pages):
                assert p["title"].strip(), f"{s['id']} page {p_i + 1} has no title"
                for b in p["blocks"]:
                    if b["type"] == "code":
                        if advanced: check_advanced_code_block(f"{s['id']} page {p_i + 1}", b)
                        else: check_code_block(f"{s['id']} page {p_i + 1}", b)
            assert qs, f"{s['id']} has no MCQs"
            for q_i, qd in enumerate(qs):
                unique(qd["id"])
                assert qd["id"] == f"{s['id']}q{q_i + 1}", f"bad question id {qd['id']}"
                if qd.get("type", "mcq") != "mcq":
                    check_new_type_question(qd, hints_seen)  # Batch 3 Part 2
                    continue
                assert len(qd["options"]) == 4, f"{qd['id']} needs 4 options"
                assert len(set(qd["options"])) == 4, f"{qd['id']} has duplicate options"
                assert 0 <= qd["correctIndex"] <= 3, f"{qd['id']} correctIndex"
                assert qd["explanation"].strip(), f"{qd['id']} explanation"
                check_hint_and_feedback(qd, hints_seen)
                if advanced: check_advanced_question(qd["id"], qd)
                else: check_question(qd["id"], qd)
            # Batch 3 Part 2: new-type questions only ever come after all MCQs of a quiz, and the
            # authoring rules below (counts, answer positions) keep applying to the MCQs only.
            types = [qd.get("type", "mcq") for qd in qs]
            assert types == sorted(types, key=lambda t: t != "mcq"), f"{s['id']}: new-type questions must be appended after the MCQs"
            qs = [qd for qd in qs if qd.get("type", "mcq") == "mcq"]
            if lvl["number"] >= 3:
                new_pages.append(len(pages)); new_mcqs.append(len(qs))
                assert 2 <= len(pages) <= 6, f"{s['id']}: {len(pages)} pages"
                assert 3 <= len(qs) <= 8, f"{s['id']}: {len(qs)} MCQs"
                kinds = {b["type"] for p in pages for b in p["blocks"]}
                assert {"text", "code"} <= kinds, f"{s['id']} needs text and code"
                assert any(b["type"] == "code" and "output" in b for p in pages for b in p["blocks"]), s["id"]
                assert any(b["type"] == "tip" and b["title"] == "Common mistake" for p in pages for b in p["blocks"]), \
                    f"{s['id']} needs a Common mistake tip"
                if advanced:
                    # Batch 1: every advanced sub-level uses every lesson block type.
                    assert kinds == {"text", "bullets", "code", "tip", "keypoint"}, f"{s['id']} block types {kinds}"
                pos = [qd["correctIndex"] for qd in qs]
                counts = [pos.count(k) for k in range(4)]
                assert max(counts) - min(counts) <= 1, f"{s['id']} answer positions unbalanced {pos}"
                assert all(not (pos[k] == pos[k - 1] == pos[k - 2]) for k in range(2, len(pos))), f"{s['id']} {pos}"

    # Every block type is used somewhere in Levels 3-6.
    used = {b["type"] for lvl in LEVELS[2:6] for s in lvl["subLevels"] for p in s["lesson"]["pages"] for b in p["blocks"]}
    assert used == {"text", "bullets", "code", "tip", "keypoint"}, used

    # Variety rules.
    assert sub_counts[:7] == [4, 3, 4, 5, 5, 4, 1], sub_counts
    assert len(set(sub_counts)) > 1
    # Batch 1: the advanced levels all have different sub-level counts, and the first one differs from Level 7.
    adv_counts = sub_counts[EXISTING_LEVEL_COUNT - 1:]
    assert all(a != b for a, b in zip(adv_counts, adv_counts[1:])), adv_counts
    assert len(set(sub_counts[EXISTING_LEVEL_COUNT:])) == len(ADVANCED_LEVELS), adv_counts
    assert len(set(new_pages)) > 1 and len(set(new_mcqs)) > 1, "per-sub-level counts must not all be equal"
    for seq, name in ((new_pages, "pages"), (new_mcqs, "MCQs")):
        assert all(a != b for a, b in zip(seq, seq[1:])), f"two consecutive sub-levels have the same {name}: {seq}"
        assert no_repeating_pattern(seq), f"repeating pattern in {name}: {seq}"
    # Harder topics get more questions than the average.
    avg = sum(new_mcqs) / len(new_mcqs)
    for hard in ("l3s4", "l4s5", "l5s5", "l6s3") + advanced_levels.HARD_SUB_LEVELS:
        assert len([qd for qd in by_id[hard]["quiz"]["questions"] if qd.get("type", "mcq") == "mcq"]) > avg, hard

    check_new_type_spread()

    # Practical tab contract: these sub-levels must exist and keep their meaning.
    for sid in ("l1s1", "l1s2", "l1s3", "l1s4", "l2s1", "l2s2", "l2s3", "l3s1", "l3s2", "l3s4", "l5s3"):
        assert sid in by_id, sid
    assert by_id["l3s1"]["title"] == "for loops"
    assert by_id["l3s2"]["title"] == "while loops"
    assert by_id["l5s3"]["title"] == "List methods"

    # Final project (required) and the bonus level projects.
    proj = by_id["l7s1"]
    assert proj["type"] == "project" and proj["steps"] == ["project"]
    check_project(proj, unique, by_id, level_number=7, stage_range=(8, 14))
    for lvl in LEVELS[:6]:
        assert len(lvl.get("projects", [])) == 1, f"{lvl['id']} needs one level project"
        for bp in lvl["projects"]:
            unique(bp["id"])
            assert bp["id"] == f"{lvl['id']}p1" and bp["type"] == "project" and bp["steps"] == ["project"], bp["id"]
            check_project(bp, unique, by_id, level_number=lvl["number"], stage_range=(6, 10))
    assert not LEVELS[6].get("projects"), "Level 7 is itself the final project"

    # Batch 1: advanced levels come after the final project, are numbered on, marked and available.
    for i, lvl in enumerate(ADVANCED_LEVELS):
        n = EXISTING_LEVEL_COUNT + 1 + i
        assert lvl["id"] == f"l{n}" and lvl["number"] == n, lvl["id"]
        assert lvl["title"].endswith("(Advanced)") and lvl.get("advanced") is True, lvl["id"]
        assert lvl["available"] is True and not lvl.get("projects"), lvl["id"]
    for lvl in LEVELS[:EXISTING_LEVEL_COUNT]:
        assert "advanced" not in lvl, f"{lvl['id']} is a core level"

    # Cheat sheets: one per level, every snippet compiles, shown outputs are real.
    for lvl in LEVELS:
        sheet = lvl.get("cheatSheet")
        assert sheet and sheet["title"].strip() and sheet["sections"], f"{lvl['id']} needs a cheat sheet"
        for sec in sheet["sections"]:
            assert sec["heading"].strip() and sec["items"], f"{lvl['id']} empty section"
            for it in sec["items"]:
                where = f"{lvl['id']} cheat sheet / {sec['heading']}"
                compile(it["code"], where, "exec")
                assert it["note"].strip(), where
                if "output" in it:
                    out, err = run_python(it["code"])
                    assert err is None, f"{where}: {err!r}"
                    assert norm(out) == norm(it["output"]), f"{where}: prints {out!r}"

    # Glossary: unique terms, linked to a real sub-level, examples compile and outputs are real.
    names = set()
    for t in GLOSSARY:
        key_ = t["term"].lower()
        assert key_ not in names, f"duplicate glossary term {t['term']}"
        names.add(key_)
        assert t["subLevelId"] in by_id, f"glossary {t['term']}: unknown sub-level {t['subLevelId']}"
        assert t["definition"].strip().endswith((".", ")")), f"glossary {t['term']}: definition"
        if "example" in t:
            compile(t["example"], t["term"], "exec")
            if "output" in t:
                out, err = run_python(t["example"])
                assert err is None and norm(out) == norm(t["output"]), f"glossary {t['term']}: prints {out!r}"
    assert len(GLOSSARY) >= 50, len(GLOSSARY)
    for lvl in LEVELS:
        assert any(t["subLevelId"].startswith(lvl["id"] + "s") for t in GLOSSARY), f"no glossary terms for {lvl['id']}"

    check_existing_unchanged()
    return new_pages, new_mcqs


# ── Batch 1 checks ──

HINT_WORDS = (6, 20)

def check_hint_and_feedback(qd, hints_seen):
    qid, right = qd["id"], qd["options"][qd["correctIndex"]]
    hint = qd.get("hint")
    assert isinstance(hint, str) and hint.strip(), f"{qid}: needs a hint"
    words = len(hint.split())
    assert HINT_WORDS[0] <= words <= HINT_WORDS[1], f"{qid}: hint has {words} words"
    assert hint.rstrip().endswith((".", "?")) and hint.count(". ") == 0, f"{qid}: hint must be one sentence"
    assert right.strip() not in hint, f"{qid}: hint contains the correct option {right!r}"
    assert hint not in hints_seen, f"{qid}: same hint as {hints_seen.get(hint)}"
    hints_seen[hint] = qid
    fb = qd.get("optionFeedback")
    assert isinstance(fb, list) and len(fb) == 4 and all(isinstance(f, str) and f.strip() for f in fb), \
        f"{qid}: optionFeedback needs exactly 4 non-empty strings"
    marked = [i for i, f in enumerate(fb) if f.startswith("Right:")]
    assert marked == [qd["correctIndex"]], f"{qid}: feedback for the correct option (and only it) starts with 'Right:' {marked}"


def _strip_batch1(level):
    """A level as it was before Batch 1: quiz questions without hint / optionFeedback, and without the
    Batch 3 Part 2 questions appended after them (types predict / fill / order)."""
    lvl = json.loads(json.dumps(strip_private(level)))
    for s in lvl["subLevels"]:
        quiz = s.get("quiz")
        if not quiz: continue
        quiz["questions"] = [qd for qd in quiz.get("questions", []) if qd.get("type", "mcq") == "mcq"]
        for qd in quiz["questions"]:
            qd.pop("hint", None); qd.pop("optionFeedback", None)
    return lvl

def _canon(x): return json.dumps(x, sort_keys=True, ensure_ascii=False, separators=(",", ":"))

def check_existing_unchanged():
    """Existing ids, titles, lessons, questions, options, correctIndex and explanations are unchanged."""
    here = pathlib.Path(__file__).resolve().parent
    base = json.loads((here / "existing_course_baseline.json").read_text(encoding="utf-8"))
    by_level = {lvl["id"]: lvl for lvl in LEVELS}
    assert [lvl["id"] for lvl in LEVELS[:EXISTING_LEVEL_COUNT]] == list(base["levels"]), "existing level order changed"
    found = {}
    for lid, info in base["levels"].items():
        lvl = _strip_batch1(by_level[lid])
        assert lvl["number"] == info["number"] and lvl["title"] == info["title"], f"{lid} renamed"
        for s in lvl["subLevels"]:
            for qd in s.get("quiz", {}).get("questions", []):
                found[qd["id"]] = {k: qd[k] for k in ("prompt", "code", "options", "correctIndex", "explanation") if k in qd}
        assert [s["id"] for s in lvl["subLevels"]] == [k for k, v in base["subLevels"].items() if v["level"] == lid], \
            f"{lid}: sub-levels renumbered or reordered"
        for s in lvl["subLevels"]:
            assert s["title"] == base["subLevels"][s["id"]]["title"] and s["code"] == base["subLevels"][s["id"]]["code"], s["id"]
    for qid, old in base["questions"].items():
        assert found.get(qid) == old, f"{qid}: existing question changed"
    assert len(found) == len(base["questions"]), "existing questions added or removed"
    for lid, info in base["levels"].items():
        digest = hashlib.sha256(_canon(_strip_batch1(by_level[lid])).encode()).hexdigest()
        assert digest == info["sha256"], f"{lid}: existing lesson content changed (only hint/optionFeedback may be added)"
    # Also compare with the course JSON committed in git, when git history is available.
    project = here.parent
    for lid in base["levels"]:
        rel = f"app/src/main/assets/course/levels/{lid}.json"
        try:
            old_text = subprocess.run(["git", "show", f"HEAD:./{rel}"], cwd=project, capture_output=True,
                                      text=True, check=True, timeout=30).stdout
        except (OSError, subprocess.SubprocessError):
            print(f"note: no git copy of {rel}; checked against content/existing_course_baseline.json only")
            continue
        assert _canon(_strip_batch1(json.loads(old_text))) == _canon(_strip_batch1(by_level[lid])), \
            f"{lid}: differs from the committed {rel} (beyond hint/optionFeedback)"


# ── Batch 3 Part 2 checks: "predict", "fill" and "order" questions ──

NEW_TYPE_MINIMUM = {"predict": 12, "fill": 12, "order": 10}
NEW_TYPE_KEYS = {
    "predict": {"id", "type", "prompt", "code", "answer", "alternatives", "caseSensitive", "explanation", "hint"},
    "fill": {"id", "type", "prompt", "code", "blanks", "distractors", "output", "explanation", "hint"},
    "order": {"id", "type", "prompt", "lines", "alternativeOrders", "output", "explanation", "hint"},
}
_MARKER = re.compile(r"\{\{(\d+)\}\}")

def normalize_output(text, case_sensitive=True):
    """Same rule as the app (QuestionCheck.normalizeOutput): lines trimmed, inner runs of spaces/tabs
    collapsed, blank lines at both ends dropped, optional case folding."""
    lines = [re.sub(r"[ \t]+", " ", ln.strip()) for ln in text.replace("\r\n", "\n").replace("\r", "\n").split("\n")]
    while lines and not lines[0]: lines.pop(0)
    while lines and not lines[-1]: lines.pop()
    joined = "\n".join(lines)
    return joined if case_sensitive else joined.lower()

def run_subprocess(src):
    """Runs a snippet in a separate Python process (the validator's own interpreter, 3.11 in CI)."""
    with tempfile.TemporaryDirectory() as scratch:
        p = subprocess.run([sys.executable, "-I", "-c", src], cwd=scratch, capture_output=True, text=True, timeout=20)
    return p.stdout, p.returncode, p.stderr

def _check_new_hint(qd, hints_seen, secrets):
    qid, hint = qd["id"], qd.get("hint")
    assert isinstance(hint, str) and hint.strip(), f"{qid}: needs a hint"
    words = len(hint.split())
    assert HINT_WORDS[0] <= words <= HINT_WORDS[1], f"{qid}: hint has {words} words"
    assert hint.rstrip().endswith((".", "?")) and hint.count(". ") == 0, f"{qid}: hint must be one sentence"
    for secret in secrets:
        secret = secret.strip()
        if len(secret) >= 2:
            assert not re.search(r"(?<![\w])" + re.escape(secret) + r"(?![\w])", hint), f"{qid}: hint gives away {secret!r}"
    assert hint not in hints_seen, f"{qid}: same hint as {hints_seen.get(hint)}"
    hints_seen[hint] = qid
    assert qd["explanation"].strip() and qd["prompt"].strip(), f"{qid}: prompt and explanation"
    assert "optionFeedback" not in qd and "options" not in qd and "correctIndex" not in qd, f"{qid}: MCQ fields on a {qd['type']} question"

def fill_code(code, tokens):
    return _MARKER.sub(lambda m: tokens[int(m.group(1)) - 1], code)

def check_new_type_question(qd, hints_seen):
    qid, kind = qd["id"], qd.get("type")
    assert kind in NEW_TYPES, f"{qid}: unknown type {kind!r}"
    extra = set(qd) - NEW_TYPE_KEYS[kind]
    assert not extra, f"{qid}: unexpected keys {sorted(extra)}"
    if kind == "predict":
        code_, answer = qd["code"], qd["answer"]
        assert isinstance(code_, str) and code_.strip() and isinstance(answer, str) and answer.strip(), qid
        assert "input(" not in code_, f"{qid}: predict snippets cannot read input"
        alts = qd.get("alternatives", [])
        assert isinstance(alts, list) and all(isinstance(a, str) and a.strip() for a in alts), qid
        cs = qd.get("caseSensitive", True)
        assert isinstance(cs, bool), qid
        out, code_rc, err = run_subprocess(code_)
        assert code_rc == 0, f"{qid}: snippet failed\n{err}"
        assert out.rstrip("\n") == answer, f"{qid}: prints {out!r}, answer is {answer!r}"
        for a in alts:
            assert normalize_output(a, cs) != normalize_output(answer, cs), f"{qid}: alternative equals the answer"
        _check_new_hint(qd, hints_seen, [answer] + alts if "\n" not in answer else alts)
    elif kind == "fill":
        code_, blanks, distractors, expected = qd["code"], qd["blanks"], qd["distractors"], qd["output"]
        assert 1 <= len(blanks) <= 3, f"{qid}: needs 1 to 3 blanks"
        assert 1 <= len(distractors) <= 2, f"{qid}: needs 1 or 2 distractors"
        bank = blanks + distractors
        assert all(isinstance(t, str) and t.strip() and t == t.strip() for t in bank), f"{qid}: empty token"
        assert len(set(bank)) == len(bank), f"{qid}: bank tokens must be distinct for a unique solution"
        markers = [int(m.group(1)) for m in _MARKER.finditer(code_)]
        assert markers == list(range(1, len(blanks) + 1)), f"{qid}: markers must be {{{{1}}}}..{{{{{len(blanks)}}}}} once each, in order"
        assert "input(" not in code_ and "while" not in code_, f"{qid}: no input() or while loops in fill code"
        right = fill_code(code_, blanks)
        out, err = run_python(right)
        assert err is None and out.rstrip("\n") == expected, f"{qid}: solution prints {out!r} ({err!r}), expected {expected!r}"
        # Unique valid solution: every other way of filling the blanks from the bank fails or prints something else.
        import itertools
        for combo in itertools.permutations(bank, len(blanks)):
            if list(combo) == blanks: continue
            got, e = run_python(fill_code(code_, list(combo)))
            assert e is not None or normalize_output(got) != normalize_output(expected), \
                f"{qid}: {list(combo)} is also a valid solution"
        # The hint must not contain a completed line.
        _check_new_hint(qd, hints_seen, [fill_code(raw, blanks).strip() for raw in code_.split("\n") if _MARKER.search(raw)])
    else:
        lines, expected = qd["lines"], qd["output"]
        assert 4 <= len(lines) <= 7, f"{qid}: needs 4 to 7 lines"
        assert len(set(lines)) == len(lines), f"{qid}: lines must be distinct"
        assert all(ln.strip() and ln == ln.rstrip() for ln in lines), f"{qid}: empty line or trailing spaces"
        assert all((len(ln) - len(ln.lstrip(" "))) % 4 == 0 and "\t" not in ln for ln in lines), f"{qid}: indent with 4 spaces"
        src = "\n".join(lines)
        assert "input(" not in src and "while" not in src, f"{qid}: no input() or while loops in order questions"
        alts = qd.get("alternativeOrders", [])
        n = len(lines)
        for a in alts:
            assert sorted(a) == list(range(n)) and a != list(range(n)), f"{qid}: bad alternative order {a}"
        out, err = run_python(src + "\n")
        assert err is None and out.rstrip("\n") == expected, f"{qid}: prints {out!r} ({err!r}), expected {expected!r}"
        import itertools
        valid = []
        for perm in itertools.permutations(range(n)):
            got, e = run_python("\n".join(lines[i] for i in perm) + "\n")
            if e is None and normalize_output(got) == normalize_output(expected):
                valid.append(list(perm))
        allowed = [list(range(n))] + [list(a) for a in alts]
        assert sorted(valid) == sorted(allowed), f"{qid}: valid orders {valid} but allowed {allowed}"
        _check_new_hint(qd, hints_seen, [ln.strip() for ln in lines if len(ln.strip()) > 6])

def check_new_type_spread():
    """At least 12 predict, 12 fill and 10 order; at most 2 new ones per sub-level; 20 to 25 sub-levels."""
    per_sub, per_type = {}, {t: 0 for t in NEW_TYPES}
    for lvl in LEVELS:
        for s in lvl["subLevels"]:
            for qd in s.get("quiz", {}).get("questions", []):
                t = qd.get("type", "mcq")
                if t == "mcq": continue
                per_type[t] += 1
                per_sub[s["id"]] = per_sub.get(s["id"], 0) + 1
    for t, minimum in NEW_TYPE_MINIMUM.items():
        assert per_type[t] >= minimum, f"only {per_type[t]} {t} questions (need {minimum})"
    assert all(v <= 2 for v in per_sub.values()), f"more than 2 new questions in a sub-level: {per_sub}"
    assert 20 <= len(per_sub) <= 25, f"new questions spread over {len(per_sub)} sub-levels"
    return per_type, per_sub

def strip_private(x):
    if isinstance(x, dict): return {k: strip_private(v) for k, v in x.items() if not k.startswith("_")}
    if isinstance(x, list): return [strip_private(v) for v in x]
    return x

def render():
    """{relative path: file text} for every generated file."""
    files = {}
    for lvl in LEVELS:
        files[f"levels/{lvl['id']}.json"] = json.dumps(strip_private(lvl), indent=2, ensure_ascii=False) + "\n"
    files["course.json"] = json.dumps(course, indent=2, ensure_ascii=False) + "\n"
    glossary = {"terms": [dict(t, level=level_number_of(t["subLevelId"])) for t in strip_private(GLOSSARY)]}
    files["glossary.json"] = json.dumps(glossary, indent=2, ensure_ascii=False) + "\n"
    return files

def on_disk(root):
    return {str(p.relative_to(root)).replace(os.sep, "/"): p.read_text(encoding="utf-8")
            for p in sorted(root.rglob("*.json"))}

if __name__ == "__main__":
    if sys.version_info[:2] != (3, 11):
        print(f"warning: running on Python {sys.version.split()[0]}; the app bundles 3.11", file=sys.stderr)
    if SANDBOX is None:
        print("note: pypath_runner.py not found (or not Python 3.11); Level 8+ examples checked with plain exec",
              file=sys.stderr)
    pages, mcqs = validate()
    root = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/course"
    files = render()
    if "--check" in sys.argv[1:]:
        if on_disk(root) != files:
            sys.exit("course JSON is out of date: run python content/build_course.py and commit the result")
        print("course JSON is up to date")
        sys.exit(0)
    (root / "levels").mkdir(parents=True, exist_ok=True)
    for old in (root / "levels").glob("*.json"):
        old.unlink()
    for rel, txt in files.items():
        (root / rel).write_text(txt, encoding="utf-8")
        print("wrote", root / rel)
    assert on_disk(root) == files, "written JSON does not match the build"
    qs = [qd for l in LEVELS for s_ in l["subLevels"] for qd in s_.get("quiz", {}).get("questions", [])]
    print("validation OK:", sum(len(l["subLevels"]) for l in LEVELS), "sub-levels,",
          sum(len(l.get("projects", [])) for l in LEVELS), "level projects,",
          sum(1 for l in LEVELS if l.get("cheatSheet")), "cheat sheets,", len(GLOSSARY), "glossary terms;",
          sum(1 for qd in qs if qd.get("type", "mcq") == "mcq"), "MCQs, all with hint + optionFeedback;",
          "new types", check_new_type_spread()[0], ";",
          "Levels 3+ pages", pages, "MCQs", mcqs)
