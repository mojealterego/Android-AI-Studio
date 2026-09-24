# OMNI Agent Architecture

## A01–A06

The uploaded WDA Ω∞ specification defines a deterministic visual pipeline built around intent, reference assignment, locked state, transformation, constraints, compilation and validation. It also requires explicit reference roles and identity preservation. fileciteturn804file0L68-L118

- **A01 — Intent & Creative Director** — target specification, ambiguity detection and creative boundary.
- **A02 — Reference & Identity Analyst** — identity/reference roles, conflict detection and identity firewall.
- **A03 — Image Production Agent** — image/edit specification, masks, composition, materials, lighting and change budget.
- **A04 — Model & Pipeline Engineer** — runtime/model selection, formats, limits, latency and cost.
- **A05 — Visual QA & Continuity** — identity drift, anatomy, composition, continuity and regression checks.
- **A06 — Video & Cinematic Agent** — script, storyboard, blocking, shot list, camera movement, timeline and generation prompts.

The WDA source explicitly models SOURCE → LOCKED → TRANSFORM → CONSTRAINTS → TARGET and requires local changes to remain local. fileciteturn804file0L124-L150

## Governance

Authority Boundary, Spend & Rate Limits, Action Receipt, Revocation and Credential Handoff are backend controls. An image, video, webpage or model prompt cannot expand them.

## Three resident GGUF slots

The runtime contract is intentionally **three models loaded concurrently**:

1. **CHAT + CODE** — one GGUF resident model shared by conversation/JARVIS and coding.
2. **IMAGE** — one image-generation GGUF/runtime.
3. **VIDEO** — one video-generation GGUF/runtime.

The UI therefore does not use a single global model selector. Each slot has independent lifecycle state. The current control surface does not falsely report native loading until a concrete runtime reports a successful load.

Hugging Face documents GGUF as a Hub-supported format designed for GGML-family executors and provides GGUF model discovery. citeturn0search6turn0search14

## Hugging Face Model Vault

Authenticated endpoints now provide:

- model search through the Hugging Face Hub API;
- server-side model download;
- path traversal protection;
- configurable maximum download size;
- temporary-file download followed by atomic rename.

Model files stay on the private model host rather than inside the APK.

## Character and video continuity

Google Flow supports reusable visual ingredients/references across clips, while Picsart Flow documents character-reference continuity and multi-shot workflows. citeturn0search4turn0search1

The future A06 pipeline should therefore store a project-level **Character Lock / Reference Set** and feed the same references into each shot rather than regenerate the character from text alone.

Picsart's current model catalog also exposes image-to-video models including Kling, Happy Horse, Wan, PixVerse and other reference-driven pipelines.

## Publishing

The app has a unified publish surface and Android share fallback. Provider adapters can be added behind the same interface.

- TikTok Content Posting API supports direct video/photo posting; direct posting requires creator authorization and the video.publish scope. Unaudited clients are restricted to private visibility. citeturn0search0turn0search2
- YouTube supports video upload through videos.insert; unverified API projects are restricted to private viewing. citeturn0search13

The credential rule remains: provider tokens belong to the secure integration layer, not to A01–A06 prompt context.
