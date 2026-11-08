"""
Reference solutions for the checked Practical tasks (app/src/main/assets/practical/tasks.json).

NOT shipped in the APK: this file lives in content/, which Gradle never packages.
content/validate_tasks.py runs every solution against every test of its task and fails if a
single one does not match, so each solution here is a proof that its task can be solved with
exactly the wording in the task statement.

Rules for solutions:
  * Use only concepts taught up to the task's unlocking sub-level ("afterSubLevel").
  * Standard library only, nothing the Practical sandbox blocks.
  * Deterministic: no unseeded random numbers, no clock, no unordered set output.

SOLUTIONS maps task id -> Python source code.
"""

SOLUTIONS = {
    # ───────────── Existing tasks that are now checkable ─────────────
    "p-type-detective": '''age_text = "14"
print(type(age_text))
age = int(age_text)
print(age + 5)
''',
    "p-greeting": '''name = input("Enter your name: ")
print("Hello", name)
''',
    "p-birthday-maths": '''name = input("Name: ")
age = int(input("Age: "))
print(f"{name}, next year you will be {age + 1}")
''',
    "p-compare": '''a = int(input("First: "))
b = int(input("Second: "))
print(f"{a} > {b} is {a > b}")
print(f"{a} == {b} is {a == b}")
''',
    "p-voting": '''age = int(input("Age: "))
if age >= 18:
    print("You can vote")
else:
    years = 18 - age
    print(f"You can vote in {years} years")
''',
    "p-grades": '''mark = int(input("Mark: "))
if mark >= 90:
    print("Grade A")
elif mark >= 75:
    print("Grade B")
elif mark >= 50:
    print("Grade C")
else:
    print("Keep practising")
''',
    "p-times-table": '''number = int(input("Number: "))
for i in range(1, 11):
    print(f"{number} x {i} = {number * i}")
''',
    "p-countdown": '''n = int(input("Start: "))
while n > 0:
    print(n)
    n -= 1
print("Lift off!")
''',

    # ───────────── New tasks (Batch 2) ─────────────
    "p-piggy-bank": '''total = 0
while True:
    coin = int(input("Coin (0 to stop): "))
    if coin == 0:
        break
    total += coin
print(f"Total: {total}")
''',
    "p-star-stairs": '''rows = int(input("Rows: "))
for row in range(1, rows + 1):
    print("*" * row)
''',
    "p-banner": '''def banner():
    print("==========")
    print("Welcome to PyCafe")
    print("==========")

times = int(input("How many banners? "))
for i in range(times):
    banner()
''',
    "p-floor-tiles": '''def show_tiles(length, width):
    print(f"A {length} by {width} room needs {length * width} tiles")

length = int(input("Length: "))
width = int(input("Width: "))
show_tiles(length, width)
''',
    "p-bus-tickets": '''def ticket_price(age):
    if age < 5:
        return 0
    elif age < 18:
        return 50
    elif age < 60:
        return 100
    else:
        return 60

travellers = int(input("Travellers: "))
total = 0
for i in range(travellers):
    age = int(input("Age: "))
    price = ticket_price(age)
    print(f"Ticket price: Rs {price}")
    total += price
print(f"Total: Rs {total}")
''',
    "p-cafe-order": '''def order_text(item, size="medium"):
    return f"One {size} {item}, please"

item = input("Item: ")
size = input("Size (leave empty for medium): ")
if size == "":
    text = order_text(item)
else:
    text = order_text(item, size=size)
print(text)
''',
    "p-day-finder": '''days = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
n = int(input("Day number (1-7): "))
print(f"Day {n} is {days[n - 1]}")
''',
    "p-expense-check": '''count = int(input("How many items? "))
prices = []
for i in range(count):
    prices.append(int(input("Price: ")))

total = 0
big = 0
for price in prices:
    total += price
    if price >= 100:
        big += 1
print(f"Total: {total}")
print(f"Items of 100 or more: {big}")
''',
    "p-sale-prices": '''prices = [120, 80, 45, 300]
discount = int(input("Discount: "))
sale = [p - discount for p in prices]
cheap = [p for p in sale if p < 50]
print(f"Sale prices: {sale}")
print(f"Under 50: {cheap}")
''',
    "p-username": '''name = input("Full name: ")
username = name.strip().lower().replace(" ", "_")
print(f"Username: {username}")
''',
    "p-report-card": '''name = input("Student: ")
m1 = int(input("Mark 1: "))
m2 = int(input("Mark 2: "))
m3 = int(input("Mark 3: "))
total = m1 + m2 + m3
average = total / 3
print(f"{name}: total {total}, average {average:.2f}")
''',
    "p-menu-lookup": '''menu = {"tea": 15, "coffee": 25, "samosa": 20}
item = input("Item: ").strip().lower()
if item in menu:
    qty = int(input("How many? "))
    print(f"{qty} {item} = Rs {menu[item] * qty}")
else:
    print(f"Sorry, we don't sell {item}")
''',
    "p-vote-counter": '''counts = {}
while True:
    vote = input("Vote (done to finish): ")
    if vote == "done":
        break
    counts[vote] = counts.get(vote, 0) + 1

if len(counts) == 0:
    print("No votes")
else:
    winner = ""
    best = 0
    for option, n in counts.items():
        print(f"{option}: {n}")
        if n > best:
            best = n
            winner = option
    print(f"Winner: {winner} ({best})")
''',
    "p-safe-divide": '''first = input("First number: ")
second = input("Second number: ")
try:
    a = int(first)
    b = int(second)
    print(f"Result: {a / b:.2f}")
except ValueError:
    print("Please type whole numbers")
except ZeroDivisionError:
    print("Cannot divide by zero")
''',
    "p-cash-machine": '''while True:
    text = input("Amount: ")
    try:
        amount = int(text)
    except ValueError:
        print("Please type a whole number.")
        continue
    if amount < 100:
        print("Amount must be at least 100.")
    elif amount > 10000:
        print("Amount must be at most 10000.")
    elif amount % 100 != 0:
        print("Amount must be a multiple of 100.")
    else:
        break
print(f"Dispensing Rs {amount}")
''',
    "p-box-packer": '''import math

books = int(input("Books: "))
per_box = int(input("Books per box: "))
boxes = math.ceil(books / per_box)
print(f"Boxes needed: {boxes}")
print(f"Empty spaces: {boxes * per_box - books}")
''',
    "p-seeded-dice": '''import random

seed = int(input("Seed: "))
random.seed(seed)
total = 0
for i in range(1, 4):
    roll = random.randint(1, 6)
    print(f"Roll {i}: {roll}")
    total += roll
print(f"Total: {total}")
''',
    "p-diary": '''with open("diary.txt", "w") as f:
    f.write("")

while True:
    entry = input("Entry (leave empty to finish): ")
    if entry == "":
        break
    with open("diary.txt", "a") as f:
        f.write(entry + "\\n")

print("Your diary:")
count = 0
with open("diary.txt") as f:
    for line in f:
        count += 1
        print(f"{count}. {line.strip()}")
print(f"{count} entries saved")
''',
    "p-bank-account": '''class Account:
    def __init__(self, owner, balance=0):
        self.owner = owner
        self.balance = balance

    def deposit(self, amount):
        self.balance += amount

    def withdraw(self, amount):
        if amount > self.balance:
            print("Not enough money")
        else:
            self.balance -= amount


owner = input("Owner: ")
acc = Account(owner)
while True:
    action = input("Action (deposit/withdraw/quit): ")
    if action == "quit":
        break
    if action == "deposit":
        amount = int(input("Amount: "))
        acc.deposit(amount)
        print(f"Balance: {acc.balance}")
    elif action == "withdraw":
        amount = int(input("Amount: "))
        acc.withdraw(amount)
        print(f"Balance: {acc.balance}")
    else:
        print("Unknown action")
print(f"{acc.owner} finished with Rs {acc.balance}")
''',
    "p-book-shelf": '''class Book:
    def __init__(self, title, author, pages):
        self.title = title
        self.author = author
        self.pages = pages

    def __str__(self):
        return f"{self.title} by {self.author}, {self.pages} pages"


count = int(input("How many books? "))
books = []
for i in range(count):
    title = input("Title: ")
    author = input("Author: ")
    pages = int(input("Pages: "))
    books.append(Book(title, author, pages))

if len(books) == 0:
    print("No books")
else:
    longest = books[0]
    for book in books:
        print(book)
        if book.pages > longest.pages:
            longest = book
    print(f"Longest: {longest.title}")
''',
}
