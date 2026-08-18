# WardLog — Bed Records App

A simple Android app for maintaining ward/bed records in a spreadsheet-style
table (Bed No. / Patient Name / Primary Consultant / Details), with search,
voice input, and PDF export/print. All data stays on the phone — nothing is
sent to the internet.

## Features

- **Spreadsheet-style table** — scrollable rows and columns (Bed No., Patient
  Name, Primary Consultant, Details), stored locally in an on-device SQLite
  database (via Room). Tap a row to edit it, long-press to delete.
- **Search** — the search box filters across all four columns instantly.
- **Sorted by save time** — the newest record always appears at the top,
  based on when it was actually saved, regardless of the order fields were
  filled in or spoken.
- **Voice input** — tap the mic in the add/edit dialog and speak the record.
  Recording keeps going until you tap **Stop Recording** — it does *not*
  auto-stop after a pause. The app then splits your speech into Bed No. /
  Patient Name / Primary Consultant / Details by spotting cue words ("bed",
  "patient", "consultant"/"doctor"/"dr"), and:
  - **Order doesn't matter** — say patient name before bed number, or any
    order at all, and each field still lands correctly, since fields are
    matched by *where* each cue word appears in the sentence, not by which
    one you say first.
  - **Saying "details" hands over control** — once you say "details" (or
    "notes"), everything from that word onward is captured as one Details
    blob and is *not* re-scanned for bed/patient/consultant cues, even if it
    contains words like "bed" or "patient" in passing.
  - **Ward vocabulary correction** — a built-in list of ~60 procedures,
    conditions, and abbreviations (OGD scopy, ERCP, MRCP, NBM, RL, Loose
    stools, Cholecystectomy, etc.) plus your team's consultant names (Dr
    Yatin Sagwekar, Dr Chaitanya, Dr Bharat, Dr Poonam, Dr Sonali Gautam, Dr
    Dipak Bhangale) is used two ways: it's fed to the speech recognizer as a
    hint so it's more likely to hear these terms correctly in the first
    place, and it's used again afterward to fuzzy-correct anything the
    recognizer still got wrong or misspelled.
  - **Review before saving** — this is still a rule-based matcher and fuzzy
    corrector, not a true AI/language model, so unusual phrasing may not
    split perfectly. Always glance over the filled fields before hitting
    Save.
- **PDF export & print** — generates a table-formatted PDF of whatever is
  currently visible (i.e. respects your search filter) and either opens it to
  share/save, or sends it straight to Android's Print dialog (which also lets
  you "Save as PDF" or send to a real printer/print service).

## A note on patient data

This app stores information locally in the phone's app-private database and
never makes a network call. Still, since it holds real patient details (bed
number, name, consultant), please treat the phone itself as sensitive:

- Use a phone with a lock screen / passcode.
- Follow your hospital's/institution's own policy on where patient data may
  be recorded on personal devices (this varies by country and by hospital).
- If you ever want to swap the voice parser for a smarter AI model (e.g. via
  an API), remember that would mean sending patient data off the device —
  that needs its own privacy review before turning on.

## Project structure

```
WardLog/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/wardlog/app/      # Kotlin source
│       └── res/                       # layouts, strings, colors, icon
├── build.gradle
├── settings.gradle
├── gradle.properties
└── .github/workflows/build-apk.yml    # builds the APK automatically
```

## Option A — Build the APK with GitHub (no Android Studio needed)

1. Create a new **empty** repository on GitHub (don't add a README there).
2. Unzip this project and push it to that repo:
   ```
   cd WardLog
   git init
   git add .
   git commit -m "Initial WardLog app"
   git branch -M main
   git remote add origin https://github.com/<your-username>/<your-repo>.git
   git push -u origin main
   ```
3. On GitHub, open the **Actions** tab of your repo. The "Build APK" workflow
   runs automatically on push (you can also trigger it manually with the
   "Run workflow" button).
4. When it finishes (usually 2–4 minutes), open the completed run and scroll
   to **Artifacts** — download **WardLog-debug-apk**. It's a zip containing
   `app-debug.apk`.
5. Transfer `app-debug.apk` to your Android phone (email it to yourself,
   Google Drive, USB, WhatsApp — anything works) and open it on the phone to
   install it.
6. Android will likely warn about installing from an unknown source. Go to
   **Settings → Apps → Special access → Install unknown apps**, allow it for
   whichever app you used to open the file (e.g. Files, Chrome, Gmail), then
   tap the APK again to install.

This is a **debug build**, which is fine for personal/internal use. If you
ever want to publish it on the Play Store, it would need to be signed as a
"release" build with your own signing key — ask if you'd like help with that
later.

## Option B — Build locally with Android Studio

1. Install [Android Studio](https://developer.android.com/studio).
2. Open the `WardLog` folder as a project (File → Open).
3. Let Gradle sync (it will download dependencies the first time).
4. Click **Run ▶** with a phone connected (USB debugging on) or an emulator,
   or use **Build → Build Bundle(s)/APK(s) → Build APK(s)** to just produce
   the APK file (found under `app/build/outputs/apk/debug/`).

## Requirements

- Android 7.0 (API 24) or newer on the phone.
- Microphone permission (requested the first time you tap the voice-input
  button) for the voice-fill feature.

## Managing the dictionary (no rebuild needed)

Tap the pencil/edit icon next to the search bar to open **Dictionary**. From
there you can:

- **Add a medical term**: enter what the recognizer tends to hear it as
  (e.g. "colon oscopy") and the correct spelling (e.g. "Colonoscopy").
- **Add a doctor**: enter their full name without "Dr" — voice input will
  fuzzy-match whatever the recognizer hears after "dr"/"doctor" against every
  name in this list and snap it to the correct spelling.
- **Edit or remove** any entry by tapping it (edit) or long-pressing it
  (remove).

Changes save straight to the on-device database and apply to voice input
immediately — no need to rebuild or reinstall the app. The app ships with
~50 built-in ward terms and your 6 consultants already pre-loaded the first
time it runs; after that, everything is fully user-editable.

## Customizing

- **Colors/branding**: `app/src/main/res/values/colors.xml` (all premium
  palette tokens — primary teal, gold accent, surfaces — live here) and
  `app/src/main/res/drawable/ic_launcher.xml`.
- **Column widths**: `app/src/main/res/values/themes.xml` (header styles
  `ColBed`/`ColName`/`ColConsultant`/`ColDetails`, row styles
  `CellBed`/`CellName`/`CellConsultant`/`CellDetails`).
- **Voice keyword matching**: `app/src/main/java/com/wardlog/app/VoiceParser.kt`
  — add more cue words there (e.g. local language terms, abbreviations your
  ward uses) to improve auto-fill accuracy.
- **Medical terms & doctor names**: use the in-app **Dictionary** screen
  described above — no code changes needed. `MedicalTermCorrector.kt` only
  holds the built-in *default* list used to pre-populate the dictionary the
  first time the app runs; after that, the database is the source of truth.
- **Extra columns**: add a field to `Record.kt`, add a column to the table
  layouts (`activity_main.xml`, `item_record.xml`), add an input to
  `dialog_add_record.xml`, and wire it up in `MainActivity.kt` — happy to
  help with this if you want a specific column added.
