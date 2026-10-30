# Subtitle Translator

Real-time, fully offline translation of game subtitles on an Android tablet.

Your console stays connected straight to the TV over HDMI — zero added input lag. The tablet reads the subtitles either **straight from the PS5 over Wi-Fi (Remote Play)** or with its camera (or a USB camera) pointed at the TV, translates them on-device and shows them in large, readable text. Read-aloud is optional.

Built for a PS5 → LG webOS TV setup, but it works with any screen and any console.

## Features

- **Two sources, picked at launch** — *PS5 Remote Play* reads the console's own picture over Wi-Fi: no camera, no reflections, the sharpest text. *Camera* works with any console, TV app or screen.
- **PS5 Remote Play** (built on [chiaki-ng](https://github.com/streetpea/chiaki-ng)) — paired consoles connect with one tap and are woken from rest mode when needed; consoles on the Wi-Fi are found automatically; pair once with the 8-digit code from *Settings › System › Remote Play › Link Device*. The PSN account id is filled in by signing in to PlayStation (or can be pasted). Give each PS5 a name. Only the picture is streamed (no audio, no controller input), so you keep playing on the TV with your controller.
- **Offline and accurate** — on-device OCR (model bundled in the APK) and on-device translation.
- **Robust on busy scenes** — each frame is read twice: as captured and as a high-contrast copy that isolates white/yellow subtitle ink from colorful backgrounds. Reads from several frames are then voted on, so one garbled frame can't spoil a line. Digit/letter mix-ups (`1eave`, `G0D`) and ALL-CAPS text are fixed before translation, and `- line - line` dialogue is translated line by line.
- **Dictionary repair (English)** — on real camera footage OCR gets nearly every letter right but drops word spaces (`sothey swallowthingswhole`, `upa tent`). A 126k-word frequency dictionary restores spacing and fixes one-letter typos instantly; on recorded TV footage this took most committed lines from ~96% to 100% character accuracy. The same dictionary helps pick the better OCR pass.
- **Optional AI translation (offline)** — Gemma 4 E2B (Apache-2.0, ~2.6 GB, downloaded once after consent) translates on the tablet via LiteRT-LM, on the GPU with CPU fallback. It writes more natural target-language text, silently fixes leftover OCR mistakes and gets the last few lines as context so names and tone stay consistent. Each line appears once, complete, when the model has finished; speculative decoding with Gemma 4's MTP drafter is enabled when supported, and subtitles are committed after 2 frames since the model fixes OCR mistakes itself. ML Kit is used only as a fallback if the AI fails. No internet, tokens or costs. Gemma 4 E2B was chosen as the best-scoring small local model in an independent game-text translation benchmark.
- **Speed vs quality setting** — from *Instant* (~150 ms, single pass) to *Max* (~800 ms, 6-frame voting). Default: *Best* (~550 ms).
- **Selectable languages** — game language: English, Turkish, German, French, Spanish, Italian, Portuguese, Dutch, Polish. Target language: any of those plus Russian, Arabic, Japanese, Korean and Chinese.
- **Asks before downloading** — if a language pack (≈30 MB) is missing, the app asks first. After that, no network is needed.
- **Camera picker** — a dropdown in the top bar lists rear, front and external USB (UVC) cameras. A USB camera plugged in while the app is running is added and selected automatically. HDMI capture cards that appear as USB cameras work too.
- **Customizable subtitles** — text size, font, weight, color and background.
- **Scrollable transcript** — every translated line is kept. The newest is large and bright, older lines get smaller and darker; scroll back anytime and jump to the latest with one tap.
- **Clean results** — small HUD text is dropped and speaker names (`ELLIE: …`) are kept but not translated.
- **Fullscreen mode** keeps translating, with a small live camera preview in the corner for re-aiming. Pause anytime.
- **English and Turkish UI**, switchable in the app.

## Install

1. Open the [latest release](../../releases/latest) on the tablet.
2. Download `SubtitleTranslator-<version>.apk` and open it. Allow "install unknown apps" for your browser when prompted.
3. Future releases install over the previous version — no uninstall needed.

## First run

1. Choose **PS5 Remote Play** or **Camera**.
2. When asked, download the language pack (one time, Wi-Fi recommended).
3. *Remote Play:* on the PS5 open **Settings › System › Remote Play**, enable it and choose **Link Device**. In the app tap **Add PS5** (or **Pair** next to a console found on your Wi-Fi), sign in to PlayStation and enter the code. From then on, tap the console to connect.
   *Camera:* grant camera access and point the camera at the TV.
4. Drag the yellow frame over the subtitle area. It's remembered separately for each source.
5. Optional: open **Settings** to change languages, fonts, colors or turn on read-aloud.

## Tips for best accuracy

- In the game, choose the **largest subtitle size** and enable a **subtitle background**. This matters more than anything else.
- Keep the camera as **square to the screen** as possible and avoid lamp or window reflections on the TV.
- Fit the frame tightly around the subtitle lines so menus and HUD text stay out.
- An external 1080p autofocus webcam mounted below the TV gives the sharpest image.
- If accuracy is still low on a particular show or game, try **Max**; if you want snappier subtitles, step down to **Balanced** or **Fast**.

## Architecture

```
CameraX (1080p, keep-only-latest)            PS5 Remote Play (chiaki-ng, 1080p HEVC → MediaCodec → ImageReader)
  → FrameCropper       crop the subtitle region  → YuvFrames  convert only the subtitle region (+ a small preview)
  → SubtitleEnhancer   high-contrast copy isolating white/yellow subtitle ink (dual pass)
  → SubtitleOcr        ML Kit Latin OCR on each pass, drops lines much smaller than the subtitle
  → SubtitleText       fix digit/letter mix-ups, normalize
  → SpellingRepair     restore word spacing, fix typos (English dictionary); ReadScore picks the better pass
  → SubtitleStabilizer vote across frames, commit only stable, genuinely new lines
  → OfflineTranslator  sentence-case ALL CAPS, split dialogue, ML Kit Translate, LRU cache
  → AiTranslator       optional: Gemma 4 E2B re-translates with context and replaces the line (AiPrompt checks the reply)
  → Compose UI (+ optional TextToSpeech)
```

| Package | Responsibility |
|---|---|
| `core` | Pure Kotlin logic: read scoring, voting stabilizer, similarity, text cleanup, ROI mapping (unit-tested) |
| `camera` | Camera discovery and frame cropping |
| `ocr` | On-device text recognition and high-contrast preprocessing |
| `translate` | Language pack management and translation |
| `ai` | Optional on-device LLM translation and model download |
| `remote` | PS5 discovery, pairing, wake-up and streaming (Kotlin side of the JNI bridge in `app/src/main/cpp`) |
| `tts` | Optional read-aloud |
| `data` | Settings model and DataStore persistence |
| `ui` | Jetpack Compose (Material 3) screens and the ViewModel |

## Building

CI builds every push to `main`, runs the unit tests and publishes the APK as the `latest` release.

Locally (Android Studio Ladybug+ or JDK 17 with the Android SDK and NDK, `protoc`, and Python 3 with `protobuf`):

```bash
git submodule update --init third_party/chiaki-ng
git -C third_party/chiaki-ng submodule update --init third-party/nanopb third-party/jerasure third-party/gf-complete third-party/curl
./gradlew testDebugUnitTest assembleRelease
```

The native part (`app/src/main/cpp`) builds chiaki-ng's protocol library with OpenSSL, json-c and miniupnpc fetched at build time.

### Signing

Builds are signed with `keystore/sideload.jks`, committed on purpose so every CI build has the same signature and updates install in place. It is meant for personal sideloading only. Before distributing the app more widely, generate a private key and move it to a GitHub secret.

## License and third-party code

Remote Play is built on [chiaki-ng](https://github.com/streetpea/chiaki-ng) (`third_party/chiaki-ng`, AGPL-3.0 with an OpenSSL exception); the JNI bridge in `app/src/main/cpp` and `remote/chiaki/Chiaki.kt` are adapted from its Android client. Because the app links this code, the app is distributed under the **GNU AGPL-3.0** (see `LICENSE`, which includes the OpenSSL exception). This project is not affiliated with Sony Interactive Entertainment; PlayStation and PS5 are trademarks of their owners.

`app/src/main/assets/dict/en_words.txt.gz` is the word list from [wordninja](https://github.com/keredson/wordninja) (MIT); see `app/src/main/assets/dict/NOTICE.txt`. The optional AI model is [Gemma 4 E2B](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm) (Apache-2.0), downloaded at runtime only if the user enables AI translation.

## Limitations

- OCR reads Latin-script game text only (the game language list reflects this).
- Offline translation is good for following a story but less natural than cloud translators.
- Camera quality, angle and reflections directly affect accuracy.
- Remote Play needs the tablet and the PS5 on the same network, and only one Remote Play device can use a PS5 at a time. Streaming apps (Netflix etc.) are not sent over Remote Play; use the camera for those.
