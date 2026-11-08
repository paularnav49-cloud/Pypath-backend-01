"""Batch 1: hints and per-option feedback for every existing lesson MCQ (Levels 1-6, 125 questions).

Format of each entry:
    [question id]
    hint: one sentence that nudges without giving the answer away
    A: feedback for option index 0
    B: feedback for option index 1
    C: feedback for option index 2
    D: feedback for option index 3
Feedback for the correct option starts with "Right:"; build_course.py checks this lines up with
correctIndex. The question text, options, correctIndex and explanation are NOT stored here and are
never changed: these two fields are only added to the existing questions.
"""

FEEDBACK_TEXT = r'''
[l1s1q1]
hint: Think about what you saw appear after running print("Hello, World!").
A: print() does not restart anything; Python runs each line once, from top to bottom.
B: Right: print() displays whatever is inside its parentheses on the screen.
C: Only the # symbol makes a comment; print() does the opposite and shows text to the user.
D: print() does not stop the program; the lines after it still run, one after another.

[l1s1q2]
hint: Notice there are no quotes inside the parentheses, so Python treats it as maths.
A: You would only see 2 + 3 if it were text inside quotes, like print("2 + 3").
B: print shows the value itself without quotes, so the quotes would not appear on screen.
C: Right: without quotes, 2 + 3 is maths, so Python works it out and prints the answer.
D: This is valid code: print can show the result of a sum, so nothing goes wrong.

[l1s1q3]
hint: Recall the Comments page: which symbol did those ignored lines begin with?
A: // starts comments in some other languages, but Python does not use it for comments.
B: Right: Python ignores everything after # on a line.
C: -- is used for comments in some other languages, not in Python.
D: /* comes from other languages; Python does not treat it as the start of a comment.

[l1s1q4]
hint: Remember the key point about a program being a list of instructions.
A: Python is predictable: it never picks lines at random, so the same code runs the same way each time.
B: Python starts with the first line you wrote, not the last one.
C: Right: Python reads your code line by line, starting with the first line.
D: Line length doesn't matter; Python follows the order the lines are written in.

[l1s2q1]
hint: Recall which side of the = sign the variable name goes on.
A: Right: the name goes on the left, then =, then the value to store.
B: This is backwards: the name must be on the left of = and the value on the right.
C: Without the = sign, Python is never told to store the value, so this is an error.
D: Python does not use the word var; you just write the name, =, and the value.

[l1s2q2]
hint: Think about what happens to the old value when you store a new one.
A: 10 was the first value, but the second line replaces it before print runs.
B: Right: the newest value replaces the old one, so score holds 15.
C: Storing a new value replaces the old one; Python does not add them together.
D: score has no quotes in print(score), so Python shows the value stored in it, not the name.

[l1s2q3]
hint: Go through the naming rules page and test each name against every rule.
A: Variable names can't start with a number, so 2fast is not allowed.
B: Names can't contain spaces; Python would see two separate words.
C: Right: user_age uses only letters and an underscore, and starts with a letter.
D: A hyphen is not allowed in a name; only letters, numbers and underscores are.

[l1s2q4]
hint: Replace each variable with the number stored in it, then work out the sum.
A: a and b have no quotes, so print shows the result of the sum, not the letters.
B: 3 and 4 are numbers, so + does real maths with them; it does not write them side by side.
C: Right: a is 3 and b is 4, so a + b is 7.
D: Both variables are created before print uses them, so the code runs fine.

[l1s3q1]
hint: Look closely at the dot in the number and recall the four main types.
A: int is only for whole numbers like 42, but 3.14 has a decimal part.
B: A str is text and would need quotes, like "3.14".
C: Right: a number with a decimal point is a float.
D: bool only has two values, True and False, so 3.14 can't be a bool.

[l1s3q2]
hint: Notice the quotes around each value and recall how + treats text.
A: You'd get 5 only with numbers; the quotes make these strings, so they are not added as maths.
B: Right: both values are strings, so + joins them side by side.
C: The + is outside the quotes, so Python performs it rather than printing it as text.
D: Joining two strings with + is allowed, so there is no error here.

[l1s3q3]
hint: A bool has no quotes and is one of exactly two special values.
A: The quotes make this text (a str), even though the word inside looks like a bool.
B: Right: True without quotes is one of the two bool values.
C: 1.0 has a decimal point, so it is a float, not a bool.
D: "yes" is in quotes, so it is a str; Python's bool values are only True and False.

[l1s3q4]
hint: Remember that each conversion function is named after the type it produces.
A: str() turns a value into text, which is the opposite of what we need here.
B: float() gives a decimal number such as 42.0, not a whole number.
C: type() only tells you what type a value is; it doesn't change it.
D: Right: int() turns text like "42" into the whole number 42.

[l1s4q1]
hint: Think about what type(age) printed even when the user typed 18.
A: Even when the user types digits like 18, input() hands them back as text.
B: Right: input() always gives back what was typed as a string (text).
C: input() never turns the answer into True or False; it keeps the typed characters as text.
D: input() does give something back: the text the user typed, which you can store in a variable.

[l1s4q2]
hint: Think about which job happens first: asking the question or converting the answer.
A: Right: input() gets the text first, then int() turns it into a whole number.
B: This puts int() inside input(), so the conversion happens before the user has typed anything.
C: Python has no number() function; the converter for whole numbers is int().
D: There is no .int on text; conversion is done by wrapping the value in int( ).

[l1s4q3]
hint: Remember what the f before the quotes does to anything inside curly braces.
A: Because of the f before the quotes, {x} is replaced by the value, so the braces don't appear.
B: Inside an f-string, {x} means the value of x, not the letter x.
C: Right: {x} is replaced with the value of x, which is 5.
D: The f only marks the string as an f-string; it is not printed itself.

[l1s4q4]
hint: Think about the "What is your name?" example and when that text appeared.
A: The text inside the parentheses is shown as a question before the user types.
B: Python runs input(); it shows your question text, not the name of the function.
C: Right: the text inside input() is displayed as the question.
D: Giving input() a question in quotes is correct, so there is no error.

[l2s1q1]
hint: Read >= aloud as "greater than or equal to", then test the two numbers.
A: Right: 7 is equal to 7, and >= allows equal values, so the answer is True.
B: False would be right for 7 > 7, but >= also counts values that are equal.
C: A comparison always answers True or False; it never gives back one of the numbers.
D: >= is a valid comparison operator, so this works without an error.

[l2s1q2]
hint: Remember the key point: one symbol stores a value, another one compares.
A: A single = stores a value in a variable; it does not compare.
B: Right: == asks whether two values are equal and gives True or False.
C: != checks the opposite: it is True when the values are not equal.
D: => is not a Python operator; "greater or equal" is written >= instead.

[l2s1q3]
hint: Ask yourself whether ten is smaller than three.
A: True would mean 10 is less than 3, but 10 is the bigger number.
B: A comparison prints True or False, not one of the numbers being compared.
C: Right: 10 is not less than 3, so the comparison is False.
D: Comparing two numbers with < is valid code, so there is no error.

[l2s2q1]
hint: Look at the very end of the if age >= 18 line in the first example.
A: Right: an if line always ends with a colon, and the indented lines follow.
B: Python does not end if lines with a semicolon; that habit comes from other languages.
C: A full stop at the end of an if line is an error; Python expects a different symbol.
D: Leaving the end empty causes an error; the lesson warns this is the most common mistake.

[l2s2q2]
hint: Work out whether 3 > 10 is True or False first.
A: Big prints only when n > 10 is True, but 3 is not greater than 10.
B: Right: 3 > 10 is False, so the else branch runs.
C: With if and else, exactly one of the two branches runs, never both.
D: When the if condition is False, the else branch runs, so something is always printed here.

[l2s2q3]
hint: Look at how the print line sits further to the right than the if line.
A: Curly braces are used in some other languages, but Python does not use them for this.
B: Python has no end word to close an if; it uses something you can see at the start of each line.
C: Right: lines indented under the if (usually 4 spaces) belong to it.
D: Line numbers only help you find lines; they don't group code.

[l2s3q1]
hint: Recall the start of the lesson, where elif is explained as a short form.
A: Right: elif is short for else if, so it is tried only when the conditions above are False.
B: elif does not end anything; the chain ends where the indented lines stop.
C: elif doesn't leave the program; it tests another condition.
D: There is no "either if" in Python; elif joins the words else and if.

[l2s3q2]
hint: Check the conditions one at a time from the top, stopping at the first True one.
A: High needs x > 80, and 50 is not greater than 80.
B: Right: 50 > 80 is False but 50 > 30 is True, so Medium prints.
C: else only runs when every condition above is False, but 50 > 30 is True.
D: Only the first True branch runs, so you never see two messages from one chain.

[l2s3q3]
hint: Remember the key point about how Python checks the conditions from top to bottom.
A: Python stops at the first True branch, so later True conditions are skipped.
B: Two branches never run together; once one runs, the rest of the chain is skipped.
C: Right: Python runs the first True branch, or the else if none are True.
D: With an else at the end, one branch always runs, even when every condition is False.

[l3s1q1]
hint: Remember what each of the three numbers in range means, especially the stop number.
A: This includes 9, but range always stops before the stop number, so 9 is left out.
B: range starts at the first number, which is 0 here, and it also stops before 9.
C: The third number is the step, so the loop jumps by 3 instead of counting by 1.
D: Right: it starts at 0, jumps by 3, and stops before 9, giving 0, 3, 6.

[l3s1q2]
hint: Think about which end of range() is included and which end is left out.
A: Right: range includes the start, 2, and stops just before 6.
B: This includes 6, but range stops before the stop number.
C: range includes the start number, so the sequence begins at 2, not 3.
D: range gives every whole number from the start up to (but not including) the stop, not just the two ends.

[l3s1q3]
hint: Compare this for line with the if lines you wrote in Level 2: how do they end?
A: range() uses round brackets, exactly as in range(3); square brackets are not needed.
B: Right: every for line must end with a colon, just like an if line.
C: The loop variable can have any valid name; the lesson used i, n and others.
D: The body of a loop must be indented; removing the indent would break the code.

[l3s1q4]
hint: Remember the common mistake tip about where range stops counting.
A: Right: start at 1 and stop at 11, so the last number printed is 10.
B: range(1, 10) stops before 10, so it prints only 1 to 9.
C: range(0, 10) starts at 0 and stops before 10, so it adds 0 and misses 10.
D: range(10) also stops before 10, so 10 is never printed.

[l3s1q5]
hint: Track the value of total after each repeat, and list the numbers range(1, 4) gives.
A: 10 would need 4 to be added too, but range(1, 4) stops before 4.
B: 3 is only the last number the loop sees; total keeps adding every number, not just the last one.
C: Right: total goes 1, then 3, then 6.
D: total += n runs inside the loop on every repeat, so total does not stay at 0.

[l3s2q1]
hint: Check the condition just before each repeat, especially when x has become 4.
A: When x becomes 4, x < 4 is False, so the loop ends before printing 4.
B: Right: 1, 2 and 3 are printed, then x is 4 and the condition fails.
C: x starts at 1, and print runs before x += 1, so the first number shown is 1.
D: print is inside the loop, so it runs on every repeat, not only once at the end.

[l3s2q2]
hint: Recall what a while loop checks before every single repeat.
A: A while loop has no built-in repeat limit; it depends only on its condition.
B: It's the other way round: the loop keeps going while the condition is True.
C: Reaching the end of the loop body only sends Python back to check the condition again.
D: Right: the loop repeats while the condition is True and stops as soon as it is False.

[l3s2q3]
hint: Follow the value of n through a few repeats and see whether it ever changes.
A: range() is used with for loops; a while loop only needs a condition.
B: A single = stores a value; a condition needs a comparison such as >.
C: Right: nothing inside the loop changes n, so n > 0 stays True forever.
D: Any lines can go inside a while loop, including print; the lesson's countdown prints inside its loop.

[l3s2q4]
hint: The condition is n > 0, so think about which direction n must move.
A: Right: n -= 1 makes n smaller each time until n > 0 is False.
B: n += 1 makes n bigger, so n > 0 stays True and the loop never ends.
C: n = 3 resets n to the same value each time, so the loop never finishes.
D: == only compares and gives True or False; it does not change n.

[l3s2q5]
hint: Write down n after each subtraction and check n > 3 every time.
A: Right: n goes 10, 7, 4, 1, and only at 1 is n > 3 False.
B: At 4, the condition n > 3 is still True, so the loop subtracts 3 once more.
C: n never equals 3 here: it jumps from 4 straight down to 1.
D: 7 is n after only the first repeat; 7 > 3 is True, so the loop keeps going.

[l3s2q6]
hint: Think about when the condition is checked: before or after the first repeat?
A: The condition is checked before the first repeat, so a False start means the body never runs.
B: Right: if the condition is False at the start, the indented lines are skipped completely.
C: While loops can count down too, like the countdown that printed 3, 2, 1.
D: while is the loop to use when you don't know the number of repeats in advance.

[l3s3q1]
hint: Notice that the if with break comes before print inside the loop.
A: Right: when n is 3, break ends the loop before print runs.
B: break runs before print(n) when n is 3, so 3 is never shown.
C: The numbers before 3 are printed first; break only stops what comes after.
D: Skipping one number and carrying on is what continue does; break leaves the loop entirely.

[l3s3q2]
hint: Remember the key point: one keyword leaves the loop, the other skips a repeat.
A: When n is 2, continue jumps past print, so 2 is not shown.
B: Stopping after 1 is what break would do; continue lets the loop keep going.
C: n is 1 on the first repeat and the if is False, so 1 is printed.
D: Right: only the repeat where n is 2 is skipped.

[l3s3q3]
hint: Look at the odd numbers example and notice which numbers were missing.
A: Stopping the loop completely is what break does.
B: continue moves on to the next number; it never goes back to the start.
C: Right: continue skips the rest of the current repeat and moves to the next one.
D: Waiting for the user is what input() does; continue doesn't wait for anything.

[l3s3q4]
hint: Think of the leftover after sharing seven sweets equally between three friends.
A: 2 is how many times 3 fits into 7; % gives what is left over instead.
B: Right: 3 fits into 7 twice (6), leaving a remainder of 1.
C: % does not give a decimal answer; it gives the whole-number remainder.
D: 21 is 7 times 3; the % symbol means remainder, not multiply.

[l3s4q1]
hint: Count how many times the inner loop runs for each single outer repeat.
A: 7 adds the two counts, but the inner loop runs fully for every outer repeat, so you multiply.
B: Right: 4 inner repeats for each of 3 outer repeats is 3 x 4 = 12.
C: 4 is just one pass of the inner loop; it runs again for every outer repeat.
D: 3 counts only the outer repeats, but print is inside the inner loop.

[l3s4q2]
hint: Work out what r is on each repeat, then how many hashes that line gets.
A: r starts at 1 and grows, so the first line is the shortest.
B: Right: r is 1, 2, 3, so each line has one more # than the last.
C: "#" * r repeats the hash r times, so the lines grow longer.
D: print runs inside the loop, once per repeat, so there are three lines, not one.

[l3s4q3]
hint: Recall what print normally does straight after it shows a value.
A: Right: end=" " puts a space after the value instead of a new line.
B: end is a setting of print, not a command to stop the program.
C: end=" " removes the move to a new line; it does not add an extra blank line.
D: end is a setting name, not text in quotes, so the word end is never printed.

[l3s4q4]
hint: Recall what multiplying a string by a whole number does.
A: * with a string repeats it; it does not stick the number on the end.
B: Repeating a string adds no spaces between the copies.
C: A string times a whole number is allowed; only a string times a string causes an error.
D: Right: "ab" is repeated 3 times with nothing in between.

[l3s4q5]
hint: Look at how far the final print() is indented compared with each loop.
A: end="" is allowed anywhere; it just keeps the stars on the same line.
B: Both ranges are fine; the problem is where the new line is printed, not how many stars there are.
C: Right: print() runs only once after both loops, so indent it inside the outer loop.
D: Changing to range(4) would just print more stars, still all on one line.

[l3s4q6]
hint: List every pair of i and j in order, then add each pair.
A: This counts up by one, but i and j only go up to 1, so the biggest sum is 1 + 1.
B: That only covers pairs where i and j are equal; the inner loop runs fully for each i.
C: Right: the pairs are (0,0), (0,1), (1,0), (1,1), giving 0, 1, 1, 2.
D: Both loops start at 0, so the first sum is 0 + 0, not 1.

[l3s4q7]
hint: Think about the print setting that replaces the usual move to a new line.
A: Right: end=" " replaces the new line with a space, so 1 2 3 stay on one line.
B: print has no space setting; the setting that controls what comes after the value is end.
C: A plain " " is printed as a second value, but print still moves to a new line each time.
D: print has no line setting, so this would cause an error.

[l3s4q8]
hint: Remember how 0 0, 0 1, 0 2 all printed before row changed to 1.
A: The inner loop runs inside each outer repeat, so the outer loop can't finish first.
B: Each loop has its own range, so they can repeat different numbers of times, like 2 and 3 in the example.
C: The inner loop can use the outer variable, as in for col in range(row).
D: Right: for each outer repeat, the inner loop starts again and runs to the end.

[l4s1q1]
hint: Look at the very first word of the greet example in this lesson.
A: Right: Python starts every function definition with def.
B: function is used in some other languages, but Python uses a much shorter word.
C: func is not a Python keyword; Python uses def.
D: Close, but Python uses the short form def, not the full word define.

[l4s1q2]
hint: Remember that a def block is only remembered until the function is called.
A: Right: the def is only remembered, so print("B") runs first and then show() prints A.
B: Defining show() does not run it, so A can't appear until show() is called below print("B").
C: show() is called on the last line, so A is printed too.
D: print("B") is outside the function and runs on its own, so B is printed as well.

[l4s1q3]
hint: Compare the def line with the if and for lines you learned earlier.
A: Function names are usually lowercase, like greet and say_hi; capitals are not required.
B: Right: a def line must end with a colon, like if and for lines.
C: The body of a function must be indented, just like the body of an if.
D: Python does not use semicolons at the end of def lines.

[l4s1q4]
hint: Count how many times the function is called, not how many times it is defined.
A: The function is called three times, and each call prints beep again.
B: beep() is a call, not text in quotes, so it runs the function instead of printing its name.
C: The three beep() lines below the def are calls, so the body does run.
D: Right: each of the three calls prints beep once.

[l4s1q5]
hint: Think about the say_hi example that failed because it was called too early.
A: The body only runs when you call the function, not when Python reads def.
B: Python prints nothing when it reads def; only print() inside a called function shows text.
C: Right: def stores the function so you can call it later, as many times as you like.
D: A function can be called many times, like greet() twice in the lesson; it is not removed.

[l4s2q1]
hint: Replace the parameter with the value passed in the call, then do the joining.
A: Right: word holds "Hi", and + joins it with "!".
B: word has no quotes inside the function, so its value is used, not the name.
C: The function adds "!" to the word before printing it.
D: Both values are strings, so + can join them without an error.

[l4s2q2]
hint: Recall the difference between the names in the def line and the values in a call.
A: Arguments are the actual values passed in a call, like area(3, 4).
B: Right: names inside the brackets of the def line are parameters.
C: The function's name is area; width and height are inside its brackets.
D: A call would look like area(3, 4); this line defines the function instead.

[l4s2q3]
hint: Arguments are matched in order, so work out which value goes into a and b.
A: 7 would be 10 - 3, but a is 3 and b is 10, so the answer is negative.
B: a - b is not in quotes, so Python works out the subtraction instead of printing the text.
C: Right: a is 3 and b is 10, so a - b is -7.
D: Subtracting a bigger number is allowed; it just gives a negative answer.

[l4s3q1]
hint: Put 4 in place of x and work out what * does with it.
A: 7 is 4 + 3, but * means multiply, not add.
B: Right: 4 * 3 is 12, and return sends it back to print.
C: x is the number 4, not text, so * multiplies instead of repeating.
D: The function has a return line, so it gives back a value instead of None.

[l4s3q2]
hint: Think about what a function gives back when it has no return line.
A: f prints 3, but it has no return, so y is None, not 3.
B: print(x) inside f still runs when f(3) is called, so 3 appears first.
C: 3 is printed inside f, but there is a second print for y too.
D: Right: f prints 3, then returns None because it has no return line.

[l4s3q3]
hint: Recall the print vs return page and what the variable b received.
A: Showing a value on screen is print's job; return passes it back to your code.
B: Right: return hands the value back to the caller and the function stops there.
C: return ends the function; it never starts it again from the top.
D: return gives the value only to the code that called the function; it doesn't share it everywhere.

[l4s3q4]
hint: Check whether result is ever handed back to the code that called area.
A: Parameter names can be anything; w and h work fine.
B: print can show what a function returns, like print(square(3)) in the lesson.
C: Right: result is calculated but not returned, so the call gives back None.
D: Printing inside would show 10, but area(2, 5) would still return None for the outer print.

[l4s3q5]
hint: Check whether -2 < 0 is True, and remember what return does to the function.
A: Right: -2 < 0 is True, so the first return runs and the function ends.
B: The second return is never reached, because the first return already ended the function.
C: return ends the function, so only one of the two returns can ever run.
D: Both paths have a return, so the function always gives back a word, never None.

[l4s3q6]
hint: Think about the difference between showing a value and handing it back to the caller.
A: Right: return a + b sends 3 back, and print shows it.
B: print would show 3 inside the function, but add would return None, so None is printed too.
C: give is not a Python keyword; the word that sends a value back is return.
D: result = a + b only stores the sum in a local variable, so the function still returns None.

[l4s4q1]
hint: times was not passed in the call, so check the def line for its value.
A: times defaults to 2, so the word is repeated twice, not shown once.
B: The default is 2, not 3, so "ha" is repeated only twice.
C: times has a default value, so leaving it out is allowed.
D: Right: the default times=2 is used, and "ha" * 2 is "haha".

[l4s4q2]
hint: Keyword arguments are matched by name, so check which value went to each name.
A: With keyword arguments the order in the call doesn't matter; name is still "Mia".
B: Right: name="Mia" and age=9 are matched by name, so the f-string gives Mia-9.
C: The f-string shows the values in {name} and {age}, not the parameter names.
D: Passing arguments by name in any order is allowed, so there is no error.

[l4s4q3]
hint: Recall the common mistake about where parameters with defaults must go.
A: This is valid: the parameter with a default comes after the one without.
B: This is valid: every parameter has a default, so the order rule is never broken.
C: Right: b has no default but comes after a=1, which Python does not allow.
D: This is valid: two normal parameters with no defaults.

[l4s4q4]
hint: Compare greet("Asha") with greet("Ravi", "Welcome") and look at the greetings.
A: Right: the default only fills in when the caller leaves that argument out.
B: A value passed by the caller always replaces the default, as "Welcome" did.
C: Defaults have nothing to do with return; they are about missing arguments.
D: Defaults also work with normal calls, like order("tea") in the lesson.

[l4s5q1]
hint: Remember what happens to a variable made inside a function once the call ends.
A: y only existed while f was running, so it's gone when print(y) runs.
B: Right: y is local to f, so print(y) outside can't find it and Python raises a NameError.
C: None is what a function returns without return; here the problem is that y doesn't exist outside.
D: A TypeError is about wrong arguments, like a missing one; a name that can't be found gives a different error.

[l4s5q2]
hint: Recall what a function may do with a variable created outside all functions.
A: Right: show() can read the global n, so it prints 1.
B: n was created outside the function, and functions can read global variables, so there is no error.
C: show() prints the value of n; None would only appear if you printed what show() returns.
D: Nothing ever sets n to 0; it holds 1 from the first line.

[l4s5q3]
hint: Assigning to a name inside a function creates a separate box for it.
A: a = 7 inside set_a makes a new local a; the global a is not changed.
B: print(a) prints the global variable a, which holds a number, not None.
C: Right: the global a is untouched and still holds 3.
D: a was created at the top of the file, so print(a) can find it.

[l4s5q4]
hint: Think about the make_tea example and where cups could be used.
A: No function can change a local from another function; locals belong to one call only.
B: A variable made at the top of the file, outside all functions, is global, not local.
C: Right: a local variable is made inside a function and lives only during that call.
D: Local variables can be changed while the function runs; local is about where they live, not whether they change.

[l4s5q5]
hint: Think about what happens to local variables when a call ends and a new one starts.
A: Right: locals are made fresh on each call and thrown away when it ends.
B: Locals are thrown away when the call ends, so nothing is kept for the next call.
C: Functions can read global variables, like welcome() reading shop.
D: Parameters belong to the function call, so they are local, not global.

[l4s5q6]
hint: Remember the "Pass in, return out" page and how add_bonus changed score.
A: Calling twice doesn't help: total is still local inside add, so the same error happens.
B: Right: pass total in and return total + n, then store the result outside.
C: Deleting the global doesn't fix it; total inside the function still has no value to read.
D: Printing total first still treats total as local, so it fails before anything is printed.

[l4s5q7]
hint: Every call starts again from the first line of the function body.
A: The first call's c is thrown away, so the second call starts again from 0.
B: c += 1 runs before return, so the function gives back 1, not 0.
C: count() has a return line, so print shows a number, not None.
D: Right: c is fresh on every call: it starts at 0 and becomes 1.

[l5s1q1]
hint: Recall which kind of brackets wrap a list, and what quotes do to anything inside them.
A: The quotes make this one string of text, not a list of three numbers.
B: Right: square brackets with commas between the values make a list.
C: Even with brackets inside, the quotes turn everything into a single string.
D: == compares two values; a single = is needed to store the list.

[l5s1q2]
hint: Think about whether len() adds up the values or counts them.
A: Right: len() counts the items, and there are three of them.
B: 27 is the total of the numbers, but len() counts items instead of adding them.
C: 15 is the last value in the list; len() tells you how many items there are.
D: 2 is the number of commas; len() counts the items, which is one more.

[l5s1q3]
hint: Check whether "cow" is exactly one of the items in the list.
A: True would need "cow" to be in pets, but pets only holds "cat" and "dog".
B: in answers a yes/no question with True or False; it doesn't print the value.
C: Right: "cow" is not one of the items, so in gives False.
D: Checking for a value that isn't there is allowed; it simply gives False.

[l5s1q4]
hint: Look carefully at what sits between each pair of names in the brackets.
A: Lists can hold any values, including text like names.
B: Lists use square brackets, which this code already has.
C: Single and double quotes both work for strings in a list.
D: Right: without a comma, Python joins "Asha" and "Ravi" into one string.

[l5s2q1]
hint: Remember which number the first position in a list starts at.
A: Right: indexes start at 0, so x[1] is the second item.
B: red is at index 0; x[1] is the item after it.
C: blue is at index 2, because counting starts at 0.
D: The list has 3 items, so index 1 exists and there is no error.

[l5s2q2]
hint: Recall where a negative index starts counting from.
A: Right: -1 is always the last item.
B: 5 is the first item, x[0]; negative indexes count from the end.
C: 7 is x[-2], the item just before the last one.
D: Negative indexes are allowed in Python, so there is no error.

[l5s2q3]
hint: Find the value at index 1, then remember the slice stops before the stop index.
A: The slice starts at index 1, which is the value 2, not the first item.
B: Right: it starts at index 1 and stops before index 4.
C: The stop index 4 is not included, so the 5 at index 4 is left out.
D: A slice gives every item from start up to the stop, not just two values.

[l5s2q4]
hint: List the indexes a two-item list has, then compare them with 2.
A: book is items[1]; index 2 would be a third item, which doesn't exist.
B: Right: the indexes are only 0 and 1, so items[2] goes past the end.
C: pen is items[0]; indexes don't wrap around to the start.
D: items exists, so this is not a NameError; the problem is the index.

[l5s2q5]
hint: Leaving out the start begins at 0; remember which index the slice stops before.
A: 1:3 starts at index 1, so it skips the first item and only gives two items.
B: 0:2 stops before index 2, so it gives only the first two items.
C: :2 also stops before index 2, giving only two items.
D: Right: :3 gives indexes 0, 1 and 2, the first three items.

[l5s2q6]
hint: Think about whether a[0] = 9 adds a new item or replaces one.
A: a[0] = 9 changes the list, so it doesn't stay the same.
B: Assigning to an index replaces that item; it doesn't insert a new one.
C: Right: a[0] = 9 replaces the first item, so the list is [9, 2, 3].
D: Index 0 is the first item, not the second.

[l5s3q1]
hint: Recall where append() puts the new value and whether the old items stay.
A: Right: append() adds 3 to the end and keeps 1 and 2.
B: append() adds to the end; adding at the front is a job for insert(0, ...).
C: append() changes the list itself, so 3 is added.
D: append() keeps the items already in the list; it doesn't replace them.

[l5s3q2]
hint: Think about which order sort() uses when you give it nothing in the brackets.
A: Largest first needs sort(reverse=True); plain sort() puts the smallest first.
B: sort() changes the list itself, so the order is not the same afterwards.
C: Right: sort() puts the numbers in order, smallest first.
D: sort() returns None, but here n itself is printed, and n was sorted.

[l5s3q3]
hint: Look at the Removing items example and what the variable last ended up holding.
A: pop() takes from the end of the list, not the start.
B: Adding to the end is append(); pop() removes.
C: Unlike append() and sort(), pop() gives the removed item back, so it isn't None.
D: Right: pop() removes the last item and hands it back so you can store it.

[l5s3q4]
hint: Remember the "Methods change the list" page and what sort() gives back.
A: Right: sort() sorts x in place and returns None, which x = then stores.
B: Numbers sort fine with sort(), as the marks example showed.
C: print can show any list; the problem is that x now holds None.
D: reverse=True is optional; plain sort() works too.

[l5s3q5]
hint: Think about what index 0 means and what happens to the items already there.
A: Adding to the end is what append() does; insert(0, ...) puts the item at the front.
B: Right: "a" goes to index 0 and the others move one place along.
C: insert() adds a new item; it doesn't replace "b".
D: Index 0 is the front of the list, not the middle.

[l5s4q1]
hint: The loop variable takes each item in turn, and print runs once per item.
A: Right: c is "x" then "y", and each print starts a new line.
B: The loop prints each item on its own, not the whole list.
C: Each print() starts on a new line, so the letters aren't joined.
D: The loop goes over the items themselves, not their index numbers.

[l5s4q2]
hint: These are numbers without quotes, so think about what += does with each one.
A: 6 is just the last number; total adds every number in the list.
B: Right: 2 + 4 + 6 is 12.
C: These are numbers, not strings, so += adds them instead of joining.
D: 3 is how many items there are; the loop adds up their values.

[l5s4q3]
hint: Recall the common mistake tip about looping over a list.
A: range() needs a number, not a list, so range(names) is an error.
B: The loop variable comes after for and the list after in; this has them mixed up.
C: Right: loop over the list directly, and name takes each item in turn.
D: Python uses in, not of, in a for loop.

[l5s4q4]
hint: Check each number against n > 4 and keep only those that pass.
A: 1 is not greater than 4, so it is the one number left out.
B: The if only appends numbers greater than 4, so not every number is kept.
C: print(big) shows the list itself, not how many items it has.
D: Right: only 5 and 8 are greater than 4, so they are appended.

[l5s5q1]
hint: List the numbers range(3) gives first, then double each one.
A: range(3) starts at 0, so the first value is 0 * 2, which is 0.
B: Right: range(3) gives 0, 1, 2, and each is doubled.
C: These are the numbers from range(3) before doubling; n * 2 changes each one.
D: range(3) stops before 3, so there are only three values.

[l5s5q2]
hint: The if at the end decides which items are kept, so test each one.
A: Right: only 3 and 4 are greater than 2.
B: These are the items that fail x > 2; the filter keeps the ones that pass.
C: 2 > 2 is False, so 2 is not kept.
D: The value at the front is x, so the items themselves go in, not True or False.

[l5s5q3]
hint: Remember the key point about what a list comprehension always makes.
A: Right: it's the same as looping and appending w + "s" to a new, empty list.
B: A comprehension builds a list; it doesn't print anything.
C: Nothing is removed, and no while loop is involved; it's a for loop that builds a list.
D: The original words list is not changed; a brand-new list is made.

[l5s5q4]
hint: Recall the common mistake tip about the order of the parts.
A: List comprehensions use square brackets, which this code already has.
B: Any list can be the source; it doesn't have to be range().
C: No colon or semicolon belongs here; comprehensions have neither.
D: Right: write [n * 2 for n in nums] with the value first and no colon.

[l5s5q5]
hint: For each word, work out w + w, then remember the result is a list.
A: w + w joins each word with itself into one item; it doesn't add extra items.
B: A comprehension makes a list, so the result is shown in square brackets with separate items.
C: Right: each word is joined with itself, giving one new item per word.
D: w is one word at a time, so the two words are never joined together.

[l5s5q6]
hint: Recall what the remainder is when an even number is divided by 2.
A: n % 2 == 1 keeps numbers with a remainder of 1, which are the odd ones.
B: Right: even numbers have no remainder when divided by 2.
C: A filter in a comprehension uses if, not while.
D: for is already used once; the filter at the end must start with if.

[l5s5q7]
hint: Look at the "Keep it simple" example: what happened to prices afterwards?
A: The original list stays the same; prices was unchanged after with_tax was made.
B: Any list can be the source, like marks or names in the lesson.
C: A comprehension builds a list; it only shows something if you print the result.
D: Right: a list comprehension always makes a brand-new list.

[l5s5q8]
hint: Apply the filter first, then multiply only the numbers that are kept.
A: The filter n != 2 removes 2, so 20 is not in the result.
B: n != 2 means "not equal to 2", so 2 is the one number left out.
C: Right: 2 is skipped, and 1 and 3 become 10 and 30.
D: The value at the front is n * 10, so the kept numbers are multiplied.

[l6s1q1]
hint: Recall what upper() does to every letter, not just the first one.
A: Right: upper() turns every letter into a capital.
B: upper() capitalises every letter, not only the first.
C: upper() returns a capital version, and that new string is what gets printed.
D: The brackets call the method; they are not part of the text that is printed.

[l6s1q2]
hint: Remove the spaces at both ends first, then count what is left.
A: 6 is the length before strip(); the spaces at both ends are removed first.
B: Right: strip() leaves "hi", which has 2 characters.
C: strip() removes the spaces at both ends, not just one side.
D: len() gives a number, the count of characters, not the text itself.

[l6s1q3]
hint: Think about what split("-") does with each dash it finds.
A: Right: split cuts at every dash and drops the dashes, giving three strings.
B: split cuts at each dash, so the string is broken into separate pieces.
C: split() gives back a list, so the result is shown in square brackets.
D: The dashes are where the string is cut, so they are thrown away, not kept.

[l6s1q4]
hint: Think about what the key point said string methods give back.
A: upper() needs no argument; the empty brackets are correct.
B: print can show capital letters; the problem is that name itself never changed.
C: Single and double quotes work the same for every string method.
D: Right: upper() made a new string, but it wasn't stored, so name is still "ravi".

[l6s1q5]
hint: You start with a list and need one string, which is the opposite of splitting.
A: split() goes the other way: it breaks a string into a list.
B: replace() swaps text inside a string; it doesn't combine a list.
C: Right: "-".join([...]) glues the items together with "-" between them.
D: append() is a list method that adds an item; strings don't have it.

[l6s1q6]
hint: Spell out banana letter by letter and count only one particular letter.
A: banana has an a after b, after the first n and after the second n, so there's one more.
B: Right: b-a-n-a-n-a has three a's.
C: count() counts every match in the word, not just the first.
D: 6 is the length of the whole word; count("a") counts only the a's.

[l6s2q1]
hint: Look at the third decimal digit to decide whether to round up or down.
A: The third decimal is 1, so the number rounds down, not up.
B: :.2f shows only 2 decimal places, so the extra digits are not shown.
C: That is 1 decimal place, which is what :.1f would give.
D: Right: :.2f rounds to 2 decimal places, giving 3.14.

[l6s2q2]
hint: Check what is, or isn't, written just before the opening quote.
A: Right: with no f before the quotes, the braces are printed as they are.
B: Hi Ali would need an f before the quotes: f"Hi {n}".
C: Without an f, nothing inside the braces is replaced, and the braces stay too.
D: Braces in a normal string are allowed, so nothing goes wrong; they are just printed.

[l6s2q3]
hint: Recall the "Lining up columns" page and what the < and > symbols did.
A: > inside the braces is about alignment, not comparing values.
B: Right: > means right-align and 5 is the width.
C: Decimal places use a dot and f, like :.5f.
D: Repeating uses * with a string; this format sets a width instead.

[l6s2q4]
hint: Replace each pair of braces with what is worked out inside it.
A: a and b are numbers, so {a + b} is added, not joined into 45.
B: The f before the quotes makes Python fill in each { }.
C: Right: {a} and {b} become 4 and 5, and {a + b} is worked out as 9.
D: {a} is replaced with the value of a, not the letter a.

[l6s2q5]
hint: Recall the exact format used on the Decimal places page for two places.
A: Right: :.2f shows exactly 2 decimal places, so 12.35.
B: Without the colon, .2 is not a valid format and causes an error.
C: :2 only sets a width of 2, so the number is shown unchanged.
D: Without the dot, :2f does not mean 2 decimal places; it shows 12.345600.

[l6s3q1]
hint: Find the key in the square brackets, then look at what is stored under it.
A: 1 is stored under the key "a"; d["b"] looks up a different key.
B: d["b"] gives the value stored under "b", not the key itself.
C: "b" is a key in the dictionary, so the lookup works.
D: Right: the value stored under "b" is 2.

[l6s3q2]
hint: Remember the key point about how many values one key can hold.
A: Keys are unique, so a dictionary can't hold two "x" keys.
B: Assigning d["x"] = 7 changes the stored value, so it is no longer 5.
C: Right: assigning to an existing key replaces its old value.
D: print(d) shows the whole dictionary, not just one value.

[l6s3q3]
hint: "milk" isn't a key, so check what the second value given to get() is for.
A: None is what get() returns with no fallback; here a fallback is given.
B: Right: "milk" is missing, so get() returns the fallback 0.
C: 10 belongs to "tea"; get() looks up "milk", which isn't there.
D: get() doesn't crash on a missing key; that's its whole purpose.

[l6s3q4]
hint: Think about how student["name"] found Asha without using a position number.
A: Right: a key is the label you use to look up a value.
B: Position numbers are how lists work; dictionaries use labels.
C: A key isn't about when a value was added; every value has its own key.
D: Keys don't lock anything; they just label values.

[l6s3q5]
hint: Compare the key in the dictionary with the key in the lookup, letter by letter.
A: Dictionaries can hold any values, such as text like "Asha".
B: Right: "Pen" and "pen" are different keys, and only "pen" exists.
C: Lookups use square brackets, which this code already uses.
D: print can show dictionary values; the lookup fails before print gets anything.

[l6s3q6]
hint: Count the key-value pairs after the new key has been added.
A: 1 was the count before d["b"] = 2 added a new pair.
B: len() counts pairs, not the values added together.
C: Right: there are now two pairs, and len() counts pairs.
D: len(d) gives a number, not the dictionary itself.

[l6s3q7]
hint: Recall the safe way to look up a key that may not be there yet.
A: Dictionaries have no count() method; count() belongs to strings and lists.
B: append() is a list method; dictionaries don't have it.
C: values() gives all the values and takes no word, so it can't look one up.
D: Right: get(word, 0) gives the current count, or 0 for a new word.

[l6s3q8]
hint: Remember what happened when stock["pens"] was assigned a second time.
A: Right: keys are unique, so assigning to an existing key replaces its value.
B: Values can be of different types, like "Asha" and 21 in the student example.
C: Keys are labels you choose; dictionaries are not numbered from 0 like lists.
D: Dictionaries can be changed: you can add, change and pop keys.

[l6s4q1]
hint: Recall what a plain for loop over a dictionary gives you each time.
A: Right: looping over a dictionary gives its keys.
B: Those are the values; you'd need .values() to get them.
C: Getting keys and values together needs .items() and two loop variables.
D: Nothing adds a colon here; the loop variable is just each key.

[l6s4q2]
hint: items() gives pairs, and print with two values puts a space between them.
A: Printing only k would give x and y, but v is printed too.
B: Printing only v would give 3 and 4, but k is printed too.
C: The two variables take each pair apart, so no dictionary braces are printed.
D: Right: each line shows the key, a space, then its value.

[l6s4q3]
hint: Think about what values() gives here and what sum() does with it.
A: Right: values() gives 2 and 5, and sum() adds them.
B: values() gives the numbers, not the keys, and sum() adds numbers.
C: sum() adds every value, not just the first.
D: sum() works here because the values are numbers, so there is no error.

[l6s4q4]
hint: Look at the loop on the keys(), values() and items() page with two loop variables.
A: Only the values is what .values() gives.
B: Right: items() gives each key with its value, so you can use two loop variables.
C: Only the keys is what a plain loop, or .keys(), gives.
D: The number of pairs comes from len(d).

[l6s4q5]
hint: Recall the common mistake tip about using two loop variables.
A: keys() gives only keys, so there is still nothing for mark.
B: The name of the loop variable isn't the problem; there's still only one value per repeat.
C: Right: items() gives pairs, so name and mark each get a value.
D: Moving the loop into a function doesn't change what the loop gives.

[l6s4q6]
hint: Think about which method gives what is stored, not the labels.
A: keys() gives the labels, not the values.
B: Right: values() gives just the values.
C: items() gives key-value pairs, not just the values.
D: get() looks up one key and needs that key; it doesn't loop over everything.
'''


def parse(text=FEEDBACK_TEXT):
    """Returns {question id: {"hint": str, "optionFeedback": [4 strings]}}."""
    out, cur = {}, None
    for raw in text.strip().splitlines():
        line = raw.strip()
        if not line:
            continue
        if line.startswith("[") and line.endswith("]"):
            cur = line[1:-1]
            assert cur not in out, f"duplicate feedback entry {cur}"
            out[cur] = {"hint": None, "optionFeedback": [None] * 4}
            continue
        tag, _, value = line.partition(": ")
        assert cur is not None and value, f"bad feedback line: {line!r}"
        if tag == "hint":
            out[cur]["hint"] = value
        elif tag in ("A", "B", "C", "D"):
            out[cur]["optionFeedback"]["ABCD".index(tag)] = value
        else:
            raise AssertionError(f"{cur}: unknown tag {tag!r}")
    for qid, d in out.items():
        assert d["hint"] and all(d["optionFeedback"]), f"{qid}: incomplete feedback entry"
    return out


FEEDBACK = parse()
