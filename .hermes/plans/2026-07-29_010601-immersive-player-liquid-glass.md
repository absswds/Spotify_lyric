# Immersive Playback UI and Selective Liquid Glass Implementation Plan

> **For Hermes:** Implement this plan task-by-task. This is a visual/UI change. Do not alter Spotify integration, lyric matching, Room schema, notification behavior, or playback command semantics.

**Goal:** Redesign the portrait playback homepage into an original immersive, album-art-driven lyric view: artwork-derived background, compact title-row controls, fixed-anchor lyrics with uniform out-of-focus lines, and a short progress indicator that expands into a liquid-glass seek control while dragging.

**Architecture:** Keep the existing `PlaybackViewModel` state and its `seekTo`, `togglePlayPause`, `skipNext`, and `skipPrevious` commands. Replace only the portrait branch composition in `PlaybackScreen.kt`, extracting small presentation composables where needed. Add a narrowly scoped liquid-glass surface primitive for transient/floating controls; do not make every page or card transparent.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Coil, Android minSdk 26 / targetSdk 35. Existing app uses Compose BOM and Material Icons. Runtime glass must degrade gracefully below Android 12 / API 31.

---

## Non-negotiable Visual Contract

Reference intent is the two supplied screenshots, interpreted as original UI principles rather than a pixel-for-pixel clone.

1. **Portrait homepage structure**
   - Top: full-width album artwork region, roughly 30–36% of the portrait screen height. It is background artwork, not a small square card.
   - The lower edge of the artwork dissolves into the same muted dominant color extracted from that artwork. The lower screen must **not** turn black by default.
   - Directly below the transition: song title and artist on the left; compact previous, play/pause, next controls on the right. No favorite/heart control on this row.
   - Title and artist are intentionally more legible/larger than the old compact layout. Starting target: title `24.sp`, artist `16.sp`, with ellipsis protection.
   - Give the title/artist row a deliberate 24–28dp gap before the lyrics viewport.

2. **Lyrics behavior and appearance**
   - The lyrics list gets its own clipped viewport below the metadata row.
   - Current lyric is anchored at a stable location inside that viewport. When lyrics advance, the list moves under that anchor; a late lyric must never slide up next to, or overlap with, the artist label.
   - The active line is clear white, bold, and larger.
   - **Every non-current line uses the same visual style. Do not use distance-based progressive blur, opacity, font size, or color.** Use one constant muted white alpha, one constant font size, and one constant blur radius for all inactive lines, regardless of whether they are above or below the current line.
   - On the current-line change, animate the new active line character by character: opacity 0 → 1, `scale(0.76f) → 1.06f → 1f`, small upward translation, approximately 25–35ms stagger. No spring/bounce loop and respect reduced-motion settings.
   - Tapping a lyric still seeks to that lyric’s timestamp.

3. **Progress control**
   - Idle: centered 92dp-wide, 3dp-tall progress line with no thumb and no time text.
   - While the user presses, drags, keyboard-focuses, or scrubs: expand into a full-width horizontal pill. The pill shows elapsed time, seek bar, and duration. It collapses approximately 650ms after release/blur.
   - Expanded state gets the project’s only strong liquid-glass treatment: translucent fill, 1dp light edge, top highlight, subtle inner shadow, backdrop blur/color treatment on supported devices. It remains usable and readable without real blur on API 26–30.

4. **Liquid glass posture**
   - Treat it as a transient material for controls floating above album-color backgrounds, not as a global skin.
   - No rainbow/chromatic aberration, exaggerated refraction, thick white borders, or large glass cards.
   - Stronger glass: expanded seek pill, possibly active bottom navigation container.
   - Light glass: title-row transport button pressed states and compact overflow/menu surface.
   - No glass: lyrics body, cache result rows, correction forms, settings groups, empty states, normal playlist rows.

---

## Existing Code Context

- Main portrait playback layout: `app/src/main/java/com/example/spotifylyricsproxy/ui/playback/PlaybackScreen.kt:110-307`.
  - It currently builds a gradient background from `albumPalette`, calls `AlbumArtHero`, `TrackTitleBlock`, `CompactLyricsBlock`, and `BottomPlaybackPane`.
  - Relevant existing controls and handlers are already passed through at lines 280–292.
  - `albumPalette` lives in the same file around lines 993–1041 and currently computes an average RGB color. Improve this in place or extract it; do not add a network dependency.
  - `ExpandedLyricsView` starts around line 1044 and already uses a `LazyColumn` plus `onSeek`; preserve its functional behavior while sharing the lyric style rules where feasible.

- Navigation and current bottom bar: `app/src/main/java/com/example/spotifylyricsproxy/ui/navigation/AppNavigation.kt:198-289`.
  - The bar already has a dark translucent container. This is a suitable optional use of the shared subtle liquid-glass primitive.

- Non-playback pages are operational, not immersive:
  - Playlist browser: `ui/playlist/PlaylistScreen.kt`.
  - Cache list: `ui/cache/CacheScreen.kt`.
  - Playlist precache: `ui/precache/PrecacheScreen.kt`.
  - Settings: `ui/settings/SettingsScreen.kt`.
  - Lyrics correction: `ui/playback/LyricsCorrectionScreen.kt`.

- Project requirements and UI constraints: `AGENTS.md:22-30`, `AGENTS.md:71-81`.
  - Read `docs/SPOTIFY_LYRICS_PROXY_PLAN.md` before implementation.
  - Preserve dirty working-tree changes. Check branch/status before edits.
  - Run relevant Gradle tests/checks. UI changes also require OPPO device verification; compile pass is not device verification.

---

## Open-source Research Notes

Use these only as implementation references; do not copy a branded UI wholesale.

- Web reference: `nikdelvin/liquid-glass` is an MIT-style open implementation using CSS/SVG displacement and backdrop-filter. It demonstrates refraction, edge highlights, and fallbacks. It is not directly reusable in Compose, but establishes that a small glass container needs: translucent tint, edge highlight, background sampling/blur, and graceful fallback.
  - Repository: https://github.com/nikdelvin/liquid-glass
- Compose reference candidate: `Kyant0/AndroidLiquidGlass`, a Compose Multiplatform liquid-glass library. Before adding it, the implementer must inspect its license, API/AGP compatibility, minSdk behavior, release cadence, binary size, and whether it works with this exact Compose/Kotlin setup.
  - Candidate: https://github.com/Kyant0/AndroidLiquidGlass
- Default recommendation: **do not add a glass library in the first UI pass.** Build a local `Modifier.subtleGlass(...)` fallback with `Surface`, low-alpha tint, 1dp edge, and shadows. Add API 31+ `RenderEffect`/runtime shader only after it is demonstrated on the actual OPPO device. A third-party rendering library is justified only if it produces a verified material improvement without degraded scrolling, jank, or dependency risk.

---

## Task 1: Establish a Safe UI Baseline

**Objective:** Record the current project state and ensure the implementation works on the actual codebase rather than the HTML study.

**Files:**
- Read: `AGENTS.md`
- Read: `docs/SPOTIFY_LYRICS_PROXY_PLAN.md`
- Inspect: `app/src/main/java/com/example/spotifylyricsproxy/ui/playback/PlaybackScreen.kt`
- Inspect: `app/build.gradle.kts`

**Steps:**
1. Open a new feature branch, e.g. `feature/immersive-playback-ui`, only after preserving any current user changes.
2. Run `git status --short`, capture current branch, and do not discard unrelated files.
3. Run the existing relevant local check. Use `./gradlew test` if it is fast; at a minimum run `./gradlew assembleDebug` before claiming the UI builds.
4. Create a screenshot/emulator baseline of the portrait playback screen if an emulator/device is available.

**Acceptance:** Baseline build result recorded; no behavior changes made yet.

---

## Task 2: Add a Local, Selective Glass Surface Primitive

**Objective:** Provide a reusable material for transient controls without adding a dependency prematurely.

**Files:**
- Create: `app/src/main/java/com/example/spotifylyricsproxy/ui/theme/GlassSurface.kt`
- Optionally modify later: `ui/navigation/AppNavigation.kt:198-289`

**Implementation details:**
1. Create a composable or modifier API such as:
   ```kotlin
   @Composable
   fun GlassSurface(
       modifier: Modifier = Modifier,
       shape: Shape = RoundedCornerShape(999.dp),
       content: @Composable BoxScope.() -> Unit
   )
   ```
2. Baseline appearance for all supported APIs:
   - tinted `Color.White.copy(alpha = 0.10f..0.16f)` surface;
   - 1dp white edge at approximately 0.18–0.24 alpha;
   - one restrained top highlight and inner/dropped dark shadow;
   - no hard card elevation or thick stroke.
3. API 31+ enhancement may apply `RenderEffect.createBlurEffect` only to a deliberately contained backing layer. Do not blur the actual controls or text.
4. Add API 26–30 fallback which is visually coherent but skips background refraction/blur.
5. Do not use a global `CompositionLocal` or force all Material `Card`s through this style.

**Validation:**
- A simple Compose preview or temporary visual test should show text/icons readable in light and dark album palettes.
- Test TalkBack content descriptions and ensure glass does not remove visible pressed/focused feedback.

**Commit:** `feat(ui): add subtle glass surface primitive`

---

## Task 3: Recompose the Portrait Playback Screen

**Objective:** Replace the current card-like portrait arrangement with the immersive album-art-to-palette layout.

**Files:**
- Modify: `app/src/main/java/com/example/spotifylyricsproxy/ui/playback/PlaybackScreen.kt:159-307`
- Modify or replace: helper functions around `AlbumArtHero`, `TrackTitleBlock`, `CompactLyricsBlock`, and `BottomPlaybackPane`.

**Steps:**
1. Keep landscape playback and the existing expanded lyrics route functional; change portrait only first.
2. Replace the 66%-width nested `AlbumArtHero` with a top `Box` that uses album art full width at roughly `0.30f..0.36f` of the usable portrait height.
3. Add a separate blurred/tinted duplicate layer behind/under the artwork and a bottom fade into `albumPalette`’s deep/mid colors.
4. Update palette extraction:
   - retain it locally in `PlaybackScreen.kt` initially;
   - calculate a stable darkened dominant/mid color suitable for text contrast;
   - never use pure black as the default lower background when album art exists;
   - retain a conservative dark fallback when art is unavailable.
5. Create an `ImmersiveTrackHeader`:
   - left: 24sp title and 16sp artist/album, two lines max and ellipsized;
   - right: compact `SkipPrevious`, `Play/Pause`, `SkipNext` icon buttons;
   - remove heart/favorite from this homepage layout;
   - do not show playlist/menu in this row. Keep existing playlist/correction access in the overflow menu until a separately approved interaction redesign.
6. Remove the large lower five-button control strip from portrait mode. Keep shuffle/repeat available in the existing overflow menu or another explicitly designed secondary control path; do not silently delete functionality.
7. Insert 24–28dp vertical space after artist metadata before the lyric viewport begins.

**Validation:**
- Test no-art, long track title, long artist name, disconnected state, connecting state, synced lyrics, no lyrics, plain lyrics, low-confidence lyrics, and imported lyrics states.
- Confirm skip/play still call the existing ViewModel methods exactly once.

**Commit:** `feat(playback): build immersive portrait player layout`

---

## Task 4: Implement Fixed-Anchor Uniform-Blur Lyrics

**Objective:** Match the requested lyric focal behavior without progressive blur levels.

**Files:**
- Modify: `app/src/main/java/com/example/spotifylyricsproxy/ui/playback/PlaybackScreen.kt`
- Potentially create: `app/src/main/java/com/example/spotifylyricsproxy/ui/playback/ImmersiveLyrics.kt`
- Modify tests/create test helper only if a current UI-test setup exists.

**Steps:**
1. Build a dedicated clipped lyric viewport with stable measured height between header and seek control.
2. Use `LazyColumn`/`LazyListState` with an anchor layout such that the active line appears at a constant point within this viewport. A target around 28dp below the viewport top is acceptable; calculate with real item/viewport geometry rather than `index * fixedPx`.
3. Auto-scroll only after the current lyric index changes. Do not let auto-scroll place the active line above the lyrics viewport or next to the artist metadata.
4. Line states must be exactly:
   - `current`: white, bold, ~27sp, no blur;
   - `inactive`: **same constant** muted white alpha, same constant non-current font size, same constant blur radius for every non-current line.
5. Do not make prior/future lines differ. Do not apply a distance ladder or progressively fade into invisibility.
6. On a newly active line, split the `String` into grapheme-safe units where possible; animate each unit with a 25–35ms stagger and a short scale/opacity/translation entrance. For languages or emoji where character segmentation is uncertain, use a safe fallback that animates the complete line rather than corrupting Unicode clusters.
7. Honor reduced motion: switch to a quick crossfade/no stagger when the user/device asks for reduced motion.
8. Preserve `onSeek(line.startMs)` for lyric tapping and show translation beneath current line only when enabled.

**Validation:**
- Advance through at least 10 lyric lines; measure or visually inspect that active line stays inside its lyric viewport and never crowds title/artist.
- Verify all inactive lines have identical alpha, font size, and blur even when they are at the far end of the list.
- Test with Chinese, English, Japanese, long lines, and no current lyric.

**Commit:** `feat(lyrics): add anchored uniform-focus lyric motion`

---

## Task 5: Implement the Expand-on-Scrub Glass Progress Control

**Objective:** Preserve the quiet idle indicator while revealing precise seek information only during interaction.

**Files:**
- Modify: `app/src/main/java/com/example/spotifylyricsproxy/ui/playback/PlaybackScreen.kt`, replacing or refactoring `ProgressBlock` around lines 797–844.
- Reuse: `ui/theme/GlassSurface.kt`.

**Steps:**
1. Do not use the default Material slider appearance for idle state.
2. Idle UI: 92dp x 3dp centered track, no time labels, no visible thumb.
3. On press/drag/focus:
   - animate width to the available content width;
   - wrap the expanded controls in `GlassSurface` with a pill shape;
   - show elapsed `m:ss`, seek track, duration `m:ss`;
   - increase track height modestly to ~4dp;
   - provide a usable thumb only while actively scrubbing if it helps touch precision.
4. Keep a local drag value so the elapsed label follows the drag immediately, then call `viewModel.seekTo(...)` only on `onValueChangeFinished`.
5. Collapse after release/cancel/loss of focus with a short 500–700ms delay; do not collapse while a finger is still down.
6. Ensure the expanded pill does not overlap navigation insets and does not force lyrics beneath it.

**Validation:**
- Drag left/right: full pill appears, labels update, seek fires once on release.
- Tap/keyboard focus: expansion works and is accessible.
- Return to idle: pill collapses and leaves only the centered short line.
- Validate 0 duration and disconnected states do not crash or show bogus time.

**Commit:** `feat(playback): add expandable glass seek control`

---

## Task 6: Apply Glass Only to Other Appropriate Controls

**Objective:** Improve global coherence without turning operational screens into decorative overlays.

**Files:**
- Modify (optional, only after Task 2 visual review): `app/src/main/java/com/example/spotifylyricsproxy/ui/navigation/AppNavigation.kt:198-289`
- Do not broadly modify operational page cards yet.

**Recommended scope:**
1. **Bottom navigation:** update only its dark-mode container in `CompactBottomBar` to use the subtle glass primitive. Keep the active nav item readable and retain the existing icon/text semantics.
2. **Playback overflow menu trigger:** use a mild pressed-state/halo, not a glass card behind every menu item.
3. **Expanded lyric jump-to-current control:** suitable for a small round glass button because it is a transient overlay.

**Explicitly do not apply glass to:**
- `PlaylistScreen.kt` track rows / playlist carousel;
- `CacheScreen.kt` cache result cards and search surface;
- `PrecacheScreen.kt` auth and job rows;
- `SettingsScreen.kt` setting groups and switches;
- `LyricsCorrectionScreen.kt` correction forms and candidate picker rows.

**Reason:** these are operate/configure surfaces that need high scanability, clear grouping, and reliable contrast. Transparent blur would reduce information density and make the product look inconsistent with the music-focused playback page.

**Commit:** `style(ui): apply glass to transient playback chrome`

---

## Task 7: Optional Typography Customization (Separate Follow-up)

**Objective:** Let users tune display sizes later without delaying the visual redesign.

**Files:**
- Modify: `ui/playback/LyricDisplayPreferences.kt`
- Modify: `ui/playback/PlaybackScreen.kt`
- Modify: `ui/settings/SettingsScreen.kt` or the existing lyric display dialog.

**Requirements:**
1. Do this only after Tasks 1–6 are accepted.
2. Add user preferences for title/artist scale and lyric scale with restrained ranges, e.g. 0.90–1.15 for metadata and 0.85–1.25 for lyrics.
3. Persist through the existing preference mechanism, not a new data store.
4. The fixed lyric anchor must still work after text size changes; use measured layout, not assumed row heights.

**Commit:** `feat(settings): add playback typography scale controls`

---

## Test and Verification Matrix

### Automated

1. Run project tests first:
   ```bash
   ./gradlew test
   ```
2. Run debug compilation before handing off:
   ```bash
   ./gradlew assembleDebug
   ```
3. If Compose UI tests are added, test:
   - compact idle seek control;
   - seek expansion on interaction;
   - active lyric anchor remains in lyric viewport;
   - previous/play/next click handlers are exposed and enabled/disabled correctly.

### Visual and functional

1. Portrait phone emulator: 360dp, 393dp, and 430dp widths.
2. Long Chinese and English title/artist cases, including no album art.
3. Lyrics through at least 10 current-line transitions; confirm non-current text remains uniform in blur/alpha/size.
4. Scrub left/right repeatedly; check no jumpy layout, no labels clipped, and no stuck expanded pill.
5. Dark and light theme: immersive playback remains album-derived; operational screens retain their current readable surfaces.
6. Real OPPO verification is required for the playback UI. Record separate facts for:
   - compile passed;
   - emulator screenshot passed;
   - OPPO physical device rendering and touch seeking passed.

---

## Risks and Guardrails

- Do not claim true Apple liquid-glass reproduction. Android does not offer the exact same compositor; this is an original, restrained album-tinted glass material.
- Backdrop/refraction effects may be expensive or inconsistent on OPPO devices. The visual fallback must be complete without live blur.
- Album palette values must preserve text contrast. Darken and desaturate as necessary; never directly place white text over a bright raw palette.
- Avoid touching Spotify connection, notification, MediaSession, lyrics repository, playback clock, or Room data for this design task.
- Do not remove shuffle/repeat/playlist/correction access merely because they leave the primary title row. Preserve them via existing overflow/menu paths unless a new interaction proposal is approved.
- The repo currently showed no detected `app/src/test` files from inspection; do not fabricate a test framework. Add focused UI tests only if the project setup supports them cleanly.

---

## Handoff Checklist

Before accepting an implementation from another AI, verify:

- [ ] Portrait homepage follows the album-art-to-same-color transition and does not fade to black by default.
- [ ] Favorite is absent from the title row.
- [ ] Previous, play/pause, and next are compact and to the title/artist row’s right.
- [ ] Active lyric remains in a fixed lyric viewport and never crowds artist metadata.
- [ ] All inactive lyrics look identically blurred/muted; no distance-based gradient remains.
- [ ] Active lyric characters visibly enter in sequence, with a reduced-motion fallback.
- [ ] Idle seek line is short and label-free.
- [ ] Dragging/focusing expands seek into a time-labeled, glass pill and release collapses it.
- [ ] Glass is limited to transient playback chrome/bottom nav, not operational-page cards.
- [ ] `./gradlew assembleDebug` passes and OPPO device behavior is reported separately.
