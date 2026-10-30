# Subtitle Translator

Real-time, fully offline translation of game subtitles on an Android tablet.

Your console stays connected straight to the TV over HDMI — zero added input lag. The tablet (or a USB camera plugged into it) looks at the TV, reads the subtitle area, translates it on-device and shows it in large, readable text. Read-aloud is optional.

Built for a PS5 → LG webOS TV setup, but it works with any screen and any console.

## Features

- **Offline and fast** — on-device OCR (model bundled in the APK) and on-device translation. Typical latency from subtitle to translation is well under a second.
- **Selectable languages** — game language: English, Turkish, German, French, Spanish, Italian, Portuguese, Dutch, Polish. Target language: any of those plus Russian, Arabic, Japanese, Korean and Chinese.
- **Asks before downloading** — if a language pack (≈30 MB) is missing, the app asks first. After that, no network is needed.
- **Camera sources at the top** — rear, front and external USB (UVC) cameras. A USB camera plugged in while the app is running is selected automatically. HDMI capture cards that appear as USB cameras work too.
- **Customizable subtitles** — text size, font, weight, color and background.
- **Clean results** — a stabilizer filters OCR jitter and half-read frames, small HUD text is dropped, speaker names (`ELLIE: …`) are kept but not translated, and the last two lines stay visible in case you missed one.
- **Fullscreen subtitle mode** and pause.
- **English and Turkish UI**, switchable in the app.

## Install

1. Open the [latest release](../../releases/latest) on the tablet.
2. Download `SubtitleTranslator-<version>.apk` and open it. Allow "install unknown apps" for your browser when prompted.
3. Future releases install over the previous version — no uninstall needed.

## First run

1. Grant camera access.
2. When asked, download the language pack (one time, Wi-Fi recommended).
3. Point the camera at the TV and drag the yellow frame over the subtitle area. It's remembered.
4. Optional: open **Settings** to change languages, fonts, colors or turn on read-aloud.

## Tips for best accuracy

- In the game, choose the **largest subtitle size** and enable a **subtitle background**. This matters more than anything else.
- Keep the camera as **square to the screen** as possible and avoid lamp or window reflections on the TV.
- Fit the frame tightly around the subtitle lines so menus and HUD text stay out.
- An external 1080p autofocus webcam mounted below the TV gives the sharpest image.
- **Response speed**: *Fast* reacts on the first frame, *Balanced* (default) confirms over two frames, *Stable* over three for shaky or blurry images.

## Architecture

```
CameraX (1080p, keep-only-latest)
  → FrameCropper      crop the subtitle region on the raw frame, rotate only the crop
  → SubtitleOcr       ML Kit Latin OCR, drops lines much smaller than the subtitle
  → SubtitleStabilizer commit only stable, genuinely new lines
  → OfflineTranslator ML Kit Translate, LRU cache, warm-up, newest subtitle wins
  → Compose UI (+ optional TextToSpeech)
```

| Package | Responsibility |
|---|---|
| `core` | Pure Kotlin logic: stabilizer, similarity, text cleanup, ROI mapping (unit-tested) |
| `camera` | Camera discovery and frame cropping |
| `ocr` | On-device text recognition |
| `translate` | Language pack management and translation |
| `tts` | Optional read-aloud |
| `data` | Settings model and DataStore persistence |
| `ui` | Jetpack Compose (Material 3) screens and the ViewModel |

## Building

CI builds every push to `main`, runs the unit tests and publishes the APK as the `latest` release.

Locally (Android Studio Ladybug+ or JDK 17 with the Android SDK):

```bash
./gradlew testDebugUnitTest assembleRelease
```

### Signing

Builds are signed with `keystore/sideload.jks`, committed on purpose so every CI build has the same signature and updates install in place. It is meant for personal sideloading only. Before distributing the app more widely, generate a private key and move it to a GitHub secret.

## Limitations

- OCR reads Latin-script game text only (the game language list reflects this).
- Offline translation is good for following a story but less natural than cloud translators.
- Camera quality, angle and reflections directly affect accuracy.
