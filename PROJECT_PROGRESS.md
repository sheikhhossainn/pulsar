# Pulsar project progress

**5 November 2026 · Android alpha 0.1**

Pulsar has progressed from a proposal to an installable Android app. The current build lets someone speak a Bangla command, review a reminder or call before confirming it, and open an installed app. The same app runs on a Windows PC through a dedicated Android emulator. The most thoroughly checked demo is the reminder flow.

## What we built

| Area | Available in the alpha |
|---|---|
| Bangla interface | A large microphone button, touch shortcuts for the three main tasks, an upcoming-reminders list, and a help menu with commands users can try. [Home screen preview](app/build/reports/pitch/home.png) |
| Offline speech | The app bundles a Bengali Zipformer model and runs it locally with sherpa-onnx. A Kotlin parser interprets supported commands. Users can also type a command. Spoken readback uses an installed offline Bengali voice when available. |
| Reminders | Users can create one-time or daily reminders, review the date and time, edit or cancel them, mark a reminder done, or snooze it for ten minutes. The app stores reminders in SQLite and uses Android alarms for delivery. It shows when alarm or notification access needs attention. |
| Calls | Users can add a Bangladesh mobile number manually or select a contact from their phone and save a familiar name such as **আম্মু**. Pulsar shows the recipient and number for confirmation before requesting a call or opening the dialer. |
| App launching | A recognized app name opens the matching installed application. A searchable list handles missing or ambiguous matches. |
| PC testing | A Windows launcher rebuilds the source, installs the x86_64 APK in the **Pulsar_PC** Android 15 emulator, and opens the app. Reinstallation preserves the virtual phone's reminders and favourites. |

## Product and engineering decisions

The first release focuses on tasks shared by older adults, people with limited digital confidence, and busy users in Bangladesh. Each group uses the same interface. Document scanning and family monitoring were excluded from the MVP scope.

The Android app uses Kotlin and Jetpack Compose, with Android SQLite for this first build. Speech recognition runs during user-initiated interaction, and microphone capture stops when the activity pauses. The Android platform handles scheduled reminders independently of the speech model. The SRS documents the larger target, including the proposed accent-training protocol, acceptance criteria, delivery schedule, budget, and pitch walkthrough. [Original proposal](SUGGESTIONS.md) · [Editable MVP specification](PULSAR_MVP_SRS.md) · [Printable specification](PULSAR_MVP_SRS.html)

## What has been verified

| Check | Recorded result |
|---|---|
| Automated tests | **7 passed** across four command-parser tests and three Android UI or storage tests. Coverage includes ambiguous times, confirmation before saving, and duplicate daily-reminder delivery. |
| Android packages | Built separate debug APKs for ARM64 phones and x86_64 emulators. The ARM64 package is approximately **149 MB**. Its signature and four bundled model files were verified. |
| Speech model | The matching desktop runtime decoded a Bengali sample recording using the packaged model files. On-device microphone accuracy and speed still require handset measurements. |
| PC workflow | Started the emulator, opened Pulsar, selected a Bangla example with the mouse, reached reminder confirmation, and returned home. A second launcher run successfully rebuilt, reinstalled, and reopened the app. |

## How to try it

**On this PC**, double-click [Run Pulsar on PC.cmd](Run%20Pulsar%20on%20PC.cmd). To walk through a reminder with the mouse, open **?**, select **আধা ঘণ্টা পরে পানি খেতে মনে করিও**, and press **এগিয়ে যান**. Pulsar shows the task and resolved time before saving. Run the same launcher after changing the code.

**On a phone**, install the [ARM64 debug APK](app/build/outputs/apk/debug/app-arm64-v8a-debug.apk) on a compatible Android 10+ device. Tap the microphone and speak the same command. The README covers microphone and alarm permissions, the call demo, and building from a fresh checkout. [Run and build instructions](README.md)

## What remains

1. **Physical-device validation.** Measure startup time, response delay, memory use, and sustained battery drain on a 3 GB Android phone. Check microphone input, actual call routing, and alarm delivery while locked, after reboot, and under battery-saving settings.
2. **Accent adaptation.** The alpha uses the upstream Bengali model. Recruitment, consented recording, training, and separate evaluation for standard Bangla, Rajshahi-area speech, and Dhaka-area conversational speech remain pending.
3. **Voice and scheduling coverage.** Extend the reminder grammar, add recorded spoken labels for devices without Bengali text-to-speech, and evaluate hands-free operation across supported apps and screens.
4. **User pilot.** Observe people from each of the three audiences completing the same tasks. Record where they ask for help, correct a recognition error, or abandon a task before making broader usability claims.
