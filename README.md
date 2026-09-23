# Android AI Studio

Native Android client for a self-hosted image/video generation backend (FastAPI + ComfyUI).

## Status

Phase G complete for the current single-instance architecture. The Android client implements the approval-aware v2 generation flow, the backend provides server-owned workflow dispatch, durable job tracking and lifecycle receipts, and GitHub CI verifies both the backend tests and the Android debug build.

## Architecture

`Android (Kotlin + Jetpack Compose) -> authenticated FastAPI backend -> ComfyUI on private GPU host`

Do not expose ComfyUI directly to the public internet or embed backend secrets in the APK.

## Open in Android Studio

Open this repository as a Gradle project. Use JDK 17 and Android SDK 35. Sync Gradle, then run the `app` configuration on an Android device/emulator.

## Configure backend

Set the backend HTTPS URL, API key and separate approval token in the Android client. Credentials are held only in screen memory and are not persisted by the current client. Use HTTPS outside a trusted local development network. The backend API contract is documented in `docs/backend-api.md`.

## Safety and content

Only use lawful content involving consenting adults and models/workflows whose licenses permit the intended use. The app is a client; it does not bypass provider or model restrictions.


## Phase F status

Production job/media lifecycle is implemented: authenticated job cancellation, queue-aware status reconciliation, sampled Android image preview, MediaStore export for Android 10+, and private cache cleanup. ComfyUI remains private behind the backend proxy.


## Phase G status

Real-time generation progress is proxied through an authenticated backend WebSocket. Android receives ComfyUI execution/progress events without direct ComfyUI access and retains authenticated polling as a fallback.
