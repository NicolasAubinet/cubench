# Privacy Policy

Cubench (formerly Nano Timer) is a free and open source Android app developed by Nicolas Aubinet. The source code is available on GitHub under the GNU GPL v3.0 license. The app is also available on Google Play.

**In short: everything you record stays on your phone. If you do not use a smart cube, nothing about you is sent. If you do, three things can be sent. Which algorithm you use for each case, under a random ID, so Cubench can show which algorithms cubers really use (on by default, and you can turn it off). If you use the optional coach (by subscription, or free while it is in beta), a summary of your solve statistics once a week, so a practice plan can be written for you. If you report a wrong reconstruction, that one solve. Otherwise nothing about you is sent.**

# Data on your phone

Your solves, times, smart cube recordings and settings are stored on your device only. They are erased when you clear the app's data or uninstall it. The app has no accounts, and I never see this data.

**Bluetooth and location.** The app uses Bluetooth only to connect to a smart cube. On Android 11 and older, Android requires the location permission for any Bluetooth scan; the app never reads or stores your location.

# Algorithm usage sharing (smart cube users)

If you use a smart cube, the app shares which algorithm you turn for each case, so Cubench can show every user which algorithms cubers really use. It is on by default, and you can turn it off in Settings, Smart cube, "Share which algorithms I use". **Until a smart cube has been connected, nothing is sent.**

**What is sent.** For each OLL, PLL and F2L pair case, the one algorithm you turn for it, read from your recent smart cube solves: only cases solved in one look, and only an algorithm you turned at least twice in that case's five latest solves. The first send carries every case; later sends carry only the cases whose algorithm changed. Nothing else about your solves is sent: no times, scrambles, dates or solve type names. With it goes a random sharing ID, created by the app for this alone. It is not your Coach ID, it is shown nowhere, and it is linked to nothing else: no name, email address or Google account.

**When.** At most once a day, in the background, when the phone is online and something has changed.

**Checking the app is genuine.** So that votes cannot be faked in bulk, each send carries a Google Play Integrity token: the app asks Google Play to vouch that it is the real Cubench from Google Play, running on a genuine Android device, and the server has Google check that token. Google Play handles this under Google's own privacy policy. The token says nothing about your solves, and the server does not keep it.

**Who processes it.** The same server as the coach (Google Cloud in Belgium, with its database hosted by Neon in Germany). It is never sent to Anthropic.

**What is kept, and for how long.** Under your sharing ID, the server keeps the algorithm you use for each case and the date the ID was first seen, for as long as sharing stays on. It also keeps a record of each vote it receives for 90 days, so that a batch of fake votes can be removed and the votes it replaced put back. Once a day, it combines everyone's votes into a share per algorithm for each case. A case is published only once 30 users have voted on it, and an algorithm that is not in the app's list only once 5 users turn it. These combined figures name nobody, and they are what the app shows. The app downloads them about once a week, for any smart cube user whether sharing is on or off; that download sends nothing about you, not even the sharing ID. Your IP address is used only to limit how often sends can be made: the server holds it in memory and never stores it, and the hosting provider's request logs keep it for a day. The sharing ID is never written to the server's logs.

**Turning it off.** Turning the switch off stops the sending, and the app asks the server to delete everything kept under your sharing ID (it keeps asking until the server confirms), then forgets the ID. Turning it back on starts with a new ID. If you uninstall the app without turning sharing off, what was sent stays on the server, under an ID that nobody can link to you.

**Why (legal basis).** Showing every user which algorithms are actually used, from data that identifies nobody and that you can stop sending at any time (legitimate interest, article 6(1)(f) of the GDPR). Turning the switch off is your right to object.

# Reconstruction reports

If a smart cube solve was read wrong, you can tap "Reconstruction wrong?" on it and then Send report. Nothing is sent until you tap Send.

**What is sent.** That one solve: its time, scramble, date and solve type name, its smart cube recording (the moves, their timing and the cube's orientation during the solve), the breakdown as the app showed it, and the name of the cube model. With it go the comment you typed, your email address if you gave one (it is optional, and only used to reply to you), the app version, your phone's model and Android version, and your Coach ID.

**Who processes it, and for how long.** The report goes to the same server as the coach (Google Cloud in Belgium), which emails it to my inbox at nanotimercube@gmail.com (Gmail, Google) and does not keep it. The server keeps only a note of the report: its id, your Coach ID and when it arrived, for two days (database by Neon in Germany), to limit how many reports can be sent in a day. If the phone is offline, the app keeps the report and sends it when it can; one not sent after 14 days is deleted. Your IP address is handled as for the coach, below. Reports are never sent to Anthropic. I use them only to fix how solves are read, including as test cases for the app.

**Why (legal basis).** You choose to send it (your consent, article 6(1)(a) of the GDPR).

# The coach (optional, paid, free while in beta)

The coach reads your solve statistics and writes you a practice plan every week. It runs on a server, which it shares with reconstruction reports and algorithm usage sharing. **Nothing about you is sent unless you subscribe, start the free trial, join the beta, or enter an access code.** When your subscription, the beta or your access ends, sending stops.

**Beta status.** While the beta runs, the app asks the server about once a day whether it still has places, so it can offer it to you. If you have not joined, that request carries nothing about you: no Coach ID, no statistics. Like any request over the internet, it shows the server your IP address, which is handled as described below. Once the beta has ended, the app stops asking.

**When.** Once a week, in the background, when a new plan is due. Also when you open the Plan tab, at most once a day, to refresh your practice sets, and when you send a plan rating.

**What is sent.** A summary of one solve type's statistics: average and best times per step and per last layer case, move and pause counts, which algorithms you know, your drill results, and what your last plan asked of you and what you have done since. It does not contain your individual solves, scrambles, dates, or any text you typed, such as the names of your solve types. It carries no name, email address or Google account. With it go random identifiers created by the app (your Coach ID, shown in Settings, Coach), your Google Play purchase token so the subscription can be checked, and the app's language so the plan is written in it.

**Plan ratings.** During the beta, and whenever you turn plan feedback on, you can rate a plan: its stars, whether each focus suits you and why not, whether you worked on last week's plan, and any text you choose to type. Beta members rate each plan before the next one is written. With a rating go a few measures of how the plan was used: which focuses you looked at, how many drill repetitions you did for each, and how long after the plan arrived you rated it. Ratings go to the same server, are never sent to Anthropic, and are read only by me, to improve the coach.

**Plan reports.** You can report a plan from the Plan tab's menu, for example if it says something offensive or gives harmful advice. The report (the reason you picked and any text you typed), the plan it is about, the app version and your Coach ID are emailed to me (Gmail, Google) to review and are not kept on the server, which keeps only a note of the report (its id, which install sent it and when it arrived) for two days, to limit how many can be sent. Reports are never sent to Anthropic. Sending one is your choice (your consent, article 6(1)(a) of the GDPR).

**Who processes it.** The server runs on Google Cloud in Belgium, with its database hosted by Neon in Germany. To write the plan, the summary is sent to Anthropic in the United States, whose AI model, Claude, writes it. Anthropic processes it only on my behalf, does not use it to train its models, and deletes it within 30 days. The transfer to the United States is covered by the European Commission's Standard Contractual Clauses, which are part of Anthropic's data processing terms.

**What is kept, and for how long.** The server keeps each summary and the plan written from it for 30 days, to limit how many plans are written per week, to avoid writing the same plan twice, and so I can check the plans to improve the coach. It also keeps a record of each request made to the AI model, with your Coach ID, for 14 days, to keep its cost in check. If you entered an access code, the server keeps the code with your Coach ID so it knows your access, and deletes both 30 days after your access ends. If the code can still be entered by then, the note that your Coach ID used it is kept until I withdraw the code, so the same code cannot be used twice. Plan ratings are kept for 30 days. If you join the beta, the server keeps your Coach ID and the date you joined until the beta's thank-you offer has been given, then deletes them. Your IP address is used to limit how often requests can be made and is deleted within a day; the hosting provider's request logs keep it for a day. The server also writes a short record of each request, kept 14 days to find the cause of a problem: which part of the coach was asked, whether it answered, how long it took and what the plan cost, with a shortened one-way code computed from your Coach ID in place of the ID itself, and never your IP address. To tell whether a case is slow for you or slow for everyone, the server combines all users' latest summaries into one average per case; only those combined averages are kept, so no stored figure can be traced back to you. Before anything is deleted, the server also counts each day's totals: how many plans were written and to how many users, what they cost, how many ratings were sent and their stars, and how often each kind of focus was answered "Yes" or "Not quite" and why. These totals name nobody and are kept.

**Why (legal basis).** Sending the summary, and during the beta the ratings, is needed to provide the coach you subscribed to or the beta you joined (article 6(1)(b) of the GDPR). A rating you send outside the beta is your choice (your consent, article 6(1)(a)).

**Notifications.** The "plan ready" and reminder notifications are created on your phone; nothing is sent to show them. The reminder can be turned off in the settings.

**Payments.** Subscriptions are handled by Google Play. I never see your payment details.

# Your rights

You can ask for a copy of the data the server holds about you, or for its deletion, by emailing me your Coach ID (Settings, Coach). Coach summaries, plans and plan ratings are deleted after 30 days in any case. For algorithm usage, the sharing ID is shown nowhere, so I cannot tell which votes are yours; turning sharing off deletes them. You can also file a complaint with your data protection authority.

# Contact

If you have any questions about this privacy policy, you can contact me by email: contact@cubench.app

Last updated: November 14, 2026
