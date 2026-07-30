# Spotify Lyrics Proxy

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://developer.android.com/about/versions/oreo)
[![Spotify](https://img.shields.io/badge/Spotify-Android%20Remote-green.svg)](https://developer.spotify.com/documentation/android)

**Reads Spotify playback state and displays synchronized lyrics on system notifications and media cards — no root required.**

**English** · [简体中文](README-zh.md) · [繁體中文](README-zh-TW.md) · [日本語](README-ja.md)

[Quick Start](#quick-start) · [Features](#features) · [Architecture](#architecture) · [Content Sources & Compliance](#content-sources-references-and-compliance) · [Disclaimer](#disclaimer)

---

## News

- **2026-07** — Project initialized. Core pipeline: App Remote connection, LRCLIB lyrics fetch, Room cache, MediaSession display, notification, playlist precache, lyrics correction.
- **2026-07** — Added immersive playback UI, a right-side lyric settings drawer, portrait/landscape layouts, and display controls for font size, weight, alignment, and inactive-line blur.
- **2026-07** — Added online lyrics sources: NetEase Cloud Music, QQ Music, and LRCLIB; results are searched concurrently, scored against Spotify metadata, and can be manually switched in Lyrics Correction.
- **2026-07** — Added Simplified Chinese, Traditional Chinese, English, and Japanese interface localization, plus Simplified/Traditional conversion while lyric translation is enabled.

---

## Overview

Spotify Lyrics Proxy is an Android application that bridges Spotify's playback state with third-party lyrics sources. It does not play audio, does not modify the Spotify APK, and does not call private Spotify APIs.

The app reads the currently playing track via Spotify App Remote, queries local cache and compatible third-party sources for synchronized lyrics, and renders the current line on:

- System notification (foreground service)
- MediaSession media card (lock screen, control center)

---

## Features

### Playback Synchronization

| Feature | |
|---------|-|
| Play state | Read current track, playback position, and play/pause state from Spotify |
| Controls | Forward play/pause/skip/seekTo commands to Spotify |
| Auto-detection | Reacts to track changes and playback state transitions |

### Lyrics Display

| Feature | |
|---------|-|
| Notification | Foreground service notification showing the current lyric line |
| MediaSession | Media card with synchronized lyrics on lock screen and control center |
| Customization | Immersive portrait/landscape layouts; adjustable font size, current-line weight, inactive-line blur, dimming, and alignment |
| Localization | Simplified Chinese, Traditional Chinese, English, and Japanese interface options |

### Caching

| Feature | |
|---------|-|
| Auto-cache | First playback searches compatible third-party sources and persists accepted lyrics to Room |
| Playlist precache | Background WorkManager task precaches lyrics for selected playlists (Wi-Fi / charging) |
| Offline | Cached lyrics available without network |

### Lyrics Management

| Feature | |
|---------|-|
| Correction | Inspect candidates with their provider, select a preferred match, mark incorrect matches, and re-search |
| Offset | Adjust lyric timing forward or backward |
| Import | Load local `.lrc` files via system file picker |
| Manual override | Manually imported lyrics always take priority over online results |
| Cleanup | Clear lyric cache and album art cache |

### Lyrics Translation

| Feature | |
|---------|-|
| Auto-detect | Detects lyrics language and translates on the fly |
| Target selector | Choose Simplified Chinese, Traditional Chinese, English, or Japanese |
| Chinese conversion | Convert original lyric text between Simplified and Traditional Chinese while translation is enabled |
| ML Kit | On-device translation engine, no network required after model download |

### UI

| Feature | |
|---------|-|
| Design | Material 3 with dynamic album-art theming |
| Theme | System / Light / Dark mode |
| Language | Chinese, English, and Japanese |

---

## Quick Start

### Prerequisites

- **JDK 17** or later
- **Android SDK** (API 26+)
- **Spotify App** installed on your device and logged into a Spotify account
- A **Spotify Client ID** from the [Developer Dashboard](https://developer.spotify.com/dashboard)

### Obtain a Client ID

This app connects to Spotify using Spotify's Android SDK, which requires a registered application on the Spotify Developer Dashboard.

1. Go to [Spotify Developer Dashboard](https://developer.spotify.com/dashboard) and log in with your Spotify account
2. Click **Create App**
3. Fill in the form as follows:

   | Field | Value |
   |-------|-------|
   | **App name** | Any name you like (e.g. "Lyrics Card") — this is just for your reference |
   | **App description** | e.g. "Personal lyrics display app" |
   | **Website** | Leave empty |
   | **Redirect URIs** | Add exactly: `spotifylyricsproxy://callback` |
   | **Android packages** | Leave empty (not required — see note below) |
   | **iOS app bundles** | Leave empty |
   | **Which API/SDKs are you planning to use?** | Check **Web API** |

   > The **Redirect URI** is the callback URL the app uses to receive the OAuth token after you authorize. It must match **character-for-character** — no trailing slash, no extra spaces.

   > **Android packages** — You do NOT need to add your package name or SHA fingerprint here. This app uses Spotify's Auth SDK (browser-based OAuth), which only requires the redirect URI. The package/SHA fields are only needed if you use Spotify's App Remote SDK with deep links, which this app does not require.

4. Click **Save** at the bottom of the page
5. Copy the **Client ID** from the top of the page (a 32-character hex string, e.g. `81a57006ff4a4d5d96cb72f180aa4ab5`)

> The Client ID is not a secret (it's embedded in the APK), but do not commit it to a public repo.

### Build from Source

Clone the repository:

```bash
git clone https://github.com/absswds/Spotify_lyric.git
cd Spotify_lyric
```

Configure the Client ID:

```bash
cp local.properties.example local.properties
```

Open `local.properties` and set the value you copied from the dashboard:

```properties
# Path to your Android SDK
# Windows: C:\Users\<username>\AppData\Local\Android\Sdk
# macOS:   ~/Library/Android/sdk
# Linux:   ~/Android/Sdk
sdk.dir=C\:\\Users\\YourUser\\AppData\\Local\\Android\\Sdk

spotify.client.id=YOUR_SPOTIFY_CLIENT_ID
```

Build and install:

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

> If you use **Android Studio**, you can also open the project directly — `local.properties` is generated automatically from your SDK settings.

### First Run

1. Open the app
2. Tap **Connect Spotify** — the Spotify App will open for authorization
3. Authorize **playback state access** when prompted
4. Play any track in Spotify — the notification will display the current lyric line

> On **Android 13+**, you need to grant the **notification permission** when prompted, otherwise lyrics won't appear in the notification bar.

### Keep the App Alive in Background

This app runs a foreground service to display lyrics in the notification. Some Android systems (especially OPPO ColorOS, Xiaomi MIUI, Huawei HarmonyOS, vivo OriginOS) aggressively kill background apps to save battery. If the notification disappears after a while, you need to manually allow the app to run in the background:

1. **Disable battery optimization** — Settings → Battery → Battery Optimization → Find this app → Select "Don't optimize"
2. **Enable auto-start** (if available) — Settings → Apps → Manage Apps → Find this app → Enable "Auto-start"
3. **Lock the app in recent tasks** — Open recent tasks, swipe down on this app's card to lock it (prevents system from killing it)

> Without these settings, the system may kill the foreground service and lyrics will stop updating.

### Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| `MISSING_CLIENT_ID` in logs | `local.properties` not configured | Set `spotify.client.id` |
| Gradle build fails with JDK error | JDK 17+ not installed | Install JDK 17, set `JAVA_HOME` |
| "Spotify not installed" | Spotify App missing | Install Spotify from Play Store |
| "Connection failed" on tap | Spotify not logged in | Log in to the Spotify App first |
| Lyrics not showing in notification | Notification permission denied | Grant permission in Settings → Notifications |
| Auth redirect closes immediately | Wrong redirect URI in Dashboard | Verify `spotifylyricsproxy://callback` is set |
| Notification disappears after a while | System killed the background service | Disable battery optimization, enable auto-start, lock in recent tasks (see [Keep the App Alive](#keep-the-app-alive-in-background)) |

---

## Architecture

```
app/src/main/java/com/example/spotifylyricsproxy/
├── core/model/          Data models
├── database/            Room database and DAOs
├── lyrics/              Lyrics fetching, parsing, matching, sync
│   ├── lrclib/         LRCLIB source
│   ├── netease/        NetEase Cloud Music source
│   └── qqmusic/        QQ Music source
├── mediasession/        MediaSession controls
├── notification/        Foreground service notification
├── playback/clock/      Playback position estimation
├── spotify/             Spotify integration
│   ├── remote/         App Remote connection and state subscription
│   └── webapi/         Web API (OAuth, playlists)
├── ui/                  Compose screens
│   ├── cache/          Cache management
│   ├── playback/       Playback screen and ViewModel
│   ├── playlist/       Playlist precache
│   ├── precache/       Precache management
│   ├── settings/       Settings screen
│   └── theme/          Theme and locale configuration
└── worker/              WorkManager background tasks
```

### Data Flow

```
Spotify PlayerState
  → SpotifyRemoteRepository
  → LyricsRepository
  → Room cache / LRCLIB
  → LrcParser
  → PlaybackClock
  → LyricSyncEngine
  → Notification / MediaSession / UI
```

### Technology Stack

| Component | |
|-----------|-|
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Repository Pattern |
| Database | Room (SQLite) |
| Networking | Retrofit + OkHttp |
| Async | Kotlin Coroutines + Flow |
| Background | WorkManager |
| Image Loading | Coil |
| Lyrics Translation | ML Kit (on-device, offline) |
| Spotify Integration | Spotify Android SDK (App Remote + Auth) |

---

## Content Sources, References, and Compliance

### What this repository includes

- Original application code, configuration, and documentation released under the repository's [Apache-2.0 license](LICENSE).
- A source adapter interface and optional adapters for LRCLIB, NetEase Cloud Music, and QQ Music. The adapters search and render lyrics only on the user's device; this repository does **not** include a lyrics corpus, track metadata dump, album-art archive, or hosted lyrics API.
- Spotify integration through the official Spotify Android SDK / Web API dependencies declared in `gradle/libs.versions.toml`.

### What it does **not** grant

The Apache-2.0 license covers this repository's code only. It does **not** grant any right to redistribute, publish, sublicense, train on, mirror, or commercially exploit lyrics, translations, album artwork, Spotify content, or metadata returned by third-party services.

### Third-party sources and legal risk

| Item | How the app uses it | Important limit |
|------|---------------------|-----------------|
| Spotify Android SDK / Web API | Reads playback state, controls playback, obtains playlist metadata after user authorization | Spotify remains the playback provider. Follow the [Spotify Developer Terms](https://developer.spotify.com/terms) and [design guidelines](https://developer.spotify.com/documentation/design). Do not imply Spotify endorsement. |
| LRCLIB | Optional on-device synchronized-lyrics lookup | Check LRCLIB's current terms and API policy before distribution or commercial use. |
| NetEase Cloud Music / QQ Music | Optional on-device lookup adapters, based on endpoints that may be undocumented or change | Availability and authorization are not guaranteed. These adapters are provided for personal interoperability experiments only; they should be disabled or removed if the provider's terms, copyright rules, or local law prohibit the use. Do not operate a proxy, mirror, bulk downloader, public lyrics API, or prebuilt lyric database. |
| ML Kit Translation | On-device lyric-language detection and translation | Translation does not remove underlying lyrics copyright restrictions. |

**Before publishing an APK, app-store listing, paid product, server feature, or public hosted service:** obtain legal review and re-check the current terms of every provider and the law applicable to your jurisdiction. The maintainers make no claim that any optional third-party lyrics adapter is suitable for public distribution.

### Reference implementations and attribution

See [Attribution, Content Sources, and Compliance](docs/ATTRIBUTION_AND_COMPLIANCE.md) for the detailed source-by-source boundary, dependency attribution, and public-release checklist.

- [Lyricify-Lyrics-Helper](https://github.com/WXRIW/Lyricify-Lyrics-Helper) — consulted as a protocol/reference implementation for the NetEase lyrics adapter. Its repository is Apache-2.0; this project does not copy its lyric data or bundle its code.
- Spotify Android SDK/Auth SDK, AndroidX, Jetpack Compose, Room, WorkManager, Coil, OkHttp, Retrofit, Gson, and Google ML Kit — declared dependencies; their respective licenses and notices remain applicable.

---

## Disclaimer

**This project is not affiliated with Spotify AB and is not an official Spotify product.**

- This application does not play audio or replace the Spotify player
- All audio playback remains handled by the Spotify App
- This application only reads playback state and displays lyrics
- Lyrics are sourced from compatible third-party services or user-provided local files; provider terms and copyright restrictions remain applicable
- This repository license covers only this project's code, not externally sourced lyrics, artwork, or metadata
- The app is intended for personal on-device use and must not be used to host, mirror, bulk-export, or redistribute third-party lyrics or artwork
- This application does not call private Spotify APIs or modify the Spotify APK
- This application does not collect, store, or transmit user listening history outside the user's device
- Users must comply with Spotify Developer Terms, provider terms, and applicable copyright law

---

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## License

This project is licensed under the [Apache License 2.0](LICENSE).
