# Vocaloid List · BiliBoard Android

English | **[中文](README.zh.md)**

An Android client for [biliboard.uk](https://biliboard.uk) — the Biliboard Vocaloid (術力口) weekly chart.
Built with Jetpack Compose and a Liquid Glass look.

The app ships with a bundled snapshot of the chart data, so it opens instantly and works offline.
It refreshes **incrementally once every 3 days**.

> Package `za.bilbd.app` · version `1.145` · minSdk 26 · targetSdk 37

---

## Features

| | |
|---|---|
| **Charts** | Every board and issue, with per-song movement, rank history and score |
| **Search** | Full-text search over the whole catalog (14k+ songs) |
| **Favorites** | Star songs locally, export/import as JSON |
| **Producer pages** | Songs grouped by producer, follow producers, avatars fetched from Bilibili |
| **AI song notes** | Explain what a song is about — optional web search + an OpenAI-compatible LLM |
| **AI chart analysis** | Summarise an issue's trends |
| **Follow-up Q&A** | Ask questions after an analysis; answers stay grounded in the same material |
| **Built-in update cycle** | Manual / daily at 18:00 / every N days, with a visible countdown |
| **Liquid Glass UI** | Translucent surfaces derived from the theme, non-linear motion |
| **Cover-themed background** | Song detail tints its backdrop from the cover's dominant colour |
| **Adaptive layout** | Phone / tablet / wide layouts, two-column song detail on tablets |
| **App icon** | Six preset icon palettes, or pin a custom image shortcut |
| **OOBE** | Four-page first-run guide |

The UI is **Chinese-only** at the moment. English support was implemented and then removed at the
user's request — the resource/plumbing is gone, not just hidden.

---

## Environment Setup

Cloning the repo is **not enough to build**. Two things are deliberately excluded: the JDK/Gradle
binaries (`.tools/`, listed in `.gitignore`) and `local.properties` (it pins a machine-specific `sdk.dir`).

| Requirement | Version | Note |
|---|---|---|
| JDK | **21** (Temurin 21.0.12+) | A `java -version` of 1.8 fails outright |
| Gradle | **9.8.1** | Anything below 9.x does not support AGP 9 |
| Android SDK | **compileSdk 37** (`android-37.1`), **Build-Tools 37.0.0** | `compileSdkMinor = 1` is required alongside `compileSdk = 37` |
| AGP | 9.4.1 | **AGP 9 ships built-in Kotlin** — do *not* apply `org.jetbrains.kotlin.android`, it will fail |
| Kotlin Compose plugin | 2.4.21 | Only `org.jetbrains.kotlin.plugin.compose` is used |

### Steps

1. Install JDK 21 and Gradle 9.8.1.
2. Create a `.tools/` folder **next to** the project, laid out the way `build.ps1` expects:

   ```
   .tools\jdk21\jdk-21.x.y\bin\java.exe
   .tools\gradle-9.8.1\gradle-9.8.1\bin\gradle.bat
   ```

   …or edit `$JdkHome` / `$GradleBin` in `build.ps1` to point at your own installation.
3. Create `local.properties` containing `sdk.dir=<path to your Android SDK>`.
4. Build (see below).

### Build commands

```powershell
# debug APK
powershell -File build.ps1 -Task ":app:assembleDebug"

# unit tests (219 tests: plain JVM + Robolectric)
powershell -File build.ps1 -Task ":app:testDebugUnitTest"

# release APK — runs the tests first; a failing test means no APK
powershell -File build.ps1 -Task ":app:assembleRelease"
```

`build.ps1` redirects `JAVA_HOME`, `GRADLE_USER_HOME`, `TMP` and `ANDROID_USER_HOME` into the project,
so it never depends on system temp directories.

### Other scripts

| Script | Purpose |
|---|---|
| `run.ps1` | Launch the installed debug build and wait until it is up |
| `tap.ps1` | Find a widget by its text and tap it — used for UI automation (`-Text/-Wait/-List/-Back/-Scroll`) |

> All `.ps1` files are **ASCII-only on purpose**: PowerShell 5.1 reads a BOM-less script as GBK, and
> non-ASCII comments have broken them before.

---

## Project structure

```
app/src/main/java/za/bilbd/app/
├── MainActivity.kt              entry point; picks the theme from settings
├── data/                        everything that is not UI
│   ├── BoardApi / BoardRepository    snapshot fetch + incremental update
│   ├── Parsers / Model               JSON parsing and domain models
│   ├── LiveStore / SettingsStore     local preferences
│   ├── FavoritesStore / FavoritesCodec   favorites + export/import format
│   ├── FollowStore / ProducerIndex   producer follows + inverted index
│   ├── BiliApi / BiliEnricher        Bilibili metadata (plays, covers, avatars)
│   ├── DeepSeekApi / AiPrompt        LLM client and prompt building
│   ├── AiProvider                    provider presets (DeepSeek/OpenAI/Qwen/custom)
│   ├── DoubaoSearchApi               optional web search
│   └── AppLog                        in-app log, exportable as txt
└── ui/                          Compose screens
    ├── AppRoot                     screen stack, navigation, transitions
    ├── AppViewModel                all state and actions
    ├── Glass / Motion              Liquid Glass surfaces, non-linear animation
    ├── Common / Widgets            shared components (CoverImage lives here)
    ├── AiCard / AiChat             AI result card and follow-up chat
    └── *Screen.kt                  one file per screen
app/src/main/assets/             bundled snapshot + search catalog (~4.4 MB)
app/src/test/                    219 unit tests
tools/                           Node scripts that regenerate the bundled data
```

---

## Testing

There are **219 unit tests**, in two layers:

1. **Plain JVM tests** — parsers, search, prompt building, favourites codec, provider presets, time
   calculations. No Android runtime needed.
2. **Robolectric tests** — render real Compose screens on the JVM (song detail layout, AI card,
   navigation chrome). This catches layout regressions without an emulator.

Three notes that cost real debugging time:

- Robolectric writes its download lock to `user.home`; the Gradle `Test` task redirects it into
  `app/build/test-home`, otherwise it fails on machines where the home directory is not writable.
- `compileSdk 37` has no matching `android-all` artifact, so `robolectric.properties` pins `sdk=34`.
- In Compose tests `setContent` may only be called once per test case — write one test per state.

---

## Build-time guards

Some classes of bug are cheap to make and expensive to notice, so they are enforced **by the build**,
not by convention. A guard task fails the build; it does not warn.

| Guard | What it prevents |
|---|---|
| `guardGlassTheme` | Hard-coded `dark = true/false` in `ui/` — glass brightness must follow the theme |
| `assembleRelease → testDebugUnitTest` | Shipping a release with failing tests |

Each guard was verified with a deliberate failure probe before being trusted.

---

## Data pipeline

`tools/` holds Node scripts that turn the upstream chart data into the two bundled assets:

| Asset | Contents |
|---|---|
| `biliboard_snapshot.json` (2.3 MB) | boards, issues, rankings |
| `catalog.json` (2.2 MB) | every song ever recorded, with producer and date |

At runtime `BoardRepository` treats the snapshot as a baseline and applies incremental updates on top.

---

## AI features

Everything is **bring-your-own-key**. No key ships with the app, and nothing is sent anywhere except
to the AI provider the user chose.

- **Providers**: DeepSeek, OpenAI, Qwen (DashScope compatible mode) and a custom base URL.
  All of them speak the OpenAI `/chat/completions` shape, so only the base URL and model name differ.
- **Keys are stored per provider** — switching DeepSeek → OpenAI → back does not lose either key.
- **Optional web search** via Doubao (Volcengine) pulls lyrics and background before the model writes.
- **Follow-up chat**: the prompt that produced the analysis is cached and reused as the system message,
  so follow-up answers stay grounded in the same material instead of drifting.
- **Persona mode**: an optional toggle that changes tone only — the accuracy constraints stay in the prompt.
- **In-app log**: operations are logged locally and can be exported as a `.txt` from Settings.

---

## Data sources

- Chart data: [biliboard.uk](https://biliboard.uk)
- Bilibili metadata (plays, covers, uploader avatars): public Bilibili web endpoints
- This is an unofficial client. All chart data and artwork belong to their original owners.

---

## License

**MIT** — see [LICENSE](LICENSE).

```
Copyright (c) 2026 Vocaloid List contributors
```

Note that the licence covers **the source code in this repository only**. Chart data comes from
[biliboard.uk](https://biliboard.uk) and artwork/avatars come from Bilibili; those remain the
property of their original owners and are not relicensed here.
