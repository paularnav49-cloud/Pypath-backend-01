"""Batch 3 Part 2: "predict", "fill" and "order" questions.

They are APPENDED at the end of existing quizzes with new ids (the next free {sub-level}q{n}), at
most 2 per sub-level. Existing questions, their ids and their order are never touched.
build_course.py checks every question here:
  predict - the snippet is run with subprocess (Python 3.11) and must print `answer`;
  fill    - every way of filling the blanks from the bank is run; only the intended one may print `output`;
  order   - every order of the lines is run; only the intended order (or a listed alternative) may print `output`.
Each question tests only what its sub-level's lesson teaches, and has a hint and an explanation.
"""


def predict(prompt, code, answer, explanation, hint, case_sensitive=True, alternatives=()):
    q = {"type": "predict", "prompt": prompt, "code": code, "answer": answer}
    if alternatives:
        q["alternatives"] = list(alternatives)
    if not case_sensitive:
        q["caseSensitive"] = False
    q.update(explanation=explanation, hint=hint)
    return q


def fill(prompt, code, blanks, distractors, output, explanation, hint):
    return {"type": "fill", "prompt": prompt, "code": code, "blanks": list(blanks),
            "distractors": list(distractors), "output": output, "explanation": explanation, "hint": hint}


def order(prompt, lines, output, explanation, hint, alternatives=()):
    q = {"type": "order", "prompt": prompt, "lines": list(lines), "output": output}
    if alternatives:
        q["alternativeOrders"] = [list(a) for a in alternatives]
    q.update(explanation=explanation, hint=hint)
    return q


# sub-level id -> new questions, appended in this order.
NEW_QUESTIONS = {
    # ── Level 1 ──
    "l1s1": [
        predict(
            "What does this program print?",
            '# print("A")\nprint("B")  # print("C")\nprint(1 + 2)',
            "B\n3",
            "The first line is only a comment, and everything after # on the second line is ignored. "
            "print(1 + 2) works out the sum first.",
            "Python ignores everything that comes after a hash sign on a line.",
        ),
    ],
    "l1s2": [
        predict(
            "What does this program print?",
            "x = 5\nx = x + 2\nprint(x)",
            "7",
            "The right side is worked out first using the old value (5 + 2), then the result is stored back in x.",
            "A variable can be updated using its own old value on the right side.",
        ),
        order(
            "Put the lines in order so the program prints the total cost.",
            ["price = 20", "qty = 3", "total = price * qty", "print(total)"],
            "60",
            "Both variables must exist before total uses them, and total must exist before it is printed. "
            "The first two lines can go either way round.",
            "A variable has to be created before any line can use it.",
            alternatives=[[1, 0, 2, 3]],
        ),
    ],
    "l1s3": [
        fill(
            "Fill the blanks so the program adds 1 to the number stored as text.",
            'age_text = "20"\nage = {{1}}(age_text)\nprint(age {{2}} 1)',
            ["int", "+"], ["str", "-"],
            "21",
            'int("20") turns the text into the number 20, so age + 1 is 21. Adding 1 to the text itself would be a TypeError.',
            "Text that looks like a number must be converted before you can do maths with it.",
        ),
    ],
    "l1s4": [
        predict(
            "What does this program print?",
            'name = "Meera"\nlevel = 2\nprint(f"{name}: level {level}")',
            "Meera: level 2",
            "In an f-string each {name} is replaced by the variable's value; everything else is printed as written.",
            "Replace each pair of curly braces with the value of the variable inside it.",
        ),
    ],
    # ── Level 2 ──
    "l2s1": [
        predict(
            "What does this program print?",
            'print(7 >= 7)\nprint(3 != 3)\nprint("cat" == "Cat")',
            "True\nFalse\nFalse",
            '7 >= 7 is True because 7 equals 7. 3 != 3 is False. "cat" and "Cat" differ because capital letters count.',
            "Check each comparison on its own, and remember that capital letters make text different.",
        ),
    ],
    "l2s2": [
        fill(
            "Fill the blanks so the program prints \"It's hot\".",
            'temp = 30\nif temp {{1}} 25:\n    print("It\'s hot")\n{{2}}:\n    print("It\'s cool")',
            [">", "else"], ["<", "elif"],
            "It's hot",
            "30 > 25 is True, so the if block runs. else needs no condition; elif would need one.",
            "Think about which comparison is true for 30 and which keyword needs no condition.",
        ),
        order(
            "Put the lines in order so the program prints \"Not yet\".",
            ["age = 16", "if age >= 18:", '    print("Adult")', "else:", '    print("Not yet")'],
            "Not yet",
            "age must be set before the if checks it. 16 >= 18 is False, so the indented line under else runs.",
            "The variable comes first, then the condition, with each indented line under the part it belongs to.",
        ),
    ],
    "l2s3": [
        predict(
            "What does this program print?",
            'marks = 65\nif marks >= 90:\n    print("A")\nelif marks >= 60:\n    print("B")\nelse:\n    print("C")\nprint("done")',
            "B\ndone",
            "65 >= 90 is False, 65 >= 60 is True, so only the elif block runs. The last line is not indented, so it always runs.",
            "Python checks the conditions from the top and runs only the first one that is true.",
        ),
        fill(
            "Fill the blanks so this grade checker prints only \"A\" for 95 marks.",
            'marks = 95\nif marks >= 90:\n    print("A")\n{{1}} marks >= 60:\n    print("B")\n{{2}}:\n    print("C")',
            ["elif", "else"], ["if"],
            "A",
            "elif and else belong to the same chain as the first if, so only one block runs. A second if would start "
            "a new check and also print B.",
            "Only one block of a single chain runs, and a fresh check would run separately.",
        ),
    ],
    # ── Level 3 ──
    "l3s1": [
        fill(
            "Fill the blanks so the loop prints 1, 2 and 3.",
            "for n in {{1}}(1, {{2}}):\n    print(n)",
            ["range", "4"], ["len", "3"],
            "1\n2\n3",
            "range(1, 4) counts 1, 2, 3: it starts at the first number and stops just before the second.",
            "The stop number of a range is never included in the numbers it gives.",
        ),
        order(
            "Put the lines in order so the program prints the sum of 1, 2 and 3.",
            ["total = 0", "for n in range(1, 4):", "    total += n", "print(total)"],
            "6",
            "total starts at 0 before the loop, the indented line adds each number, and the total is printed once after the loop.",
            "Set up the running total before the loop and print it after the loop ends.",
        ),
    ],
    "l3s2": [
        predict(
            "What does this program print?",
            "count = 2\nwhile count <= 8:\n    print(count)\n    count = count * 2",
            "2\n4\n8",
            "count goes 2, 4, 8 and each is printed. Then it becomes 16, the condition 16 <= 8 is False and the loop stops.",
            "Follow the variable through each pass and stop as soon as the condition becomes false.",
        ),
    ],
    "l3s3": [
        fill(
            "Fill the blanks so the program prints only 1 and 3.",
            "for n in range(1, 8):\n    if n == 5:\n        {{1}}\n    if n % 2 == 0:\n        {{2}}\n    print(n)",
            ["break", "continue"], ["stop"],
            "1\n3",
            "continue skips the print for even numbers, and break ends the whole loop when n reaches 5, before 5 is printed.",
            "One keyword ends the loop completely, the other only skips the rest of one pass.",
        ),
        order(
            "Put the lines in order so the program prints 1, 2 and then end.",
            ["for n in range(1, 10):", "    if n == 3:", "        break", "    print(n)", 'print("end")'],
            "1\n2\nend",
            "The check for 3 comes before the print, so break stops the loop before 3 is printed. The unindented "
            'print("end") runs after the loop.',
            "The loop must stop before printing three, and the last message comes after the loop.",
        ),
    ],
    "l3s4": [
        predict(
            "What does this program print?",
            'for i in range(3):\n    print(i, end="-")\nprint()\nprint("ok")',
            "0-1-2-\nok",
            'end="-" prints a dash instead of a new line, so 0-1-2- stays on one line. print() then ends that line.',
            "Each print inside the loop ends with a dash instead of starting a new line.",
        ),
    ],
    # ── Level 4 ──
    "l4s1": [
        order(
            "Put the lines in order so the program prints Go, Team and then Start.",
            ["def cheer():", '    print("Go")', '    print("Team")', "cheer()", 'print("Start")'],
            "Go\nTeam\nStart",
            "A function must be defined before it is called. Calling cheer() prints its two lines, then Start is printed last.",
            "Define the function first, then call it, and put the last message at the very end.",
        ),
    ],
    "l4s2": [
        fill(
            "Fill the blanks so the program prints 7.",
            "def add(a, b):\n    print(a {{1}} b)\n\nadd({{2}}, 4)",
            ["+", "3"], ["*", "5"],
            "7",
            "The call add(3, 4) puts 3 in a and 4 in b, and a + b prints 7.",
            "The arguments fill the parameters in order, then the operator combines them.",
        ),
    ],
    "l4s3": [
        predict(
            "What does this program print?",
            "def double(n):\n    return n * 2\n\nprint(double(3) + 1)",
            "7",
            "double(3) returns 6, and 6 + 1 is 7. A returned value can be used inside a bigger expression.",
            "Work out what the function call gives back first, then finish the sum.",
        ),
        order(
            "Put the lines in order so the program prints 7.",
            ["def triple(n):", "    return n * 3", "x = triple(2)", "print(x + 1)"],
            "7",
            "The function is defined first. triple(2) returns 6, which is stored in x, and x + 1 prints 7.",
            "The function must exist before it is called, and x must exist before it is printed.",
        ),
    ],
    "l4s4": [
        fill(
            "Fill the blanks so the program greets Asha with Hello and Ravi with Hi.",
            'def greet(name, greeting={{1}}):\n    print(f"{greeting}, {name}!")\n\ngreet("Asha")\ngreet("Ravi", {{2}})',
            ['"Hello"', '"Hi"'], ['"Bye"'],
            "Hello, Asha!\nHi, Ravi!",
            "The default value is used when no greeting is passed (Asha). Passing a second argument replaces it (Ravi).",
            "The default is used only when the call leaves that argument out.",
        ),
    ],
    "l4s5": [
        predict(
            "What does this program print?",
            "x = 5\n\ndef change():\n    x = 50\n    print(x)\n\nchange()\nprint(x)",
            "50\n5",
            "x = 50 inside the function makes a new local variable. The global x outside is still 5.",
            "A variable assigned inside a function is separate from one with the same name outside.",
        ),
    ],
    # ── Level 5 ──
    "l5s1": [
        fill(
            "Fill the blanks so the program prints how many colours there are, then whether pink is one of them.",
            'colours = ["red", "green", "blue"]\nprint({{1}}(colours))\nprint("pink" {{2}} colours)',
            ["len", "in"], ["max", "not"],
            "3\nFalse",
            'len(colours) counts the items (3). "pink" in colours checks membership and gives False.',
            "One function counts the items, and one keyword checks whether a value is in the list.",
        ),
    ],
    "l5s2": [
        predict(
            "What does this program print?",
            "nums = [10, 20, 30, 40]\nprint(nums[-1])\nprint(nums[1:3])",
            "40\n[20, 30]",
            "Index -1 is the last item. The slice 1:3 takes index 1 and 2 and stops before index 3.",
            "Negative indexes count from the end, and a slice stops before its second number.",
        ),
    ],
    "l5s3": [
        order(
            "Put the lines in order so the program prints study.",
            ['tasks = ["wash", "cook"]', 'tasks.append("study")', "last = tasks.pop()", "print(last)"],
            "study",
            'append adds "study" to the end, and pop() then removes and returns the last item, which is "study".',
            "pop takes the item that is at the end of the list at that moment.",
        ),
        fill(
            "Fill the blanks so the marks are sorted and then 48 is taken out.",
            "marks = [72, 95, 48]\nmarks.{{1}}()\nprint(marks)\nmarks.{{2}}(48)\nprint(marks)",
            ["sort", "remove"], ["pop"],
            "[48, 72, 95]\n[72, 95]",
            "sort() puts the list in order. remove(48) deletes the value 48; pop(48) would look for index 48 and fail.",
            "One method orders the list, and the other deletes an item by its value.",
        ),
    ],
    "l5s4": [
        order(
            "Put the lines in order so the program prints the total of all prices.",
            ["prices = [40, 25, 60]", "total = 0", "for price in prices:", "    total += price", "print(total)"],
            "125",
            "The list and the running total both exist before the loop, each price is added inside it, and the "
            "total is printed after it. The first two lines can go either way round.",
            "Prepare everything the loop needs before it starts, and print the result once at the end.",
            alternatives=[[1, 0, 2, 3, 4]],
        ),
    ],
    "l5s5": [
        fill(
            "Fill the blanks so the program keeps the numbers above 2 and multiplies them by 10.",
            "nums = [1, 2, 3, 4]\nbig = [n * 10 {{1}} n in nums {{2}} n > 2]\nprint(big)",
            ["for", "if"], ["in", "while"],
            "[30, 40]",
            "A comprehension reads: expression, for each item in the list, if a condition. Only 3 and 4 pass n > 2.",
            "A comprehension has the loop part first and the filter at the end.",
        ),
        predict(
            "What does this program print?",
            'words = ["hi", "yo"]\nloud = [w + "!" for w in words]\nprint(loud)\nprint(words)',
            "['hi!', 'yo!']\n['hi', 'yo']",
            "The comprehension builds a new list; the original words list is not changed.",
            "A comprehension makes a brand new list and leaves the original one alone.",
        ),
    ],
    # ── Level 6 ──
    "l6s1": [
        predict(
            "What does this program print?",
            'line = "a,b,c"\nparts = line.split(",")\nprint(len(parts))\nprint("-".join(parts))',
            "3\na-b-c",
            'split(",") cuts the text into a list of 3 pieces. "-".join puts them back together with dashes between.',
            "Splitting gives a list of pieces, and joining glues them back with the chosen text.",
        ),
        fill(
            "Fill the blanks so the program swaps tea for coffee and checks the first letter.",
            'text = "I like tea"\nprint(text.{{1}}("tea", "coffee"))\nprint(text.{{2}}("I"))',
            ["replace", "startswith"], ["split"],
            "I like coffee\nTrue",
            'replace swaps one piece of text for another. startswith("I") checks the beginning and gives True.',
            "One method swaps words, the other checks how the text begins.",
        ),
    ],
    "l6s2": [
        fill(
            "Fill the blanks so the average shows 2 decimals and Tea is padded to 6 characters on the left side.",
            "average = 72.456\nprint(f\"{average:{{1}}}\")\nprint(f\"{'Tea':{{2}}}|\")",
            [".2f", "<6"], [".1f", ">6"],
            "72.46\nTea   |",
            ".2f rounds to 2 decimal places. <6 makes the text 6 characters wide and lines it up on the left.",
            "The number after the dot sets the decimals, and the arrow points where the text lines up.",
        ),
    ],
    "l6s3": [
        predict(
            "What does this program print?",
            'prices = {"tea": 15, "coffee": 25}\nprint(prices.get("juice", 0))\nprices["tea"] = 20\nprint(prices["tea"])',
            "0\n20",
            'get returns the default 0 because "juice" is not a key. Assigning to an existing key replaces its value.',
            "A missing key gives the default you passed, and assigning to a key replaces its value.",
        ),
        order(
            "Put the lines in order so the program counts each letter.",
            ["counts = {}", 'for v in ["a", "b", "a"]:', "    counts[v] = counts.get(v, 0) + 1", "print(counts)"],
            "{'a': 2, 'b': 1}",
            "The empty dictionary is made first, the indented line adds 1 for each letter, and the result is printed after the loop.",
            "Create the empty dictionary first and print it only when the loop has finished.",
        ),
    ],
    "l6s4": [
        order(
            "Put the lines in order so the program prints each day's sales and then Done.",
            ['sales = {"Mon": 120, "Tue": 90}', "for day, amount in sales.items():", '    print(f"{day}: {amount}")', 'print("Done")'],
            "Mon: 120\nTue: 90\nDone",
            "The dictionary must exist before the loop. items() gives each key and value together, and Done comes after the loop.",
            "The dictionary comes before the loop, and the final message comes after it.",
        ),
        fill(
            "Fill the blank so the loop gets each name and age together.",
            'ages = {"Asha": 21, "Ravi": 19}\nfor name, age in ages.{{1}}():\n    print(name, age)',
            ["items"], ["keys", "values"],
            "Asha 21\nRavi 19",
            "items() gives (key, value) pairs, so both name and age get a value. keys() or values() give only one thing each.",
            "You need a method that gives both the key and its value in each pass.",
        ),
    ],
}
