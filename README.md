# QR Scanner

A premium, privacy-first native Android QR code scanner. No account, no ads,
no analytics, no tracking — scanning runs fully offline on the device.

## Features

- **Instant camera scanning** — CameraX + on-device ML Kit barcode model,
  throttled analysis (newest frame only), duplicate suppression.
- **Smart results** — URLs (with suspicious-link flagging), plain text,
  Wi-Fi (show/hide password, connect flow), contacts (vCard/MeCard),
  email, phone, SMS, geo locations, calendar events, app deep links.
  Dangerous schemes (`javascript:`, `file:`, `intent:`, …) are never
  dispatched — they get an inspection-only screen.
- **Image import** — system photo picker (no storage permission), multiple
  codes per image, graceful handling of bad/oversized/QR-less images.
- **History** — local Room database: search, type filter, favorites, sorting,
  per-row delete, clear-all with confirmation, CSV/JSON export.
- **Continuous mode** — keep scanning code after code with a session strip.
- **Extras** — torch, pinch-to-zoom + zoom slider, tap-to-focus, clipboard
  paste, text shared from other apps, haptic/sound feedback, auto-open links
  (off by default), light/dark/system themes, privacy screen.

## Architecture

Single-activity + Compose Navigation. No DI framework (a small explicit
`AppContainer` in `QrScannerApp`).

- `scanner/` — `QrBarcodeAnalyzer` (CameraX), `ImageQrDecoder` (gallery)
- `data/model/` — `ScanContent` sealed hierarchy + `QrClassifier`
  (deliberately avoids `android.net.Uri` for opaque URIs — see progress.md)
- `data/db/` — Room (`ScanEntity`, `ScanDao`, `AppDatabase`)
- `data/repo/` — `ScanRepository` (60 s dedup window), `SettingsRepository`
  (DataStore)
- `ui/scanner|result|history|settings` — screens + ViewModels
- `util/` — `IntentActions` (guarded intents + `<queries>` in manifest),
  `WifiConnector`, `ExportUtils`, `Feedback`

## Build

Requirements: JDK 17+, Android SDK with API 34, internet for first build.

```sh
./gradlew assembleDebug        # → app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # unsigned; sign before distributing
```

Install on a connected device:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Release signing: the `release` build type currently uses the debug key so a
personal release can be sideloaded immediately. For store publishing, add a
`signingConfigs.release` block with a real keystore and point the release
build type at it.

## Test QR images

`test-qr/*.png` covers URL, text, Wi-Fi, vCard, email, phone, SMS, geo,
calendar event, unsafe scheme, deep link and a 900-char payload. Push them
to the device to exercise the gallery-import path:

```sh
adb push test-qr /sdcard/Pictures/QRTest
```

## Permissions

- `CAMERA` — requested only when the scanner opens; gallery import works
  without it.
- `VIBRATE` — scan feedback (normal-level, granted by default).

## Privacy

Scans and history live in a private on-device database. Nothing is uploaded,
logged externally, or shared. Links always require an explicit tap (auto-open
is opt-in and never applies to flagged links).
