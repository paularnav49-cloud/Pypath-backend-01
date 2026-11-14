"""Extra course content for PyPath v0.4.0, used by build_course.py.

- Level projects: one optional guided project for each of Levels 1-6 ("bonus" projects).
  They unlock when their level is finished and never block the main path.
- Cheat sheets: a one-page summary per level, unlocked when the level is finished.
- Glossary: searchable Python terms, each linked to the sub-level that teaches it.

Every code snippet here is executed by build_course.py's validation, so the outputs
shown in the app are always the real outputs.
"""
import textwrap


def lines(src):
    return textwrap.dedent(src).strip("\n").split("\n")


def out(src):
    return textwrap.dedent(src).strip("\n")


# ───────────────────────────── LEVEL PROJECTS ─────────────────────────────
# Each project: the finished program (finalCode), its exact output for the sample inputs,
# and build stages. A stage reveals some lines of finalCode when answered correctly.

def make_projects(stage):
    """stage = build_course.stage (n, title, instruction, lines, prompt, right, wrongs, explanation, why_wrong)."""
    projects = []

    # ── Level 1: Party Bill Splitter ──
    projects.append({
        "level": "l1", "title": "Party Bill Splitter", "file": "bill_splitter.py", "minutes": 8,
        "summary": "Ask for the bill and the number of friends, add a tip and split it fairly.",
        "briefing_title": "Build a Party Bill Splitter",
        "text": [
            "You and your friends had dinner. This program asks for the bill and how many people there are, adds a 10% tip and tells everyone what to pay.",
            "You build it one piece at a time. At each step you pick the right line of code and it appears in the program.",
        ],
        "why": [
            "Splitting a bill is something you'll really do",
            "It uses input(), type conversion, maths and f-strings: everything from Level 1",
        ],
        "concepts": [("l1s1", "print()"), ("l1s2", "Variables"), ("l1s3", "Data types"), ("l1s4", "Input and f-strings")],
        "inputs": ["Asha", "1200", "4"],
        "code": lines('''
            # Party Bill Splitter
            print("=== Bill Splitter ===")
            name = input("Your name: ")
            bill = float(input("Total bill (Rs): "))
            people = int(input("How many people? "))
            tip = bill * 0.1
            total = bill + tip
            share = total / people
            print(f"Thanks, {name}!")
            print(f"Tip (10%): Rs {tip}")
            print(f"Total with tip: Rs {total}")
            print(f"Each person pays: Rs {share}")
        '''),
        "output": out('''
            === Bill Splitter ===
            Your name: Asha
            Total bill (Rs): 1200
            How many people? 4
            Thanks, Asha!
            Tip (10%): Rs 120.0
            Total with tip: Rs 1320.0
            Each person pays: Rs 330.0
        '''),
        "stages": [
            stage(1, "Show a title",
                  "Every program should tell the user what it does. Print a title line first.",
                  [1, 2], "Which line prints the title?",
                  'print("=== Bill Splitter ===")',
                  ['print(=== Bill Splitter ===)', 'Print("=== Bill Splitter ===")', 'print "=== Bill Splitter ==="'],
                  "Text goes inside quotes, and print is written in lowercase with brackets (1.1).",
                  ["Without quotes Python tries to read === as code. This is a SyntaxError.",
                   "Python is case-sensitive: Print is not the same as print, so this is a NameError.",
                   "In Python 3, print needs brackets. This is a SyntaxError."]),
            stage(2, "Ask for a name",
                  "Ask the user for their name and keep the answer in a variable called name.",
                  [3], "Which line asks for the name and stores it?",
                  'name = input("Your name: ")',
                  ['input("Your name: ") = name', 'name = "input(Your name: )"', 'name == input("Your name: ")'],
                  "input() shows the question and gives back what was typed; = stores it in name (1.2, 1.4).",
                  ["The variable name must be on the left of =. This is a SyntaxError.",
                   "This stores the words input(Your name: ) as text. Nothing is asked.",
                   "== compares, it doesn't store. name doesn't exist yet, so this is a NameError."]),
            stage(3, "Read the bill as a number",
                  "The bill can have paise, like 1199.50, so it must become a decimal number before we do maths with it.",
                  [4], "Which line reads the bill correctly?",
                  'bill = float(input("Total bill (Rs): "))',
                  ['bill = input("Total bill (Rs): ")', 'bill = int(input("Total bill (Rs): "))',
                   'bill = float("Total bill (Rs): ")'],
                  "input() always gives text. float() turns it into a decimal number (1.3).",
                  ["The bill would stay text, so bill * 0.1 would be a TypeError.",
                   "int() fails on decimals: typing 1199.50 would be a ValueError.",
                   "This tries to turn the question itself into a number, which is a ValueError."]),
            stage(4, "Count the people",
                  "Now ask how many people are sharing. People come in whole numbers.",
                  [5], "Which line reads the number of people?",
                  'people = int(input("How many people? "))',
                  ['people = input(int("How many people? "))', 'people = int(input)("How many people? ")',
                   'people = str(input("How many people? "))'],
                  "int() wraps input() so the typed text becomes a whole number (1.4).",
                  ["The brackets are in the wrong order: int() runs on the question text first, a ValueError.",
                   "int(input) tries to turn the input function itself into a number. This is a TypeError.",
                   "str() keeps it as text, so total / people would be a TypeError."]),
            stage(5, "Do the maths",
                  "Work out a 10% tip, the total with the tip, and each person's share.",
                  [6, 7, 8], "Which three lines do the maths?",
                  "tip = bill * 0.1\ntotal = bill + tip\nshare = total / people",
                  ["tip = bill * 10%\ntotal = bill + tip\nshare = total / people",
                   "tip = bill * 0.1\ntotal = bill + tip\nshare = people / total",
                   "tip = bill * 0.1\ntotal = bill * tip\nshare = total / people"],
                  "10% is 0.1 of the bill. Add it to the bill, then divide by the number of people (1.3).",
                  ["% means remainder in Python, not percent. bill * 10% is a SyntaxError.",
                   "Backwards: 4 / 1320 gives a tiny number, not each person's share.",
                   "The tip must be added, not multiplied: 1200 * 120 is far too much."]),
            stage(6, "Show the results",
                  "Thank the user by name, then print the tip, the total and each share.",
                  [9, 10, 11, 12], "Which line thanks the user by name?",
                  'print(f"Thanks, {name}!")',
                  ['print("Thanks, {name}!")', 'print(f"Thanks, name!")', 'print(f"Thanks, " name "!")'],
                  "The f before the quotes lets {name} be replaced by the value (1.4).",
                  ["Without the f, Python prints the braces as they are: Thanks, {name}!",
                   "Without braces, the word name is printed instead of the value.",
                   "Text and a variable can't just sit next to each other. This is a SyntaxError."]),
        ],
        "walkthrough": [
            "Line 2 prints a title so the user knows what the program does.",
            "input() always gives text, so float() and int() turn the answers into numbers.",
            "The tip is 0.1 of the bill (10%). The total adds the tip; the share divides it by the people.",
            "f-strings put the values into the messages. Decimal results show .0, like 330.0.",
        ],
    })

    # ── Level 2: Movie Ticket Pricer ──
    projects.append({
        "level": "l2", "title": "Movie Ticket Pricer", "file": "ticket_pricer.py", "minutes": 10,
        "summary": "Work out a cinema ticket price from age and day using if, elif and else.",
        "briefing_title": "Build a Movie Ticket Pricer",
        "text": [
            "A cinema charges different prices for children, students, adults and seniors, with Rs 50 off on Tuesdays. Your program works out the price.",
            "Pick the right line at each stage and watch the program grow.",
        ],
        "why": [
            "Shops, buses and apps all use rules like these to set prices",
            "It practises comparisons and choosing between several options with elif",
        ],
        "concepts": [("l1s4", "Input"), ("l2s1", "Comparisons"), ("l2s2", "if and else"), ("l2s3", "elif")],
        "inputs": ["15", "Tue"],
        "code": lines('''
            # Movie Ticket Pricer
            print("=== Movie Tickets ===")
            age = int(input("Your age: "))
            day = input("Day (e.g. Tue): ")
            if age < 5:
                price = 0
            elif age < 18:
                price = 120
            elif age >= 60:
                price = 100
            else:
                price = 200
            if day == "Tue":
                price = price - 50
            if price < 0:
                price = 0
            print(f"Ticket price: Rs {price}")
            if price == 0:
                print("Enjoy the movie for free!")
            else:
                print("Enjoy the movie!")
        '''),
        "output": out('''
            === Movie Tickets ===
            Your age: 15
            Day (e.g. Tue): Tue
            Ticket price: Rs 70
            Enjoy the movie!
        '''),
        "stages": [
            stage(1, "Ask for the age",
                  "Print a title, then ask for the user's age. We'll compare it with numbers, so it must be a number.",
                  [1, 2, 3], "Which line reads the age as a number?",
                  'age = int(input("Your age: "))',
                  ['age = input("Your age: ")', 'age = int("Your age: ")', 'int(age) = input("Your age: ")'],
                  "int() turns the typed text into a whole number, so age < 5 works (1.4).",
                  ["age would be text, and comparing text with 5 is a TypeError.",
                   "This tries to turn the question into a number: a ValueError.",
                   "You can't assign to int(age). This is a SyntaxError."]),
            stage(2, "Ask for the day",
                  "Ask which day it is. Days are words like Tue, so keep the answer as text.",
                  [4], "Which line reads the day?",
                  'day = input("Day (e.g. Tue): ")',
                  ['day = int(input("Day (e.g. Tue): "))', 'day == input("Day (e.g. Tue): ")',
                   'input("Day (e.g. Tue): ") = day'],
                  "Words stay as text, so plain input() is right (1.4).",
                  ["int() can't turn Tue into a number. This is a ValueError.",
                   "== compares instead of storing. day doesn't exist yet: a NameError.",
                   "The variable must be on the left of =. This is a SyntaxError."]),
            stage(3, "Free for small children",
                  "Children under 5 go free. Start the price rules with this check.",
                  [5, 6], "Which line starts the check for small children?",
                  "if age < 5:",
                  ["if age < 5", "if age = 5:", "if age <= 5:"],
                  "if, a comparison, then a colon. The indented line below runs only when it's True (2.2).",
                  ["The colon at the end is missing. This is a SyntaxError.",
                   "= stores a value; comparisons use ==. This is a SyntaxError.",
                   "<= 5 would let 5-year-olds in free too. The rule is under 5."]),
            stage(4, "More age groups",
                  "Under 18 pays Rs 120 and 60 or over pays Rs 100. These checks should only happen if the earlier ones were False.",
                  [7, 8, 9, 10], "Which line checks the next age group?",
                  "elif age < 18:",
                  ["if age < 18:", "else age < 18:", "elif age < 18"],
                  "elif is checked only when everything above it was False, so a 3-year-old stays free (2.3).",
                  ["A new if is checked again: a 3-year-old would be set to 120 after getting 0.",
                   "else never takes a condition. This is a SyntaxError.",
                   "The colon is missing. This is a SyntaxError."]),
            stage(5, "Everyone else",
                  "Anyone who didn't match a rule above is an adult and pays Rs 200.",
                  [11, 12], "Which line catches everyone else?",
                  "else:",
                  ["else", "elif:", "else age >= 18:"],
                  "else needs no condition: it runs when every check above was False (2.2).",
                  ["The colon is missing. This is a SyntaxError.",
                   "elif must have a condition. This is a SyntaxError.",
                   "else never takes a condition. This is a SyntaxError."]),
            stage(6, "Tuesday discount",
                  "On Tuesdays every ticket is Rs 50 cheaper, but a price can never go below 0.",
                  [13, 14, 15, 16], "Which line gives the Tuesday discount?",
                  "    price = price - 50",
                  ["    price - 50", "    price = 50", "    price == price - 50"],
                  "Work out price - 50 and store it back in price (1.2). The next check stops it going below 0.",
                  ["This works out the answer but throws it away. The price doesn't change.",
                   "This sets every Tuesday ticket to Rs 50 instead of taking 50 off.",
                   "== only compares. The price doesn't change."]),
            stage(7, "Show the price",
                  "Print the price, then a special message when the ticket is free.",
                  [17, 18, 19, 20, 21], "Which line checks for a free ticket?",
                  "if price == 0:",
                  ["if price = 0:", 'if price == "0":', "if price < 0:"],
                  "== checks whether two values are equal (2.1).",
                  ["= stores; it can't be used in a condition. This is a SyntaxError.",
                   "price is the number 0, not the text \"0\", so this is never True.",
                   "The line above already stops the price going below 0, so this is never True."]),
        ],
        "walkthrough": [
            "int() turns the age into a number; the day stays as text.",
            "if / elif / elif / else picks exactly one price. Order matters: the first True check wins.",
            "On Tuesday, 50 is taken off. If that makes the price negative, it is set back to 0.",
            "A 15-year-old pays 120, minus 50 on Tuesday: Rs 70.",
        ],
    })

    # ── Level 3: Number Guessing Game ──
    projects.append({
        "level": "l3", "title": "Number Guessing Game", "file": "guessing_game.py", "minutes": 10,
        "summary": "Give the player five tries to guess a secret number, with hints and stars.",
        "briefing_title": "Build a Number Guessing Game",
        "text": [
            "The computer thinks of a number from 1 to 10. The player has five tries and gets a hint after each wrong guess.",
            "Fewer guesses earn more stars. A for loop counts the tries and break stops it early.",
        ],
        "why": [
            "Games are a fun way to see loops in action",
            "It combines for, range(), break, conditions and repeating text with *",
        ],
        "concepts": [("l1s3", "True and False"), ("l2s3", "elif"), ("l3s1", "for and range()"),
                     ("l3s3", "break"), ("l3s4", "Repeating text with *")],
        "inputs": ["4", "9", "7"],
        "code": lines('''
            # Number Guessing Game
            secret = 7
            max_tries = 5
            won = False
            print("I'm thinking of a number from 1 to 10.")
            for attempt in range(1, max_tries + 1):
                guess = int(input(f"Guess {attempt}: "))
                if guess == secret:
                    won = True
                    break
                elif guess < secret:
                    print("Too low!")
                else:
                    print("Too high!")
            if won:
                print(f"Correct! You got it in {attempt} tries.")
                print("Stars: " + "*" * (max_tries - attempt + 1))
            else:
                print(f"Out of tries! It was {secret}.")
        '''),
        "output": out('''
            I'm thinking of a number from 1 to 10.
            Guess 1: 4
            Too low!
            Guess 2: 9
            Too high!
            Guess 3: 7
            Correct! You got it in 3 tries.
            Stars: ***
        '''),
        "stages": [
            stage(1, "Set up the game",
                  "The secret number and the number of tries are ready. Add a variable that remembers whether the player has won yet.",
                  [1, 2, 3, 4, 5], "Which line sets up the 'has won' flag?",
                  "won = False",
                  ['won = "False"', "won == False", "won = True"],
                  "A Boolean flag starts as False and is switched to True when something happens (1.3).",
                  ["\"False\" in quotes is text, and any non-empty text counts as True in an if.",
                   "== compares instead of storing, and won doesn't exist yet: a NameError.",
                   "The player hasn't won before the game even starts."]),
            stage(2, "Count the tries",
                  "Repeat the guessing once for each try, numbering the tries 1 to 5.",
                  [6], "Which loop gives attempt the values 1, 2, 3, 4, 5?",
                  "for attempt in range(1, max_tries + 1):",
                  ["for attempt in range(max_tries):", "for attempt in range(1, max_tries):",
                   "while attempt in range(1, max_tries + 1):"],
                  "range(1, 6) starts at 1 and stops before 6, so it gives 1 to 5 (3.1).",
                  ["range(5) gives 0 to 4, so the first guess would be called Guess 0.",
                   "range(1, 5) stops before 5: only four tries.",
                   "attempt doesn't exist before the loop, so this is a NameError."]),
            stage(3, "Read a guess",
                  "Inside the loop, ask for a guess, showing which try it is, and turn it into a number.",
                  [7], "Which line reads the guess?",
                  '    guess = int(input(f"Guess {attempt}: "))',
                  ['    guess = input(f"Guess {attempt}: ")', '    guess = int(input("Guess {attempt}: "))',
                   '    guess = int(input(f"Guess {attempt}: ")'],
                  "The f-string shows the try number, and int() makes the answer a number (1.4).",
                  ["The guess stays text, so guess == secret would never be True.",
                   "Without the f, the prompt literally shows {attempt}.",
                   "One closing bracket is missing. This is a SyntaxError."]),
            stage(4, "A correct guess",
                  "If the guess matches the secret, remember the win and stop asking.",
                  [8, 9, 10], "What should happen when the guess is right?",
                  "Set won to True, then break out of the loop",
                  ["Use continue to go to the next guess", "Set won to True and keep looping",
                   "Set won to False, then break out of the loop"],
                  "break ends the loop at once, so no more guesses are asked for (3.3).",
                  ["continue skips to the next try, so the player keeps guessing after winning.",
                   "The player would be asked again even after getting it right.",
                   "won would stay False, so the program would say they lost."],
                  code_options=False),
            stage(5, "Give a hint",
                  "For a wrong guess, tell the player whether to go higher or lower.",
                  [11, 12, 13, 14], "Which line checks for a guess that is too low?",
                  "    elif guess < secret:",
                  ["    elif guess > secret:", "    elif guess < secret", "    elif guess =< secret:"],
                  "If the guess is smaller than the secret, it's too low (2.1, 2.3).",
                  ["This checks for too high, but prints Too low!",
                   "The colon is missing. This is a SyntaxError.",
                   "The operator is <=, not =<. This is a SyntaxError."]),
            stage(6, "Award stars",
                  "After the loop, if the player won, print how many tries it took and one star for every try left, plus one.",
                  [15, 16, 17], "Which line prints the stars?",
                  '    print("Stars: " + "*" * (max_tries - attempt + 1))',
                  ['    print("Stars: " + "*" + (max_tries - attempt + 1))',
                   '    print("Stars: " * (max_tries - attempt + 1))',
                   '    print("Stars: " + "*" * max_tries - attempt + 1)'],
                  "\"*\" * 3 repeats the star three times (3.4). The brackets do the maths first.",
                  ["You can't + text and a number. This is a TypeError.",
                   "This repeats the word Stars: three times instead.",
                   "Without brackets, \"*\" * 5 runs first, then text minus a number is a TypeError."]),
            stage(7, "Out of tries",
                  "Finish with an else that runs when the player didn't win.",
                  [18, 19], "When does the else part run?",
                  "When all five tries were used without guessing the number",
                  ["After every wrong guess", "Only when the first guess is right",
                   "Never, because break always runs"],
                  "won stays False only if the loop ran out without a correct guess (2.2).",
                  ["This else belongs to if won after the loop, not to the hints inside it.",
                   "A right guess sets won to True, so the if part runs instead.",
                   "break only runs after a correct guess. Five wrong guesses never reach it."],
                  code_options=False),
        ],
        "walkthrough": [
            "won starts as False. The for loop gives attempt the values 1 to 5.",
            "Each try reads a number. A correct guess sets won to True and break stops the loop.",
            "Wrong guesses get a hint from elif / else.",
            "After the loop, if won prints the result and \"*\" * 3 draws three stars for a 3rd-try win.",
        ],
    })

    # ── Level 4: Quiz Game ──
    projects.append({
        "level": "l4", "title": "Python Quiz Game", "file": "quiz_game.py", "minutes": 12,
        "summary": "A three-question quiz with reusable ask() and grade() functions and a bonus question.",
        "briefing_title": "Build a Python Quiz Game",
        "text": [
            "You'll write a small quiz. One function asks a question and returns the points earned, another turns the score into a rating.",
            "The last question is a bonus worth 2 points, thanks to a default argument.",
        ],
        "why": [
            "Writing a function once and calling it many times is how real programs stay short",
            "It uses parameters, return values, default and keyword arguments",
        ],
        "concepts": [("l2s3", "elif"), ("l4s1", "Defining functions"), ("l4s2", "Parameters"),
                     ("l4s3", "Return values"), ("l4s4", "Default arguments"), ("l4s5", "Scope")],
        "inputs": ["show", "no", "def"],
        "code": lines('''
            # Python Quiz Game
            def ask(question, answer, points=1):
                reply = input(question + " ")
                if reply == answer:
                    print("Correct!")
                    return points
                print(f"Not quite. The answer is {answer}.")
                return 0
            def grade(score, total):
                percent = score * 100 / total
                if percent >= 80:
                    return "Python star"
                elif percent >= 50:
                    return "Good effort"
                return "Keep practising"
            print("=== Python Quiz ===")
            score = 0
            score += ask("What does print() do? (show/store)", "show")
            score += ask("Is 10 > 3 True? (yes/no)", "yes")
            score += ask("Keyword to define a function?", "def", points=2)
            total = 4
            print(f"You scored {score} out of {total}.")
            print(f"Rating: {grade(score, total)}")
        '''),
        "output": out('''
            === Python Quiz ===
            What does print() do? (show/store) show
            Correct!
            Is 10 > 3 True? (yes/no) no
            Not quite. The answer is yes.
            Keyword to define a function? def
            Correct!
            You scored 3 out of 4.
            Rating: Good effort
        '''),
        "stages": [
            stage(1, "Define ask()",
                  "ask() needs the question, the right answer and how many points it's worth. Most questions are worth 1 point.",
                  [1, 2], "Which def line is correct?",
                  "def ask(question, answer, points=1):",
                  ["def ask(points=1, question, answer):", "def ask(question, answer, points):",
                   "def ask(question, answer, points=1)"],
                  "Parameters with a default value go last (4.4).",
                  ["A parameter with a default can't come before ones without. This is a SyntaxError.",
                   "Without a default, every call would have to pass the points.",
                   "The colon at the end is missing. This is a SyntaxError."]),
            stage(2, "Ask the question",
                  "Show the question with a space after it and keep what the player types.",
                  [3], "Which line asks the question?",
                  '    reply = input(question + " ")',
                  ['    reply = input(question, " ")', "    reply = print(question)", '    input(question + " ") = reply'],
                  "+ joins the question and a space into one prompt for input() (1.4).",
                  ["input() takes only one prompt. Two arguments is a TypeError.",
                   "print() shows the question but returns None, so nothing is typed.",
                   "The variable must be on the left of =. This is a SyntaxError."]),
            stage(3, "Return the points",
                  "If the reply matches the answer, say so and send the points back to whoever called ask().",
                  [4, 5, 6], "Which line sends the points back?",
                  "        return points",
                  ["        print(points)", "        return 1", '        return "points"'],
                  "return hands a value back to the caller, so score += ask(...) can add it (4.3).",
                  ["print shows the number but the function still returns None. score += None is a TypeError.",
                   "Always returning 1 ignores the bonus question's 2 points.",
                   "This returns the word points, and score += \"points\" is a TypeError."]),
            stage(4, "A wrong answer",
                  "If the reply was wrong, show the right answer and return 0 points.",
                  [7, 8], "Why can these lines sit outside the if?",
                  "A right answer already left the function with return, so only wrong answers get here",
                  ["Code after an if always runs before the if", "Python ignores lines after return",
                   "Because answer is a global variable"],
                  "return ends the function at once, so the lines after it only run for wrong answers (4.3).",
                  ["Code runs from top to bottom. The if comes first.",
                   "Only lines after a return that actually ran are skipped. A wrong answer never reaches it.",
                   "answer is a parameter of ask(), which makes it local (4.5)."],
                  code_options=False),
            stage(5, "Work out a percentage",
                  "grade() turns a score into a rating. First work out the score as a percentage of the total.",
                  [9, 10], "Which line works out the percentage?",
                  "    percent = score * 100 / total",
                  ["    percent = score / 100 * total", "    percent = total * 100 / score",
                   "    percent = score * 100 / total()"],
                  "3 out of 4 is 3 * 100 / 4 = 75.0 percent (1.3).",
                  ["This gives 3 / 100 * 4 = 0.12, not a percentage.",
                   "Upside down: 4 * 100 / 3 is over 100%.",
                   "total is a number, not a function. Calling it is a TypeError."]),
            stage(6, "Pick a rating",
                  "Check the best rating first: 80% or more is a Python star, 50% or more is a good effort.",
                  [11, 12, 13, 14, 15], "Which line should be checked first?",
                  "    if percent >= 80:",
                  ["    if percent >= 50:", "    if percent > 80:", "    if percent => 80:"],
                  "Check from the highest rule down; the first True return wins (2.3, 4.3).",
                  ["Checked first, 90% would also match >= 50 and get Good effort.",
                   "Exactly 80% wouldn't count as a Python star.",
                   "The operator is >=, not =>. This is a SyntaxError."]),
            stage(7, "Play the quiz",
                  "Start the score at 0, then ask three questions. The last is the bonus question worth 2 points.",
                  [16, 17, 18, 19, 20], "Which line asks the bonus question?",
                  'score += ask("Keyword to define a function?", "def", points=2)',
                  ['score = ask("Keyword to define a function?", "def", points=2)',
                   'score += ask("Keyword to define a function?", "def", 2 = points)',
                   'ask("Keyword to define a function?", "def", points=2)'],
                  "points=2 is a keyword argument, and += adds the returned points to the score (4.4).",
                  ["= replaces the score, so the first two answers would be lost.",
                   "Keyword arguments are name=value, not value=name. This is a SyntaxError.",
                   "The returned points are thrown away, so the score doesn't change."]),
            stage(8, "Show the result",
                  "Print the score, then call grade() inside an f-string to show the rating.",
                  [21, 22, 23], "Which line prints the rating?",
                  'print(f"Rating: {grade(score, total)}")',
                  ['print(f"Rating: {grade}")', 'print(f"Rating: {grade(total, score)}")',
                   'print("Rating: {grade(score, total)}")'],
                  "You can call a function inside the braces of an f-string; its return value is shown (4.3).",
                  ["Without brackets the function isn't called; Python shows <function grade ...>.",
                   "Arguments are matched in order, so score and total would be swapped.",
                   "Without the f, the braces are printed as plain text."]),
        ],
        "walkthrough": [
            "ask() shows a question, compares the reply and returns the points or 0.",
            "points=1 is a default, so only the bonus question passes points=2.",
            "grade() checks from the highest rating down and returns the first that matches.",
            "score lives outside the functions; each score += ask(...) adds the returned points.",
            "3 out of 4 is 75%, which is a Good effort.",
        ],
    })

    # ── Level 5: To-Do List ──
    projects.append({
        "level": "l5", "title": "To-Do List", "file": "todo_list.py", "minutes": 12,
        "summary": "Add, number, finish, remove and sort tasks using list methods.",
        "briefing_title": "Build a To-Do List",
        "text": [
            "You'll manage a to-do list: add tasks, show them with numbers, tick some off, sort the rest and find the quick ones.",
            "Every step uses a list method you met in Level 5.",
        ],
        "why": [
            "To-do apps are lists underneath",
            "It practises append, insert, pop, remove, sort, loops and a list comprehension",
        ],
        "concepts": [("l3s1", "range()"), ("l5s1", "Creating lists"), ("l5s2", "Indexing"),
                     ("l5s3", "List methods"), ("l5s4", "Looping over lists"), ("l5s5", "List comprehensions")],
        "inputs": None,
        "code": lines('''
            # To-Do List
            todos = []
            done = []
            todos.append("Finish Python homework")
            todos.append("Buy milk")
            todos.append("Call grandma")
            todos.insert(0, "Drink water")
            print(f"You have {len(todos)} tasks.")
            for i in range(len(todos)):
                print(f"{i + 1}. {todos[i]}")
            finished = todos.pop(1)
            done.append(finished)
            todos.remove("Buy milk")
            done.append("Buy milk")
            todos.sort()
            print("--- Still to do ---")
            for task in todos:
                print("[ ] " + task)
            print("--- Done ---")
            for task in done:
                print("[x] " + task)
            short = [t for t in todos if len(t) < 12]
            print(f"Quick tasks: {short}")
        '''),
        "output": out('''
            You have 4 tasks.
            1. Drink water
            2. Finish Python homework
            3. Buy milk
            4. Call grandma
            --- Still to do ---
            [ ] Call grandma
            [ ] Drink water
            --- Done ---
            [x] Finish Python homework
            [x] Buy milk
            Quick tasks: ['Drink water']
        '''),
        "stages": [
            stage(1, "Two empty lists",
                  "One list holds the tasks still to do and another the finished ones. Both start empty.",
                  [1, 2, 3], "Which line creates the empty to-do list?",
                  "todos = []",
                  ["todos = {}", 'todos = [""]', 'todos = ""'],
                  "[] is an empty list, ready for append() (5.1).",
                  ["{} is an empty dictionary, not a list.",
                   "This list already holds one empty task.",
                   "That's an empty string. Strings don't have append()."]),
            stage(2, "Add tasks",
                  "Add three tasks to the end of the list, one after another.",
                  [4, 5, 6], "Which line adds a task to the end?",
                  'todos.append("Buy milk")',
                  ['todos = "Buy milk"', 'todos.append["Buy milk"]', 'append(todos, "Buy milk")'],
                  "append() adds one item to the end of a list (5.3).",
                  ["This replaces the whole list with one piece of text.",
                   "Methods are called with round brackets. Square brackets are a TypeError.",
                   "append is a list method, not a function on its own. This is a NameError."]),
            stage(3, "Put one task first",
                  "Drinking water is the most important task, so put it at the very start of the list.",
                  [7], "Which line puts the task first?",
                  'todos.insert(0, "Drink water")',
                  ['todos.insert("Drink water", 0)', 'todos.insert(1, "Drink water")', 'todos.append(0, "Drink water")'],
                  "insert(position, item). Position 0 is the start (5.2, 5.3).",
                  ["The position comes first, then the item. This is a TypeError.",
                   "Position 1 is the second place, not the first.",
                   "append() takes one item, not a position. This is a TypeError."]),
            stage(4, "Number the tasks",
                  "Print how many tasks there are, then each task with a number starting from 1.",
                  [8, 9, 10], "Which line prints each numbered task?",
                  '    print(f"{i + 1}. {todos[i]}")',
                  ['    print(f"{i}. {todos[i]}")', '    print(f"{i + 1}. {todos}")', '    print(f"{i + 1}. {todos[i + 1]}")'],
                  "Positions start at 0, so i + 1 gives the human numbering 1, 2, 3, 4 (5.2).",
                  ["The numbering would start at 0.",
                   "This prints the whole list on every line.",
                   "This skips the first task and the last i + 1 is out of range: an IndexError."]),
            stage(5, "Finish a task",
                  "The homework is done. Take the task at position 1 out of the list and keep it.",
                  [11, 12], "Which line removes position 1 and keeps the task?",
                  "finished = todos.pop(1)",
                  ["finished = todos.pop()", "finished = todos[1]", "finished = todos.remove(1)"],
                  "pop(1) removes the item at position 1 and gives it back (5.3).",
                  ["pop() with no position removes the last task, Call grandma.",
                   "This reads the task but leaves it in the list.",
                   "remove() looks for the value 1, which isn't in the list: a ValueError."]),
            stage(6, "Remove by name and sort",
                  "Milk is bought too. Remove it by its name, then sort what's left.",
                  [13, 14, 15], 'Which line removes "Buy milk" by its value?',
                  'todos.remove("Buy milk")',
                  ['todos.pop("Buy milk")', 'todos - "Buy milk"', 'todos.remove(todos)'],
                  "remove() deletes the first item equal to the value (5.3).",
                  ["pop() needs a position number, not text. This is a TypeError.",
                   "Lists can't be subtracted from. This is a TypeError.",
                   "The list isn't inside itself, so this is a ValueError."]),
            stage(7, "Print both lists",
                  "Show the tasks still to do with [ ] and the finished ones with [x].",
                  [16, 17, 18, 19, 20, 21], "Which line prints a task still to do?",
                  '    print("[ ] " + task)',
                  ['    print("[ ] " + todos)', '    print("[ ] " + todos[task])', '    print("[ ] " + task[0])'],
                  "for task in todos gives one task at a time, ready to print (5.4).",
                  ["You can't + text and a list. This is a TypeError.",
                   "task is text, not a position. List indices must be numbers: a TypeError.",
                   "task[0] is just the first letter of the task."]),
            stage(8, "Find quick tasks",
                  "Build a new list of the tasks with fewer than 12 characters and print it.",
                  [22, 23], "Which line builds the list of quick tasks?",
                  "short = [t for t in todos if len(t) < 12]",
                  ["short = [t for t in todos if t < 12]", "short = [len(t) for t in todos if len(t) < 12]",
                   "short = [for t in todos if len(t) < 12]"],
                  "Value, then for, then the filter: a list comprehension (5.5).",
                  ["You can't compare text with a number. This is a TypeError.",
                   "This keeps the lengths, like [11], not the task names.",
                   "The value must come before for. This is a SyntaxError."]),
        ],
        "walkthrough": [
            "append() adds to the end; insert(0, ...) puts Drink water first.",
            "range(len(todos)) gives the positions 0-3; i + 1 prints them as 1-4.",
            "pop(1) removes and returns the homework; remove() deletes Buy milk by value.",
            "sort() puts the remaining tasks in alphabetical order.",
            "The comprehension keeps only tasks shorter than 12 characters.",
        ],
    })

    # ── Level 6: Mini Text Adventure ──
    projects.append({
        "level": "l6", "title": "Mini Text Adventure", "file": "adventure.py", "minutes": 15,
        "summary": "Explore rooms stored in dictionaries, collect items and find the treasure.",
        "briefing_title": "Build a Mini Text Adventure",
        "text": [
            "You'll build a tiny adventure game. Each room is a dictionary of exits, and the player types a direction to move.",
            "Items are picked up along the way. Reach the cellar to win.",
            "The player can type in any case and with extra spaces; string methods clean it up.",
        ],
        "why": [
            "Many games store their world as data, just like this",
            "It uses nested dictionaries, get(), pop(), join(), upper(), strip() and lower()",
        ],
        "concepts": [("l3s3", "while True and break"), ("l5s3", "append()"), ("l6s1", "String methods"),
                     ("l6s3", "Dictionaries"), ("l6s4", "keys()")],
        "inputs": ["north", "South", "east", "Down"],
        "code": lines('''
            # Mini Text Adventure
            rooms = {
                "hall": {"north": "library", "east": "kitchen"},
                "library": {"south": "hall"},
                "kitchen": {"west": "hall", "down": "cellar"},
                "cellar": {},
            }
            items = {"library": "map", "kitchen": "lamp", "cellar": "treasure"}
            bag = []
            room = "hall"
            while True:
                print(f"== {room.upper()} ==")
                if room in items:
                    item = items.pop(room)
                    bag.append(item)
                    print(f"You found: {item}")
                if room == "cellar":
                    break
                exits = ", ".join(rooms[room].keys())
                move = input(f"Exits: {exits}. Go? ").strip().lower()
                room = rooms[room].get(move, room)
            print(f"You escaped with: {', '.join(bag)}")
        '''),
        "output": out('''
            == HALL ==
            Exits: north, east. Go? north
            == LIBRARY ==
            You found: map
            Exits: south. Go? South
            == HALL ==
            Exits: north, east. Go? east
            == KITCHEN ==
            You found: lamp
            Exits: west, down. Go? Down
            == CELLAR ==
            You found: treasure
            You escaped with: map, lamp, treasure
        '''),
        "stages": [
            stage(1, "Build the map",
                  "Each room is a key. Its value is another dictionary that maps an exit to the room it leads to.",
                  [1, 2, 3, 4, 5, 6, 7], "Which line describes the kitchen?",
                  '    "kitchen": {"west": "hall", "down": "cellar"},',
                  ['    "kitchen": ["west", "hall", "down", "cellar"],',
                   '    "kitchen": {"west" = "hall", "down" = "cellar"},',
                   '    kitchen: {"west": "hall", "down": "cellar"},'],
                  "A dictionary inside a dictionary: rooms[\"kitchen\"][\"down\"] is \"cellar\" (6.3).",
                  ["A list has no labels, so you couldn't look up which room \"down\" leads to.",
                   "Dictionaries use a colon between key and value. This is a SyntaxError.",
                   "Without quotes, kitchen is a variable that doesn't exist: a NameError."]),
            stage(2, "Items and the start",
                  "Some rooms hold an item. Store them by room name, start with an empty bag, and begin in the hall.",
                  [8, 9, 10], "Which line stores the items by room?",
                  'items = {"library": "map", "kitchen": "lamp", "cellar": "treasure"}',
                  ['items = {"map": "library", "lamp": "kitchen", "treasure": "cellar"}',
                   'items = ["library": "map", "kitchen": "lamp", "cellar": "treasure"]',
                   'items = {"library", "map", "kitchen", "lamp", "cellar", "treasure"}'],
                  "The room is the key, so items[room] finds what's in the current room (6.3).",
                  ["Keys and values are swapped, so you couldn't look up a room.",
                   "key: value pairs need curly braces. This is a SyntaxError.",
                   "Without colons there are no keys and values, so nothing can be looked up."]),
            stage(3, "Show the room",
                  "Loop until the game ends. Each time, print the room name in capitals.",
                  [11, 12], "Which line prints the room name in capitals?",
                  '    print(f"== {room.upper()} ==")',
                  ['    print(f"== {room.upper} ==")', '    print(f"== {upper(room)} ==")', '    print("== {room.upper()} ==")'],
                  "upper() is a string method, called with brackets (6.1).",
                  ["Without brackets, the method isn't called and Python prints <built-in method ...>.",
                   "upper is a method of strings, not a function on its own: a NameError.",
                   "Without the f, the braces are printed as plain text."]),
            stage(4, "Pick up items",
                  "If the room still has an item, take it out of items, put it in the bag and say what was found.",
                  [13, 14, 15, 16], "Which line checks whether this room has an item?",
                  "    if room in items:",
                  ["    if items in room:", "    if items[room]:", "    if room == items:"],
                  "in checks whether a key is in a dictionary (6.3). pop() then removes it so it's found only once.",
                  ["Backwards: you can't look for a dictionary inside text. This is a TypeError.",
                   "The hall has no item, so items[\"hall\"] is a KeyError.",
                   "Text is never equal to a whole dictionary, so this is never True."]),
            stage(5, "The goal",
                  "The game ends when the player reaches the cellar.",
                  [17, 18], "Which line ends the game in the cellar?",
                  '    if room == "cellar":',
                  ['    if room = "cellar":', '    if "cellar":', "    if room == cellar:"],
                  "Compare the room with the text \"cellar\", then break stops the while True loop (3.3).",
                  ["= stores a value; comparisons use ==. This is a SyntaxError.",
                   "Non-empty text is always True, so the game would end at once.",
                   "Without quotes, cellar is an unknown variable: a NameError."]),
            stage(6, "List the exits",
                  "Show the exits of the current room as one line, like north, east.",
                  [19], "Which line builds the list of exits?",
                  '    exits = ", ".join(rooms[room].keys())',
                  ['    exits = rooms[room].keys().join(", ")', '    exits = ", ".join(rooms.keys())',
                   '    exits = ", ".join(rooms[room].values())'],
                  "keys() gives the exit names, and \", \".join() puts them in one string (6.1, 6.4).",
                  ["join() belongs to the separator string, not to keys(). This is an AttributeError.",
                   "This lists every room in the game, not the exits from this one.",
                   "values() gives where the exits lead, not which way to type."]),
            stage(7, "Read the move",
                  "Ask which way to go. Clean the answer so \" North \" and \"north\" both work.",
                  [20], "Which line reads and cleans the move?",
                  '    move = input(f"Exits: {exits}. Go? ").strip().lower()',
                  ['    move = input(f"Exits: {exits}. Go? ").lower',
                   '    move = input(f"Exits: {exits}. Go? ").upper().strip()',
                   '    move = input(f"Exits: {exits}. Go? ").split()'],
                  "strip() removes spaces at the ends and lower() makes it lowercase, like the keys (6.1).",
                  ["Without brackets, lower isn't called; move becomes a method, not text.",
                   "The exits are lowercase, so NORTH would never match.",
                   "split() gives a list of words, which can't be a dictionary key."]),
            stage(8, "Move safely",
                  "Move to the room the exit leads to. If the player types a direction that isn't an exit, stay put.",
                  [21, 22], "Which line moves the player safely?",
                  "    room = rooms[room].get(move, room)",
                  ["    room = rooms[room][move]", "    room = rooms.get(move, room)", "    room = move"],
                  "get(key, default) returns the default when the key is missing, so a typo keeps you in the same room (6.3).",
                  ["A typo like \"nrth\" would crash with a KeyError.",
                   "This looks up directions among room names, so the player could never move.",
                   "room would become \"north\", which isn't a room: a KeyError on the next turn."]),
        ],
        "walkthrough": [
            "rooms is a dictionary of dictionaries: room -> {exit: next room}.",
            "while True repeats until break, which happens in the cellar.",
            "in checks for an item; pop() takes it out so it's found only once.",
            "join() and keys() list the exits; strip() and lower() clean what the player types.",
            "get(move, room) moves only when the exit exists; otherwise the player stays.",
        ],
    })
    return projects


# ───────────────────────────── CHEAT SHEETS ─────────────────────────────
# item(code, note, output=None). If output is given, validation runs the code and compares.

def item(code, note, output=None):
    d = {"code": textwrap.dedent(code).strip("\n"), "note": note}
    if output is not None:
        d["output"] = textwrap.dedent(output).strip("\n")
    return d


def section(heading, *items):
    return {"heading": heading, "items": list(items)}


CHEAT_SHEETS = {
    "l1": {
        "title": "Python Basics",
        "sections": [
            section("Printing",
                    item('print("Hello!")', "Show text. Text goes in quotes.", "Hello!"),
                    item('print("Age:", 18)', "Commas print several values with spaces between.", "Age: 18"),
                    item("# This is a comment", "Python ignores everything after #.")),
            section("Variables",
                    item('name = "Meera"\nscore = 10\nscore = score + 5\nprint(name, score)',
                         "= stores a value. A variable can be changed later.", "Meera 15"),
                    item("my_score = 1", "Names: letters, digits and _, can't start with a digit, case matters.")),
            section("Data types",
                    item('print(type("hi"), type(7), type(2.5), type(True))',
                         "str, int, float and bool.",
                         "<class 'str'> <class 'int'> <class 'float'> <class 'bool'>"),
                    item('print(int("42") + 1)\nprint(str(42) + "!")', "Convert with int(), float() and str().", "43\n42!")),
            section("Input and output",
                    item('name = input("Name? ")', "input() shows a question and always gives back text."),
                    item('age = int(input("Age? "))', "Convert input before doing maths."),
                    item('x = 3\nprint(f"{x} apples")', "f-strings put values inside text.", "3 apples")),
        ],
        "remember": ["Text needs quotes; numbers don't.", "input() always returns a string.",
                     "Python is case-sensitive: print, not Print."],
    },
    "l2": {
        "title": "Conditions",
        "sections": [
            section("Comparisons",
                    item("print(5 > 3, 5 == 3, 5 != 3)", "== equal, != not equal, <, >, <=, >=.", "True False True"),
                    item('print("a" == "A")', "Text comparisons are case-sensitive.", "False")),
            section("if and else",
                    item('age = 20\nif age >= 18:\n    print("Adult")\nelse:\n    print("Minor")',
                         "End with a colon and indent the lines that belong to it.", "Adult")),
            section("elif",
                    item('marks = 72\nif marks >= 90:\n    print("A")\nelif marks >= 60:\n    print("B")\nelse:\n    print("C")',
                         "Checked from top to bottom; only the first True branch runs.", "B")),
        ],
        "remember": ["= stores, == compares.", "Every if, elif and else line ends with a colon.",
                     "Put the most specific check first."],
    },
    "l3": {
        "title": "Loops",
        "sections": [
            section("for and range()",
                    item("for i in range(3):\n    print(i)", "range(3) gives 0, 1, 2.", "0\n1\n2"),
                    item("print(list(range(2, 10, 3)))", "range(start, stop, step). stop is never included.", "[2, 5, 8]"),
                    item("total = 0\nfor n in range(1, 5):\n    total += n\nprint(total)", "Add up with a running total.", "10")),
            section("while",
                    item("n = 3\nwhile n > 0:\n    print(n)\n    n -= 1", "Repeats while the condition is True. Change the variable!", "3\n2\n1")),
            section("break and continue",
                    item("for n in range(10):\n    if n == 3:\n        break\n    print(n)", "break stops the loop at once.", "0\n1\n2"),
                    item("for n in range(5):\n    if n % 2 == 0:\n        continue\n    print(n)", "continue skips to the next repeat.", "1\n3")),
            section("Patterns",
                    item('for row in range(1, 4):\n    print("*" * row)', "Text * number repeats the text.", "*\n**\n***"),
                    item('print(1, end=" ")\nprint(2)', 'end=" " stays on the same line.', "1 2")),
        ],
        "remember": ["The loop body is indented.", "A while loop must change something, or it never ends.",
                     "range(1, 6) gives 1 to 5."],
    },
    "l4": {
        "title": "Functions",
        "sections": [
            section("Defining and calling",
                    item('def greet():\n    print("Hi!")\n\ngreet()\ngreet()', "Define once with def, call as often as you like.", "Hi!\nHi!")),
            section("Parameters and return",
                    item("def add(a, b):\n    return a + b\n\nprint(add(2, 3))", "Parameters take values in; return sends a result back.", "5"),
                    item("def show(x):\n    print(x)\n\nresult = show(1)\nprint(result)", "Without return, a function gives back None.", "1\nNone")),
            section("Default and keyword arguments",
                    item('def hello(name, greeting="Hi"):\n    return f"{greeting}, {name}"\n\nprint(hello("Ravi"))\nprint(hello("Ravi", greeting="Hey"))',
                         "Defaults go last; keyword arguments name the parameter.", "Hi, Ravi\nHey, Ravi")),
            section("Scope",
                    item("def f():\n    x = 1\n    return x\n\nprint(f())", "Variables made inside a function are local to it.", "1"),
                    item("rate = 2\ndef double(n):\n    return n * rate\n\nprint(double(5))", "A function can read global variables.", "10")),
        ],
        "remember": ["Define a function before you call it.", "return ends the function.",
                     "print shows a value; return hands it back."],
    },
    "l5": {
        "title": "Lists",
        "sections": [
            section("Creating and reading",
                    item('fruits = ["apple", "mango", "kiwi"]\nprint(len(fruits), fruits[0], fruits[-1])',
                         "Positions start at 0; -1 is the last item.", "3 apple kiwi"),
                    item("nums = [10, 20, 30, 40]\nprint(nums[1:3])", "Slices stop before the end position.", "[20, 30]"),
                    item('print("kiwi" in ["apple", "kiwi"])', "in checks whether an item is in the list.", "True")),
            section("Changing lists",
                    item("nums = [3, 1]\nnums.append(2)\nnums.insert(0, 5)\nprint(nums)", "append() adds to the end; insert() at a position.", "[5, 3, 1, 2]"),
                    item("nums = [5, 3, 1, 2]\nnums.remove(3)\nlast = nums.pop()\nprint(nums, last)", "remove() by value; pop() by position (last by default).", "[5, 1] 2"),
                    item("nums = [4, 1, 3]\nnums.sort()\nprint(nums, max(nums), sum(nums))", "sort() changes the list; max, min, sum, len help.", "[1, 3, 4] 4 8")),
            section("Looping and comprehensions",
                    item("for n in [1, 2, 3]:\n    print(n * 10)", "Visit every item.", "10\n20\n30"),
                    item("nums = [1, 2, 3, 4]\nprint([n * n for n in nums if n % 2 == 0])", "[value for item in list if condition]", "[4, 16]")),
        ],
        "remember": ["The last position is len(list) - 1.", "List methods change the list and return None (except pop).",
                     "Keep comprehensions short and simple."],
    },
    "l6": {
        "title": "Strings and Dictionaries",
        "sections": [
            section("String methods",
                    item('s = "  Hello World  "\nprint(s.strip().lower())', "strip() trims spaces; lower() and upper() change case.", "hello world"),
                    item('print("a,b,c".split(","))\nprint("-".join(["a", "b"]))', "split() makes a list; join() glues a list into text.", "['a', 'b', 'c']\na-b"),
                    item('print("banana".replace("a", "o"), "banana".count("a"))', "replace() and count().", "bonono 3")),
            section("f-string formatting",
                    item('price = 3.14159\nprint(f"{price:.2f}")', ":.2f rounds to 2 decimal places.", "3.14"),
                    item("print(f\"[{'tea':<6}][{15:>4}]\")", ":<6 left-aligns in 6 spaces, :>4 right-aligns in 4.", "[tea   ][  15]")),
            section("Dictionaries",
                    item('ages = {"Asha": 21}\nages["Ravi"] = 19\nprint(ages["Ravi"], len(ages))', "Look up, add and change values by key.", "19 2"),
                    item('ages = {"Asha": 21}\nprint(ages.get("Sam", 0), "Asha" in ages)', "get() with a default avoids KeyError; in checks keys.", "0 True"),
                    item('counts = {}\nfor w in ["a", "b", "a"]:\n    counts[w] = counts.get(w, 0) + 1\nprint(counts)', "The counting pattern.", "{'a': 2, 'b': 1}")),
            section("Looping over dictionaries",
                    item('prices = {"tea": 15, "coffee": 25}\nfor item, cost in prices.items():\n    print(item, cost)',
                         "items() gives key and value together; keys() and values() give one side.", "tea 15\ncoffee 25")),
        ],
        "remember": ["Strings never change; methods return a new string.", "Dictionary keys must be unique.",
                     "Use get() when a key might be missing."],
    },
    "l7": {
        "title": "Final Project patterns",
        "sections": [
            section("Store records",
                    item('expenses = []\nexpenses.append({"name": "Lunch", "amount": 120})\nprint(expenses[0]["amount"])',
                         "A list of dictionaries: one dictionary per record.", "120")),
            section("Total and group",
                    item('rows = [{"cat": "Food", "amt": 120}, {"cat": "Food", "amt": 250}, {"cat": "Fun", "amt": 200}]\ntotals = {}\nfor r in rows:\n    totals[r["cat"]] = totals.get(r["cat"], 0) + r["amt"]\nprint(totals)',
                         "get(key, 0) starts a new group at 0.", "{'Food': 370, 'Fun': 200}")),
            section("Report neatly",
                    item('for name, amt in [("Lunch", 120), ("Bus pass", 300)]:\n    print(f"{name:<10} Rs {amt:>4}")',
                         "Line columns up with :< and :>.", "Lunch      Rs  120\nBus pass   Rs  300")),
            section("Plan a program",
                    item("# 1. data  2. functions  3. calls  4. output", "Set up data first, then write small functions, then use them.")),
        ],
        "remember": ["Build a program one small, tested piece at a time.",
                     "Name functions after what they do: total_spent(), add_expense()."],
    },
}


# ───────────────────────────── GLOSSARY ─────────────────────────────
# term(name, sub_level_id, definition, example=None, output=None, aliases=())

def term(name, sub, definition, example=None, output=None, aliases=()):
    d = {"term": name, "subLevelId": sub, "definition": definition}
    if example is not None:
        d["example"] = textwrap.dedent(example).strip("\n")
    if output is not None:
        d["output"] = textwrap.dedent(output).strip("\n")
    if aliases:
        d["aliases"] = list(aliases)
    return d


GLOSSARY = [
    # Level 1
    term("Python", "l1s1", "A popular programming language that is easy to read. PyPath teaches Python 3."),
    term("print()", "l1s1", "A built-in function that shows values on the screen.", 'print("Hi", 5)', "Hi 5", aliases=["output", "display"]),
    term("String", "l1s3", "Text: letters, digits or symbols inside quotes. Its type is str.", 'word = "hello"\nprint(type(word))', "<class 'str'>", aliases=["str", "text"]),
    term("Comment", "l1s1", "A note for humans that starts with #. Python ignores it.", "# This line is ignored", aliases=["#"]),
    term("Variable", "l1s2", "A name that stores a value so you can use it later.", "score = 10\nprint(score)", "10"),
    term("Assignment", "l1s2", "Storing a value in a variable with =.", "x = 5\nx = x + 1\nprint(x)", "6", aliases=["="]),
    term("Integer", "l1s3", "A whole number, such as 7 or -3. Its type is int.", "print(type(7))", "<class 'int'>", aliases=["int"]),
    term("Float", "l1s3", "A number with a decimal point, such as 2.5. Its type is float.", "print(7 / 2)", "3.5", aliases=["decimal"]),
    term("Boolean", "l1s3", "A value that is either True or False. Its type is bool.", "print(5 > 3)", "True", aliases=["bool", "True", "False"]),
    term("Type conversion", "l1s3", "Changing a value from one type to another with int(), float() or str().", 'print(int("4") + 1)', "5", aliases=["casting", "int()", "str()", "float()"]),
    term("input()", "l1s4", "Pauses the program, shows a question and returns what the user typed, always as a string.", 'name = input("Name? ")'),
    term("f-string", "l1s4", "Text with f before the quotes; values in { } are filled in.", 'n = 3\nprint(f"{n} cats")', "3 cats", aliases=["format", "formatted string"]),
    # Level 2
    term("Comparison operator", "l2s1", "== != < > <= >= compare two values and give True or False.", "print(3 == 3, 3 != 4)", "True True", aliases=["==", "!="]),
    term("Condition", "l2s2", "An expression that is True or False, used by if, elif and while.", "age = 20\nprint(age >= 18)", "True"),
    term("if statement", "l2s2", "Runs its indented block only when the condition is True.", 'if 2 > 1:\n    print("yes")', "yes", aliases=["if"]),
    term("else", "l2s2", "The block that runs when the if condition is False."),
    term("elif", "l2s3", "Short for else if: checks another condition when the ones above were False."),
    term("Indentation", "l2s2", "Spaces at the start of a line. They show which lines belong to an if, loop or function.", aliases=["indent", "IndentationError"]),
    # Level 3
    term("Loop", "l3s1", "Code that repeats. Python has for loops and while loops.", aliases=["iteration", "repeat"]),
    term("for loop", "l3s1", "Repeats once for each value in a sequence, such as range() or a list.", "for i in range(2):\n    print(i)", "0\n1", aliases=["for"]),
    term("range()", "l3s1", "Makes a sequence of whole numbers: range(stop), range(start, stop) or range(start, stop, step).", "print(list(range(1, 4)))", "[1, 2, 3]"),
    term("while loop", "l3s2", "Repeats while its condition stays True.", "n = 2\nwhile n > 0:\n    print(n)\n    n -= 1", "2\n1", aliases=["while"]),
    term("Infinite loop", "l3s2", "A loop that never ends because its condition never becomes False."),
    term("break", "l3s3", "Stops a loop immediately.", "for i in range(5):\n    if i == 2:\n        break\n    print(i)", "0\n1"),
    term("continue", "l3s3", "Skips the rest of this repeat and goes on to the next one."),
    term("Nested loop", "l3s4", "A loop inside another loop. The inner loop runs fully for every outer repeat."),
    term("Augmented assignment", "l3s1", "A shortcut that updates a variable: += -= *= /=.", "total = 5\ntotal += 2\nprint(total)", "7", aliases=["+=", "-="]),
    # Level 4
    term("Function", "l4s1", "A named block of code you can run (call) whenever you need it.", aliases=["def"]),
    term("def", "l4s1", "The keyword that defines a function.", 'def hi():\n    print("hi")\nhi()', "hi"),
    term("Call", "l4s1", "Running a function by writing its name followed by brackets.", aliases=["calling", "invoke"]),
    term("Parameter", "l4s2", "A name in the def line that receives a value when the function is called."),
    term("Argument", "l4s2", "The actual value you pass to a function when you call it."),
    term("return", "l4s3", "Sends a value back from a function and ends it.", "def sq(n):\n    return n * n\nprint(sq(4))", "16", aliases=["return value"]),
    term("None", "l4s3", "Python's 'nothing' value. A function without return gives back None."),
    term("Default argument", "l4s4", "A parameter with a value used when no argument is passed.", 'def hi(name="friend"):\n    print("Hi", name)\nhi()', "Hi friend"),
    term("Keyword argument", "l4s4", "An argument passed by name, like greet(name=\"Ravi\")."),
    term("Scope", "l4s5", "Where a variable can be used. Variables made inside a function are local to it.", aliases=["local", "global"]),
    # Level 5
    term("List", "l5s1", "An ordered collection of items in square brackets.", "nums = [1, 2, 3]\nprint(len(nums))", "3", aliases=["[]", "array"]),
    term("len()", "l5s1", "Gives the number of items in a list, or characters in a string.", 'print(len("hey"))', "3", aliases=["length"]),
    term("Index", "l5s2", "The position of an item. The first item is at index 0; -1 is the last.", 'x = ["a", "b"]\nprint(x[0], x[-1])', "a b", aliases=["position"]),
    term("Slice", "l5s2", "A part of a list or string: list[start:stop].", "print([1, 2, 3, 4][1:3])", "[2, 3]", aliases=["slicing"]),
    term("IndexError", "l5s2", "The error you get when you use a position that doesn't exist."),
    term("Method", "l5s3", "A function that belongs to a value, called with a dot: nums.append(4).", aliases=["dot"]),
    term("append()", "l5s3", "Adds one item to the end of a list.", "x = [1]\nx.append(2)\nprint(x)", "[1, 2]"),
    term("pop()", "l5s3", "Removes an item by position (the last by default) and returns it."),
    term("sort()", "l5s3", "Puts a list in order, changing the list itself.", "x = [3, 1, 2]\nx.sort()\nprint(x)", "[1, 2, 3]"),
    term("List comprehension", "l5s5", "A one-line way to build a list: [value for item in list if condition].", "print([n * 2 for n in [1, 2, 3]])", "[2, 4, 6]"),
    # Level 6
    term("String method", "l6s1", "Methods like upper(), lower(), strip(), replace() and find() that return a new string.", 'print("Hi".upper())', "HI"),
    term("split()", "l6s1", "Breaks a string into a list of words or parts.", 'print("a b c".split())', "['a', 'b', 'c']"),
    term("join()", "l6s1", "Joins a list of strings into one string, with a separator between.", 'print(", ".join(["a", "b"]))', "a, b"),
    term("Format specifier", "l6s2", "The part after : in an f-string's braces, like :.2f or :>5.", 'print(f"{2/3:.2f}")', "0.67", aliases=[":.2f", "alignment"]),
    term("Dictionary", "l6s3", "A collection of key: value pairs in curly braces.", 'd = {"a": 1}\nprint(d["a"])', "1", aliases=["dict", "{}"]),
    term("Key", "l6s3", "The label used to look up a value in a dictionary. Keys are unique."),
    term("get()", "l6s3", "Looks up a key and returns a default (or None) if it's missing.", 'd = {"a": 1}\nprint(d.get("b", 0))', "0"),
    term("KeyError", "l6s3", "The error you get when you look up a key that isn't in a dictionary."),
    term("items()", "l6s4", "Gives each key and value of a dictionary together, for looping.", 'for k, v in {"x": 1}.items():\n    print(k, v)', "x 1", aliases=["keys()", "values()"]),
    # Level 7
    term("Program", "l7s1", "A complete set of instructions that does a job, like the Expense Tracker."),
    term("Bug", "l7s1", "A mistake in a program. Finding and fixing bugs is called debugging.", aliases=["debugging", "error"]),
]
