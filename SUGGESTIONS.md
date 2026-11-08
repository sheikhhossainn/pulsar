# Pulsar MVP Plan & Decision Log (`SUGGESTIONS.md`)

Scope, architecture, and build plan for the **Pulsar MVP**: an offline-first, ultra-fast Bangla voice agent for elderly, non-tech-savvy, and busy ("lazy") users in Bangladesh.

---

## 1. MVP Scope

### Product Rule
A feature ships in the MVP only if it is:
1. Used **daily or weekly**,
2. Replaces **5+ taps** or replaces asking a family member for help,
3. Works for **all three users**: elderly, non-tech-savvy, busy.

### In Scope

| # | Feature | One-line pitch |
|---|---|---|
| 1 | **Smart Reminders** | "কাল সকাল ৮টায় প্রেশারের ওষুধ মনে করিয়ে দিও" — medicine + tasks, by voice. |
| 2 | **Scan & Send** | Select photos → "এগুলো স্ক্যান করে ছেলেকে PDF করে পাঠাও" — done. |

### Accent Scope
* **MVP:** Standard Bangla (প্রমিত) + **Rajshahi** accent — ASR must understand both. Spoken replies in standard Bangla only.
* **Phase 2:** 8 divisional accent clusters (Dhaka, Chattogram, Rajshahi, Khulna, Barishal, Sylhet, Rangpur, Mymensingh) via a deep-learning accent-robust model (accent embeddings / adaptation) — generalize per cluster instead of collecting data for every local accent.

### Cut From MVP (Parked for v2+)
| Feature | Reason |
|---|---|
| Kinship calling | Elders can already call; low pain. Kinship graph **kept** — reused by Scan & Send. |
| bKash/Nagad scam guard | High value, but second priority. First v2 feature. |
| Gemini fact-checking | Needs cloud, not daily pain. |
| OCR / prescription reading | Heavy model work. |
| Accent-matched talk-back | Phase 2, after accent ASR. |
| Save contact from SMS, email check | Nice-to-have. |
| Always-on "Hey Pulsar" wake word | Battery + latency cost (see §6). |

---

## 2. Feature 1 — Smart Reminders

### Capabilities
* **Medicine:** "প্রতিদিন সকাল ৮টা আর রাত ১০টায় প্রেশারের ওষুধ", "৭ দিন ধরে দিনে ৩ বার".
* **Tasks:** "কাল বিকালে বিল দিতে মনে করিয়ে দিও", "পরশু ছেলের স্কুলে যাওয়া".
* **Relative time:** "১ ঘণ্টা পরে", "আধা ঘণ্টা পর".
* **Repeat rules:** daily, weekly, every N hours, for N days, specific weekdays.
* **Manage by voice:** "আজকে কী কী আছে?", "ওষুধের রিমাইন্ডার বন্ধ করো".

### Flow
1. User speaks → ASR → intent `REMIND_CREATE`.
2. Rust time parser extracts `{when, repeat, task}`.
3. Agent confirms: *"কাল বিকাল ৪টায় বিল দেওয়ার কথা মনে করাব, ঠিক আছে?"* → user: "হ্যাঁ".
4. Saved in SQLite; Kotlin schedules via `AlarmManager.setExactAndAllowWhileIdle`.
5. On fire: full-screen alert + loud voice. User says **"খেয়েছি" / "হয়ে গেছে"** → done; **"১০ মিনিট পরে"** → snooze.
6. **Missed medicine escalation (optional):** 2 missed attempts → notify a family member. Key selling point to the child who installs the app.

### Bangla Time Parser (Rust, rule-based — no model)
* Day words: `আজ`, `কাল` (always = tomorrow in reminder context), `পরশু`, `তরশু`, weekdays (`শনিবার`…`শুক্রবার`).
* Day-parts with default hours: `ভোর`≈5, `সকাল`≈8, `দুপুর`≈13, `বিকাল`≈16, `সন্ধ্যা`≈18, `রাত`≈21.
* Numbers: Bangla digits (`৪টায়`), number words (`চারটায়`, `সাড়ে চারটায়`, `পৌনে পাঁচটা`, `সোয়া`), English digits.
* Relative: `X মিনিট/ঘণ্টা/দিন পরে`.
* Repeat: `প্রতিদিন`, `রোজ`, `প্রতি সপ্তাহে`, `X দিন ধরে`, `দিনে X বার`.
* Rajshahi variants to include in the test set (e.g., local verb endings and pronunciations of time words).
* **Target:** < 1 ms parse, ≥ 95% accuracy on a 300+ sentence test set.

### Reminder Voice Playback
* Fixed parts ("মনে করিয়ে দিচ্ছি…") → pre-rendered clips.
* Task content → **replay the user's own recorded voice** for that reminder. Zero TTS cost, most natural, best for memory.

---

## 3. Feature 2 — Scan & Send

### Flow
1. User selects images in Gallery → taps **Share → Pulsar** (`ACTION_SEND_MULTIPLE`).
2. Pulsar floating agent bubble appears over the gallery with images loaded, mic open.
3. User: *"এগুলো স্ক্যান করে ছেলেকে PDF করে পাঠাও"*.
4. Rust pipeline per image: page edge detection → perspective correction → deskew → contrast/threshold "scan look" → merge into one PDF.
5. Kinship graph resolves `ছেলে` → contact (disambiguates if multiple sons).
6. Agent confirms: *"৩টা পাতা বড় ছেলের হোয়াটসঅ্যাপে পাঠাব?"* → "হ্যাঁ".
7. Opens WhatsApp chat with the PDF attached; user taps send.

### Variants
* "ছবিগুলো শুধু পাঠাও" → send as images, no PDF.
* "এই নাম্বারে পাঠাও" / named non-kin contact → contact resolver.
* No selection: "আজকের তোলা ছবিগুলো ছেলেকে পাঠাও" → existing file pinpointer finds them.

### Android Constraints
* Pulsar **cannot read another app's gallery selection** (sandboxing). The **Share sheet** is the reliable entry point. The bubble is the agent's visual presence, not a screen reader.
* Bubble overlay requires `SYSTEM_ALERT_WINDOW`; ignore Play Store policy for MVP.
* WhatsApp direct-to-contact uses `ACTION_SEND` + `com.whatsapp` package + `jid` extra (unofficial; fallback to WhatsApp's own contact picker).

### Target
* 3-page scan + PDF in **< 500 ms** on a budget phone.

---

## 4. Architecture — Rust Harness

### Principle
Rust core is the **brain**; hosts are **dumb executors**. Core takes PCM audio in, emits typed actions out. It never calls OS APIs directly.

```
┌──────────────────── pulsar-core (Rust) ────────────────────┐
│ PCM → streaming ASR → fast-path / BanglaBERT → planner     │
│                                          ↓                 │
│        Action enum: CreateReminder{..} | SendFiles{..} |   │
│                     PlayClip{..} | AskConfirm{..}          │
│ State: kinship graph, contact index, reminders (SQLite)    │
└────────────────────────────────────────────────────────────┘
        ↑ PCM frames / share intents      ↓ Actions
┌──────────── Host adapters (thin) ──────────────────────────┐
│ Android (Kotlin): AudioRecord, AlarmManager, share intent, │
│                   overlay bubble, WhatsApp handoff, audio  │
│ Desktop CLI:      wav in, mock storage, prints actions     │
└────────────────────────────────────────────────────────────┘
```

### Workspace
```
pulsar/
  crates/
    core/     # pipeline, planner, Action enum, Host trait
    asr/      # sherpa-onnx streaming wrapper
    nlu/      # keyword fast path + BanglaBERT (ort + tokenizers)
    remind/   # Bangla time parser, repeat rules, SQLite store
    scan/     # edge detect, warp, enhance, PDF
    kin/      # kinship graph + fuzzy contact index
    voice/    # clip bank + slot concatenation
    cli/      # desktop host for testing
    ffi/      # uniffi bindings → Kotlin
```

### Crates
| Need | Crate |
|---|---|
| ASR | `sherpa-rs` or `bindgen` over sherpa-onnx C API |
| Intent model | `ort` (ONNX Runtime) + `tokenizers` |
| Fast path | `regex` (RegexSet) / `aho-corasick` |
| Fuzzy names | `strsim`, `fst` |
| Reminders DB | `rusqlite` |
| Image scan | `image`, `imageproc` |
| PDF | `printpdf` |
| Threading | `crossbeam-channel` (no tokio — CPU-bound pipeline) |
| Android bindings | `uniffi`, built with `cargo-ndk` (arm64-v8a) |

### Python's Role
Python stays for **training & experiments only** (Kaggle notebooks, hotword tuning, ASR fine-tuning). Logic in `agent_core/*.py` (contact resolver, file pinpointer, kinship) is **ported to Rust** — hard prerequisite for Android.

---

## 5. Speed — "Blazingly Fast" Budget

### Latency Targets (after user stops speaking)
| Stage | Target |
|---|---|
| Endpoint detection (trailing silence) | 300–500 ms — **main bottleneck, tune hard** |
| Final ASR decode (streaming) | 20–50 ms |
| Intent: fast path / BanglaBERT | < 1 ms / ~9 ms |
| Time parse / kin resolve | < 1 ms |
| Reply audio start (pre-rendered clip) | ~0 ms |
| **Total perceived** | **< 550 ms** |
| Scan & PDF (3 pages) | < 500 ms |

### Techniques
1. **Streaming ASR** — decode during speech, not after.
2. **Warm models** — load once at service start, keep resident in a foreground service.
3. **Keyword fast path** — RegexSet catches ~80% of commands in < 1 ms; BanglaBERT only on miss.
4. **Speculative intent** — classify on partial transcripts, prepare action before speech ends.
5. **Hotwords** — contact names, kinship words, time words, medicine words injected as ASR hotwords.
6. **Pre-rendered voice** — no runtime TTS for 80%+ of replies.
7. **No GC, no Python on device** — Rust core, threads + channels, zero-copy PCM.
8. **CPU + XNNPACK** — benchmark against NNAPI; NNAPI is often slower on budget chips.
9. **Test device** — 3 GB RAM, ~৳8–10k phone. Fast there → fast everywhere.

### Memory
* Always-on resident footprint **< 250 MB**.

---

## 6. Activation

* **MVP:** Push-to-talk only — floating bubble tap, long-press, or Share → Pulsar. Zero battery cost, zero false triggers, fastest.
* Wake response: **150 ms earcon + haptic**, mic opens after the chime ends (prevents ASR hearing its own speaker).
* **Deferred:** "Hey Pulsar" wake word. Third-party apps cannot use the hardware DSP (`AlwaysOnHotwordDetector`); screen-off mic wake costs ~15–25% battery/day on budget phones and OEMs (MIUI/HyperOS/Transsion) kill mic services. Revisit as "screen-on or charging only".

---

## 7. Voice Output — Realistic Standard Bangla

Three output tiers:

| Tier | Share | Method | Latency | Offline |
|---|---|---|---|---|
| **Fixed phrases** | ~80% | Pre-rendered with a premium TTS (ElevenLabs / Sarvam / Smallest.ai) at build time, bundled | ~0 ms | Yes |
| **Slots** (numbers, times, kinship words) | ~15% | Pre-rendered word clips, concatenated in Rust; reminder task = user's own recording | ~0–5 ms | Yes |
| **Dynamic text** | ~5% | Cloud streaming TTS when online; local Bangla VITS (sherpa-onnx) fallback | 100–400 ms | Fallback only |

### Rules
* Write every reply so it fits Tier 1 or 2. Keep spoken replies **< 3 seconds**.
* Use a **paid** TTS plan for commercial rights to bundled clips.
* **Do not** train an own TTS model on ElevenLabs output (likely ToS violation). Future own voice: hire a voice actor, record 5–10 h, train VITS/Piper.
* Verify ElevenLabs Bengali support per model (v3 reportedly supports Bengali; Flash support unconfirmed). Build-time rendering makes v3's slower speed irrelevant.

---

## 8. UX Rules

1. **One button, no menus.** Bubble or share sheet only.
2. **Echo-confirm** every action before executing ("…ঠিক আছে?" → "হ্যাঁ").
3. **Forgiving language** — "ঐ ছবিটা", "কালকের বিল" must work.
4. **Short replies** — under 3 seconds.
5. **Zero setup** — auto-learn kinship from contact names (আম্মু, ভাইয়া, ছোট চাচা, Choto Chele).
6. **Big, high-contrast UI**; every visual prompt also spoken.

---

## 9. Assets Already Built (Carry Forward)

| Asset | Status | MVP use |
|---|---|---|
| Streaming Zipformer ASR (`sherpa-onnx-streaming-zipformer-bn-vosk-2026-02-09`) + 76 daily hotwords | Verified | Core ASR; extend hotwords with time + medicine words |
| INT8 BanglaBERT intent classifier (13.69 MB, 9.07 ms CPU) | Verified | Retrain for MVP intents (below) |
| Kinship graph + 100-contact resolver (`agent_core/contact_resolver.py`) | Verified (Python) | Port to `kin` crate |
| File pinpointer (`agent_core/file_pinpointer.py`) | Verified (Python) | Port for "আজকের ছবিগুলো পাঠাও" |
| Real-audio benchmark: 4/4 correct, ~220 ms batch | Verified | Baseline; re-run in Rust CLI |

### Known Fix to Carry Into Rust
* **Prosthetic vowel prefix** — ASR outputs "আবসের" for "বসের". Resolver must strip leading hesitation vowels (`আ-`, `এ-`) + suffix markers (`-ের`, `-কে`, `-রে`, `-টারে`, `-টাকে`), then Levenshtein ≤ 2 fallback before asking to disambiguate.

### MVP Intent Set (retrain BanglaBERT)
`REMIND_CREATE`, `REMIND_LIST`, `REMIND_CANCEL`, `REMIND_DONE`, `REMIND_SNOOZE`, `SCAN_SEND_PDF`, `SEND_IMAGES`, `CONFIRM_YES`, `CONFIRM_NO`, `UNKNOWN`.

---

## 10. Build Plan (8 Weeks)

### Week 1–2 — Rust Core & Voice In
- [ ] Create Rust workspace (crates in §4).
- [ ] Wrap sherpa-onnx streaming ASR; log per-stage latency.
- [ ] **Collect Rajshahi audio:** 20–30 speakers × ~100 command sentences.
- [ ] Measure WER on Rajshahi vs standard; fine-tune on Kaggle if bad.
- [ ] Keyword fast path for MVP intents.
- [ ] Milestone: `cli` turns `remind.wav` → printed `Action` + timings.

### Week 3–4 — Reminders
- [ ] Write 300+ sentence reminder test set first (TDD), standard + Rajshahi.
- [ ] Bangla time parser + repeat rules (`remind` crate).
- [ ] SQLite store.
- [ ] Kotlin: `AlarmManager`, full-screen alert, voice done/snooze.
- [ ] Retrain BanglaBERT on MVP intents.

### Week 5–6 — Scan & Send
- [ ] Share-target activity (`ACTION_SEND_MULTIPLE`).
- [ ] `scan` crate: edge detect, warp, enhance, PDF.
- [ ] Port kinship resolver + file pinpointer to Rust.
- [ ] WhatsApp handoff + confirm step.
- [ ] Floating bubble overlay.

### Week 7 — Voice Out & Speed
- [ ] Write ~60 reply phrases; render with premium TTS; bundle.
- [ ] Slot clips: numbers, times, day-parts, kinship words.
- [ ] Benchmark on 3 GB RAM phone; hit < 550 ms end-to-end.

### Week 8 — Real Users
- [ ] 5–10 testers: Rajshahi elders + busy users.
- [ ] Observe without helping; log every confusion; fix.

### Start Today
1. Write the reminder phrase test set (drives parser, intents, hotwords).
2. Recruit Rajshahi speakers (slowest dependency).
3. `cargo new` workspace; get wav → intent in CLI.

---

## 11. v2 Roadmap

1. **bKash/Nagad scam guard** — passive SMS rules + family alert.
2. **8-division accent ASR** — accent-robust deep model.
3. **Accent-matched talk-back** — per-division clip banks.
4. **Read-it-for-me** — offline Bangla OCR, printed prescriptions → auto medicine reminders.
5. **Fact-check bridge** — PII-scrubbed cloud verification with offline advisory fallback.
6. **Screen-on wake word.**
