<!-- Copy of app/src/main/assets/legal/privacy_policy.txt for publishing on the web (e.g. GitHub Pages or the Play Console privacy policy URL). Keep both in sync and replace every [ ... to be filled in ...] placeholder before release. -->

# PyPath Privacy Policy

Version 2026-11-13.2

This policy explains what information the PyPath Android app (package com.pypath.app) uses, why, where it is kept and what choices you have. It describes how the app actually works today.

PyPath is provided by [OPERATOR NAME - to be filled in by the app owner] ("we", "us"). Contact details are in section 13.

## 1. Information we collect

### When you use PyPath without an account (guest)
Nothing is sent to PyPath's servers. Your first-lesson progress, quiz answers and settings are stored only on your phone. Guest progress is kept separate from every account: it is not copied or uploaded to an account unless the signed-in account holder chooses "Add guest progress to this account" in Privacy & account.

### When you create an account or log in
- Your Google account details that Google shares with apps on sign-in: your name, email address, Google account ID and, if you have one, your profile picture link.
- A PyPath account ID created by our sign-in provider (Supabase).
- Your learning data, once you have agreed to this policy: course progress, quiz results, completed lessons and projects, XP, streaks, badges, question history and review state, daily activity, saved programs from the code editor, and synced preferences (sound effects, certificate name, daily goal, learning time goal, whether an introductory offer was shown).
- Your Privacy Policy choices: the policy version, the time, the status (agreed or withdrawn) and your age confirmation (that you are 18 or older, or that you are 12 to 17 and a parent or guardian has read this policy and agrees to you using a PyPath account). We do not ask for your date of birth or for your parent's or guardian's details.
- Security information needed to protect sign-in, described in section 6.

### What we do not collect
PyPath has no advertising, no analytics or tracking tools, and does not use your location, contacts, camera or microphone. The Python code you run is executed on your phone and is not sent to us.

## 2. Google Sign-In
When you tap "Sign in with Google" or "Login with Google", Google Play services on your phone shows Google's account picker. Google then gives PyPath a short-lived sign-in token for the account you chose. PyPath uses it once to sign you in and does not store it. Google's own processing is covered by Google's Privacy Policy (https://policies.google.com/privacy).

"Sign in with Google" creates your PyPath account (or opens it if it already exists). "Login with Google" only opens an existing account and never creates a new one.

## 3. Supabase authentication and cloud storage
PyPath uses Supabase (supabase.com) to run accounts and to store your cloud backup. Supabase acts as our service provider. Your account and your synced learning data are stored in our Supabase project, and database rules make sure each signed-in user can read and change only their own data. Supabase's privacy information is at https://supabase.com/privacy. Data may be stored or processed outside India, depending on the hosting region of our Supabase project ([HOSTING REGION - to be filled in by the app owner]).

## 4. Synced progress, activity, question history, programs and settings
When you are signed in and have agreed to this policy, PyPath backs up the learning data listed in section 1 to your account, so you can continue on another phone. Changes made on a phone are merged with your cloud copy. Some data stays only on your phone and is never synced, for example study reminder settings, Premium study and Code Battle records, and the Premium test switch. When you sign out, your account's data is no longer shown on that phone (it stays saved for your next login), and the phone shows its separate guest progress instead. One account's data is never uploaded to another account.

## 5. Local storage on your phone
PyPath stores your progress, activity, settings, saved programs and account details in the app's private storage on your phone. Your sign-in session is encrypted with a key kept in the Android Keystore. Android may include the app's data in your phone's own backup (for example Google device backup) if you have turned that on; you control this in your phone's settings. Uninstalling PyPath or clearing its storage deletes the data on that phone, but not your cloud backup.

## 6. Data transmission and security
All communication between PyPath and Google or Supabase uses encrypted HTTPS connections. Only the app's public project key is built into the app; no administrator keys are. To stop abuse of sign-in, our server checks the Google sign-in token and keeps short-lived counters of attempts, stored only as one-way hashes of your network address and account, and a short-lived note (also hashed) that a new account is being created. The code runner inside the app has no internet access. No system is perfectly secure, but we use reasonable measures to protect your data.

## 7. Data retention
- Account and synced learning data: kept while your account exists.
- Privacy Policy agreement and withdrawal records: kept while your account exists, as proof of your choices.
- Sign-in attempt counters: deleted after about one day; account-creation notes: after about ten minutes.
- Sign-in logs kept by Supabase: according to Supabase's own retention.
- Data on your phone: until you delete it, clear the app's storage or uninstall the app.

## 8. Account deletion
You can delete your PyPath account inside the app: open your account, choose "Privacy & account" and then "Delete my account". If you have not agreed to this policy (for example after withdrawing your consent), you can also choose "Delete my account" on the Privacy Policy screen shown after you log in. You must confirm by typing DELETE. Our server then deletes your PyPath account, your cloud backup and your Privacy Policy records. The app tells you only when the server has confirmed the deletion; if it fails, the app says so and you can try again or contact us (section 13). The account's copy on that phone is removed too; guest progress on the phone is not affected. Data on other phones where you were signed in stays on those phones until you sign out there, clear the app's storage or uninstall it. Signing out alone does not delete your account. Sign-in logs kept by Supabase follow Supabase's own retention.

## 9. Third-party services
- Google (Google Sign-In through Google Play services), for signing in.
- Supabase, for accounts and cloud storage.
We do not sell your personal data and do not share it with advertisers. We may disclose information if the law requires it.

## 10. Offline functionality
Lessons, quizzes and the Python code runner work without internet. Changes you make offline are saved on your phone and backed up the next time you are online and signed in. Creating an account, logging in and agreeing to this policy need an internet connection.

## 11. Children and teenagers
PyPath is a Python-learning app designed for learners aged 12 and up. Teenagers aged 12 to 17 are welcome to use PyPath, with or without an account.

You can always use PyPath as a guest without an account. Guest progress stays only on your phone and nothing is sent to our servers.

Under India's Digital Personal Data Protection Act, 2023, anyone under 18 is a child, and processing a child's personal data needs the verifiable consent of a parent or lawful guardian. If you are 12 to 17, please ask a parent or guardian to read this policy with you before you create an account. When you agree to this policy, you confirm that you are 18 or older, or that you are 12 to 17 and a parent or guardian has read this policy and agrees to you using a PyPath account. This is your own confirmation: PyPath does not currently check your age or verify a parent's or guardian's identity or consent.

A parent or guardian can ask us to delete a child's account and data at any time (section 13), or delete it inside the app (section 8). PyPath is not designed for children under 12: if you are under 12, please do not create an account, and choose "I'm under 12" on the policy screen to delete an account straight away.

We do not use anyone's data, including children's data, for tracking, behavioural monitoring or targeted advertising, and PyPath shows no ads. If you live outside India, your local law may set a different age.

## 12. Your rights and changes to this policy
Depending on the law that applies to you, including India's Digital Personal Data Protection Act, 2023, you may have the right to get information about and a copy of your personal data, to have it corrected, completed or erased, to withdraw your consent, to nominate another person to exercise your rights, and to raise a grievance with us and, if it is not resolved, with the Data Protection Board of India. To use these rights, contact us (section 13).

You can withdraw your consent inside the app at any time (account, "Privacy & account", "Withdraw consent"). Your withdrawal is recorded, cloud backup stops straight away and you are signed out on that phone. Without consent you can use PyPath only as a guest. Your account and cloud backup stay stored until you delete your account (section 8), so you can choose to agree again later; if you want them erased, delete your account. Withdrawing consent does not affect processing done before it.

If we change this policy, we will update the version shown at the top. If a change affects how your information is used, PyPath will show the new policy and ask you to agree again before you continue with your account.

## 13. Contact
[OPERATOR NAME - to be filled in by the app owner]
Email: [CONTACT EMAIL - to be filled in by the app owner]
Postal address: [POSTAL ADDRESS - to be filled in by the app owner]
Grievance contact: [GRIEVANCE OFFICER NAME AND EMAIL - to be filled in by the app owner]
