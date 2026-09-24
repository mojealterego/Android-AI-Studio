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

## Omni Command Center

The launcher is now the unified Omni Creative System. It includes dedicated entry points for Image, Video, Audio, Voice, Music, Avatar, Story/Cinema, Coding, Research, Model Vault, Knowledge, Evaluation, Evolution, Safety, Communication and Plugin Hub.

The visual production pipeline is governed by A01–A06:

- A01 Intent & Creative Director
- A02 Reference & Identity Analyst
- A03 Image Production Agent
- A04 Model & Pipeline Engineer
- A05 Visual QA & Continuity
- A06 Video & Cinematic Agent

The backend exposes a governed orchestration-plan API. Agent autonomy is not expanded by prompt, image, video or web content.

## Three resident GGUF slots

Model Vault requires three simultaneous resident GGUF slots:

1. CHAT + CODE
2. IMAGE
3. VIDEO

POST /api/omni/runtime/load-all now starts all three deployment-configured runtime processes as one operation and rolls back already-started processes if any slot fails. Configure the private worker with:

- RUNTIME_COMMAND_CHAT_CODE
- RUNTIME_COMMAND_IMAGE
- RUNTIME_COMMAND_VIDEO

Commands are deployment configuration and never originate from the client or agent.

## Hugging Face

The Model Vault searches Hugging Face and downloads selected files through the authenticated backend. Public downloads work without a token; gated/private repositories can use the server-side HF_TOKEN.

## Publishing

Generated media can be sent to installed social applications through Android ACTION_SEND share mechanism. Authentication remains in the receiving social application.

## Cinema

Long-form video is a scene/timeline problem: reference-locked characters and assets feed individual shots, Visual QA checks each shot, and the final timeline can target up to 124 minutes. Current AI video services still generate short clips and achieve long-form duration by assembling them; Picsart documents this workflow explicitly, while Google Flow provides Ingredients to Video and scene-building/extension mechanisms.