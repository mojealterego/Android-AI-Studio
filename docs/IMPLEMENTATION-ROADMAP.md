# Android AI Studio — implementation roadmap

This document separates implemented behavior from planned production capabilities. It is not a claim that unimplemented features are available.

## Current baseline

- Android: Jetpack Compose UI and Retrofit API client exist, but the Generate action is still a placeholder; no live job submission, polling, history, or result gallery is wired into the screen.
- Backend: FastAPI adapter to ComfyUI supports health, job submission, listing, status, and result metadata. `API_KEY` is fail-closed for protected routes.
- Job state is process-local and is lost on restart.
- The request currently accepts a client-supplied ComfyUI workflow. This is prototype behavior and must not be exposed as a public production interface.
- Result metadata is returned, but there is no authenticated media delivery/proxy endpoint.
- Cancellation, durable storage, real progress streaming, and automated tests are not yet implemented.

## Required architecture before public deployment

1. **Server-owned workflows**
   - Client submits a stable `workflowId` and typed parameters, never arbitrary ComfyUI graph JSON.
   - Backend loads allowlisted workflow templates from server storage, validates node IDs/input names, substitutes only declared parameters, and rejects unknown IDs/fields.
   - Model/checkpoint/LoRA selection is configured on the server and constrained to installed, licensed assets. Do not expose filesystem paths or arbitrary node classes to the client.

2. **Authentication and network boundary**
   - Keep ComfyUI bound to a private interface/network; only the backend may reach it.
   - Require a high-entropy server secret for protected endpoints; use TLS or a private VPN/reverse proxy for remote access.
   - Never embed the server secret in the APK. For multiple users, replace a shared bearer secret with user authentication and per-user authorization.
   - Add rate limits, payload limits, request timeouts, and structured security logs without recording prompts or secrets unnecessarily.

3. **Durable jobs**
   - Persist jobs in PostgreSQL (or equivalent) and use a durable queue/worker strategy for multi-process deployments.
   - Track explicit states: `QUEUED`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED`; persist sanitized failure details and timestamps.
   - Reconcile interrupted jobs on startup. Implement cancellation only when supported by the configured ComfyUI execution path.

4. **Media delivery**
   - Keep ComfyUI output storage private. Serve results through an authenticated backend endpoint or short-lived signed object-storage URLs.
   - Validate output type and size; set safe content types and `Content-Disposition`; never accept arbitrary filesystem paths from clients.

5. **Android client**
   - Replace placeholder Generate action with validated API submission and lifecycle-aware polling.
   - Add connection settings with secure handling (Android Keystore-backed storage), job history, progress/error states, cancellation, and an image/video result viewer.
   - Model network failures, authentication failures, timeouts, and backend validation errors as distinct UI states.
   - Avoid blocking the main thread; use lifecycle-aware coroutines and explicit loading states.

6. **Quality gates**
   - Backend: unit/API tests for auth, validation, state transitions, ComfyUI failures, and output handling; lint/type checks; dependency scanning.
   - Android: unit tests for request/response mapping and ViewModel state; Compose UI tests for empty prompt, loading, failure, and completed job.
   - CI: run tests on every pull request; build a debug APK; do not claim deployment or GPU integration until verified against a running ComfyUI instance.

## Integration acceptance criteria

- A configured, server-owned image workflow can be selected by ID and completes against a real ComfyUI instance.
- A configured video workflow is separately validated against its required custom nodes and model files; image workflow success does not imply video readiness.
- The Android app submits a job, survives rotation/backgrounding, reports state accurately, and displays the authenticated result.
- No ComfyUI port is publicly exposed, no API secret is packaged in the APK, and one user cannot read another user's jobs.
- Tests and build logs are attached to the release; unverified GPU/model compatibility is explicitly listed.

## Model and content policy

Model freedom is a deployment/model-selection property, not an effect of the backend API key. Use only models and workflows whose licenses permit the intended use. The service must be restricted to lawful adult content involving consenting adults and fictional adult characters; it must not support sexual content involving minors, non-consensual intimate imagery, or sexualized depictions of real people without consent.
