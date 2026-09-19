# Android AI Studio

Native Android client for a self-hosted image/video generation backend (FastAPI + ComfyUI).

## Status

Initial scaffold. The Android client currently contains a Compose UI prototype and API contract; backend deployment, workflow IDs, authentication, and device build verification remain to be completed.

## Architecture

`Android (Kotlin + Jetpack Compose) -> authenticated FastAPI backend -> ComfyUI on private GPU host`

Do not expose ComfyUI directly to the public internet or embed backend secrets in the APK.

## Open in Android Studio

Open this repository as a Gradle project. Use JDK 17 and Android SDK 35. Sync Gradle, then run the `app` configuration on an Android device/emulator.

## Configure backend

Set the backend base URL in the app's Settings screen. Use HTTPS outside a trusted local development network. The backend API contract is documented in `docs/backend-api.md`.

## Safety and content

Only use lawful content involving consenting adults and models/workflows whose licenses permit the intended use. The app is a client; it does not bypass provider or model restrictions.
