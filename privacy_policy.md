# Privacy Policy

Cubench (formerly Nano Timer) is a free and open source Android app developed by Nicolas Aubinet. The source code is available on GitHub under the GNU GPL v3.0 license. The app is also available on Google Play.

**In short: everything you record stays on your phone, with two exceptions. If you subscribe to the optional paid coach, a summary of your solve statistics is sent once a week so a practice plan can be written for you. If you report a wrong reconstruction, that one solve is sent. Otherwise nothing is sent.**

# Data on your phone

Your solves, times, smart cube recordings and settings are stored on your device only. They are erased when you clear the app's data or uninstall it. The app has no accounts, and I never see this data.

**Bluetooth and location.** The app uses Bluetooth only to connect to a smart cube. On Android 11 and older, Android requires the location permission for any Bluetooth scan; the app never reads or stores your location.

# Reconstruction reports

If a smart cube solve was read wrong, you can tap "Reconstruction wrong?" on it and then Send report. Nothing is sent until you tap Send.

**What is sent.** That one solve: its time, scramble, date and solve type name, its smart cube recording (the moves, their timing and the cube's orientation during the solve), the breakdown as the app showed it, and the name of the cube model. With it go the comment you typed, your email address if you gave one (it is optional, and only used to reply to you), the app version, your phone's model and Android version, and your Coach ID.

**Who processes it, and for how long.** The report goes to the same server as the coach (Google Cloud in Belgium, database by Neon in Germany), which keeps it for 12 months and emails it to my inbox at nanotimercube@gmail.com (Gmail, Google). If the phone is offline, the app keeps the report and sends it when it can; one not sent after 14 days is deleted. Your IP address is handled as for the coach, below. Reports are never sent to Anthropic. I use them only to fix how solves are read, including as test cases for the app.

**Why (legal basis).** You choose to send it (your consent, article 6(1)(a) of the GDPR).

# The coach (optional, paid)

The coach reads your solve statistics and writes you a practice plan every week. It runs on a server, which it shares with reconstruction reports; apart from those, it is the one part of the app that sends data over the internet. **Nothing is sent unless you subscribe, start the free trial, or enter an access code.** When your subscription or access ends, sending stops.

**When.** Once a week, in the background, when a new plan is due. Also when you open the Plan tab, to refresh your practice sets.

**What is sent.** A summary of one solve type's statistics: average and best times per step and per last layer case, move and pause counts, which algorithms you know, your drill results, and what your last plan asked of you and what you have done since. It does not contain your individual solves, scrambles, dates, or any text you typed, such as the names of your solve types. It carries no name, email address or Google account. With it go random identifiers created by the app (your Coach ID, shown in Settings, Coach), your Google Play purchase token so the subscription can be checked, and the app's language so the plan is written in it.

**Who processes it.** The server runs on Google Cloud in Belgium, with its database hosted by Neon in Germany. To write the plan, the summary is sent to Anthropic in the United States, whose AI model, Claude, writes it. Anthropic processes it only on my behalf, does not use it to train its models, and deletes it within 30 days. The transfer to the United States is covered by the European Commission's Standard Contractual Clauses, which are part of Anthropic's data processing terms.

**What is kept, and for how long.** The server keeps each summary and the plan written from it for 14 days, to limit how many plans are written per week and to avoid writing the same plan twice. If you entered an access code, the server keeps the code with your Coach ID so it knows your access. Your IP address is used to limit how often requests can be made and is deleted within 12 hours; the hosting provider's request logs keep it for up to 30 days. To tell whether a case is slow for you or slow for everyone, the server combines all users' latest summaries into one average per case; only those combined averages are kept, so no stored figure can be traced back to you.

**Why (legal basis).** Sending the summary is needed to provide the coach you subscribed to (article 6(1)(b) of the GDPR).

**Notifications.** The "plan ready" and reminder notifications are created on your phone; nothing is sent to show them. The reminder can be turned off in the settings.

**Payments.** Subscriptions are handled by Google Play. I never see your payment details.

# Your rights

You can ask for a copy of the data the server holds about you, or for its deletion, by emailing me your Coach ID (Settings, Coach). Coach data is deleted after 14 days and reconstruction reports after 12 months in any case. You can also file a complaint with your data protection authority.

# Contact

If you have any questions about this privacy policy, you can contact me by email: nanotimercube@gmail.com

Last updated: October 30, 2026
