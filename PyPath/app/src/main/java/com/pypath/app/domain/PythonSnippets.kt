package com.pypath.app.domain

/** Premium Phase 1 (Advanced IDE): one ready-made piece of Python code. */
data class PythonSnippet(
    val id: String,
    val category: String,
    val title: String,
    val description: String,
    val code: String,
)

/**
 * Built-in snippets library (bundled in the app as plain strings: no download, no extra assets).
 * Every snippet runs inside the Practical sandbox (no files, network or blocked modules).
 */
object PythonSnippets {
    val all: List<PythonSnippet> = listOf(
        // Basics
        PythonSnippet("hello", "Basics", "Hello, world", "Print a line of text",
            "print(\"Hello, world!\")"),
        PythonSnippet("variables", "Basics", "Variables and types", "Store values and check their type",
            "name = \"Asha\"\nage = 16\nheight = 1.62\nis_student = True\nprint(name, age, height, is_student)\nprint(type(age), type(height))"),
        PythonSnippet("if-elif", "Basics", "if / elif / else", "Choose between several branches",
            "score = 72\nif score >= 90:\n    print(\"Grade A\")\nelif score >= 60:\n    print(\"Grade B\")\nelse:\n    print(\"Keep practising\")"),
        // Input & output
        PythonSnippet("input-number", "Input & output", "Read a number", "Ask for input and convert it to int",
            "n = int(input(\"Enter a number: \"))\nprint(\"Double is\", n * 2)"),
        PythonSnippet("safe-input", "Input & output", "Safe number input", "Ask again until the input is a valid number",
            "while True:\n    text = input(\"Enter a whole number: \")\n    try:\n        value = int(text)\n        break\n    except ValueError:\n        print(\"That is not a whole number, try again.\")\nprint(\"You entered\", value)"),
        PythonSnippet("fstring", "Input & output", "f-string formatting", "Format numbers and text neatly",
            "item = \"Notebook\"\nprice = 45.5\nqty = 3\nprint(f\"{item:<10} x{qty}  Rs {price * qty:>8.2f}\")"),
        // Strings
        PythonSnippet("string-methods", "Strings", "Common string methods", "upper, lower, strip, replace, split",
            "text = \"  Learn Python with PyPath  \"\nclean = text.strip()\nprint(clean.upper())\nprint(clean.lower())\nprint(clean.replace(\"Python\", \"code\"))\nprint(clean.split())"),
        PythonSnippet("reverse-string", "Strings", "Reverse and palindrome", "Slice a string backwards",
            "word = input(\"Word: \").lower()\nif word == word[::-1]:\n    print(word, \"is a palindrome\")\nelse:\n    print(word[::-1])"),
        PythonSnippet("count-vowels", "Strings", "Count vowels", "Loop over characters",
            "sentence = \"Python is fun to learn\"\ncount = sum(1 for ch in sentence.lower() if ch in \"aeiou\")\nprint(\"Vowels:\", count)"),
        // Lists
        PythonSnippet("list-basics", "Lists", "List basics", "Add, remove, sort and slice",
            "fruits = [\"mango\", \"apple\", \"banana\"]\nfruits.append(\"kiwi\")\nfruits.remove(\"apple\")\nfruits.sort()\nprint(fruits)\nprint(fruits[0], fruits[-1], len(fruits))"),
        PythonSnippet("list-comprehension", "Lists", "List comprehension", "Build a new list in one line",
            "numbers = range(1, 11)\nsquares = [n * n for n in numbers]\nevens = [n for n in numbers if n % 2 == 0]\nprint(squares)\nprint(evens)"),
        PythonSnippet("list-stats", "Lists", "Min, max, average", "Quick statistics on a list",
            "marks = [78, 92, 65, 88, 71]\nprint(\"Highest:\", max(marks))\nprint(\"Lowest:\", min(marks))\nprint(\"Average:\", round(sum(marks) / len(marks), 1))"),
        // Dictionaries
        PythonSnippet("dict-basics", "Dictionaries", "Dictionary basics", "Store and update key-value pairs",
            "student = {\"name\": \"Ravi\", \"age\": 15}\nstudent[\"city\"] = \"Ranchi\"\nstudent[\"age\"] += 1\nfor key, value in student.items():\n    print(key, \"->\", value)"),
        PythonSnippet("word-count", "Dictionaries", "Count words", "Count how often each word appears",
            "text = \"the cat and the hat and the bat\"\ncounts = {}\nfor word in text.split():\n    counts[word] = counts.get(word, 0) + 1\nprint(counts)"),
        // Loops
        PythonSnippet("for-range", "Loops", "for loop with range", "Repeat a fixed number of times",
            "for i in range(1, 6):\n    print(\"Step\", i)"),
        PythonSnippet("while-loop", "Loops", "while loop", "Repeat until a condition changes",
            "count = 3\nwhile count > 0:\n    print(count)\n    count -= 1\nprint(\"Lift off!\")"),
        PythonSnippet("enumerate", "Loops", "enumerate and zip", "Loop with an index or over two lists",
            "names = [\"Asha\", \"Ravi\", \"Meera\"]\nscores = [88, 75, 93]\nfor i, (name, score) in enumerate(zip(names, scores), start=1):\n    print(f\"{i}. {name}: {score}\")"),
        PythonSnippet("times-table", "Loops", "Multiplication table", "Nested output with formatting",
            "n = 7\nfor i in range(1, 11):\n    print(f\"{n} x {i:>2} = {n * i:>3}\")"),
        // Functions
        PythonSnippet("function", "Functions", "Function with default value", "Define and call a function",
            "def greet(name, greeting=\"Hello\"):\n    return f\"{greeting}, {name}!\"\n\nprint(greet(\"Asha\"))\nprint(greet(\"Ravi\", \"Welcome\"))"),
        PythonSnippet("recursion", "Functions", "Recursion: factorial", "A function that calls itself",
            "def factorial(n):\n    if n <= 1:\n        return 1\n    return n * factorial(n - 1)\n\nprint(factorial(5))"),
        PythonSnippet("lambda-sort", "Functions", "Sort with a key", "Use lambda to sort records",
            "people = [(\"Asha\", 16), (\"Ravi\", 14), (\"Meera\", 15)]\nfor name, age in sorted(people, key=lambda p: p[1]):\n    print(name, age)"),
        // Classes
        PythonSnippet("class", "Classes", "Simple class", "Attributes, methods and __str__",
            "class BankAccount:\n    def __init__(self, owner, balance=0):\n        self.owner = owner\n        self.balance = balance\n\n    def deposit(self, amount):\n        self.balance += amount\n\n    def __str__(self):\n        return f\"{self.owner}: Rs {self.balance}\"\n\naccount = BankAccount(\"Asha\")\naccount.deposit(500)\nprint(account)"),
        // Errors
        PythonSnippet("try-except", "Errors", "try / except / finally", "Handle an error without crashing",
            "try:\n    result = 10 / 0\nexcept ZeroDivisionError:\n    print(\"You can't divide by zero\")\nfinally:\n    print(\"Done\")"),
        // Math & random
        PythonSnippet("random", "Math & random", "Random numbers", "Dice roll and random choice",
            "import random\n\nprint(\"Dice:\", random.randint(1, 6))\nprint(\"Pick:\", random.choice([\"rock\", \"paper\", \"scissors\"]))"),
        PythonSnippet("math", "Math & random", "math module", "Square root, pi and rounding",
            "import math\n\nprint(math.sqrt(144))\nprint(round(math.pi, 3))\nprint(math.floor(7.8), math.ceil(7.2))"),
        PythonSnippet("primes", "Math & random", "Prime numbers", "Check and list primes",
            "def is_prime(n):\n    if n < 2:\n        return False\n    for d in range(2, int(n ** 0.5) + 1):\n        if n % d == 0:\n            return False\n    return True\n\nprint([n for n in range(2, 50) if is_prime(n)])"),
    )

    val categories: List<String> get() = all.map { it.category }.distinct()

    fun byId(id: String): PythonSnippet? = all.firstOrNull { it.id == id }
}
