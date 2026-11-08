# Pulsar — Android MVP Software Requirements Specification

**Version:** 1.0 · **Date:** 5 November 2026 · **Status:** Proposed implementation baseline

**Audience:** Founders, Android and ML developers, pilot partners, incubators, and prospective funders.

**Document purpose:** Specify a buildable, demonstrable Android MVP for older adults, people with limited digital confidence, and busy users in Bangladesh. This is a product and engineering specification, not a claim that the software or its performance targets already exist.

**Planning basis:** `SUGGESTIONS.md` and the revised instruction to serve all three groups through common features. This document supersedes conflicting MVP scope and architecture recommendations in that earlier plan; it does not modify the original file. Existing assets described there have not been independently audited. The current workspace contains the proposal, not the referenced implementation assets.

---

## 1. Executive summary

Pulsar is an offline-first Bangla voice interface for everyday Android tasks. Users speak naturally to create reminders, find and call saved contacts, open applications, and perform a limited set of phone-navigation actions. The same workflows serve all three audiences; the interface does not divide people into an “elderly mode” and a “busy mode.”

**Pitch:** “Pulsar helps people use everyday Android features in Bangla—remember a task, call someone, open an app, and navigate supported screens—with less typing and less dependence on someone else.”

The MVP focuses on standard Bangla, Rajshahi-area conversational speech, and Dhaka-area conversational speech. These are recruitment and evaluation cohorts, not a claim that Bangladesh has three uniform accents. One shared recognition model is preferred; users should not have to identify their accent before speaking.

Pulsar provides voice-only completion of its supported workflows **after setup, device unlocking, and session activation**. Optional cross-app navigation uses explicit user commands and Android accessibility APIs on a tested compatibility list. It cannot promise control of every application, unlock the phone, approve system permissions by itself, or listen continuously while the screen is off.

The recommended stack is native Kotlin and Jetpack Compose, sherpa-onnx for local speech inference, a deterministic Kotlin command/dialogue engine, Room for local data, and Android platform APIs for alarms and calls. Python, PyTorch, icefall/k2, and Lhotse support speech experiments and adaptation outside the app. Rust is not an MVP prerequisite.

**Success means:** users in each audience can independently complete shared tasks, local actions work without internet, critical actions require appropriate confirmation, and results are measured on real low-cost Android phones and unseen speakers.

## 2. Goals, users, and common-feature selection

### 2.1 Product goals

| ID | Goal | Evidence |
|---|---|---|
| G-01 | Reduce interaction difficulty for all three audiences | Task completion and assistance rate reported separately by audience |
| G-02 | Make local everyday tasks available without internet | Airplane-mode task suite after model installation |
| G-03 | Understand the three selected speech cohorts | Speaker-disjoint evaluation with per-cohort results |
| G-04 | Prevent silent consequential mistakes | Confirmation, ambiguity, cancellation, and failure tests |
| G-05 | Demonstrate a credible Bangladesh-focused product | Reproducible live demo, pilot evidence, and honest limitations |

### 2.2 Intended users

| Audience | Shared problem | Design response |
|---|---|---|
| Older adults | Small targets, typing difficulty, remembering sequences | Large controls, spoken feedback, predictable recovery |
| People with limited digital confidence | Unfamiliar navigation, labels, or English terminology | Bangla instructions, familiar aliases, guided setup |
| Busy users | Repeated interaction interrupts another task | Short commands, immediate low-risk actions, session continuity |

These categories can overlap. Age alone shall not be treated as a proxy for disability, literacy, or technical ability. A family member may assist with setup, but is not automatically a monitoring user or account owner.

### 2.3 Shared feature matrix

| Feature | Older adults | Limited digital confidence | Busy users | MVP decision |
|---|---|---|---|---|
| Create and manage reminders | Remember routine tasks | Avoid complex scheduling UI | Capture tasks quickly | Required |
| Find and call saved contacts | Avoid contact-list searching | Use familiar names/relationships | Call without manual searching | Required |
| Open installed apps | Avoid hunting for icons | Use Bangla aliases | Fast navigation | Required |
| Home, Back, supported scrolling and numbered controls | Reduce precise touch requirements | Guided navigation | Hands-free interaction | Conditional MVP module |
| Help, repeat, correction, cancel, stop | Recover from confusion | Learn supported behavior | Recover without restarting | Required across all features |
| Scan documents and send PDFs | Recurring demand uncertain | Complex image selection remains | Useful for a subset | Excluded |
| Medicine-specific scheduling intelligence | Relevant to a subset | Not a universal task | Not a universal task | Excluded; medicine may be ordinary reminder content |
| Family monitoring and escalation | May help some families | Adds another user's data access | Not a shared need | Excluded |

“Common” means the same task and interaction are useful to each audience, not that every individual uses every feature every day. Frequency and value remain hypotheses to validate. A feature that fails this test during the pilot shall not be expanded merely to preserve the original list.

### 2.4 Preventing audience conflicts

- Use one consistent interface and command grammar.
- Allow optional speech-rate and text-size preferences, not persona-specific applications.
- Confirm calls and reminder mutations; do not confirm every Home or Back command.
- Ask a short clarification only when information is missing or ambiguous.
- Provide both voice and large touch controls; neither audience loses its preferred input.
- Keep routine replies brief, but allow longer readback when correctness requires it.
- Do not require a subscription, login, remote caregiver, or internet connection for core local use.

## 3. Scope and release boundaries

### 3.1 Mandatory core release

1. Assisted or self-service Bangla onboarding and capability checks.
2. Push-to-talk and a user-started hands-free session.
3. Local speech recognition across three evaluated speech cohorts.
4. Reminder creation, listing, editing, cancellation, acknowledgement, and snoozing.
5. Saved-contact lookup, user-confirmed aliases, and confirmed outgoing calls.
6. Launching user-selected installed apps by name or alias.
7. Spoken/visual help, confirmation, correction, and recovery.
8. Local storage, deletion controls, permission diagnostics, and an offline demo.

### 3.2 Conditional MVP phone-control module

Home, Back, Recents, scrolling, and numbered accessible controls may ship only after device, accessibility, consent, and distribution gates pass. This is an optional capability within the MVP, not a prerequisite for reminders and calling. A release shall explicitly label this module **available**, **experimental on listed devices**, or **unavailable**; it shall not imply universal control.

Initial external screen-action allowlist: the selected test-device launcher and calculator. Pulsar's own screens expose equivalent commands directly without accessibility automation. Contact calls use platform intents rather than tapping another app. Other apps may be launched but are not automatically eligible for internal screen control.

### 3.3 Explicit exclusions

No payments, bKash/Nagad operations, SMS reading, message sending, bulk deletion, financial decisions, prescription interpretation, automatic medical schedules, remote monitoring, cloud chatbot, general web answers, document scanning, unrestricted dictation into other apps, autonomous multi-app planning, screen-off wake word, lock-screen bypass, biometric/PIN entry, or automatic permission approval.

No iOS, desktop host product, or nationwide accent claim is included. Future possibilities are not deliverables under this SRS.

### 3.4 Meaning of voice-only operation

| Situation | MVP behavior |
|---|---|
| First install, permissions, accessibility enablement | User or helper completes Android-controlled screens; voice-only setup is not promised |
| Phone unlocked and Pulsar session active | Supported commands can be completed using speech |
| Cross-app interaction | An explicitly started, permitted microphone session may continue for core commands; operating another app's screens additionally requires enabled phone-control capability |
| Launching an app/call while Pulsar is backgrounded | Requires a platform-permitted activity-start route; a microphone foreground service alone is insufficient. Otherwise ask the user to return to Pulsar before handoff |
| Phone locked/screen off | Session terminates; scheduled reminder notifications may still fire |
| Pulsar not running | Launch/activation may require a tap, widget, or supported system assistant invocation |
| Incoming/outgoing call | Listening stops; user may need to reactivate after the call |
| Unsupported or protected screen | Explain limitation; do not guess coordinates or bypass security |
| Accessibility permission denied | Core in-app workflows remain usable; external navigation is unavailable |

System-assistant integration through `VoiceInteractionService` is a feasibility experiment, not a required activation route. It does not guarantee DSP hotword availability or universal screen-off activation. [R5] Core app/call handoff acceptance is measured from visible Pulsar. Background voice reminder processing may continue in an eligible user-started session, but launching another activity must independently satisfy Android background-activity-start restrictions; never treat microphone permission as that authorization. [R16]

## 4. Platform, assumptions, and dependencies

- **Platform baseline:** Android 10/API 29 or later, ARM64 CPU, 3 GB physical RAM, microphone, speaker, and adequate free storage. This is a proposed support floor pending device testing, not a validated guarantee.
- **Build:** use the stable Android SDK and libraries compatible with current distribution requirements at build freeze; record exact versions and hashes. Do not freeze stale dependency versions in this proposal.
- **Test floor:** at least four physical phones spanning low-cost OEM variants and multiple OS versions, including the minimum supported OS and newest supported release available to the team.
- **Language:** Bangla UI/output; Bangla and English digits, common app names, and limited mixed-language commands accepted.
- **Timezone:** initialize from the device; use Asia/Dhaka in the Bangladesh pilot. Confirm and document behavior if timezone changes.
- **Connectivity:** no internet required for local recognition, reminders, aliases, or navigation after installation. Calls require SIM/network service; opened apps have their own connectivity requirements.
- **Distribution:** signed private pilot APK or an approved testing track. Store review and optional accessibility approval are separate external dependencies.
- **Model availability:** the documented Bengali Zipformer export exists, but trainable checkpoint and recipe compatibility must be verified before promising fine-tuning. [R6]
- **No backend dependency:** the core app has no account server, subscription server, or runtime cloud inference. Research-data storage is a separate consented workflow.

## 5. Functional requirements

**Priority:** M = mandatory core; C = conditional phone-control module. “Shall” indicates a release requirement. Acceptance tests appear in Section 13.

### 5.1 Onboarding and capabilities

| ID | Priority | Requirement |
|---|---|---|
| FR-001 | M | The app shall show and speak a short Bangla explanation of supported actions and limitations. |
| FR-002 | M | The app shall request microphone, notification, alarm, contact, and calling access only when the associated feature is introduced or invoked. |
| FR-003 | M | The app shall provide a capability screen showing recognition readiness, reminder permission state, call availability, and phone-control availability. |
| FR-004 | M | The app shall support onboarding without a login and allow refusal of optional permissions without blocking unrelated features. |
| FR-005 | M | The app shall run a practice command and a test reminder during onboarding and explain failed checks. |
| FR-006 | M | The user shall explicitly select or confirm saved contact aliases and app aliases; inferred relationships shall remain suggestions until confirmed. |

### 5.2 Voice interaction and dialogue

| ID | Priority | Requirement |
|---|---|---|
| FR-010 | M | The app shall provide a large Talk button, immediate recording feedback, and visible listening/speaking states. |
| FR-011 | M | The app shall offer an explicitly started hands-free session with a persistent stop control and the required foreground-service notification. |
| FR-012 | M | A session shall end on stop command, stop control, lock/screen-off, call interruption, microphone loss, or 60 seconds without user speech; it shall warn before the inactivity timeout. |
| FR-013 | M | The app shall support help, repeat, yes, no, cancel, correction, and stop commands in every applicable dialogue state. |
| FR-014 | M | The app shall route unsupported, incomplete, or ambiguous commands to clarification or a supported-command explanation, not an invented action. |
| FR-015 | M | The app shall bind a confirmation to one pending action and its exact parameters; corrections invalidate the previous confirmation. |
| FR-016 | M | Pending confirmations shall expire after 20 seconds without user response; the app shall announce cancellation and execute nothing. |
| FR-017 | M | No side effect shall execute from partial ASR text; speculative recognition may prepare candidates only. |
| FR-018 | M | The MVP shall pause recognition during spoken output to avoid self-triggering, then signal readiness; an always-available touch stop remains usable during playback. |
| FR-019 | M | The app shall retain the original and normalized utterance in volatile dialogue state only, unless the user explicitly saves reminder audio. |
| FR-020 | M | The app shall provide an equivalent visible control for every core voice action, including correction and cancellation. |

Half-duplex speech is deliberate: voice interruption while Pulsar is speaking is not guaranteed. Prompts should normally take 1–4 seconds; lengthy schedule readback may take longer. Accessibility does not require rushing older speakers.

### 5.3 Reminders

| ID | Priority | Requirement |
|---|---|---|
| FR-030 | M | The app shall create a reminder from a task and an explicit date/time or a supported relative duration. |
| FR-031 | M | The app shall support one-time, daily, selected-weekday, and explicit multiple-times-per-day schedules, with optional end date or number of days. |
| FR-032 | M | The app shall ask for missing times, AM/PM/day-part ambiguity, past-time ambiguity, and unspecified recurrence details. |
| FR-033 | M | Before creation or edit, the app shall read back task, resolved date/time, and recurrence and obtain explicit confirmation. |
| FR-034 | M | The app shall list today's and upcoming reminders and support identifying one by title, time, or numbered candidate. |
| FR-035 | M | The app shall support edit, cancel one occurrence, and cancel a series with clear distinction and confirmation. |
| FR-036 | M | The app shall persist confirmed reminders and scheduling state; it shall announce “scheduled” only after successful platform scheduling. |
| FR-037 | M | The app shall reconcile reminders after reboot following user unlock, time/timezone change, package replacement, and permission-state change or subsequent app resume. |
| FR-038 | M | A firing reminder shall show task content and Done/Snooze controls, with audio subject to device settings and permissions. |
| FR-039 | M | The app shall support default 10-minute snooze and explicit positive snooze durations within a documented 1–1,440-minute range. |
| FR-040 | M | The app shall record acknowledgement separately from alert delivery and shall not represent acknowledgement as verified task or medication completion. |
| FR-041 | M | When verified offline TTS cannot speak a task, voice creation shall obtain consent to retain a task-label recording, replay it for confirmation, and link it to the reminder. Users shall be able to preview, replace, or remove it; stored audio shall not enter training automatically. |
| FR-042 | M | Permission denial or revocation shall mark precise reminders blocked, explain the problem, and preserve the user's intent; it shall not silently substitute an imprecise schedule. |

Supported examples: “আধা ঘণ্টা পরে পানি খেতে মনে করিও”, “প্রতিদিন সকাল আটটায় হাঁটার কথা মনে করিও”, “শুক্রবার বিকাল চারটায় বিল দিতে মনে করিয়ে দিও”, “আজকের দ্বিতীয় রিমাইন্ডারটা বন্ধ করো”.

Rules: “দিনে তিনবার” requires explicit times; “খাওয়ার পরে” requires clarification; “কাল” is resolved from utterance context, not universally mapped; “সকাল” alone asks for a time rather than silently selecting 8 AM. Every-N-hours recurrence is excluded from the first MVP to avoid confusing elapsed intervals with calendar schedules.

Each recurrence creates distinct occurrences. Editing a series affects future occurrences only. On timezone change, calendar schedules follow the device's local clock with a visible notice; elapsed-duration reminders retain their intended duration where the original deadline can be reconstructed. After reboot, overdue reminders are presented once as overdue, not replayed in a burst. No alerts before first unlock after reboot are promised in the MVP.

### 5.4 Contacts and calls

| ID | Priority | Requirement |
|---|---|---|
| FR-050 | M | The app shall resolve names and user-approved aliases from permitted saved contacts or a manually entered local favourite. |
| FR-051 | M | Multiple people, numbers, or similar fuzzy matches shall produce a numbered clarification; fuzzy matching shall never authorize a call by itself. |
| FR-052 | M | Before initiating a call, the app shall identify the selected contact and number label and obtain explicit confirmation. |
| FR-053 | M | With calling permission, the app shall use a documented Android call intent; without it, it shall offer a dialer handoff and disclose the additional touch requirement. |
| FR-054 | M | The app shall stop microphone capture before call handoff and report only that the call was requested, not that it connected. |
| FR-055 | M | The app shall support cancellation before dispatch; after handoff, call control belongs to the phone application. |
| FR-056 | M | The app shall not initiate emergency, premium/service-code, USSD, arbitrary unsaved-number, or financial-service calls in the MVP. |

Dual-SIM selection may require Android's own prompt. Voice-only call initiation is supported only on tested configurations with a usable default calling route. Contact permissions and CALL_PHONE are independent. A contact-picker fallback may be offered after device verification; manual favourites shall remain available without bulk contact access. [R8]

### 5.5 App launching and bounded phone control

| ID | Priority | Requirement |
|---|---|---|
| FR-060 | M | The app shall open a user-selected installed application by label or confirmed Bangla alias using a supported launch intent. |
| FR-061 | M | Missing, ambiguous, or inaccessible applications shall produce an explanation or selection prompt; the app shall not install applications automatically. |
| FR-062 | M | App launching shall not imply permission to operate the launched application's internal screens. |
| FR-063 | C | With accessibility enabled, the app shall map explicit commands to available Home, Back, and Recents actions and check their outcomes. |
| FR-064 | C | On allowlisted screens, the app shall scroll a supported accessible container in the requested direction and report unavailable actions. |
| FR-065 | C | The app shall label eligible accessible controls with temporary numbers and offer numbered selection by speech. |
| FR-066 | C | Screen targets shall bind to package, window, view identity, and a screen-generation token; any screen change invalidates previous numbers and confirmation. |
| FR-067 | C | The app shall prefer semantic node actions and shall not use OCR-based guessing, arbitrary coordinate tapping, or an LLM-generated action sequence. |
| FR-068 | C | Screen reading and actions shall stop outside the allowlist, on secure/permission screens, or when accessibility access is revoked. |
| FR-069 | C | An action that changes state beyond reversible navigation shall require explicit confirmation or be denied if its effect is not classified. |
| FR-070 | C | The app shall disclose accessibility use, collect explicit consent, provide an immediate disable control, and keep a versioned supported-device/app list. |

Accessibility allows certain global and node actions, but application support varies. A general assistant for all three groups shall not falsely declare itself a disability-specific accessibility tool. Store eligibility is not guaranteed by using deterministic commands. [R3, R4]

### 5.6 Data and recovery

| ID | Priority | Requirement |
|---|---|---|
| FR-080 | M | Users shall be able to delete individual reminders, stored recordings, aliases, and all Pulsar-local data. |
| FR-081 | M | Deleting a reminder shall cancel its future platform alarms and delete its linked audio after pending playback ends. |
| FR-082 | M | The app shall expose failed, blocked, pending, and completed states without silently losing confirmed requests. |
| FR-083 | M | Diagnostics shall omit raw speech, full transcripts, contact details, and screen contents by default. Export requires an explicit user action and preview. |
| FR-084 | M | The app shall validate the installed model manifest and show a recoverable error if files are missing, corrupt, or incompatible. |

## 6. End-to-end use cases

| ID | Preconditions | Main sequence | Alternate/failure behavior | Result |
|---|---|---|---|---|
| UC-01 Reminder | Microphone ready; alarm capability checked | Speak task/time → clarify if needed → readback → yes → persist → schedule | Missing time asks; denied alarm access marks blocked | One confirmed scheduled reminder |
| UC-02 Acknowledge/snooze | Reminder alert visible; user activates listening if needed | “হয়ে গেছে” or “দশ মিনিট পরে” → resolve occurrence → apply | Multiple active alerts require selection | One occurrence acknowledged or snoozed |
| UC-03 Call | Saved favourite; phone unlocked; network/calling route available | “আম্মুকে কল করো” → resolve → readback → yes → stop listening → call intent | Ambiguous contact/SIM asks or hands off; no permission opens dialer | Call requested, not claimed connected |
| UC-04 Open app | User-approved app alias exists | “ক্যালকুলেটর খোলো” → resolve → launch | Missing app explains; ambiguous alias asks | Target app launch requested |
| UC-05 Navigate | Active session; accessibility enabled; compatible screen | “নিচে যাও” or “দুই নম্বর চাপো” → validate current node → execute | Screen changed or unsupported → relabel/refuse | One bounded screen action |
| UC-06 Recover | Any pending dialogue | “না, নয়টায়” → invalidate old action → update → readback | Silence cancels pending action | No stale action executes |
| UC-07 Stop | Listening session active | “থামো” → cancel pending work → release microphone | During playback, touch stop remains available | No further commands accepted until activation |

Every use case shall be exercised with each audience. A successful developer-operated demo does not substitute for user completion evidence.

## 7. UX and dialogue design

### 7.1 Required screens

1. Welcome and scope explanation.
2. Guided permissions and practice.
3. Home: Talk, upcoming reminders, favourites, Help.
4. Listening/transcript/clarification surface with Stop.
5. Confirmation card with spoken readback and Yes/No/Edit.
6. Reminder alert and list/detail editor.
7. Contact/app alias setup and numbered choice.
8. Capability/settings/privacy page.
9. Optional phone-control overlay limited to supported screens.

### 7.2 Interaction rules

Minimum 48 dp interactive targets; high-contrast text; scalable Bangla font; meaningful Compose semantics; no information conveyed by colour alone. Validate 200% font scaling, screen-reader traversal, and portrait layouts on small screens. Android system dialogs are outside Pulsar's visual control.

Use respectful plain Bangla. Example success: “আগামীকাল সকাল আটটায় মনে করাব।” Example ambiguity: “সকাল আটটা, না রাত আটটা?” Example blocked capability: “ঠিক সময়ে মনে করাতে অনুমতি দরকার।” Unsupported request: “এটা এখন করতে পারি না। রিমাইন্ডার, কল, বা অ্যাপ খুলতে বলতে পারেন।”

Routine prompts use licensed human-recorded standard Bangla clips. Numbers, dates, weekdays, common labels, and app aliases use curated phrase units. Installed offline Android TTS may improve dynamic readback but is optional. Do not use a non-commercial-only TTS model in a commercial build without separate rights. [R10]

**Mandatory no-TTS path:** during voice reminder creation, retain the task-label audio with explicit consent and replay it together with the parsed schedule. If reliable task-only segmentation is unavailable, replay the full command or ask the user to record just the task; do not invent a spoken label. Touch-created reminders require a recorded task label to enable full spoken task readback. Uncommon favourite and app names require an approved recorded label during setup; familiar preset relationship/app clips may substitute. Contact disambiguation uses these labels plus prerecorded number-label/digit clips. Listing reminders plays each label and its schedule; editing the task replaces the label only after confirmation. These flows must pass with no Bengali TTS engine installed.

Users may refuse or delete a recording. The reminder remains usable through its visible title and spoken ordinal/date/time, and the app explicitly says task-name readback is unavailable. A subsequent voice task edit asks for a replacement recording before confirmation; it never pretends to have spoken an unavailable title. Users can still list, identify by ordinal/time, snooze, acknowledge, or cancel. This privacy choice is a disclosed reduction in spoken content, not silent data regeneration. Full voice readback is available when the user supplies the required labels or a verified offline TTS voice is present.

### 7.3 Dialogue state machine

```text
IDLE → LISTENING → INTERPRETING
                   ├─ unknown/incomplete → CLARIFYING → LISTENING
                   ├─ reversible navigation → EXECUTING
                   └─ consequential action → CONFIRMING
                                               ├─ yes → EXECUTING
                                               ├─ correction → CLARIFYING
                                               └─ no/timeout → CANCELLED
EXECUTING → RESULT or ERROR → LISTENING (if session remains active)
stop / lock / interruption → IDLE with microphone released
```

Stop and cancel do not undo an already-dispatched call or completed action. The UI shall explain that boundary rather than pretending rollback occurred.

## 8. Recommended technology stack and decision rationale

There is no universally best stack independent of devices, team skills, licensing, and measured quality. These are the recommended best-fit defaults for this Android-only MVP. Change them only through a recorded benchmark or requirement-driven decision.

| Component | Selected technology | Why it fits | Tradeoff / validation |
|---|---|---|---|
| Android app | Kotlin, Jetpack Compose, Material 3 | Direct platform integration and accessible UI | Android-specific; requires native expertise [R1] |
| State/concurrency | ViewModel, StateFlow, Kotlin coroutines | Lifecycle-aware state and cancellable work | Explicit ownership needed around services |
| Dependency management | Gradle Kotlin DSL, version catalog; small manual DI graph initially | Reproducible builds without early framework overhead | Adopt Hilt only if dependency complexity warrants it |
| Audio capture | AudioRecord, 16 kHz mono PCM after verified resampling | Direct local capture and streaming input | Test audio routing, interruptions, device formats |
| ASR runtime | sherpa-onnx Android bindings with one compatible ONNX runtime build | Existing local streaming deployment path | Native packaging/ABI and runtime memory must be measured [R7] |
| ASR baseline | Bengali streaming Zipformer/Vosk export | Relevant existing Bengali model | Published general-dataset errors are not product accuracy; trainable source access is a gate [R6] |
| Endpointing | Recognizer endpoint rules; measured silence thresholds | Avoid another model unless needed | Balance slow speech against response latency |
| Intent/dialogue | Kotlin deterministic finite-state engine and typed commands | Auditable behavior, no network, clear failure states | Constrained vocabulary; no general reasoning |
| Optional intent fallback | Existing INT8 BanglaBERT via compatible ONNX Runtime only if evaluation justifies it | Reuses a claimed asset for paraphrases | Audit availability, licence, export, and unknown-command calibration first |
| Time parser | Pure Kotlin normalization/rules plus java.time | Fits Android ownership and is unit-testable | Requires careful Bangla phrase coverage |
| Persistence | Room/SQLite; DataStore for small preferences | Migrations, transactions, observable local state | Database encryption is not automatic [R2] |
| Scheduling | AlarmManager exact one-shot occurrences, receivers, reconciliation | OS-managed wakeups independent of resident ASR | Permissions and OS/OEM conditions remain [R9] |
| Maintenance jobs | WorkManager for non-urgent cleanup only | Appropriate deferred work | Not a precise reminder scheduler |
| Contacts/calls | ContactsContract where permitted, local favourites, ACTION_CALL/ACTION_DIAL | Documented platform paths | Calling permission, SIM prompts, carrier service [R8] |
| App launch | PackageManager plus constrained launchable-app queries | Avoid hidden APIs | Package visibility and vendor behavior need testing |
| Phone control | AccessibilityService, semantic node actions, global actions | Explicit voice-driven navigation | Optional permission, policy review, incomplete app support [R3, R4] |
| Voice output | Contracted human recordings + phrase bank + user-recorded labels; optional local Android TTS | Natural Bangla and clear redistribution rights | Recording effort; dynamic speech less flexible |
| Sensitive local files | App-private storage; Android Keystore-backed AES-GCM for audio and sensitive field payloads | Protects data without exporting keys | Recovery/migration design needed; no guarantee on compromised devices |
| Model experiments | Python, PyTorch, icefall/k2, Lhotse | Training and data pipelines aligned with Zipformer | Reproduce compatible CUDA/toolchain environment [R11] |
| Experiment records | Git for code, DVC manifests/data versions, local MLflow | Traceable data/model comparisons | Private audio storage and access controls remain separate |
| Android testing | JUnit, coroutine tests, instrumented/Compose tests, Macrobenchmark, Perfetto | Logic, UI, device latency and resource evidence | Emulator success does not establish OEM reliability |
| Delivery | Gradle release build, signed APK/AAB, CI checks, model/asset manifests | Reproducible pilot artifacts | Signing secrets stay outside source control |

### 8.1 Deliberately omitted technologies

No Flutter/React Native bridge for a platform-heavy first release; no on-device general-purpose LLM; no cloud LLM for commands; no vector database; no always-running ASR service at idle; no separate Rust ownership of Android lifecycle or the primary database. Existing Python algorithms can be ported selectively after behavior tests exist.

A Rust library remains possible later if profiling identifies a specific bottleneck. Porting a sub-millisecond parser is not a priority while ASR, endpointing, and operating-system behavior dominate the experience.

### 8.2 Architecture

```text
Compose UI / Widget / user-started voice session
                        │
                 AudioRecord → sherpa-onnx ASR
                        │ final transcript only
                 Normalizer + Command Parser
                        │
                 Dialogue + Confirmation Policy
                        │ typed, validated command
              ┌─────────┼───────────────┐
              │         │               │
       Reminder use cases   Contact/App actions   Optional phone control
              │         │               │
          Room DB   Android intents   AccessibilityService
              │
     AlarmManager adapter + reconciliation
              │
       Notification / alert / voice playback

All results → spoken + visible feedback; minimal local diagnostic events
Training pipeline → versioned model artifact → release packaging (not runtime cloud)
```

Suggested modules: `app`, `speech`, `domain`, `data`, `platform`, and optional `phonecontrol`. Keep clear interfaces without creating a module for every class.

Typed command examples: `CreateReminder`, `EditReminder`, `CancelOccurrence`, `CallFavourite`, `LaunchApp`, `Navigate`, `SelectScreenTarget`, `Help`, `Stop`. Commands carry an ID, creation time, parameter version, and required capability checks.

## 9. Speech data, accent adaptation, and model release process

### 9.1 Three speech cohorts

| Cohort | Definition for recruitment | Reason |
|---|---|---|
| Standard Bangla | Speakers using a broadly standard spoken register | Common baseline and output vocabulary |
| Rajshahi-area | Participants self-reporting upbringing/use of Rajshahi-area speech; record locality | Preserves original local focus and recruitment opportunity |
| Dhaka-area conversational Bangla | Participants using ordinary Dhaka-area conversational speech; record locality and migration history | Adds a second practical conversational population |

Do not equate Dhaka-area speech with every Dhaka resident or with all Dhakaiya varieties. Record age band, self-reported speech background, relevant language mixing, device, noise condition, and prompt type. Avoid inferring sensitive demographic attributes from audio.

### 9.2 Collection plan

**Pilot corpus target:** 90 speakers, 30 per cohort, approximately 100 utterances per speaker: about 9,000 utterances. At 6–10 seconds per utterance this is approximately 15–25 hours of audio, not hundreds of hours. Recruit roughly 10 participants from each user audience within each cohort for balanced coverage; overlapping identities are recorded rather than treated as independent people.

Per speaker: 40 supported task prompts, 30 spontaneous task descriptions, 15 clarification/correction turns, and 15 unsupported/negative commands. Distribute numbers, dates, names, hesitation, background TV/fan/traffic, and code-switching across these categories. Do not make older participants read 100 sentences in one exhausting session; use short sessions with breaks and oral scenarios.

Obtain separate consent for participation, audio retention, model training, and any future release. Compensation must not depend on “correct” pronunciation. Real phone numbers, medication details, and private names are unnecessary for training; use fictional task content and consented aliases.

**Split before training:** 60 training speakers, 15 validation speakers, 15 final-test speakers, balanced 20/5/5 per cohort. Split by household as well as speaker where practical. Keep prompt families and recording sessions from leaking across partitions. Do not tune on the final test set.

Five final-test speakers per cohort provide only preliminary evidence. Expand to at least 10 final-test speakers per cohort, including new spontaneous scenarios, before making broad cohort-level support claims beyond the controlled pilot. Report speaker-level confidence intervals and the exact sample composition.

### 9.3 Step-by-step model workflow

1. **Audit assets:** obtain the actual model/export, tokenizer, normalization, licences, runtime dependencies, training checkpoint and matching recipe. The ONNX inference export alone is not evidence that convenient fine-tuning is available.
2. **Establish baseline:** run untouched Zipformer on the locked validation protocol and phones; record WER, command/slot correctness, latency, memory, and failures per cohort.
3. **Fix preprocessing first:** compare normalization, endpointing, name aliases, and supported hotword biasing. Verify hotword support for the chosen decoder instead of assuming it.
4. **Reproduce training:** restore the checkpoint, run a small training step, export, and compare desktop/mobile inference before collecting expensive additional data.
5. **Adapt one shared model:** use balanced batches across cohorts, real command audio, modest speed/noise/reverberation augmentation, and retained broad Bengali data where licensed. Avoid training three unrelated models unless evidence requires it.
6. **Control overfitting:** track speaker-disjoint validation, task slots, negative commands, and per-cohort regressions. Tune learning rate, freezing, and duration through small experiments rather than asserting a universal recipe.
7. **Evaluate deployment candidates:** compare full precision and supported INT8 exports; measure accuracy after quantization and on the actual mobile decoder. Select by task quality within latency/memory limits.
8. **Freeze and test:** select using validation only, then evaluate once on final test speakers. Record model hash, training code revision, data manifest, seed, export settings, hardware, and results.
9. **Field-test:** separately measure user interaction success in the household pilot. Laboratory WER is not the same as independent task completion.
10. **Release:** bundle one selected model with a manifest and rollback-compatible app build. Never replace a working model solely because its average WER is lower.

### 9.4 If trainable Zipformer assets are unavailable

Spend at most the first two engineering weeks resolving checkpoint/recipe access. In parallel, benchmark a trainable multilingual Whisper tiny/base candidate using its public training-compatible weights and an Android deployment path. Whisper is a fallback experiment, not a claim of equivalent streaming performance; its licence is documented upstream. [R12]

If the fallback meets the same release gates, record the changed runtime/model decision. If no trainable candidate meets device limits, either ship the baseline with honest evaluated support and label accent adaptation incomplete, or delay the trained-model milestone. Never describe hotword tuning alone as having trained an accent model. The pitch must state which outcome was achieved.

### 9.5 Compute and model acceptance

Plan initial small-model experiments on one 16–24 GB GPU where compatible, with mixed precision and batch tuning. This is a starting resource estimate, not a guaranteed fit or training time. Measure one pilot run before reserving larger compute. Free notebook sessions are useful for exploration but are not a dependable schedule commitment.

Promotion requires meeting the end-to-end acceptance gates and either a meaningful critical-slot improvement or at least a proposed 10% relative WER reduction on a previously weak validation cohort, without more than a 2-percentage-point task-success regression in another cohort. Report uncertainty; if already at a quality ceiling, do not require artificial training gains.

## 10. Data model, scheduling consistency, and interfaces

| Entity | Principal fields | Lifecycle |
|---|---|---|
| Reminder | UUID, title, optional audio reference, local schedule, timezone policy, recurrence, end date, version, enabled | Created only after confirmation; future edits versioned |
| Occurrence | UUID, reminder/version, due time, platform request code, scheduling state, fired/acknowledged timestamps | Unique per reminder version and due time |
| Favourite | UUID, contact reference or manual number, number label, approved aliases, optional spoken label | User-controlled; refresh stale contact references |
| AppAlias | Package ID, launch component where needed, display name, approved aliases, preset/recorded spoken label | Revalidate at launch and after package changes |
| PendingAction | ID, typed payload, parameter version, expiry, confirmation state | Memory-only; lost on process death and never auto-replayed |
| CapabilityState | Permission observations, model version/readiness, supported phone-control configuration | Rechecked before use, not trusted indefinitely |
| DiagnosticEvent | Event type, timestamp, durations, error code, app/model version | Local rolling 7-day retention; excludes content by default |
| ModelManifest | Model ID/hash, runtime/tokenizer compatibility, licence notices, evaluation reference | Bundled with each release |

Room transactions persist a confirmed reminder and pending occurrence before calling the scheduler. A successful call records `scheduled`; failure records `blocked` or `failed`. Reconciliation repairs incomplete scheduling after a crash using stable occurrence IDs. Alarm delivery validates the reminder version so cancelled/edited occurrences cannot fire stale content. Repeated broadcasts must not duplicate one occurrence.

External action results use `requested`, `failed`, or `unknown` where the platform provides no completion acknowledgement. Do not automatically retry calls or node clicks after an ambiguous outcome.

Default retention: ordinary command audio discarded when the turn ends; explicitly saved reminder audio remains until its reminder is deleted; temporary screen trees remain in memory only during an active supported interaction. Opt-in research recordings use a separate project retention policy of 12 months from collection, with withdrawal/deletion handling documented; deleting raw audio cannot guarantee removal of influence from already trained weights.

## 11. Security, privacy, permissions, and distribution

| Capability | Permission/access approach | Denial behavior |
|---|---|---|
| Recording | RECORD_AUDIO, appropriate microphone foreground-service declarations | Touch workflows remain; no recording |
| Alerts | Runtime notification permission where applicable | Explain alert limitation; setup test fails clearly |
| Exact reminders | SCHEDULE_EXACT_ALARM special access where required | Precise scheduling blocked until granted |
| Full-screen alert | Optional USE_FULL_SCREEN_INTENT only where eligible and granted | Notification fallback; no guaranteed takeover |
| Contact lookup | READ_CONTACTS when user opts into lookup | Manual local favourites remain |
| Direct call | CALL_PHONE for direct confirmed call | ACTION_DIAL handoff, clearly marked |
| Phone control | User-enabled AccessibilityService with disclosure | Optional module disabled |
| App discovery | Narrow launchable-app/package visibility declarations | User sees supported/discoverable subset |
| Startup recovery | Boot receiver and post-unlock reconciliation | Explain that pre-unlock alerts are not supported |

Do not request SMS, call logs, unrestricted storage, QUERY_ALL_PACKAGES, or SYSTEM_ALERT_WINDOW for the core MVP. An accessibility overlay for the optional module is not a general-purpose application overlay permission. Avoid a permanent microphone service to keep a model warm. [R3, R13]

Threats and controls: background conversation can be mistaken for a command, so consequential actions require explicit confirmation; stale screens can cause wrong clicks, so target tokens expire on change; contact ambiguity can cause wrong calls, so aliases and recipients are confirmed; logs can expose private speech, so content logging is off; shared phones can expose task content, so lock-screen task detail defaults to hidden.

Speech is not identity authentication. Sensitive actions remain outside scope. Calls and interaction with stored private content require the phone to be unlocked. No remote access service or hidden microphone capture is permitted. App-private data shall be excluded from uncontrolled cloud backup; verify backup rules and key loss behavior. A Keystore failure shall not silently reset user data.

Accessibility policy allows certain deterministic automation but restricts autonomous planning and the accessibility-tool designation. This broad-audience assistant shall not assert `isAccessibilityTool=true` without a legitimately different, evidenced disability-primary product scope. Prepare the required declaration, disclosure, demo, and data inventory; approval remains an external decision. [R3]

Before public distribution in Bangladesh, obtain a focused review of applicable privacy, consumer, and health-related claims for the actual data flows. This specification does not assert legal certification or medical-device status. The product schedules user-defined tasks and makes no treatment or emergency-reliability promise.

## 12. Non-functional requirements and performance budgets

All values below are **proposed acceptance targets**, not measurements. Freeze the reference devices, OS/build, model, test corpus, and conditions before reporting results. P95 means 95th-percentile latency.

| ID | Requirement | Target / measurement |
|---|---|---|
| NFR-01 | Warm local response | Median ≤1 s, P95 ≤2 s from end of user speech to useful feedback, including endpointing; measure ≥100 commands per reference phone |
| NFR-02 | Cold readiness | P95 ≤5 s from activation to ready-to-listen on reference devices; ≥30 cold starts |
| NFR-03 | Input feedback | Visual/haptic recording indication ≤150 ms after accepted activation; do not signal readiness before capture works |
| NFR-04 | Speech interpretation | ≥95% correct intent plus critical slots in quiet and ≥90% in defined household noise, separately per cohort on supported commands |
| NFR-05 | Unknown-command handling | ≥95% rejection/clarification on a held-out negative set; zero consequential side effects without valid confirmation |
| NFR-06 | Alarm scheduling | ≥99% of ≥300 scheduled test occurrences per reference phone alert within 60 s in declared supported states; no duplicate alerts |
| NFR-07 | Memory | Target ≤300 MB total process PSS during ordinary listening; ≤400 MB peak; profile native allocations, not only JVM heap |
| NFR-08 | Storage | Target ≤250 MB installed footprint excluding user recordings; record download and installed sizes separately |
| NFR-09 | Idle resource use | No microphone capture or resident ASR service while idle; compare matched 24-hour battery runs with scheduled reminders |
| NFR-10 | Active-session energy | Proposed target: ≤5 percentage points additional battery drain over a matched screen-on baseline per 30-minute session, median of 3 paired runs; no OS severe thermal status or temperature-related shutdown |
| NFR-11 | Accessibility | 48 dp targets, 200% text scaling, spoken equivalents, screen-reader labels, no clipped required controls |
| NFR-12 | Stability | ≥99% crash-free sessions over ≥500 controlled/pilot sessions; no unresolved data loss or wrong-target severity-1 failures |
| NFR-13 | Offline availability | All core local workflows pass airplane-mode suite after installation; actual cellular calls tested separately with mobile data off |
| NFR-14 | User completion | In each audience, ≥85% of standard task attempts succeed without human help within 120 s and at most one Pulsar clarification; ≥90% within 180 s and at most three Pulsar clarifications |
| NFR-15 | Phone-control accuracy | ≥95% success for each declared action on the frozen compatibility matrix; zero stale-node/wrong-target actions in adversarial suite |
| NFR-16 | Maintainability | Repeatable signed build, versioned DB migrations, reproducible model manifest, and documented permission failure handling |

Alarm tests include locked screen, idle, process death, reboot followed by unlock, and supported battery settings. Powered-off periods, pre-unlock reboot periods, revoked permissions, force-stopped apps, and unsupported OEM restrictions are reported separately as unavailable conditions—not hidden as successful deliveries. A 99% pilot target is not an emergency or medical reliability guarantee.

“No critical errors observed” must always include the sample size. Average results shall not hide a weak audience, accent cohort, or device. Performance targets may be revised only in a documented scope decision before the release claim is made.

**Measurement protocol:** at Gate A freeze the reference devices, battery starting range (40–80%), ambient temperature range, screen brightness, session command rate, and control workload. Pair energy runs on the same phone with matched screen/audio workload and disabled unrelated updates; measure power more precisely where equipment is available. If the energy target cannot be met, revise session duration or supported devices before release rather than silently waive it. Report coarse battery-counter uncertainty.

For speech tests, report quiet recordings separately from household fan, television, and street-noise conditions. The initial reproducible noise suite uses separately licensed noise at 10 dB and 20 dB SNR, with an additional genuinely recorded household-noise subset; augmentation source clips in the final test must not appear in training. Report each condition separately, and apply the noisy accuracy gate to each cohort's prespecified pooled noisy set with equal condition weighting. Gate A freezes the normalization, critical-slot scoring, and audio protocol. Live pilot recordings provide complementary realism, not a replacement for a reproducible suite.

Independent completion means no human prompt, correction, or physical assistance after the task is presented. Any such intervention, abandonment, wrong outcome, or timeout is a failure for that attempt. Required confirmations do not count as clarifications; system requests for missing/ambiguous information do. Each pilot participant attempts the same 10 prespecified core tasks, yielding at least 100 attempts per audience; report participant-level variation as well as the aggregate. Conditional phone-control tasks are scored separately and do not inflate core success.

## 13. Verification, acceptance tests, and traceability

| Test | Requirements | Procedure | Pass condition |
|---|---|---|---|
| AT-01 Setup/denial | FR-001–006 | Fresh install; grant/deny each permission independently | Capabilities accurately shown; unrelated functions still work |
| AT-02 Voice lifecycle | FR-010–020 | Start, speak, timeout, stop, lock, call interrupt, playback | Correct state transitions; capture stops; no stale confirmation |
| AT-03 Schedule language | FR-030–035 | ≥500 text cases, held-out paraphrases, dates around midnight, ambiguous times | Supported cases match expected schedule; ambiguous cases ask |
| AT-04 Alarm endurance | FR-036–042, NFR-06 | Device-state matrix and ≥300 occurrences per phone | Delivery gate met; no duplicates; failures attributed |
| AT-05 Call resolution | FR-050–056 | Duplicate names, aliases, two numbers, denied permission, dual SIM, cancel | Correct target only after confirmation; limitations disclosed |
| AT-06 App launching | FR-060–062 | Installed/missing app, duplicate aliases, changed package | Correct launch or explained failure; no install/extra automation |
| AT-07 Phone navigation | FR-063–070, NFR-15 | At least 100 attempts per declared action across the frozen launcher/calculator/device matrix; changed/secure screens | ≥95% success per action and matrix entry; zero stale/wrong-target actions in adversarial cases |
| AT-08 Speech cohorts | NFR-04–05 | Frozen speaker-disjoint audio and negative commands | Cohort gates met; raw and normalized metrics reported |
| AT-09 Offline behavior | NFR-13 | Airplane mode; model installed; local tasks | No hidden cloud dependence; network actions labelled accurately |
| AT-10 Privacy/deletion | FR-080–084 | Inspect logs/storage, delete reminders/data, revoke access | No content leakage; pending alarms and linked audio removed |
| AT-11 Accessibility | NFR-11, FR-020 | Small screen, large font, screen reader, slow speech | All essential actions understandable and reachable |
| AT-12 Performance | NFR-01–03, 07–10 | Cold/warm runs, resource traces, matched battery tests | Targets met or explicit approved support restriction |
| AT-13 Field usability | G-01, NFR-14 | 30 users, 10 per audience, balanced across speech cohorts | Per-audience independent task gates met |
| AT-14 Recovery/update | NFR-12,16; FR-037,082,084 | Interrupt DB/scheduler boundary; upgrade DB; corrupt model | No duplicated actions/data loss; recoverable error shown |
| AT-15 Alert operations | FR-038–042 | Multiple due alerts; 1/10/1,440-minute snooze; reject out-of-range input; acknowledgement; revoked access | Correct occurrence changes; acknowledgement never means verified medication intake; blocked access explained |
| AT-16 No-TTS readback | FR-033–034,041,052; Section 7.2 | Remove Bengali TTS; create/edit/list reminder; set up names; decline/delete audio; call disambiguation | Recorded labels support full readback; missing-label limitations and recovery are explicit |
| AT-17 Core session continuity | FR-011–012,060–062 | Accessibility disabled; launch another app during a permitted active session; speak a core command; lock phone | Core command accepted through the eligible service/visible Pulsar surface; screen automation unavailable; lock ends capture |

The text parser set is not reused as the complete speech evaluation set. A training participant is not a final unseen-speaker test participant. Measure live user tasks separately from replayed recordings.

Pilot design: 30 users over four weeks, with shared tasks in week one and natural usage afterward. Log helper interventions and abandonment, not just successes. Record audience/age/cohort overlap rather than double-counting people. A proposed product gate is ≥60% of participants in each audience voluntarily using at least one core feature weekly by week four; this is a small-pilot decision rule, not market-size evidence.

Required release evidence: test summary, device/app compatibility matrix, per-cohort speech report, latency/memory traces, permission demo, privacy checklist, known limitations, and severity-ranked issue list.

## 14. Step-by-step implementation plan

**Planning assumption:** one experienced Android developer, one ML/data contributor, and part-time participant coordination/QA. Approximately 16 weeks is a planning estimate with work streams overlapping. A solo student should plan approximately 5–8 months depending on availability, learning, and recruitment. A smaller core demonstration may be possible sooner; it is not the full validated MVP.

| Stage | Timing | Implementation steps | Deliverable and gate |
|---|---|---|---|
| 1. Scope and user evidence | Week 1 | Observe 5 users from each audience; document shared tasks; confirm recruitment access; freeze initial exclusions | Common-task evidence and signed-off requirement baseline |
| 2. Platform/model feasibility | Weeks 1–2 | Create Kotlin app; run local ASR on low-cost phones; schedule real alarms; prototype confirmed call; investigate accessibility and trainable assets | Gate A: core viable; optional phone control classified; training route established or explicit blocker |
| 3. UX and test fixtures | Weeks 2–3 | Build screens and dialogue states; write parser cases and negative commands; design consent and speech collection | Usability walkthrough and frozen initial evaluation protocol |
| 4. First complete reminder | Weeks 3–5 | Audio → transcript → parser → confirmation → Room → AlarmManager → alert → Done/Snooze; add recovery states | Gate B: one reliable end-to-end workflow on devices |
| 5. Contacts/apps | Weeks 5–6 | Favourite setup; aliases; candidate disambiguation; call/dial fallback; app launch; cancellation | Shared core task demo with permissions denied/granted |
| 6. Speech collection | Weeks 2–7 | Recruit, consent, record, transcribe, audit, split by speaker/household | Versioned 90-speaker target corpus with usable consent records |
| 7. Accent adaptation | Weeks 6–9 | Baseline; reproduce training/export; adapt; quantize; compare; freeze candidate | Gate C: cohort quality and phone resource report; trained-model claim only if earned |
| 8. Hands-free module | Weeks 7–9 | User-started service lifecycle; stop/timeout; optional numbered nodes and navigation allowlist | Gate D: explicit command control works within listed limits |
| 9. Reliability/accessibility | Weeks 9–11 | Overnight alarms; reboot/update tests; larger font; speech pacing; failure recovery; remove redundant permissions | No unresolved critical correctness issues; internal alpha |
| 10. Field pilot | Weeks 12–15 | 30 participants, four weeks; balanced cohorts/audiences; collect consented diagnostics and support effort | Retention, independence, and device-reliability evidence |
| 11. Release/pitch package | Week 16 | Fix release blockers; freeze compatibility; produce signed build, model card, demo, and results | Gate E: core pilot release; optional module only if its gates pass |

### 14.1 Concrete first ten working days

1. Create a requirement/test register and assign one owner per release gate.
2. Inventory the referenced Python/model assets; distinguish available files from proposal claims.
3. Obtain/borrow the four reference phones and record chip, RAM, OS, vendor build, and battery condition.
4. Create the Kotlin/Compose project and reproducible build.
5. Integrate one ASR model through official Android bindings; record baseline traces.
6. Implement a standalone exact-alarm permission/check/schedule/fire test.
7. Test call intents and app launches with explicit user commands.
8. Prototype microphone lifecycle across app switching and screen locking.
9. Run a minimal accessibility node/global-action experiment and review its distribution requirements.
10. Freeze trainable-model route, recruitment consent, split protocol, and the first vertical slice backlog.

Do not spend the first two weeks rewriting all proposed modules before these feasibility checks.

### 14.2 Release checklist

- All mandatory functional requirements have passing tests or an explicitly revised requirement baseline.
- All three audience groups and selected speech cohorts are represented in evidence.
- No unresolved wrong-recipient, wrong-time, duplicate-action, private-data leak, or data-loss defect.
- Alarms and model inference have passed the declared physical-device matrix.
- Permission denial/revocation and offline operation are demonstrated.
- Model and voice-asset licences, consent, manifests, and exact dependency versions are recorded.
- Optional accessibility functionality is labelled and distributed only through an appropriate reviewed path.
- Pitch materials distinguish implemented behavior, measured results, and future/conditional capabilities.

## 15. Resources, costs, feasibility, and risks

### 15.1 Resource plan

Android lead owns platform behavior, UX, scheduling, and release. ML/data contributor owns dataset quality, training/export, and reproducible evaluation. A coordinator owns consent/recruitment and pilot observations. Each role may be part-time or combined, but the workload does not disappear when one person holds multiple roles.

Illustrative Bangladesh pilot cash allowances, **not vendor quotes**, excluding salaries, existing laptop, office costs, taxes, and ongoing public-launch support:

| Item | Planning allowance |
|---|---:|
| Four test phones/accessories, with borrowing where possible | ৳35,000–70,000 |
| Participant compensation, travel, transcription, coordination | ৳35,000–80,000 |
| GPU experiments and protected research storage | ৳10,000–35,000 |
| Human voice recordings and asset rights | ৳10,000–25,000 |
| Pilot distribution, QA incidentals, materials | ৳5,000–15,000 |
| Contingency | ৳20,000–45,000 |
| **Total allowance** | **৳115,000–270,000** |

Replace allowances with local quotations before funding commitments. The dataset plan is larger than a tiny reminder-only demonstration and therefore costs more. Model retraining, paid engineering, legal review, and widespread device support can materially increase the total.

### 15.2 Benefits and tradeoffs

| Decision | Benefit | Cost or limitation |
|---|---|---|
| One shared product | Coherent pitch and reusable interaction | Must test each audience; averages are insufficient |
| Offline ASR | Predictable local availability and fewer runtime API costs | Larger app, local compute, model maintenance |
| Three speech cohorts | Focused adaptation and evaluation | Limited geographic generalization |
| Native Android | Direct access to lifecycle and platform APIs | No immediate cross-platform product |
| Deterministic actions | Predictable and auditable | Cannot fulfill arbitrary requests |
| Confirmed calls/reminders | Reduces harmful mistakes | Adds a conversational turn |
| Optional accessibility | Enables useful voice navigation | Permission friction and external review/support risk |
| No cloud accounts | Simpler privacy and onboarding | No remote family dashboard or cross-device sync |

### 15.3 Risk register

| Risk | Impact | Mitigation / decision trigger | Owner |
|---|---|---|---|
| Low accent accuracy | Users abandon voice | Early real-speaker baseline; targeted data; no support claim before cohort gates | ML lead |
| Training assets unavailable | Adaptation milestone blocked | Two-week checkpoint gate and benchmarked fallback; disclose incomplete training | ML lead |
| Alarm/OEM restrictions | Missed tasks and lost trust | Early multi-device endurance tests; supported-state diagnostics | Android lead |
| Excess memory/battery use | Poor low-cost-phone experience | No idle ASR; profile native allocations; bounded sessions | Android lead |
| Accessibility review/support fails | Phone control cannot ship broadly | Keep core independent; narrow module and claims | Product/Android |
| Incorrect recipient/time | Consequential error | Explicit readback, clarification, versioned confirmations, zero critical open defects | QA/Android |
| Dataset bias/leakage | Misleading accuracy | Speaker/household split, spontaneous speech, per-cohort reports | ML/data |
| Shared-phone privacy | Unwanted exposure | Hidden lock-screen detail, local consent, no family monitoring | Product/Android |
| Users do not return | Weak product demand | Four-week pilot; investigate actual repeated tasks before expansion | Product |
| Recruitment/compute delays | Schedule slips | Begin recruitment immediately; time-box experiments; revise milestones transparently | Project owner |
| Voice expectations exceed scope | Pitch disappoints | Show compatibility boundary and failure recovery in live demo | Product |

Commercial feasibility remains unproven. The initial pitch should seek pilot access, device support, data-collection funding, or incubation—not claim established demand, medical efficacy, or a national addressable customer count from mobile subscriptions.

## 16. Pitch and demonstration package

### 16.1 Five-minute demo

1. **Problem, 30 seconds:** explain the shared difficulty and three audiences without stereotypes.
2. **Offline reminder, 60 seconds:** mobile data off; ask an ambiguous reminder; clarify; confirm; show it scheduled.
3. **Contact/app tasks, 60 seconds:** resolve a saved alias; demonstrate confirmation/cancel; launch the calculator. Place a real call only to a consenting demo recipient.
4. **Phone control, 60 seconds if enabled:** while unlocked and in an active session, use Home/Back and numbered calculator controls. State supported devices/apps.
5. **Speech evidence, 45 seconds:** present consented examples and results from unseen speakers in all three cohorts; recordings are labelled as recordings.
6. **Proof and request, 45 seconds:** show measured device performance, one failure/recovery, known limitations, and the specific pilot/funding request.

### 16.2 Required pitch artifacts

- This SRS and a one-page product brief derived from it.
- Signed installable demo build with exact version/model hash.
- Short demonstration video, including activation and permission prerequisites.
- Per-cohort/per-audience results and device compatibility matrix.
- Data consent summary, asset licence inventory, and model card.
- Budget with actual quotations and a named milestone owner.

Acceptable claim before evaluation: “Designed for offline Bangla interaction, with standard, Rajshahi-area, and Dhaka-area speech under evaluation.” After passing tests, replace that with the actual measured population and numbers. Never claim “understands all Bangla,” “controls any phone entirely by voice,” “zero battery cost,” or guaranteed medical adherence.

## 17. Decisions, open gates, and change control

| Decision | Status |
|---|---|
| Serve all three user audiences through common workflows | Adopted |
| Android-only, local-first core | Adopted |
| Reminders, confirmed saved-contact calls, app launching | Mandatory MVP |
| Standard + Rajshahi-area + Dhaka-area speech | Proposed collection scope; recruit and evaluate explicitly |
| Native Kotlin/Compose instead of mandatory Rust rewrite | Recommended architecture baseline |
| User-started hands-free sessions | Mandatory; not equivalent to always-on activation |
| Cross-app navigation | Conditional on Gate D, compatibility and distribution review |
| Exact ASR training checkpoint/runtime versions | Freeze after Gate A; unknown until audited |
| Installed offline Bengali TTS | Optional enhancement; required flows use owned/licensed clips and recorded labels |
| Commercial demand and pricing | Unvalidated; not a release correctness dependency |

Any scope change shall state user value, affected requirements/tests, device/performance implications, permission implications, cost, and schedule. A conditional feature cannot silently become mandatory halfway through development. Removing a mandatory feature requires a new SRS baseline and revised pitch.

## 18. Sources and terminology

Sources checked on 5 November 2026; platform policy and implementation details must be rechecked at release. Official references establish API capabilities and constraints, not that Pulsar has implemented or passed them.

- **R1:** [Android architecture recommendations](https://developer.android.com/topic/architecture/recommendations) — native UI and lifecycle architecture.
- **R2:** [Room persistence documentation](https://developer.android.com/training/data-storage/room) — local structured storage and migrations.
- **R3:** [Google Play AccessibilityService policy](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en-en) — deterministic automation, disclosures, designation restrictions, review.
- **R4:** [Android AccessibilityService API](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService) — global actions and service capabilities.
- **R5:** [VoiceInteractionService API](https://developer.android.com/reference/android/service/voice/VoiceInteractionService) — optional system assistant integration.
- **R6:** [Bengali Vosk streaming model card](https://huggingface.co/alphacep/vosk-model-small-streaming-bn) — model identity, licence, published benchmark context.
- **R7:** [sherpa-onnx Android integration](https://github.com/k2-fsa/sherpa-onnx/blob/master/android/README.md) — native Android deployment entry points.
- **R8:** [Android common intents](https://developer.android.com/guide/components/intents-common) — platform action handoffs, including phone actions.
- **R9:** [Android alarm scheduling](https://developer.android.com/develop/background-work/services/alarms) — exact scheduling and permissions.
- **R10:** [Meta MMS-TTS model licence](https://huggingface.co/facebook/mms-tts) — example of a non-commercial TTS restriction; not a selected runtime model.
- **R11:** [icefall training framework](https://github.com/k2-fsa/icefall) — model training recipes and tooling.
- **R12:** [Whisper upstream repository](https://github.com/openai/whisper) — fallback experiment, available models and licence.
- **R13:** [Android foreground-service start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start) — microphone/session lifecycle constraints.
- **R14:** [Google Play full-screen intent requirements](https://support.google.com/googleplay/android-developer/answer/13392821?hl=en-en) — optional full-screen alarm presentation.
- **R15:** [GSMA Bangladesh connectivity context, September 2025](https://www.gsma.com/newsroom/press-release/high-spectrum-fees-risk-slowing-bangladeshs-digital-future-new-gsma-study-finds/) — contextual motivation only, not a Pulsar market-demand study.
- **R16:** [Android background activity start restrictions](https://developer.android.com/guide/components/activities/background-starts) — separate eligibility for app/call UI handoffs from a background session.

**ASR:** automatic speech recognition. **WER:** word error rate. **Critical slots:** action parameters whose error changes the target or outcome, such as time, date, recurrence, or recipient. **PSS:** proportional set size including shared/native process memory. **OEM:** device manufacturer. **MVP:** minimum viable product. **Gate:** an evidence-based proceed/revise/stop decision. **SRS:** software requirements specification.

---

**Document completion condition:** The SRS is complete as a proposed specification. Implementation, trained-model availability, user validation, distribution approval, and performance acceptance remain the delivery work defined above.
