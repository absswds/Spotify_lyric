# Attribution, Content Sources, and Compliance

> **Scope:** This document explains what the repository license covers, what external content/services the app can access, and the publication risks that remain. It is practical project documentation, **not legal advice**.

## 1. Repository license vs. media rights

This repository is licensed under [Apache-2.0](../LICENSE). That license applies only to original source code, project configuration, and documentation contributed to this repository.

It does **not** grant a license to redistribute, republish, sublicense, train on, mirror, sell, or commercially exploit any of the following:

- song lyrics or translated lyrics;
- music metadata returned by third-party services;
- album artwork;
- Spotify content, trademarks, or user data; or
- content returned by LRCLIB, NetEase Cloud Music, QQ Music, or another provider.

The application stores data locally on the user's device for its cache feature. This repository must not contain cached lyrics, sample lyric collections, album-art archives, user listening history, credentials, tokens, or a hosted lyrics database.

## 2. External services and risk boundaries

| Service / component | Purpose in the app | Publication boundary |
|---|---|---|
| Spotify Android SDK and Web API | Reads playback state, sends player commands, and reads playlist metadata after the user authorizes their own account | Follow the current [Spotify Developer Terms](https://developer.spotify.com/terms) and [Spotify Design & Branding Guidelines](https://developer.spotify.com/documentation/design). Do not represent the project as endorsed by, affiliated with, or replacing Spotify. |
| LRCLIB | Optional on-device lookup for synchronized lyrics | Check LRCLIB's current API policy and terms before distributing an APK or commercializing the project. Do not mirror or bulk-export returned lyric data. |
| NetEase Cloud Music adapter | Optional on-device lyric lookup | The implementation may rely on endpoints that are undocumented and may change. Use only where permitted by the provider's terms and applicable law. Disable/remove it for public releases if that cannot be established. |
| QQ Music adapter | Optional on-device lyric lookup | The same boundary applies: availability is not authorization. Do not run a proxy, mirror, downloader, public API, or central cache for QQ Music content. |
| Google ML Kit | On-device language identification and translation | A translation does not remove the copyright or contractual restrictions applicable to the source lyric. |

## 3. Explicitly out of scope

The project must not add features that:

1. modify Spotify, scrape Spotify's encrypted/offline cache, or call private Spotify lyric APIs;
2. host, proxy, bulk-download, export, or redistribute third-party lyrics or artwork;
3. provide a public lyric search/streaming service backed by the adapters;
4. bundle a copied lyric catalogue, a user playback-history dump, or cached artwork in an APK/release; or
5. claim that third-party service data is licensed under Apache-2.0 merely because this repository's code is Apache-2.0.

## 4. Third-party code, protocol references, and dependencies

### Reference implementation

- **[Lyricify-Lyrics-Helper](https://github.com/WXRIW/Lyricify-Lyrics-Helper)** — consulted as a protocol/reference implementation while creating the NetEase adapter. Its repository is Apache-2.0. This project does not vendor its source code or lyric data; the source-level reference is documented in `NeteaseLyricsSource.kt`.

### Runtime/build dependencies

The dependency catalog is in [`gradle/libs.versions.toml`](../gradle/libs.versions.toml). Notable dependencies include the Spotify Android SDK/Auth SDK, AndroidX, Jetpack Compose, Room, WorkManager, Coil, Gson, OkHttp, Retrofit, and Google ML Kit. Each dependency retains its own license and notice requirements.

## 5. Release checklist

Before creating a public release, APK, app-store listing, paid offering, or backend service:

- [ ] Re-check the current terms/policies for Spotify, LRCLIB, NetEase Cloud Music, QQ Music, Google ML Kit, and the target distribution platform.
- [ ] Obtain legal review if distributing beyond personal/private use, especially in jurisdictions where lyrics licensing is regulated.
- [ ] Confirm no `local.properties`, signing keys, OAuth tokens, user database, lyric cache, album-art cache, screenshots containing personal data, or developer workspaces are tracked.
- [ ] Confirm the app and listing do not claim Spotify endorsement or imply that Spotify content/lyrics are licensed by this repository.
- [ ] Confirm no server, mirror, proxy, bulk-export, or public API is enabled for external lyric content.
- [ ] Keep provider attribution visible in the app when a provider supplies the selected lyric candidate.

### Public-repository history notes

- The reachable Git history was checked for `local.properties`, common secret-file names, credential-like token values, APKs, local caches, and developer workspaces. No committed `local.properties`, signing material, OAuth token value, cached lyrics, album-art archive, or generated APK was found.
- Commit author identity (`absswds <binbinli64@gmail.com>`) is intentionally retained: the repository owner has confirmed that this address is their public GitHub email.
- Internal `.hermes` backups/plans and `PlaybackScreen.kt.bak` were removed from the current branch and are now ignored. They still exist in older reachable commits; removing them from Git history would require a separate force-push history rewrite, which is intentionally not performed by this documentation update.

## 6. Reporting concerns

If you believe a file or feature creates an intellectual-property, terms-of-service, privacy, or attribution issue, open an issue without pasting copyrighted lyric text, private tokens, or personal listening data.
