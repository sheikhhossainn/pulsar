# Pulsar for Android

Pulsar 0.1 is a Bangla-first, offline voice assistant for Android 10 and later. The phone build includes a Bengali streaming recognizer. Microphone audio goes directly into the on-device model and is discarded after recognition. The app declares no internet permission.

## Test on Windows

Double-click **Run Pulsar on PC.cmd** in this folder. It starts a dedicated Android 15 virtual phone, builds the current source, installs the x86_64 APK, and opens Pulsar. Leave the emulator window open and double-click the launcher again after editing the app. Reminders and favourites persist across installs and emulator restarts.

For the last built APK, run `& '.\Run Pulsar on PC.cmd' -UseExistingApk` from PowerShell. The **?** menu lets you try Bangla examples with the mouse. To try live speech, enable host microphone access under the emulator's **… → Microphone** controls and grant Pulsar microphone access when prompted. Android call screens can be tested here. Carrier calls and low-cost phone performance require a physical handset.

The launcher uses this workspace's local SDK and Gradle cache under `.buildenv`. On a fresh Windows checkout, supply Android SDK Platform 35, Build Tools 35.0.0 and platform-tools under `.buildenv/sdk`, populate Gradle dependencies once using the build steps below, and enable Windows Hypervisor Platform. The first emulator setup downloads about 1.24 GB from Google. Its image, virtual storage and temporary files stay on the project drive. Emulator logs are under `.buildenv/pc-logs`.

## Try the pitch flow

1. Install the **arm64-v8a debug APK** on an Android phone. Open Pulsar and tap the large microphone. Allow microphone use while the app is open.
2. Say **আধা ঘণ্টা পরে পানি খেতে মনে করিও**. The confirmation screen shows the task and resolved time. Say **হ্যাঁ** when prompted or press **নিশ্চিত করে রাখুন**. Use a test time a few minutes ahead to demonstrate an alert.
3. Grant notifications and precise-alarm access if Android requests them. A blocked reminder stays visible with an access button. The alert supports **হয়ে গেছে** and **১০ মিনিট পরে**.
4. Open **পরিচিতি**, choose a phone contact or enter a Bangladesh mobile number, and save it as **আম্মু**. Say **আম্মুকে কল করো** and review the number. Use a consenting demo recipient. With call permission Pulsar requests the call. Denying it opens the dialer.
5. Say **ক্যালকুলেটর খোলো** to launch a matching installed app. If the phone uses a different label, Pulsar offers a searchable app list.
6. For a quiet venue, open the **?** button and select an example. The editable command uses the same parser and confirmation screens. This demonstrates task handling without testing the microphone.

## Build

Use JDK 17 or 18, Android SDK Platform 35 and Build Tools 35.0.0. Point Android Studio or ANDROID_HOME at the SDK. The Gradle wrapper is included.

For a clean checkout, restore the pinned model and runtime using Python 3. The download script verifies SHA-256 digests before use.

```powershell
python tools/fetch_speech.py
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

Phone output: `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`. An x86_64 variant supports compatible emulators. Android Studio can import the repository root. The UI test writes a rendered home screen to `app/build/reports/pitch/home.png`.

## Current scope

- Native Kotlin and Jetpack Compose. On-device streaming recognition with sherpa-onnx and the upstream Bengali Zipformer baseline.
- Push-to-talk, foreground voice confirmation of recognized reminder and call commands, optional local Bengali TTS, typed commands and equivalent touch controls.
- One-time and daily reminders with editing, cancellation, notification actions and rescheduling after reboot/unlock. The first build uses Android SQLite transactions. Favourite contacts also stay in local storage.
- Manually approved favourites and Android's single-contact picker. Apps launch from visible Pulsar. The microphone stops when the activity pauses.
- The command parser supports the examples above, explicit Bangla day-parts and clock times, আজ/কাল/পরশু, and simple relative minutes/hours. Ambiguous times prompt for manual selection. Household speech can differ from these supported patterns.
- The three-cohort accent adaptation, continuous cross-app phone control, recorded spoken labels, full recurring schedule grammar, and physical-device performance and reliability trials remain implementation milestones. The full target specification is in PULSAR_MVP_SRS.md.

Local UI and parser tests exercise the first user flows. Actual microphone behavior, call routing, notification delivery under vendor battery settings and performance on 3 GB phones require physical-device testing. Use this debug build for a supervised demo.

