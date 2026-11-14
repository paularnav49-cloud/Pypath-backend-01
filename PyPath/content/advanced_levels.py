"""Batch 1: Levels 8-11 (Advanced) - errors and exceptions, modules, files, and basic classes.

These levels come after the Level 7 final project. They unlock in order like every other level
(finish Level 7 -> Level 8 opens, and so on) and are marked "advanced": true, so the course
certificate still only needs Levels 1-7.

Order decision: Modules (Level 9) comes before Files (Level 10) so that `import os` for
os.path.exists() is already taught when the Files level uses it.

Every code example with an output is executed by build_course.py, through the app's real
Practical sandbox (app/src/main/python/pypath_runner.py, used read-only) when Python 3.11 is
available, in a fresh empty scratch folder, exactly like the Practical tab runs main.py.
Error outputs show only the last line of the error unless the block is marked full_error=True.

Code inside r''' ''' strings is shown to learners exactly as written (\\n stays a backslash-n).
"""
from course_extras import item, section, term

# Sub-levels with the hardest topics; validation requires more MCQs than the course average.
HARD_SUB_LEVELS = ("l8s1", "l10s2", "l11s3")

LAST_LINE = "Only the last line of the error is shown."


def make_levels(h, first_number):
    code, text, bullets, tip, mistake, key, page, m, lesson = (
        h["code"], h["text"], h["bullets"], h["tip"], h["mistake"], h["key"], h["page"], h["m"], h["lesson"])
    assert first_number == 8, "Batch 1 levels are written as Levels 8-11"

    # ───────────────────────────── LEVEL 8: ERRORS ─────────────────────────────
    level8 = {
      "id": "l8", "number": 8, "title": "Handling Errors (Advanced)",
      "description": "Advanced: read error messages, catch errors with try and except, and raise your own.",
      "available": True, "advanced": True,
      "subLevels": [
        lesson("l8s1", "8.1", "Reading error messages", "Understand tracebacks and the most common error types.", 8, [
            page("Errors are clues",
                 text("""When Python meets a line it can't run, it stops and prints an error message. Errors that
                      happen while a program runs are called exceptions. Every programmer sees them every day:
                      they are clues, not failures."""),
                 code(r'''
                 print("Start")
                 print(10 / 0)
                 print("End")
                 ''', output='''
                 Start
                 Traceback (most recent call last):
                   File "main.py", line 2, in <module>
                     print(10 / 0)
                 ZeroDivisionError: division by zero
                 ''', full_error=True,
                      explain=["Line 1 runs and prints Start.", "Line 2 can't be done, so Python stops and shows the error.",
                               "Line 3 never runs: nothing after an error runs."]),
                 bullets("An exception stops the program at the line where it happens.",
                         "Everything printed before the error stays on the screen.",
                         "The lines after the error do not run.")),
            page("Reading a traceback",
                 text("The message Python prints is called a traceback. Read it from the bottom up."),
                 bullets("Traceback (most recent call last): Python is about to show where it was.",
                         'File "main.py", line 2: the line number where the error happened.',
                         "The next line repeats the code from that line.",
                         "Under that, Python 3.11 may add a row of ^ or ~ marks pointing at the exact part that failed (left out in these lessons).",
                         "The last line gives the error type and a short message. Read this first."),
                 key("Read a traceback from the bottom: the last line says what went wrong, the line number says where."),
                 tip("In the Practical tab your program is always called main.py, so tracebacks point to main.py.")),
            page("NameError, TypeError, ValueError",
                 text("Three errors you will meet a lot. Each example shows only the last line of the error."),
                 code(r'''
                 print(score)
                 ''', output="NameError: name 'score' is not defined", caption=LAST_LINE,
                      explain=["NameError: the name was never created, or it is spelled differently."]),
                 code(r'''
                 age = "20"
                 print(age + 1)
                 ''', output='TypeError: can only concatenate str (not "int") to str', caption=LAST_LINE,
                      explain=["TypeError: the values are the wrong types for this job, here text + a number."]),
                 code(r'''
                 number = int("ten")
                 ''', output="ValueError: invalid literal for int() with base 10: 'ten'", caption=LAST_LINE,
                      explain=["ValueError: the type is right (a string), but this value can't be converted."])),
            page("ZeroDivisionError, IndexError, KeyError",
                 code(r'''
                 print(5 / 0)
                 ''', output="ZeroDivisionError: division by zero", caption=LAST_LINE,
                      explain=["ZeroDivisionError: dividing by zero is impossible."]),
                 code(r'''
                 days = ["Mon", "Tue"]
                 print(days[2])
                 ''', output="IndexError: list index out of range", caption=LAST_LINE,
                      explain=["IndexError: the list has positions 0 and 1 only."]),
                 code(r'''
                 prices = {"tea": 15}
                 print(prices["milk"])
                 ''', output="KeyError: 'milk'", caption=LAST_LINE,
                      explain=["KeyError: the dictionary has no key called milk."])),
            page("Error types at a glance",
                 text("The error type on the last line already tells you what kind of problem to look for."),
                 bullets("NameError: unknown name", "TypeError: wrong type for the job", "ValueError: right type, bad value",
                         "ZeroDivisionError: divided by zero", "IndexError: list position doesn't exist",
                         "KeyError: dictionary key doesn't exist"),
                 tip("Every error type in this level is also in the Glossary, if you need a reminder.")),
            page("Fixing an error",
                 text("""To fix an error: read the last line, go to the line number, and look at the part that is
                      underlined. Here the variable was created as total but used as totl."""),
                 code(r'''
                 total = 10
                 print(totl)
                 ''', output='''
                 Traceback (most recent call last):
                   File "main.py", line 2, in <module>
                     print(totl)
                 NameError: name 'totl' is not defined
                 ''', full_error=True, explain=["Line 2 uses totl; changing it to total fixes the program."]),
                 mistake("""Reading only the top of the traceback. The useful part is at the bottom: the error type
                         and message. Also, the line number is where Python noticed the problem; sometimes the real
                         mistake is earlier, like a name spelled differently when it was created.""")),
        ], [
            m("Which error does this code cause?", "NameError", ["TypeError", "ValueError", "KeyError"],
              "total exists, but totl was never created, so Python doesn't know that name: NameError.",
              code_="total = 10\nprint(totl)", check="err:NameError",
              hint="Compare the name that was created with the name that is printed, letter by letter.",
              fb=("Right: totl is a name Python has never seen.",
                  ["A TypeError is about mixing the wrong types; here the name itself is unknown.",
                   "A ValueError needs a value that can't be converted; no conversion happens here.",
                   "A KeyError comes from a missing dictionary key; there is no dictionary here."])),
            m("In a traceback, which line tells you the type of error?", "The last line",
              ["The first line, Traceback (most recent call last):", 'The line that starts with File "main.py"',
               "The line with the ^ marks"],
              "The last line shows the error type and its message, such as ZeroDivisionError: division by zero.",
              hint="Remember which direction the lesson said you should read a traceback from.",
              fb=("Right: the error type and message are always at the bottom.",
                  ["The first line only says a traceback is starting; it never names the error.",
                   "The File line tells you where the error happened, not what kind it is.",
                   "The ^ marks underline the part of the code; the error type is printed below them."])),
            m('What does line 3 mean in File "main.py", line 3, in <module>?', "The error happened on line 3 of main.py",
              ["The program has 3 lines", "3 errors were found", "Lines 1 to 3 all have mistakes"],
              "The File line points to the line Python was running when the error happened.",
              hint="Think about what a traceback needs to tell you so you can find the problem.",
              fb=("Right: it is the line number where Python stopped.",
                  ["The number says where the error is, not how long the program is.",
                   "Python stops at the first error, so a traceback describes just one error.",
                   "Only the line named in the traceback is where Python stopped."])),
            m("Which error does this code cause?", "ValueError", ["TypeError", "NameError", "ZeroDivisionError"],
              'int() gets a string, which is the right type, but "5.5" is not a whole number, so the value is bad.',
              code_='number = int("5.5")', check="err:ValueError",
              hint="int() accepts strings, so ask yourself whether this particular string can become a whole number.",
              fb=("Right: the type is fine but this value can't become an int.",
                  ["int() is happy to take a string, so the type is not the problem.",
                   "Every name here exists: int and number are both fine.",
                   "There is no division anywhere in this line."])),
            m("Which error does this code cause?", "KeyError", ["IndexError", "NameError", "ValueError"],
              'marks has only the key "maths", so looking up "english" raises a KeyError.',
              code_='marks = {"maths": 90}\nprint(marks["english"])', check="err:KeyError",
              hint="Look at what kind of collection marks is and what is being looked up.",
              fb=("Right: the dictionary has no english key.",
                  ["IndexError is for list positions; marks is a dictionary looked up by key.",
                   "marks is a name that exists; it is the key inside it that is missing.",
                   "No text is being converted to a number here."])),
            m("Which error does this code cause?", "TypeError", ["ValueError", "NameError", "ZeroDivisionError"],
              "+ can join two strings or add two numbers, but not a string and an int: TypeError.",
              code_='print("Total: " + 5)', check="err:TypeError",
              hint="Check the types of the two values on each side of the plus sign.",
              fb=("Right: text and a number can't be joined with +.",
                  ["Nothing is being converted with int(); the problem is mixing two types.",
                   "There are no variable names here at all, only values.",
                   "This line adds, it doesn't divide."])),
            m("What appears on the screen?", "A, then the error message",
              ["Only the error message", "A and B, then the error message", "A, the error message, then B"],
              "print(\"A\") runs first. The division by zero stops the program, so print(\"B\") never runs.",
              code_='print("A")\nprint(1 / 0)\nprint("B")',
              hint="Python runs lines in order and stops for good at the first error.",
              fb=("Right: A is printed, then the program stops at line 2.",
                  ["Line 1 runs before the error, so A is already on the screen.",
                   "B would come after the error, and nothing runs after an error.",
                   "The program doesn't continue after the error, so B is never printed."])),
        ]),
        lesson("l8s2", "8.2", "try and except", "Catch an error and keep the program running.", 7, [
            page("Catching an error",
                 text("""Put code that might fail inside a try block. If an error happens there, Python jumps to
                      the except block instead of stopping the program."""),
                 code(r'''
                 try:
                     number = int("abc")
                     print("Converted!")
                 except ValueError:
                     print("That was not a number")
                 print("Program keeps going")
                 ''', output="That was not a number\nProgram keeps going",
                      explain=['int("abc") raises a ValueError, so Python jumps straight to except.',
                               '"Converted!" is skipped because it comes after the error inside try.',
                               "After the except block, the program carries on as normal."]),
                 bullets("try: the code that might fail.", "except ErrorType: what to do if that error happens.",
                         "If the try block has no error, the except block is skipped.")),
            page("Catching a specific error",
                 text("""Write the error type after except, so you only catch the problem you expect. You can have
                      several except blocks, one per error type."""),
                 code(r'''
                 def safe_divide(a, b):
                     try:
                         return a / b
                     except ZeroDivisionError:
                         return "can't divide by zero"

                 print(safe_divide(10, 2))
                 print(safe_divide(5, 0))
                 ''', output="5.0\ncan't divide by zero"),
                 code(r'''
                 items = ["pen", "book"]
                 for text in ["1", "5", "two"]:
                     try:
                         print(items[int(text)])
                     except ValueError:
                         print(text, "is not a number")
                     except IndexError:
                         print(text, "is too big")
                 ''', output="book\n5 is too big\ntwo is not a number",
                      explain=['"1" works: items[1] is book.', '"5" converts, but there is no position 5: IndexError.',
                               '"two" can\'t be converted: ValueError.']),
                 tip("If no except block matches the error, it is not caught and the program stops as usual.")),
            page("Don't catch everything",
                 text("""except: with no error type catches every error, even your own typos. Here "42" is a fine
                      number, but valeu is misspelt, and the message hides the real NameError."""),
                 code(r'''
                 try:
                     value = int("42")
                     print(valeu + 1)
                 except:
                     print("Please type a number")
                 ''', output="Please type a number"),
                 mistake("""Writing except: with no error type. It catches every error, including spelling mistakes,
                         and shows the wrong message. Name the error you expect, like except ValueError:."""),
                 key("Keep try blocks small and catch the specific error you expect.")),
        ], [
            m("What does this print?", "A\nC", ["A\nB\nC", "C", "A\nB"],
              'print("A") runs, then int("hi") fails, so print("B") is skipped and the except block prints C.',
              code_='try:\n    print("A")\n    x = int("hi")\n    print("B")\nexcept ValueError:\n    print("C")', check="out",
              hint="Follow the try block line by line and notice exactly where the jump happens.",
              fb=("Right: A prints, the error skips B, and except prints C.",
                  ["B comes after the failing line inside try, so it is skipped.",
                   "print(\"A\") runs before the error, so A is printed first.",
                   "The ValueError is caught, so the except block runs and prints C."])),
            m("What does this print?", "8", ["Not a number", "8\nNot a number", "71"],
              'int("7") works, so the try block finishes and the except block is skipped.',
              code_='try:\n    x = int("7")\n    print(x + 1)\nexcept ValueError:\n    print("Not a number")', check="out",
              hint="Ask whether anything inside the try block actually fails here.",
              fb=("Right: no error happens, so except is skipped.",
                  ['"7" is a valid whole number, so there is no ValueError to catch.',
                   "except only runs when an error happens, and here nothing fails.",
                   "x is the number 7 after int(), so + 1 does maths, not joining text."])),
            m("Which word goes in the blank to catch a division by zero?", "ZeroDivisionError",
              ["ValueError", "DivideError", "ZeroError"],
              "Dividing by zero raises ZeroDivisionError, so that is the type to name after except.",
              code_="try:\n    print(10 / 0)\nexcept ____:\n    print(\"Can't divide by zero\")",
              hint="Use the exact name Python prints on the last line when you divide by zero.",
              fb=("Right: that is the exact name of the error.",
                  ["A ValueError is for bad values like int(\"abc\"); dividing by zero has its own error type.",
                   "Python has no error called DivideError; the name must match exactly.",
                   "Close, but the real name is longer; error names must be spelled exactly."])),
            m("Which statement is true?", "If no except block matches the error, the program still stops with that error",
              ["The except block runs every time, even with no error", "Python goes back and runs the try block again",
               "Lines after the error inside try still run"],
              "except only catches the error types it names. Any other error stops the program as usual.",
              hint="Think about an except ValueError block meeting a completely different kind of error.",
              fb=("Right: an error with no matching except is not caught.",
                  ["except only runs when a matching error happens in the try block.",
                   "Python never repeats the try block by itself; it moves on.",
                   "As soon as an error happens, the rest of the try block is skipped."])),
            m('Why does this print "Please type a number" even though "42" is a number?',
              "except: catches every error, including the NameError from the typo valeu",
              ['int("42") raises a ValueError', "print() is not allowed inside try",
               "valeu + 1 raises a ValueError"],
              "valeu was never created, so line 3 raises a NameError. The bare except: catches it and shows a misleading message.",
              code_='try:\n    value = int("42")\n    print(valeu + 1)\nexcept:\n    print("Please type a number")',
              hint="Look closely at the spelling of every name inside the try block.",
              fb=("Right: the bare except: hides a NameError.",
                  ['"42" converts to 42 without any problem.',
                   "Any code, including print(), can go inside a try block.",
                   "Using a name that doesn't exist is a NameError, not a ValueError."])),
        ]),
        lesson("l8s3", "8.3", "else, finally and raise", "Run code after success, always clean up, and raise your own errors.", 8, [
            page("else: when it worked",
                 text("An else block after except runs only if the try block finished with no error."),
                 code(r'''
                 text = "25"
                 try:
                     age = int(text)
                 except ValueError:
                     print("Not a number")
                 else:
                     print("Next year you will be", age + 1)
                 ''', output="Next year you will be 26",
                      explain=['int("25") works, so except is skipped and else runs.',
                               "Keeping only the risky line in try makes it clear what you are protecting."]),
                 bullets("try: the risky line.", "except: runs if that error happens.", "else: runs if no error happened.")),
            page("finally: always runs",
                 code(r'''
                 def divide(a, b):
                     try:
                         print(a / b)
                     except ZeroDivisionError:
                         print("Cannot divide by zero")
                     finally:
                         print("Done")

                 divide(6, 3)
                 divide(1, 0)
                 ''', output="2.0\nDone\nCannot divide by zero\nDone",
                      explain=["finally runs after try works and after an error is caught: every time."]),
                 key("Order: try, then except (if there was an error) or else (if there wasn't), then finally (always).")),
            page("raise your own errors",
                 text("""raise creates an error on purpose, with your own message. Use it when a value breaks your
                      rules. It stops the function, just like a normal error."""),
                 code(r'''
                 def set_age(age):
                     if age < 0:
                         raise ValueError("age can't be negative")
                     return age

                 print(set_age(12))
                 print(set_age(-3))
                 ''', output="12\nValueError: age can't be negative", caption=LAST_LINE)),
            page("Catching your own errors",
                 text("The code that calls the function can catch it. except ValueError as error: also stores the error, and printing it shows the message."),
                 code(r'''
                 def set_age(age):
                     if age < 0:
                         raise ValueError("age can't be negative")
                     return age

                 try:
                     set_age(-3)
                 except ValueError as error:
                     print("Problem:", error)
                 ''', output="Problem: age can't be negative")),
            page("raise needs an error type",
                 code(r'''
                 raise "Too big"
                 ''', output="TypeError: exceptions must derive from BaseException", caption=LAST_LINE),
                 mistake("""Raising plain text, like raise "Too big". raise needs an error type with the message in
                         brackets: raise ValueError("Too big").""")),
        ], [
            m("What does this print?", "8", ["bad", "8\nbad", "44"],
              'int("4") works, so except is skipped and else prints 4 * 2.',
              code_='try:\n    n = int("4")\nexcept ValueError:\n    print("bad")\nelse:\n    print(n * 2)', check="out",
              hint="Decide first whether the try block raised an error, then pick the block that runs.",
              fb=("Right: no error, so else runs and prints 8.",
                  ['"4" converts fine, so the except block is skipped.',
                   "except and else never both run for the same try.",
                   "n is the number 4 after int(), so * 2 is maths, not repeating text."])),
            m("What does this print?", "oops\nend", ["end", "oops", "end\noops"],
              "The division fails, so except prints oops; finally always runs afterwards and prints end.",
              code_='try:\n    print(1 / 0)\nexcept ZeroDivisionError:\n    print("oops")\nfinally:\n    print("end")', check="out",
              hint="Work out which blocks run when there is an error, and in what order.",
              fb=("Right: except runs first, then finally.",
                  ["The error is caught, so the except block also runs before finally.",
                   "finally runs every time, even after an error was caught.",
                   "finally runs last, after the except block."])),
            m("When does the else block of a try statement run?", "Only when the try block finished with no error",
              ["Only when an error happened", "Always, after finally", "Only when there is no except block"],
              "else is the success path: it runs when try had no error.",
              hint="Think of else as the opposite path to except.",
              fb=("Right: else is for when everything worked.",
                  ["That describes except; else is for the opposite case.",
                   "finally is the block that runs every time, and it runs last.",
                   "In this lesson else comes after an except block; it runs when try succeeds."])),
            m("Which word fills the blank to create an error with your own message?", "raise",
              ["throw", "return", "except"],
              'raise ValueError("...") creates an error on purpose and stops the function.',
              code_='def set_score(score):\n    if score > 100:\n        ____ ValueError("score must be 100 or less")\n    return score',
              hint="This is the keyword from the lesson that creates an error on purpose.",
              fb=("Right: raise creates the error.",
                  ["Some other languages use throw, but Python does not have that keyword.",
                   "return gives back a value normally; it doesn't create an error.",
                   "except catches errors; it doesn't create them."])),
            m("What does this print?", "Error: too big", ["50", "too big", "Error: ValueError"],
              "check(50) raises ValueError(\"too big\"). except ... as e catches it, and printing e shows its message.",
              code_='def check(n):\n    if n > 10:\n        raise ValueError("too big")\n    return n\n\ntry:\n    print(check(50))\nexcept ValueError as e:\n    print("Error:", e)',
              check="out",
              hint="Check whether 50 breaks the rule, and remember what printing the stored error shows.",
              fb=("Right: the error is caught and its message is printed.",
                  ["50 is bigger than 10, so raise runs before return.",
                   'The except block prints "Error:" before the message.',
                   "Printing the error shows its message, not the name of its type."])),
            m("Why does this give a TypeError instead of showing the message?",
              'raise needs an error type, like raise ValueError("Out of stock")',
              ["raise only works inside a try block", "The message needs single quotes",
               "raise must be followed by finally"],
              "You can only raise errors, not plain text. Put the message inside an error type.",
              code_='raise "Out of stock"', check="err:TypeError",
              hint="Look at what comes straight after the keyword and compare it with the lesson.",
              fb=("Right: raise needs an error type, not plain text.",
                  ["raise works anywhere, inside or outside try.",
                   "Single and double quotes make the same string; the quotes are not the issue.",
                   "finally belongs to try statements; raise doesn't need it."])),
        ]),
        lesson("l8s4", "8.4", "Checking input in a loop", "Keep asking until the user types something valid.", 7, [
            page("Ask until it's valid",
                 text("""Users make typos. Combine while True, try/except and break to keep asking until the
                      answer is valid."""),
                 code(r'''
                 while True:
                     text = input("Enter your age: ")
                     try:
                         age = int(text)
                     except ValueError:
                         print("Please type a whole number.")
                         continue
                     if age < 0:
                         print("Age can't be negative.")
                     else:
                         break
                 print("Thanks! Age:", age)
                 ''', inputs=["abc", "-5", "18"], output='''
                 Enter your age: abc
                 Please type a whole number.
                 Enter your age: -5
                 Age can't be negative.
                 Enter your age: 18
                 Thanks! Age: 18
                 ''', explain=["abc can't be converted: the except block prints a message and continue asks again.",
                               "-5 converts, but it breaks the rule, so the loop asks again.",
                               "18 is valid, so break ends the loop."]),
                 bullets("while True: keep asking.", "try/except: catch answers that aren't numbers.",
                         "if: check your own rules.", "break: stop once the answer is valid.")),
            page("A reusable asking function",
                 text("Put the loop in a function. return ends the loop and gives back the valid number. raise inside try sends rule-breaking numbers to the same except block."),
                 code(r'''
                 def ask_number(question, low, high):
                     while True:
                         try:
                             n = int(input(question))
                             if n < low:
                                 raise ValueError("too small")
                             if n > high:
                                 raise ValueError("too big")
                             return n
                         except ValueError:
                             print(f"Please type a whole number from {low} to {high}.")

                 tickets = ask_number("How many tickets (1-6)? ", 1, 6)
                 print("Booked", tickets, "tickets")
                 ''', inputs=["seven", "9", "2"], output='''
                 How many tickets (1-6)? seven
                 Please type a whole number from 1 to 6.
                 How many tickets (1-6)? 9
                 Please type a whole number from 1 to 6.
                 How many tickets (1-6)? 2
                 Booked 2 tickets
                 '''),
                 mistake("""Converting the input above the try block, like age = int(input("Age: ")) on its own line.
                         A bad answer then crashes the program before try is reached. The line that can fail must
                         be inside try."""),
                 key("Validation loop: ask, try to convert, check your rules, and only leave the loop with a valid value.")),
        ], [
            m("What does this print?", "not a number\nnegative\nok 7", ["ok 7", "not a number\nok 7", "not a number\nnegative"],
              '"x" fails to convert, "-2" breaks the rule, and "7" is valid, so it prints ok 7 and break stops the loop.',
              code_='for text in ["x", "-2", "7"]:\n    try:\n        n = int(text)\n    except ValueError:\n        print("not a number")\n        continue\n    if n < 0:\n        print("negative")\n    else:\n        print("ok", n)\n        break',
              check="out",
              hint="Walk through the three answers one at a time and note what each one prints.",
              fb=("Right: each of the three answers prints one line.",
                  ['"x" and "-2" come first and each prints a message before 7 is reached.',
                   '"-2" converts to a number, but it is below 0, so it prints negative.',
                   '"7" is valid, so the loop prints ok 7 before break ends it.'])),
            m("Typing abc here still crashes the program. Why?",
              "int(input(...)) is above the try block, so its ValueError is not caught",
              ["The except should say TypeError", "print() can't be used inside try",
               "input() always crashes when letters are typed"],
              "Only errors that happen inside try are caught. The conversion happens before try starts.",
              code_='age = int(input("Age: "))\ntry:\n    print("Next year:", age + 1)\nexcept ValueError:\n    print("Please type a number")',
              hint="Find the exact line that fails when letters are typed, and check where it is.",
              fb=("Right: the failing line must be inside try.",
                  ['int("abc") raises a ValueError, so ValueError is the right type to catch.',
                   "print() is fine inside try; the problem is on the line above it.",
                   "input() happily returns letters; it is int() that fails."])),
            m("Which word goes in the blank so the loop asks again after a bad answer?", "continue",
              ["break", "return", "finally"],
              "continue jumps back to the top of the loop, which asks the question again.",
              code_='while True:\n    text = input("Number: ")\n    try:\n        n = int(text)\n    except ValueError:\n        print("Try again")\n        ____\n    break\nprint("You typed", n)',
              hint="You need the loop keyword that skips the rest and starts the next round.",
              fb=("Right: continue goes back to the question.",
                  ["break would leave the loop even though no valid number was typed.",
                   "This code is not inside a function, so return can't be used here.",
                   "finally starts a block of a try statement; it doesn't send the loop back."])),
        ]),
      ],
    }

    # ───────────────────────────── LEVEL 9: MODULES ─────────────────────────────
    level9 = {
      "id": "l9", "number": 9, "title": "Using Modules (Advanced)",
      "description": "Advanced: use ready-made tools from Python's standard library: math, random and datetime.",
      "available": True, "advanced": True,
      "subLevels": [
        lesson("l9s1", "9.1", "Importing modules", "Load a module with import and use the math module.", 7, [
            page("What is a module?",
                 text("""A module is a file of ready-made Python code. Python comes with many of them, called the
                      standard library. import loads a module so you can use its tools with a dot."""),
                 code(r'''
                 import math
                 print(math.sqrt(25))
                 print(math.pi)
                 ''', output="5.0\n3.141592653589793",
                      explain=["import math loads the math module. Put imports at the top of your program.",
                               "math.sqrt() is the square root function inside math.",
                               "math.pi is a value stored in the module."]),
                 bullets("math: square roots, rounding up and down, pi.", "random: random numbers and choices.",
                         "datetime: dates and days between them.")),
            page("Useful math tools",
                 code(r'''
                 import math
                 print(math.floor(4.7))
                 print(math.ceil(4.2))
                 books = 23
                 per_box = 5
                 print(math.ceil(books / per_box), "boxes")
                 ''', output="4\n5\n5 boxes",
                      explain=["floor() rounds down to a whole number; ceil() rounds up.",
                               "23 / 5 is 4.6; you need 5 boxes, so ceil() is the right tool."])),
            page("from ... import",
                 text("from math import sqrt, pi brings in only the names you list, and you use them without math. in front."),
                 code(r'''
                 from math import sqrt, pi
                 print(sqrt(81))
                 print(f"{pi:.2f}")
                 ''', output="9.0\n3.14")),
            page("import ... as",
                 text("import math as m loads math but lets you call it by a shorter name."),
                 code(r'''
                 import math as m
                 print(m.floor(9.99))
                 ''', output="9"),
                 bullets("import math: use math.sqrt(9).", "from math import sqrt: use sqrt(9).",
                         "import math as m: use m.sqrt(9)."),
                 code(r'''
                 import math
                 print(sqrt(16))
                 ''', output="NameError: name 'sqrt' is not defined", caption=LAST_LINE),
                 mistake("""Mixing the styles. After import math you must write math.sqrt(16); plain sqrt only works
                         after from math import sqrt.""")),
            page("Modules you can't add here",
                 text("""On a computer you can install extra modules with a tool called pip, and you can write your
                      own module file, like helpers.py, and import it. The PyPath app works offline and runs a single
                      main.py, so neither is possible in the Practical tab. This course only uses modules that come
                      with Python."""),
                 tip("""The Practical tab includes standard library modules such as math, random and datetime. A few
                     modules, like the ones for the internet, are blocked.""", title="In the app"),
                 key("import gives you ready-made, tested tools, so you don't have to write them yourself.")),
        ], [
            m("What does this print?", "7.0", ["7", "49", "NameError"],
              "math.sqrt() gives the square root as a float, so the square root of 49 prints as 7.0.",
              code_="import math\nprint(math.sqrt(49))", check="out",
              hint="Recall the kind of number math.sqrt(25) printed in the lesson.",
              fb=("Right: sqrt() always gives a float.",
                  ["The answer is 7, but sqrt() returns a float, so it shows a decimal point.",
                   "sqrt() finds the square root; it doesn't print the number you gave it.",
                   "math is imported and math.sqrt is written in full, so every name is known."])),
            m("Why does this code fail?", "After import math, sqrt must be written as math.sqrt",
              ["math has to be imported twice", "sqrt() only works on even numbers", "import must come after print()"],
              "import math makes the name math available. sqrt on its own is unknown: NameError.",
              code_="import math\nprint(sqrt(9))", check="err:NameError",
              hint="Think about which name import math actually makes available to your program.",
              fb=("Right: use math.sqrt, or change the import to from math import sqrt.",
                  ["One import is enough; the problem is how sqrt is written.",
                   "sqrt() works on any positive number, odd or even.",
                   "Imports go at the top, before you use the module."])),
            m("Which word fills the blank?", "from", ["import", "use", "get"],
              "from math import ceil brings ceil in directly, so it can be used without math. in front.",
              code_="____ math import ceil\nprint(ceil(2.1))",
              hint="The line names the module first, then the single tool you want to bring in.",
              fb=("Right: from math import ceil.",
                  ["import math import ceil is not valid Python; the first word is different.",
                   "Python has no use keyword for modules.",
                   "Python has no get keyword for modules."])),
            m("What does this print?", "7 8", ["8 7", "7 7", "8 8"],
              "floor() rounds 7.9 down to 7; ceil() rounds 7.1 up to 8.",
              code_="import math\nprint(math.floor(7.9), math.ceil(7.1))", check="out",
              hint="One of these functions always rounds down and the other always rounds up.",
              fb=("Right: floor goes down, ceil goes up.",
                  ["That would be normal rounding; floor() always goes down and ceil() always goes up.",
                   "ceil() rounds up, so 7.1 becomes 8.",
                   "floor() rounds down, so 7.9 becomes 7."])),
            m("What does import math as m do?", "Imports math and lets you call it m, as in m.sqrt(4)",
              ["Imports only a function called m", "Renames the math module file on your phone",
               "Multiplies every math result by m"],
              "as gives the module a nickname inside your program. The real module is unchanged.",
              hint="Compare it with plain import math and think about what the extra part changes.",
              fb=("Right: m becomes a short name for math.",
                  ["It imports the whole math module; m is just its new name.",
                   "Nothing on your phone changes; the nickname only exists while your program runs.",
                   "as only gives a name; it doesn't do any maths."])),
        ]),
        lesson("l9s2", "9.2", "random and datetime", "Make random choices and work with dates.", 9, [
            page("Random numbers and choices",
                 code(r'''
                 import random
                 dice = random.randint(1, 6)
                 print("You rolled", dice)
                 snacks = ["samosa", "idli", "poha"]
                 print(random.choice(snacks))
                 random.shuffle(snacks)
                 print(snacks)
                 ''', caption="Run it a few times: the results change.",
                      explain=["randint(1, 6) gives a whole number from 1 to 6. Unlike range(), both ends are included.",
                               "choice() picks one item from a list.", "shuffle() mixes up the list itself."]),
                 code(r'''
                 import random
                 cards = [1, 2, 3]
                 cards = random.shuffle(cards)
                 print(cards)
                 ''', output="None"),
                 mistake("""Writing cards = random.shuffle(cards). Like sort(), shuffle() changes the list and returns
                         None, so the list is lost. Just write random.shuffle(cards).""")),
            page("The same random numbers every time",
                 text("""random.seed() picks the starting point of the random numbers. With the same seed you get the
                      same numbers every run, which is handy for testing a game."""),
                 code(r'''
                 import random
                 random.seed(7)
                 print(random.randint(1, 100))
                 print(random.randint(1, 100))
                 ''', output="42\n20"),
                 key("Without a seed the numbers change every run; with the same seed they repeat exactly.")),
            page("Dates",
                 text("The datetime module has a date type. date(year, month, day) makes a date."),
                 code(r'''
                 from datetime import date
                 exam = date(2026, 3, 15)
                 print(exam)
                 print(exam.year, exam.month, exam.day)
                 ''', output="2026-03-15\n2026 3 15"),
                 code(r'''
                 from datetime import date
                 print(date.today())
                 ''', caption="Shows today's date, so the output depends on the day you run it."),
                 tip("Dates print as year-month-day, like 2026-03-15.")),
            page("Counting days",
                 code(r'''
                 from datetime import date, timedelta
                 start = date(2026, 1, 28)
                 print(start + timedelta(days=7))
                 holiday = date(2026, 3, 4)
                 gap = holiday - start
                 print(gap.days, "days to go")
                 ''', output="2026-02-04\n35 days to go",
                      explain=["timedelta(days=7) is a length of time; adding it moves the date on, into February.",
                               "Subtracting two dates gives a timedelta; .days is the number of days."]),
                 bullets("random.randint(a, b): a whole number from a to b.", "random.choice(list): one item.",
                         "random.shuffle(list): mixes the list.", "date(y, m, d), date.today(), timedelta(days=n).")),
        ], [
            m("Which values can random.randint(1, 3) give?", "1, 2 or 3",
              ["1 or 2 only", "0, 1 or 2", "Any decimal number between 1 and 3"],
              "randint() includes both ends, so 1, 2 and 3 are all possible.",
              hint="Remember how randint() treats its second number compared with range().",
              fb=("Right: both ends are included.",
                  ["That is how range(1, 3) works; randint() includes the end number too.",
                   "randint(1, 3) starts at 1, not 0.",
                   "randint() only gives whole numbers."])),
            m("Why does this print None?", "shuffle() changes the list itself and returns None",
              ["shuffle() deletes the list", "Lists of letters can't be shuffled", "random must be imported twice"],
              "shuffle() works like sort(): it changes the list and gives back None, which is then stored in deck.",
              code_='import random\ndeck = ["A", "K", "Q"]\ndeck = random.shuffle(deck)\nprint(deck)',
              hint="Think about what shuffle() gives back, and what deck = then stores.",
              fb=("Right: deck = stores the None that shuffle() returns.",
                  ["shuffle() keeps every item; it only changes their order.",
                   "Any list can be shuffled, whatever is inside it.",
                   "One import is enough to use random.shuffle()."])),
            m("What does this print?", "True", ["False", "It depends on the run", "TypeError"],
              "Both numbers come right after random.seed(5), so they are the same number: a == b is True.",
              code_="import random\nrandom.seed(5)\na = random.randint(1, 1000)\nrandom.seed(5)\nb = random.randint(1, 1000)\nprint(a == b)",
              check="out",
              hint="Notice that the same seed is set again before the second number is made.",
              fb=("Right: the same seed gives the same number.",
                  ["Setting the same seed again restarts the same sequence, so a and b match.",
                   "A seed makes the numbers repeat exactly, on every run.",
                   "Comparing two numbers with == is always allowed."])),
            m("What does this print?", "red", ["['red']", "r", "0"],
              "choice() picks one item from the list. With only one item, it must pick red.",
              code_='import random\nprint(random.choice(["red"]))', check="out",
              hint="Count how many items the list has to choose from.",
              fb=("Right: the only item in the list is picked.",
                  ["choice() returns one item, not the whole list.",
                   "choice() picks an item from the list, not a letter from the item.",
                   "choice() returns the item itself, not its position."])),
            m("What does this print?", "12", ["25", "2026-12-25", "December"],
              "date(2026, 12, 25) is year 2026, month 12, day 25, so .month is 12.",
              code_="from datetime import date\nd = date(2026, 12, 25)\nprint(d.month)", check="out",
              hint="date() takes its three numbers in the order year, month, day.",
              fb=("Right: the month is the second number.",
                  ["25 is the day; the month is the middle number.",
                   "That is what print(d) shows; .month gives just one part.",
                   ".month is a number, not the month's name."])),
            m("What does this print?", "2026-02-02", ["2026-01-33", "2026-02-03", "2026-01-03"],
              "January has 31 days, so 3 days after 30 January is 2 February.",
              code_="from datetime import date, timedelta\nd = date(2026, 1, 30)\nprint(d + timedelta(days=3))",
              check="out",
              hint="Count forward one day at a time and remember how many days January has.",
              fb=("Right: 31 January, 1 February, 2 February.",
                  ["Dates move into the next month; there is no 33 January.",
                   "Count again: January has 31 days, so it is one day earlier than that.",
                   "Adding days moves the date forward, it doesn't change the month back."])),
            m("What does this print?", "9", ["10", "8", "-9"],
              "From 1 May to 10 May is 9 days, so gap.days is 9.",
              code_="from datetime import date\ngap = date(2026, 5, 10) - date(2026, 5, 1)\nprint(gap.days)", check="out",
              hint="Subtract the day numbers, because both dates are in the same month.",
              fb=("Right: 10 - 1 is 9 days.",
                  ["Subtracting dates gives the gap between them, not the number of dates counted.",
                   "Check the subtraction again: 10 - 1.",
                   "The later date comes first, so the gap is positive."])),
        ]),
      ],
    }

    # ───────────────────────────── LEVEL 10: FILES ─────────────────────────────
    level10 = {
      "id": "l10", "number": 10, "title": "Working with Files (Advanced)",
      "description": "Advanced: save text to files, read it back, add to it and handle files that don't exist.",
      "available": True, "advanced": True,
      "subLevels": [
        lesson("l10s1", "10.1", "Writing and reading files", "Save text in a file and read it back.", 8, [
            page("Why files?",
                 text("""Variables disappear when a program ends. A file keeps text so the program can read it
                      again. Working with a file has three steps: open it, read or write, then close it."""),
                 code(r'''
                 file = open("notes.txt", "w")
                 file.write("Buy milk")
                 file.close()
                 print("Saved!")
                 ''', output="Saved!",
                      explain=['open("notes.txt", "w") opens the file for writing and creates it if needed.',
                               "write() puts text into the file; close() finishes and saves it."]),
                 tip("""In the Practical tab, files live in the app's private scratch folder, which is emptied when
                     each run ends. Write and read a file in the same program. Use simple names like notes.txt; other
                     folders on your phone are blocked.""", title="In the app")),
            page("Writing lines and reading them",
                 text(r'write() does not start a new line by itself. "\n" inside a string means "new line".'),
                 code(r'''
                 file = open("shopping.txt", "w")
                 file.write("milk\n")
                 file.write("bread\n")
                 file.close()

                 file = open("shopping.txt", "r")
                 print(file.read())
                 file.close()
                 ''', output="milk\nbread",
                      explain=['"r" opens the file for reading.', "read() gives back all the text in the file as one string."]),
                 bullets('"w": write. Creates the file, or empties it if it exists.', '"r": read. This is the default, so open("shopping.txt") also reads.')),
            page('"w" replaces everything',
                 code(r'''
                 file = open("score.txt", "w")
                 file.write("10")
                 file.close()
                 file = open("score.txt", "w")
                 file.write("25")
                 file.close()
                 file = open("score.txt")
                 print(file.read())
                 file.close()
                 ''', output="25", explain=['Opening with "w" again wiped the 10 before 25 was written.']),
                 key('Opening a file with "w" deletes what was in it.')),
            page("Files hold text",
                 text("Files store text only. Turn numbers into text with str() before writing, and back with int() after reading."),
                 code(r'''
                 file = open("score.txt", "w")
                 file.write(str(42))
                 file.close()
                 file = open("score.txt")
                 score = int(file.read())
                 file.close()
                 print(score + 8)
                 ''', output="50")),
            page("Numbers need str()",
                 text("Passing a number straight to write() fails, because write() only accepts strings."),
                 code(r'''
                 file = open("score.txt", "w")
                 file.write(42)
                 ''', output="TypeError: write() argument must be str, not int", caption=LAST_LINE),
                 mistake("""Writing a number directly: file.write(42) is a TypeError, because files store text.
                         Write str(42) or an f-string, and use int() on the text after reading.""")),
        ], [
            m("What does this print?", "Hi", ["a.txt", "w", "Nothing, the file is empty"],
              "The file is written with Hi and closed, then opened again and read.",
              code_='f = open("a.txt", "w")\nf.write("Hi")\nf.close()\nf = open("a.txt", "r")\nprint(f.read())\nf.close()',
              check="out",
              hint="Follow the text from write() into the file and then back out with read().",
              fb=("Right: read() gives back what was written.",
                  ["read() returns what is inside the file, not its name.",
                   '"w" is the mode used to open the file; it is not written into it.',
                   "close() saves the text, so the file holds Hi when it is read."])),
            m("What does this print?", "two", ["one", "onetwo", "one\ntwo"],
              'The second open(..., "w") empties the file, so only two is left.',
              code_='f = open("n.txt", "w")\nf.write("one")\nf.close()\nf = open("n.txt", "w")\nf.write("two")\nf.close()\nf = open("n.txt")\nprint(f.read())\nf.close()',
              check="out",
              hint="Think about what happens to old text when a file is opened with this mode again.",
              fb=("Right: \"w\" wiped one before two was written.",
                  ['Opening with "w" again removes the old text, so one is gone.',
                   '"w" doesn\'t keep old text, so nothing is joined together.',
                   'Nothing adds a new line, and "w" removed one anyway.'])),
            m('What does mode "w" do if the file already exists?', "Empties it and starts writing from the beginning",
              ["Adds the new text to the end", "Raises an error", "Opens it for reading only"],
              '"w" always starts with an empty file. The old contents are lost.',
              hint="Remember what happened to the score of 10 in the lesson.",
              fb=("Right: the old text is wiped.",
                  ['"w" never keeps the old text; it starts the file again.',
                   '"w" works on files that exist and creates files that don\'t.',
                   'Reading is "r"; "w" is for writing.'])),
            m("Why does this code fail?", "write() only accepts text, so use str(100)",
              ['The file must be opened with "r"', "100 is too big to store in a file", "close() must come before write()"],
              "Files store text. write(100) passes an int, which is a TypeError.",
              code_='f = open("total.txt", "w")\nf.write(100)\nf.close()', check="err:TypeError",
              hint="Think about what kind of value write() expects to receive.",
              fb=("Right: convert the number to text first.",
                  ['"r" is for reading; writing needs "w".',
                   "The size isn't the problem; the type is.",
                   "close() comes last; after it, you can't write any more."])),
            m("What goes in the blank to open log.txt for writing?", '"w"', ['"r"', '"write"', "w"],
              'The mode is a short string: "w" for write.',
              code_='f = open("log.txt", ____)\nf.write("started")\nf.close()',
              hint="The mode is a single letter, written as a string.",
              fb=("Right: \"w\" opens the file for writing.",
                  ['"r" opens for reading, and write() would fail.',
                   "The mode is one letter, not the whole word.",
                   "Without quotes, w is a variable name that doesn't exist."])),
            m("What does this print?", "55", ["10", "5", "TypeError"],
              'read() always gives back text, so text is "5", and "5" + "5" joins them: 55.',
              code_='f = open("n.txt", "w")\nf.write("5")\nf.close()\nf = open("n.txt")\ntext = f.read()\nf.close()\nprint(text + "5")',
              check="out",
              hint="Ask what type of value read() gives back, even when it looks like a number.",
              fb=("Right: two strings are joined.",
                  ["read() gives back text, so + joins instead of adding.",
                   "Both parts are strings, so both appear in the result.",
                   'text and "5" are both strings, and + can join two strings.'])),
        ]),
        lesson("l10s2", "10.2", "with, append and lines", "Close files automatically, add to files and read them line by line.", 9, [
            page("The with statement",
                 text("with opens a file for the indented block and closes it automatically when the block ends."),
                 code(r'''
                 with open("diary.txt", "w") as file:
                     file.write("Day 1: learned files\n")

                 with open("diary.txt") as file:
                     print(file.read())
                 ''', output="Day 1: learned files",
                      explain=["as file gives the open file a name.", "No close() needed: the block ending closes it."]),
                 key("Prefer with: the file is always closed for you, even if an error happens inside the block.")),
            page('Adding to a file with "a"',
                 code(r'''
                 with open("log.txt", "w") as f:
                     f.write("start\n")
                 with open("log.txt", "a") as f:
                     f.write("more\n")
                     f.write("end\n")
                 with open("log.txt") as f:
                     print(f.read())
                 ''', output="start\nmore\nend"),
                 bullets('"r": read (the default).', '"w": write, wiping the file first.', '"a": append, adding to the end.')),
            page("Reading line by line",
                 code(r'''
                 with open("names.txt", "w") as f:
                     f.write("Asha\nRavi\nMeera\n")

                 with open("names.txt") as f:
                     first = f.readline()
                     print(first.strip())
                     for line in f:
                         print("-", line.strip())
                 ''', output="Asha\n- Ravi\n- Meera",
                      explain=[r"readline() reads one line, including its \n at the end.",
                               "A for loop over a file gives one line at a time and continues where readline() stopped.",
                               r"strip() removes the \n."])),
            page("Lines into numbers",
                 code(r'''
                 with open("marks.txt", "w") as f:
                     for mark in [72, 95, 48]:
                         f.write(f"{mark}\n")

                 total = 0
                 with open("marks.txt") as f:
                     for line in f:
                         total += int(line.strip())
                 print("Total:", total)
                 ''', output="Total: 215"),
                 code(r'''
                 with open("pets.txt", "w") as f:
                     f.write("cat\ndog\n")
                 with open("pets.txt") as f:
                     for line in f:
                         print(line)
                 ''', output="cat\n\ndog"),
                 mistake(r"""Forgetting that every line ends with \n. print(line) then adds a second new line, so you
                         get blank lines. Use line.strip()."""),
                 tip("Lines from a file are always text. Use int() or float() to do maths with them.")),
        ], [
            m("What does this print?", "AB", ["B", "A", "A\nB"],
              '"a" adds to the end of the file, so B goes after A.',
              code_='with open("t.txt", "w") as f:\n    f.write("A")\nwith open("t.txt", "a") as f:\n    f.write("B")\nwith open("t.txt") as f:\n    print(f.read())',
              check="out",
              hint="Compare what the second mode does to existing text with what the first mode does.",
              fb=("Right: append keeps A and adds B.",
                  ['"a" doesn\'t wipe the file; that is what "w" does.',
                   '"a" adds B to the end instead of ignoring it.',
                   "write() adds no new line by itself, so A and B sit side by side."])),
            m("What does with do when its indented block ends?", "Closes the file automatically",
              ["Opens the file a second time", "Makes the file read-only", "Deletes the file"],
              "with closes the file for you when the block finishes.",
              hint="Think about which step you no longer need to write yourself when using with.",
              fb=("Right: no close() needed.",
                  ["The file is opened once, at the start of the with block.",
                   "The mode decides reading or writing; with doesn't change it.",
                   "The file and its text stay; with only closes it."])),
            m("What does this print?", "green", ["red", "red\ngreen", "blue"],
              "The first readline() reads red. The second readline() reads the next line, green.",
              code_='with open("c.txt", "w") as f:\n    f.write("red\\ngreen\\nblue\\n")\nwith open("c.txt") as f:\n    f.readline()\n    print(f.readline().strip())',
              check="out",
              hint="Each readline() call carries on from where the previous one stopped.",
              fb=("Right: the second line is green.",
                  ["red was read by the first readline(), which isn't printed.",
                   "Only the second readline() is printed, and it is one line.",
                   "Only two lines are read, so blue is never reached."])),
            m("What does this print?", "3", ["6", "1", "0"],
              "The file has three lines, so the loop runs three times.",
              code_='with open("d.txt", "w") as f:\n    f.write("x\\ny\\nz\\n")\ncount = 0\nwith open("d.txt") as f:\n    for line in f:\n        count += 1\nprint(count)',
              check="out",
              hint="Work out what one step of a for loop over a file gives you.",
              fb=("Right: one step per line.",
                  ["The loop counts lines, not characters.",
                   "The loop doesn't read the whole file at once; it goes line by line.",
                   "The file was written first, so it has lines to count."])),
            m("Why are there blank lines between the names?", r"Each line still ends with \n, so print adds a second new line",
              ['The file was opened with "a"', "for loops can't read files properly", "write() adds two new lines"],
              r"Each line keeps its \n; print() adds another. Print line.strip() instead.",
              code_='with open("p.txt", "w") as f:\n    f.write("Asha\\nRavi\\n")\nwith open("p.txt") as f:\n    for line in f:\n        print(line)',
              hint="Think about what is at the very end of every line read from the file.",
              fb=("Right: strip() removes the extra new line.",
                  ['The file is only read here, with the default "r" mode.',
                   "for loops are the normal way to read a file line by line.",
                   r"write() only adds what you give it: here one \n after each name."])),
            m("Which mode adds text to the end of a file without deleting what is there?", '"a"', ['"w"', '"r"', '"add"'],
              '"a" stands for append.',
              hint="This mode was used in the log example to add more and end.",
              fb=("Right: \"a\" appends.",
                  ['"w" wipes the file first.', '"r" is only for reading.', "Modes are single letters, not words."])),
            m("What does this print?", "10", ["46", "4\n6", "TypeError"],
              "Each line is turned into a number with int(), then added: 4 + 6 = 10.",
              code_='with open("n.txt", "w") as f:\n    f.write("4\\n6\\n")\ntotal = 0\nwith open("n.txt") as f:\n    for line in f:\n        total += int(line.strip())\nprint(total)',
              check="out",
              hint="Notice what int() does to each line before it is added to total.",
              fb=("Right: 4 + 6 is 10.",
                  ["int() turns each line into a number, so they are added, not joined.",
                   "Only total is printed, once, after the loop.",
                   "int() makes each line a number, and adding numbers is fine."])),
            m("Which statement is true?", "A for loop over a file gives one line at a time, as text",
              ["A for loop over a file gives one character at a time", "readline() reads the whole file at once",
               "Lines that look like numbers are read as numbers"],
              "Looping over a file gives lines, and every line is a string.",
              hint="Think about what the name line in for line in f actually holds.",
              fb=("Right: lines, and always strings.",
                  ["Looping over a file gives whole lines, not single characters.",
                   "read() reads everything; readline() reads one line.",
                   "Everything read from a file is text; use int() to get a number."])),
        ]),
        lesson("l10s3", "10.3", "Files that might not exist", "Handle missing files with FileNotFoundError and os.path.exists().", 7, [
            page("FileNotFoundError",
                 text('''Reading a file that doesn't exist raises FileNotFoundError. "w" and "a" create missing
                      files, but "r" can't.'''),
                 code(r'''
                 with open("missing.txt") as f:
                     print(f.read())
                 ''', output="FileNotFoundError: [Errno 2] No such file or directory: 'missing.txt'", caption=LAST_LINE),
                 code(r'''
                 try:
                     with open("settings.txt") as f:
                         print(f.read())
                 except FileNotFoundError:
                     print("No settings yet, using defaults")
                 ''', output="No settings yet, using defaults"),
                 bullets("Catch FileNotFoundError when a file might be missing.", "os.path.exists(name) checks first."),
                 code(r'''
                 import os
                 print(os.path.exists("todo.txt"))
                 with open("todo.txt", "w") as f:
                     f.write("learn files\n")
                 print(os.path.exists("todo.txt"))
                 ''', output="False\nTrue", explain=["os.path.exists() gives True if the file is there and False if not."])),
            page("Load or start fresh",
                 code(r'''
                 def load_tasks(name):
                     tasks = []
                     try:
                         with open(name) as f:
                             for line in f:
                                 tasks.append(line.strip())
                     except FileNotFoundError:
                         print("No saved tasks, starting fresh")
                     return tasks

                 print(load_tasks("tasks.txt"))
                 with open("tasks.txt", "w") as f:
                     f.write("study\nshop\n")
                 print(load_tasks("tasks.txt"))
                 ''', output="No saved tasks, starting fresh\n[]\n['study', 'shop']"),
                 mistake('''Opening a file with "w" just to see if it exists. "w" creates an empty file and wipes any
                         old text. To read safely, use "r" with try/except FileNotFoundError, or check
                         os.path.exists() first.'''),
                 key("Expect missing files: catch FileNotFoundError or check with os.path.exists() before reading."),
                 tip("In the Practical tab every run starts with an empty scratch folder, so a file is always missing until your program writes it.", title="In the app")),
        ], [
            m("Which error does this code cause?", "FileNotFoundError", ["NameError", "KeyError", "ValueError"],
              "Opening a file for reading when it doesn't exist raises FileNotFoundError.",
              code_='f = open("nope.txt")', check="err:FileNotFoundError",
              hint="Think about which mode open() uses by default and whether the file is there.",
              fb=("Right: the file isn't there to read.",
                  ["open and f are fine names; it is the file that is missing.",
                   "KeyError is for dictionaries; this is a file.",
                   "The file name is a valid string; the file just doesn't exist."])),
            m("What does this print?", "missing", ["found", "found\nmissing", "x.txt"],
              "x.txt was never created, so open() fails and the except block prints missing.",
              code_='try:\n    with open("x.txt") as f:\n        print("found")\nexcept FileNotFoundError:\n    print("missing")',
              check="out",
              hint="Every run starts with an empty folder, so check whether x.txt was ever written.",
              fb=("Right: the file doesn't exist, so except runs.",
                  ['open() fails first, so print("found") never runs.',
                   "The error happens before the found line, so only one message prints.",
                   "Neither print shows the file name."])),
            m("What does this print?", "True", ["False", "hi", "FileNotFoundError"],
              '"a" creates the file when it is missing, so it exists afterwards.',
              code_='import os\nwith open("new.txt", "a") as f:\n    f.write("hi")\nprint(os.path.exists("new.txt"))',
              check="out",
              hint="Remember which modes create a file when it doesn't exist yet.",
              fb=("Right: \"a\" created the file.",
                  ['"a" creates a missing file, so it exists now.',
                   "os.path.exists() answers True or False; it doesn't read the file.",
                   'Only reading a missing file fails; "a" creates it.'])),
            m("Which statement is true?", 'Opening a file with "w" creates it if missing and wipes it if it exists',
              ['"r" mode creates the file if it is missing', "os.path.exists() deletes the file",
               'FileNotFoundError can only happen in "w" mode'],
              '"w" always gives you an empty file, which is why it is a bad way to check for a file.',
              hint="Think about why the lesson warns against using this mode just to check for a file.",
              fb=("Right: that is why \"w\" is risky for checking.",
                  ['"r" raises FileNotFoundError when the file is missing.',
                   "os.path.exists() only looks; it never changes anything.",
                   'Missing files cause the error when reading, because "w" creates them.'])),
            m("In the Practical tab, what happens to files your program writes?",
              "They are deleted when the run ends, so read them in the same program",
              ["They are kept in your phone's Downloads folder", "They are uploaded to the internet",
               "The next program you run can read them"],
              "The scratch folder is emptied after every run, so files only last while your program runs.",
              hint="Recall the In the app tips about the scratch folder in this level.",
              fb=("Right: the scratch folder is emptied after each run.",
                  ["Files stay in the app's private scratch folder; other folders are blocked.",
                   "PyPath works offline and never uploads your files.",
                   "Each run starts with an empty scratch folder."])),
        ]),
      ],
    }

    # ───────────────────────────── LEVEL 11: CLASSES ─────────────────────────────
    level11 = {
      "id": "l11", "number": 11, "title": "Classes and Objects (Advanced)",
      "description": "Advanced: create your own types with classes, give objects data and actions, and print them nicely.",
      "available": True, "advanced": True,
      "subLevels": [
        lesson("l11s1", "11.1", "Classes and objects", "Why classes help, and how to make your first one.", 7, [
            page("Why classes?",
                 text("""So far you have stored data in variables, lists and dictionaries. Data about one thing can
                      end up spread over many variables:"""),
                 code(r'''
                 student1_name = "Asha"
                 student1_marks = [72, 95]
                 student2_name = "Ravi"
                 student2_marks = [64, 81]
                 print(student1_name, sum(student1_marks))
                 ''', output="Asha 167"),
                 text("That works, but it gets messy fast. A class keeps everything about one kind of thing together."),
                 bullets("A class is a blueprint, like the plan for a house.", "An object is one thing built from it, like one house.",
                         "One class can make many objects.")),
            page("Your first class",
                 code(r'''
                 class Dog:
                     sound = "Woof"

                 rex = Dog()
                 bella = Dog()
                 rex.name = "Rex"
                 bella.name = "Bella"
                 print(rex.name, rex.sound)
                 print(bella.name, bella.sound)
                 print(type(rex))
                 ''', output="Rex Woof\nBella Woof\n<class '__main__.Dog'>",
                      explain=["class Dog: starts the class. Class names use CapitalWords.",
                               "Dog() with brackets creates a new object.",
                               "A value stored on an object is called an attribute; you read it with a dot.",
                               "Each object keeps its own attributes, and __main__ means the class is in your program."]),
                 key("Class = blueprint. Object = one thing made from it, with its own attributes.")),
            page("Don't forget the brackets",
                 text("Dog() makes a new object. Dog on its own is just the class, the blueprint itself."),
                 code(r'''
                 class Dog:
                     sound = "Woof"

                 a = Dog()
                 b = Dog
                 print(type(a))
                 print(type(b))
                 ''', output="<class '__main__.Dog'>\n<class 'type'>"),
                 mistake("""Forgetting the brackets: b = Dog stores the class itself, not a new dog. Always write
                         Dog() to create an object.""")),
        ], [
            m("Which statement about classes and objects is true?", "A class is a blueprint; an object is one thing made from it",
              ["A class and an object are two names for the same thing", "An object is a blueprint; classes are made from it",
               "A class can only ever make one object"],
              "Like a house plan and the houses built from it, one class can make many objects.",
              hint="Think back to the house plan comparison from the lesson.",
              fb=("Right: blueprint and the thing built from it.",
                  ["They are different: one is the plan, the other is what's built from it.",
                   "It is the other way round.",
                   "One class can make as many objects as you like, like rex and bella."])),
            m("What does this print?", "4", ["legs", "Cat", "tom"],
              "tom is a Cat object, so tom.legs reads the legs attribute: 4.",
              code_="class Cat:\n    legs = 4\n\ntom = Cat()\nprint(tom.legs)", check="out",
              hint="The dot reads a value stored on the object.",
              fb=("Right: tom.legs is 4.",
                  ["The dot gives the attribute's value, not its name.",
                   "Printing an attribute shows its value, not the class name.",
                   "tom.legs shows the attribute, not the variable name."])),
            m("This should make a Cat object, but type(pet) shows <class 'type'>. What's wrong?",
              "Cat needs brackets: pet = Cat()",
              ["Class names must be lowercase", "legs must be in quotes", "type() doesn't work on objects"],
              "Without brackets, pet is the class itself. Cat() creates an object.",
              code_="class Cat:\n    legs = 4\n\npet = Cat\nprint(type(pet))",
              hint="Compare how rex was created in the lesson with how pet is created here.",
              fb=("Right: brackets create the object.",
                  ["Class names use CapitalWords, so Cat is right.",
                   "4 is a number, so it doesn't need quotes.",
                   "type() works on anything; here it shows that pet is a class."])),
            m("What does this print?", "red 4", ["blue 4", "red blue", "red red"],
              "a has its own colour, red, and every Car has wheels = 4.",
              code_='class Car:\n    wheels = 4\n\na = Car()\nb = Car()\na.colour = "red"\nb.colour = "blue"\nprint(a.colour, b.wheels)',
              check="out",
              hint="Check which object each attribute is read from.",
              fb=("Right: a's colour and b's wheels.",
                  ["a.colour belongs to a, and b's colour doesn't change it.",
                   "The second value is b.wheels, not b.colour.",
                   "The second value is b.wheels, which is a number."])),
        ]),
        lesson("l11s2", "11.2", "__init__ and attributes", "Set up each object's data when it is created.", 9, [
            page("Setting up objects with __init__",
                 text("""Instead of adding attributes one by one, write an __init__ method (two underscores on each
                      side). It runs automatically every time an object is created."""),
                 code(r'''
                 class Dog:
                     def __init__(self, name, age):
                         self.name = name
                         self.age = age

                 rex = Dog("Rex", 3)
                 print(rex.name)
                 print(rex.age)
                 ''', output="Rex\n3",
                      explain=['Dog("Rex", 3) creates a Dog and runs __init__.', '"Rex" goes to name and 3 goes to age.',
                               "self is the new object; self.name = name stores the value on it."])),
            page("What is self?",
                 text("""self means "this object". Python fills it in for you: never pass it yourself. Each object
                      gets its own values."""),
                 code(r'''
                 class Student:
                     def __init__(self, name, grade):
                         self.name = name
                         self.grade = grade

                 a = Student("Asha", 9)
                 b = Student("Ravi", 10)
                 print(a.name, a.grade)
                 print(b.name, b.grade)
                 ''', output="Asha 9\nRavi 10"),
                 bullets('Student("Asha", 9): Python makes an empty Student.', 'It calls __init__ with self = that student, name = "Asha", grade = 9.',
                         "self.name = name saves the name on that student.")),
            page("Defaults and changes",
                 code(r'''
                 class BankAccount:
                     def __init__(self, owner, balance=0):
                         self.owner = owner
                         self.balance = balance

                 acc = BankAccount("Meera")
                 print(acc.balance)
                 acc.balance = 500
                 print(acc.owner, acc.balance)
                 ''', output="0\nMeera 500", explain=["balance=0 is a default value, like in normal functions.",
                                                      "Attributes can be changed later with =."]),
                 key("__init__ gives every new object its starting attributes.")),
            page("Missing arguments",
                 text("Creating an object must give __init__ every value it asks for, except ones with defaults."),
                 code(r'''
                 class Dog:
                     def __init__(self, name, age):
                         self.name = name
                         self.age = age

                 rex = Dog("Rex")
                 ''', output="TypeError: Dog.__init__() missing 1 required positional argument: 'age'", caption=LAST_LINE,
                      explain=["__init__ needs name and age, so creating a Dog needs both values."])),
            page("Forgetting self.",
                 text("Inside __init__, the value must be stored on self, or the object never gets it."),
                 code(r'''
                 class Dog:
                     def __init__(self, name):
                         name = name

                 rex = Dog("Rex")
                 print(rex.name)
                 ''', output="AttributeError: 'Dog' object has no attribute 'name'", caption=LAST_LINE),
                 mistake("""Forgetting self. in __init__. name = name only changes a local variable, so the object
                         never gets the attribute and reading rex.name gives an AttributeError. Write
                         self.name = name.""")),
            page("Two underscores",
                 code(r'''
                 class Dog:
                     def _init_(self, name):
                         self.name = name

                 rex = Dog("Rex")
                 ''', output="TypeError: Dog() takes no arguments", caption=LAST_LINE),
                 tip("__init__ has two underscores before and after. With one, Python doesn't recognise it.")),
        ], [
            m("When does __init__ run?", "Automatically, every time a new object is created",
              ["Only when you call rex.__init__() yourself", "Once, when the class is written", "When the program ends"],
              "Creating an object, like Dog(\"Rex\", 3), runs __init__ for that new object.",
              hint="Think about what happens straight after you write a class name with brackets.",
              fb=("Right: each new object runs it once.",
                  ["Python calls it for you when the object is created.",
                   "It runs once per object, not once for the class.",
                   "It is about setting up new objects, at the start."])),
            m("What does this print?", "700", ["Gita", "pages", "Book"],
              "700 is passed as pages and stored as self.pages.",
              code_='class Book:\n    def __init__(self, title, pages):\n        self.title = title\n        self.pages = pages\n\nb = Book("Gita", 700)\nprint(b.pages)',
              check="out",
              hint="Match each value in the brackets to the parameter in the same position.",
              fb=("Right: b.pages is 700.",
                  ["Gita is the title; the code prints pages.",
                   "The dot gives the value of the attribute, not its name.",
                   "b.pages is an attribute value, not the class name."])),
            m("Which word fills the blank?", "self", ["Item", "init", "price"],
              "self.price = price stores the value on the object being created.",
              code_="class Item:\n    def __init__(self, name, price):\n        self.name = name\n        ____.price = price",
              hint="Look at the line just above it, which does the same job for name.",
              fb=("Right: self.price stores it on the object.",
                  ["Item is the class; each object needs its own price.",
                   "init is not a name in this code.",
                   "price = price would only change the local variable."])),
            m("Why does this code fail?", "brand = brand makes a local variable; it should be self.brand = brand",
              ["Phone needs brackets in the class line", '"Nokia" needs to be a number', "print() can't show attributes"],
              "Without self., the value is never stored on the object, so p.brand gives an AttributeError.",
              code_='class Phone:\n    def __init__(self, brand):\n        brand = brand\n\np = Phone("Nokia")\nprint(p.brand)',
              check="err:AttributeError",
              hint="Check whether the value is actually stored on the object inside __init__.",
              fb=("Right: self. is missing.",
                  ["class Phone: is correct as written.",
                   "Attributes can hold any type of value.",
                   "print() can show attributes; the attribute just doesn't exist."])),
            m("What does this print?", "150", ["100", "200", "10050"],
              "a uses the default balance 100, b has 50: 100 + 50 = 150.",
              code_='class Account:\n    def __init__(self, owner, balance=100):\n        self.owner = owner\n        self.balance = balance\n\na = Account("Asha")\nb = Account("Ravi", 50)\nprint(a.balance + b.balance)',
              check="out",
              hint="Work out each object's balance separately before adding them.",
              fb=("Right: 100 + 50.",
                  ["That's only a's balance; b's 50 is added too.",
                   "b was given 50, so its default of 100 is replaced.",
                   "Both balances are numbers, so + adds them."])),
            m("Which error does this code cause?", "TypeError", ["AttributeError", "NameError", "ValueError"],
              "__init__ needs both name and age, but only one value was given: TypeError (missing argument).",
              code_='class Dog:\n    def __init__(self, name, age):\n        self.name = name\n        self.age = age\n\nrex = Dog("Rex")',
              check="err:TypeError",
              hint="Count the values __init__ needs and compare that with the values given.",
              fb=("Right: a required argument is missing.",
                  ["No attribute is read here; the problem is the missing argument.",
                   "Dog and rex are both known names.",
                   '"Rex" is a fine value; one value is missing.'])),
            m("What is self inside __init__?", "The new object that is being set up",
              ["The class itself", 'An extra value you must pass, like Dog(self, "Rex")', "A variable shared by all objects"],
              "self is the object being created, so self.name belongs to that one object.",
              hint="Think about where self.name ends up being stored.",
              fb=("Right: self is this object.",
                  ["self is one object, not the blueprint.",
                   "Python passes self for you; you never write it in the brackets.",
                   "Each object has its own self, so values aren't shared."])),
        ]),
        lesson("l11s3", "11.3", "Methods", "Give objects actions with methods that use self.", 10, [
            page("Methods: functions inside a class",
                 text("A method is a function written inside a class. Its first parameter is always self."),
                 code(r'''
                 class Dog:
                     def __init__(self, name):
                         self.name = name

                     def bark(self):
                         print(f"{self.name} says Woof!")

                 rex = Dog("Rex")
                 rex.bark()
                 ''', output="Rex says Woof!",
                      explain=["rex.bark() calls the method; Python passes rex in as self.",
                               "Inside the method, self.name reads rex's name."])),
            page("Methods that change attributes",
                 code(r'''
                 class BankAccount:
                     def __init__(self, owner, balance=0):
                         self.owner = owner
                         self.balance = balance

                     def deposit(self, amount):
                         self.balance += amount

                 acc = BankAccount("Asha")
                 acc.deposit(200)
                 acc.deposit(50)
                 print(acc.balance)
                 ''', output="250",
                      explain=["deposit(200) gives amount = 200; self is acc.", "Each call adds to the same balance."]),
                 bullets("Methods can take parameters after self.", "self.balance += amount changes the object's attribute.")),
            page("Methods that return values",
                 code(r'''
                 class Student:
                     def __init__(self, name, marks):
                         self.name = name
                         self.marks = marks

                     def average(self):
                         return sum(self.marks) / len(self.marks)

                     def passed(self):
                         return self.average() >= 50

                 s = Student("Ravi", [40, 70, 55])
                 print(s.average())
                 print(s.passed())
                 ''', output="55.0\nTrue",
                      explain=["average() returns a value, like a normal function.",
                               "A method can call another method with self.average()."])),
            page("Methods can check rules",
                 code(r'''
                 class BankAccount:
                     def __init__(self, owner, balance=0):
                         self.owner = owner
                         self.balance = balance

                     def withdraw(self, amount):
                         if amount > self.balance:
                             raise ValueError("not enough money")
                         self.balance -= amount

                 acc = BankAccount("Meera", 100)
                 acc.withdraw(30)
                 print(acc.balance)
                 try:
                     acc.withdraw(500)
                 except ValueError as error:
                     print("Sorry:", error)
                 print(acc.balance)
                 ''', output="70\nSorry: not enough money\n70"),
                 key("Put the rules inside the method, so every withdrawal is checked the same way.")),
            page("Common mistakes with self",
                 code(r'''
                 class Dog:
                     def bark():
                         print("Woof")

                 rex = Dog()
                 rex.bark()
                 ''', output="TypeError: Dog.bark() takes 0 positional arguments but 1 was given", caption=LAST_LINE),
                 code(r'''
                 class Dog:
                     def __init__(self, name):
                         self.name = name

                     def bark(self):
                         print(name, "says Woof")

                 rex = Dog("Rex")
                 rex.bark()
                 ''', output="NameError: name 'name' is not defined", caption=LAST_LINE),
                 mistake("""Forgetting self. Every method needs self as its first parameter, and inside a method you
                         read attributes with self.name, not name.""")),
        ], [
            m("What does this print?", "True", ["False", "None", "switch"],
              "switch() sets self.on to True on lamp.",
              code_="class Lamp:\n    def __init__(self):\n        self.on = False\n\n    def switch(self):\n        self.on = True\n\nlamp = Lamp()\nlamp.switch()\nprint(lamp.on)",
              check="out",
              hint="Follow what each method does to the attribute, in order.",
              fb=("Right: switch() changed it.",
                  ["It started False, but switch() changed it before printing.",
                   "lamp.on is an attribute with a value; it was set to a bool.",
                   "The code prints the attribute, not the method name."])),
            m("What does this print?", "50", ["30", "20", "0"],
              "Both calls add to the same money attribute: 0 + 20 + 30 = 50.",
              code_="class Wallet:\n    def __init__(self):\n        self.money = 0\n\n    def add(self, amount):\n        self.money += amount\n\nw = Wallet()\nw.add(20)\nw.add(30)\nprint(w.money)",
              check="out",
              hint="Each call changes the same attribute on the same object.",
              fb=("Right: 20 + 30.",
                  ["+= adds to the money, so the first 20 is kept.",
                   "The second call adds 30 more.",
                   "The method changes self.money, so it doesn't stay 0."])),
            m("Why does this code fail?", "bark needs self as its first parameter: def bark(self):",
              ["rex.bark() needs an argument", "Methods can't use print()", "A class needs __init__ before it can have methods"],
              "Python passes rex in as self, but bark() accepts no parameters: TypeError.",
              code_='class Dog:\n    def bark():\n        print("Woof")\n\nrex = Dog()\nrex.bark()', check="err:TypeError",
              hint="Python passes one value to every method call, even with empty brackets.",
              fb=("Right: add self.",
                  ["Python already passes rex; the method has no parameter to receive it.",
                   "Methods can print like any function.",
                   "Methods work without __init__; the missing parameter is the problem."])),
            m("Which word fills the blank?", "self", ["width, height", "Rectangle", "this"],
              "Every method's first parameter is self, which gives access to self.width and self.height.",
              code_="class Rectangle:\n    def __init__(self, width, height):\n        self.width = width\n        self.height = height\n\n    def area(____):\n        return self.width * self.height",
              hint="The method body reads attributes through one particular name.",
              fb=("Right: area(self).",
                  ["The values are read from the object, so they aren't parameters.",
                   "The class name isn't used as a parameter.",
                   "Some languages use this, but Python uses a different word."])),
            m("What does this print?", "13", ["12", "7", "None"],
              "area() returns 3 * 4 = 12, and 12 + 1 = 13.",
              code_="class Rectangle:\n    def __init__(self, w, h):\n        self.w = w\n        self.h = h\n\n    def area(self):\n        return self.w * self.h\n\nr = Rectangle(3, 4)\nprint(r.area() + 1)",
              check="out",
              hint="Work out what the method returns, then do the rest of the line.",
              fb=("Right: 12 + 1.",
                  ["Don't forget the + 1 after the method call.",
                   "area() multiplies, it doesn't add.",
                   "area() has a return, so it gives back a number."])),
            m("What does this print?", "Error: empty\n0", ["0", "Error: empty\n-1", "-1"],
              "The first take() makes items 0. The second raises ValueError, which is caught, so items stays 0.",
              code_='class Box:\n    def __init__(self, items):\n        self.items = items\n\n    def take(self):\n        if self.items == 0:\n            raise ValueError("empty")\n        self.items -= 1\n\nb = Box(1)\nb.take()\ntry:\n    b.take()\nexcept ValueError as e:\n    print("Error:", e)\nprint(b.items)',
              check="out",
              hint="Track the value of items after each call, and remember what raise skips.",
              fb=("Right: the error is printed and items stays 0.",
                  ["The second take() raises an error, which is caught and printed.",
                   "raise stops the method before -= 1 runs.",
                   "raise stops the method, and the caught error is printed first."])),
            m("Why does this code fail?", "Inside the method it must be self.name, not name",
              ["greet() needs a return", "Cat needs a second parameter", "print() can't take two values"],
              "name only existed inside __init__. Other methods read it from the object as self.name.",
              code_='class Cat:\n    def __init__(self, name):\n        self.name = name\n\n    def greet(self):\n        print("Hi,", name)\n\nc = Cat("Tom")\nc.greet()',
              check="err:NameError",
              hint="Think about where name was stored when the object was set up.",
              fb=("Right: read it from self.",
                  ["A method doesn't need return to print.",
                   "One parameter is all this Cat needs.",
                   "print() can show several values separated by commas."])),
            m("Which statement is true?", "When you call rex.bark(), Python passes rex in as self",
              ["You must write rex.bark(rex)", "self is a new, empty object each time a method is called",
               "Methods can only read attributes, never change them"],
              "The object before the dot becomes self inside the method.",
              hint="Think about what object is written before the dot in the call.",
              fb=("Right: the object before the dot is self.",
                  ["Python passes rex automatically, so you never write it.",
                   "self is the object the method was called on, with all its attributes.",
                   "Methods can change attributes, like deposit() changing balance."])),
        ]),
        lesson("l11s4", "11.4", "Many objects", "Store objects in lists and work with them together.", 7, [
            page("Objects in a list",
                 text("Real programs have many objects: a class of students, a cart of items. Keep them in a list."),
                 code(r'''
                 class Student:
                     def __init__(self, name, mark):
                         self.name = name
                         self.mark = mark

                 students = [Student("Asha", 82), Student("Ravi", 67), Student("Meera", 91)]
                 total = 0
                 best = students[0]
                 for s in students:
                     print(s.name, s.mark)
                     total += s.mark
                     if s.mark > best.mark:
                         best = s
                 print("Average:", total / len(students))
                 print("Top:", best.name)
                 print([s.name for s in students if s.mark >= 70])
                 ''', output="Asha 82\nRavi 67\nMeera 91\nAverage: 80.0\nTop: Meera\n['Asha', 'Meera']",
                      explain=["A list can hold objects like any other value.", "The loop variable s is one Student at a time.",
                               "best remembers the student with the highest mark so far."]),
                 bullets("Make many objects by calling the class many times.", "Keep them in a list and loop over it.",
                         "Use the dot on the loop variable: s.name.")),
            page("Same object or a new one?",
                 code(r'''
                 class Counter:
                     def __init__(self):
                         self.count = 0

                 a = Counter()
                 b = a
                 c = Counter()
                 b.count = 5
                 print(a.count, b.count, c.count)
                 ''', output="5 5 0",
                      explain=["b = a doesn't copy: a and b are two names for the same object.",
                               "c = Counter() is a separate object, so it still has 0."]),
                 key("Calling the class makes a new object; = only gives an existing object another name."),
                 mistake("""Thinking b = a makes a copy. Changing b.count also changes a.count, because they are the
                         same object. Call the class again to get a separate object.""")),
        ], [
            m("What does this print?", "50", ["2", "1040", "pen book"],
              "The loop adds each item's price: 10 + 40 = 50.",
              code_='class Item:\n    def __init__(self, name, price):\n        self.name = name\n        self.price = price\n\ncart = [Item("pen", 10), Item("book", 40)]\ntotal = 0\nfor item in cart:\n    total += item.price\nprint(total)',
              check="out",
              hint="Each round of the loop adds one object's price to total.",
              fb=("Right: 10 + 40.",
                  ["The loop adds prices, not a count of items.",
                   "The prices are numbers, so they are added.",
                   "Only total is printed, and it holds prices."])),
            m("What does this print?", "9", ["1", "10", "AttributeError"],
              "y = x makes y another name for the same Box, so changing y.size changes x.size.",
              code_="class Box:\n    def __init__(self):\n        self.size = 1\n\nx = Box()\ny = x\ny.size = 9\nprint(x.size)",
              check="out",
              hint="Ask whether y = x creates a second object or not.",
              fb=("Right: x and y are the same object.",
                  ["y = x didn't make a copy, so x changed too.",
                   "size was replaced with 9, not increased.",
                   "x has a size attribute, set in __init__."])),
            m("What does this print?", "['Rex', 'Bo']", ["['Tom']", "['Rex', 'Tom', 'Bo']", "[3, 5]"],
              "Only pets older than 2 are kept, and their names are collected.",
              code_='class Pet:\n    def __init__(self, name, age):\n        self.name = name\n        self.age = age\n\npets = [Pet("Rex", 3), Pet("Tom", 1), Pet("Bo", 5)]\nprint([p.name for p in pets if p.age > 2])',
              check="out",
              hint="Check each pet's age against the condition, then look at what is collected.",
              fb=("Right: Rex and Bo are older than 2.",
                  ["Tom is 1, so he fails the condition.",
                   "The if keeps only some pets.",
                   "The list collects p.name, not p.age."])),
            m("What does this print?", "2 1", ["3 3", "1 1", "2 2"],
              "a and b are separate objects: a ticked twice and b once.",
              code_="class Counter:\n    def __init__(self):\n        self.n = 0\n\n    def tick(self):\n        self.n += 1\n\na = Counter()\nb = Counter()\na.tick()\na.tick()\nb.tick()\nprint(a.n, b.n)",
              check="out",
              hint="Each object made by calling the class keeps its own count.",
              fb=("Right: they count separately.",
                  ["Each object has its own n; they don't share it.",
                   "a.tick() was called twice.",
                   "b.tick() was called only once."])),
            m("Which statement is true?", "Calling the class again, like Dog(\"Bella\"), always makes a new, separate object",
              ["b = a makes a copy of the object a", "All objects of a class share their attributes set in __init__",
               "A list can only hold one object of each class"],
              "Each call to the class makes a new object. = only adds another name.",
              hint="Think about the difference between c and b in the Counter example.",
              fb=("Right: a new call, a new object.",
                  ["b = a gives the same object a second name.",
                   "Attributes set with self belong to each object.",
                   "A list can hold as many objects as you like."])),
        ]),
        lesson("l11s5", "11.5", "Printing objects and why classes", "Use __str__ to print objects nicely, and see why classes help.", 8, [
            page("Printing an object",
                 text("""Printing an object you made shows something like <__main__.Dog object at 0x7f...>. The
                      number is a memory address, so it changes from run to run."""),
                 code(r'''
                 class Dog:
                     def __init__(self, name):
                         self.name = name

                 print(Dog("Rex"))
                 ''', caption="Prints something like <__main__.Dog object at 0x7f3a2c1d5e90>."),
                 text("Add a __str__ method to choose the text yourself. It must return a string."),
                 code(r'''
                 class Dog:
                     def __init__(self, name, age):
                         self.name = name
                         self.age = age

                     def __str__(self):
                         return f"{self.name} ({self.age} years)"

                 rex = Dog("Rex", 3)
                 print(rex)
                 print("My dog: " + str(rex))
                 print(f"Hello {rex}")
                 ''', output="Rex (3 years)\nMy dog: Rex (3 years)\nHello Rex (3 years)",
                      explain=["print(), str() and f-strings all use __str__ to turn the object into text."])),
            page("__str__ must return text",
                 code(r'''
                 class Dog:
                     def __init__(self, name):
                         self.name = name

                     def __str__(self):
                         print(self.name)

                 rex = Dog("Rex")
                 print(rex)
                 ''', output="Rex\nTypeError: __str__ returned non-string (type NoneType)", caption=LAST_LINE),
                 mistake("""Printing inside __str__ instead of returning the text. __str__ must return a string; if it
                         returns nothing, you get a TypeError.""")),
            page("Why use classes?",
                 code(r'''
                 class BankAccount:
                     def __init__(self, owner, balance=0):
                         self.owner = owner
                         self.balance = balance

                     def deposit(self, amount):
                         if amount <= 0:
                             raise ValueError("deposit must be positive")
                         self.balance += amount

                     def __str__(self):
                         return f"{self.owner}: Rs {self.balance}"

                 accounts = [BankAccount("Asha", 300), BankAccount("Ravi")]
                 accounts[1].deposit(120)
                 for acc in accounts:
                     print(acc)
                 ''', output="Asha: Rs 300\nRavi: Rs 120"),
                 bullets("Data and actions stay together: the balance lives with deposit().",
                         "Rules live in one place: every deposit is checked.",
                         "Code reads like English: acc.deposit(120).", "Making many objects is easy."),
                 key("Use a class when you have several things of the same kind, each with its own data and actions."),
                 tip("Classes can do much more, but you now know enough to organise real programs with them.")),
        ], [
            m("What does this print?", "Fruit: mango", ["mango", "Fruit", "<__main__.Fruit object>"],
              "print(f) uses __str__, which returns \"Fruit: \" + self.name.",
              code_='class Fruit:\n    def __init__(self, name):\n        self.name = name\n\n    def __str__(self):\n        return "Fruit: " + self.name\n\nf = Fruit("mango")\nprint(f)',
              check="out",
              hint="Printing an object uses the text that one special method returns.",
              fb=("Right: __str__ chooses the text.",
                  ["__str__ adds Fruit: in front of the name.",
                   "__str__ includes the name as well.",
                   "Because there is a __str__, Python uses it instead of the default text."])),
            m("When does Python call __str__?", "When the object is printed or turned into text with str()",
              ["When the object is created", "Every time an attribute changes", "Only when you write obj.__str__() yourself"],
              "print(), str() and f-strings call __str__ to get the object's text.",
              hint="Think about the three lines in the lesson that all showed the same text.",
              fb=("Right: whenever text is needed.",
                  ["Creating an object runs __init__, not __str__.",
                   "Changing attributes doesn't print anything.",
                   "Python calls it for you, for example inside print()."])),
            m("Why does this code fail?", "__str__ must return the text instead of printing it",
              ["__str__ needs a parameter called text", "Objects can never be printed", "print(rex) should be print(Dog)"],
              "This __str__ prints and returns nothing (None), so Python raises a TypeError.",
              code_='class Dog:\n    def __init__(self, name):\n        self.name = name\n\n    def __str__(self):\n        print(self.name)\n\nrex = Dog("Rex")\nprint(rex)',
              check="err:TypeError",
              hint="Look at what the special method gives back to Python.",
              fb=("Right: replace print with return.",
                  ["__str__ only needs self.",
                   "Objects can be printed, especially with a working __str__.",
                   "Printing the class wouldn't print the dog."])),
            m("Which word fills the blank?", "return", ["print", "self.str =", "str"],
              "__str__ must give back a string with return.",
              code_='class Book:\n    def __init__(self, title, author):\n        self.title = title\n        self.author = author\n\n    def __str__(self):\n        ____ f"{self.title} by {self.author}"',
              hint="The special method has to hand a string back to Python.",
              fb=("Right: return the text.",
                  ["print shows text but gives back None, which causes a TypeError.",
                   "Storing it in an attribute doesn't give it back.",
                   "str on its own does nothing with the text."])),
            m("What does this print?", "Winner: Asha=7", ["Winner: {p}", "Winner: p", "Winner: Asha"],
              "The f-string puts p in the text using its __str__, which returns Asha=7.",
              code_='class Player:\n    def __init__(self, name, score):\n        self.name = name\n        self.score = score\n\n    def __str__(self):\n        return f"{self.name}={self.score}"\n\np = Player("Asha", 7)\nprint(f"Winner: {p}")',
              check="out",
              hint="An f-string turns the object into text the same way print() does.",
              fb=("Right: the f-string uses __str__.",
                  ["The f in front fills in {p}.",
                   "{p} is replaced with the object's text, not its name.",
                   "__str__ includes the score too."])),
            m("Which is a good reason to use a class?", "It keeps related data and the actions that use it together",
              ["It makes programs run without any errors", "Every Python program must use a class",
               "Classes are needed to use lists and dictionaries"],
              "A class bundles data (attributes) and actions (methods), like balance and deposit().",
              hint="Think about what the BankAccount class kept in one place.",
              fb=("Right: data and actions together.",
                  ["Classes can still have errors, like a missing self.",
                   "Most of this course worked without classes.",
                   "You used lists and dictionaries long before classes."])),
        ]),
      ],
    }

    return [level8, level9, level10, level11]


# ───────────────────────────── CHEAT SHEETS ─────────────────────────────

CHEAT_SHEETS = {
    "l8": {
        "title": "Handling Errors",
        "sections": [
            section("Common errors",
                    item("print(score)", "NameError: the name doesn't exist."),
                    item('print("Age: " + 5)', "TypeError: wrong types for the job."),
                    item('int("ten")', "ValueError: right type, value can't be used."),
                    item("print(5 / 0)", "ZeroDivisionError, IndexError and KeyError: no such number, position or key.")),
            section("Catching errors",
                    item('try:\n    n = int("abc")\nexcept ValueError:\n    print("Not a number")',
                         "Catch a specific error.", "Not a number"),
                    item('try:\n    n = int("5")\nexcept ValueError:\n    print("bad")\nelse:\n    print(n)\nfinally:\n    print("done")',
                         "else: no error happened. finally: always runs.", "5\ndone")),
            section("Raising errors",
                    item('def set_age(age):\n    if age < 0:\n        raise ValueError("negative")\n    return age\n\ntry:\n    set_age(-1)\nexcept ValueError as e:\n    print("Problem:", e)',
                         "raise an error with your own message.", "Problem: negative")),
        ],
        "remember": ["Read a traceback from the bottom.", "Name the error after except.",
                     "Keep only the risky line inside try."],
    },
    "l9": {
        "title": "Using Modules",
        "sections": [
            section("Importing",
                    item("import math\nprint(math.sqrt(16))", "Use the module name and a dot.", "4.0"),
                    item("from math import ceil\nprint(ceil(4.2))", "Bring in one name.", "5"),
                    item("import math as m\nprint(m.floor(4.8))", "Give the module a short name.", "4")),
            section("random",
                    item("import random\nprint(random.randint(1, 6))", "A whole number from 1 to 6, both included."),
                    item('import random\nprint(random.choice(["a", "b"]))', "Pick one item from a list."),
                    item("import random\nrandom.seed(7)\nprint(random.randint(1, 100))", "The same seed gives the same numbers.", "42")),
            section("datetime",
                    item("from datetime import date, timedelta\nd = date(2026, 1, 30)\nprint(d + timedelta(days=3))",
                         "Make dates and move them on.", "2026-02-02"),
                    item("from datetime import date\nprint((date(2026, 5, 10) - date(2026, 5, 1)).days)",
                         "Days between two dates.", "9")),
        ],
        "remember": ["Imports go at the top of the program.",
                     "The app has Python's standard library only: no pip and no module files of your own."],
    },
    "l10": {
        "title": "Working with Files",
        "sections": [
            section("Writing and reading",
                    item('with open("notes.txt", "w") as f:\n    f.write("hello\\n")\nwith open("notes.txt") as f:\n    print(f.read())',
                         '"w" writes (wiping the file); "r" reads (the default).', "hello"),
                    item('with open("log.txt", "a") as f:\n    f.write("more\\n")', '"a" adds to the end and creates the file if missing.')),
            section("Line by line",
                    item('with open("n.txt", "w") as f:\n    f.write("4\\n6\\n")\ntotal = 0\nwith open("n.txt") as f:\n    for line in f:\n        total += int(line.strip())\nprint(total)',
                         "Loop over lines; strip() and convert them.", "10")),
            section("Missing files",
                    item('try:\n    with open("missing.txt") as f:\n        print(f.read())\nexcept FileNotFoundError:\n    print("No file yet")',
                         "Catch FileNotFoundError.", "No file yet"),
                    item('import os\nprint(os.path.exists("missing.txt"))', "Check before reading.", "False")),
        ],
        "remember": ["Files hold text: use str() to write numbers and int() after reading.",
                     "In the Practical tab, files are deleted when the run ends."],
    },
    "l11": {
        "title": "Classes and Objects",
        "sections": [
            section("Class, __init__ and attributes",
                    item('class Dog:\n    def __init__(self, name, age):\n        self.name = name\n        self.age = age\n\nrex = Dog("Rex", 3)\nprint(rex.name, rex.age)',
                         "__init__ sets up each new object; self is that object.", "Rex 3")),
            section("Methods",
                    item('class Account:\n    def __init__(self, balance=0):\n        self.balance = balance\n\n    def deposit(self, amount):\n        self.balance += amount\n\nacc = Account()\nacc.deposit(50)\nprint(acc.balance)',
                         "Methods take self first and use self.attribute.", "50")),
            section("__str__",
                    item('class Dog:\n    def __init__(self, name):\n        self.name = name\n\n    def __str__(self):\n        return "Dog " + self.name\n\nprint(Dog("Rex"))',
                         "__str__ returns the text that print() shows.", "Dog Rex")),
        ],
        "remember": ["Write Dog() with brackets to make an object.", "Every method needs self.",
                     "b = a gives the same object a second name; it doesn't copy it."],
    },
}

# ───────────────────────────── GLOSSARY ─────────────────────────────

GLOSSARY = [
    # Level 8
    term("Exception", "l8s1", "An error that happens while a program runs and stops it unless it is caught.", aliases=["error"]),
    term("Traceback", "l8s1", "The error report Python prints: where the error happened and, on the last line, what it was."),
    term("NameError", "l8s1", "The error you get when you use a name that was never created.", "print(score)"),
    term("TypeError", "l8s1", "The error you get when a value has the wrong type for the job, like text + a number."),
    term("ValueError", "l8s1", "The error you get when a value has the right type but can't be used, like int(\"ten\")."),
    term("ZeroDivisionError", "l8s1", "The error you get when you divide by zero."),
    term("try / except", "l8s2", "Runs the code in try; if a named error happens, runs the except block instead of stopping.",
         'try:\n    int("x")\nexcept ValueError:\n    print("bad")', "bad", aliases=["try", "except", "catch"]),
    term("finally", "l8s3", "A block after try that always runs, error or not."),
    term("raise", "l8s3", "Creates an error on purpose, like raise ValueError(\"too big\").", aliases=["throw"]),
    term("Input validation", "l8s4", "Checking what the user typed and asking again until it is valid.", aliases=["validate"]),
    # Level 9
    term("Module", "l9s1", "A file of ready-made Python code you load with import.", aliases=["library", "standard library"]),
    term("import", "l9s1", "Loads a module: import math, from math import sqrt or import math as m.",
         "import math\nprint(math.sqrt(9))", "3.0", aliases=["from", "as"]),
    term("random module", "l9s2", "Tools for random numbers and choices: randint(), choice(), shuffle() and seed().", aliases=["randint", "choice"]),
    term("datetime module", "l9s2", "Tools for dates: date(year, month, day), date.today() and timedelta(days=n).",
         "from datetime import date\nprint(date(2026, 3, 15))", "2026-03-15", aliases=["date", "timedelta"]),
    # Level 10
    term("open()", "l10s1", "Opens a file and returns a file object for reading or writing.", aliases=["file"]),
    term("File mode", "l10s1", "The second argument of open(): \"r\" read, \"w\" write (wipes the file), \"a\" append.",
         aliases=["mode", "append"]),
    term("with statement", "l10s2", "Opens a file for an indented block and closes it automatically afterwards.", aliases=["with"]),
    term("readline()", "l10s2", "Reads the next single line of a file, including its new line at the end."),
    term("FileNotFoundError", "l10s3", "The error you get when you try to read a file that doesn't exist."),
    term("os.path.exists()", "l10s3", "Gives True if a file exists and False if it doesn't.",
         'import os\nprint(os.path.exists("nothing.txt"))', "False"),
    # Level 11
    term("Class", "l11s1", "A blueprint for making objects of your own type.", aliases=["class"]),
    term("Object", "l11s1", "One thing made from a class, with its own attributes.", aliases=["instance"]),
    term("Attribute", "l11s1", "A value stored on an object, read with a dot like rex.name."),
    term("__init__", "l11s2", "The method that runs automatically to set up each new object.", aliases=["constructor"]),
    term("self", "l11s2", "Inside a method, the object the method is working on."),
    term("__str__", "l11s5", "A method that returns the text shown when an object is printed.",
         'class A:\n    def __str__(self):\n        return "an A"\n\nprint(A())', "an A"),
]
