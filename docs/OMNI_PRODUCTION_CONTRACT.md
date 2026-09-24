# OMNI Production Contract

## Agent chain
1. A01 — Intent & Creative Director: intent, ambiguity and target specification.
2. A02 — Reference & Identity Analyst: reference roles, identity separation and conflicts.
3. A03 — Image Production Agent: image production, masks, composition and change control.
4. A04 — Model & Pipeline Engineer: model compatibility, limits, formats and cost.
5. A05 — Visual QA & Continuity: identity drift, anatomy, continuity and specification QA.
6. A06 — Video & Cinematic Agent: storyboard, blocking, timeline, camera and video continuity.

The supplied WDA Ω∞ specification defines reference roles, immutable state, transformations and validation as the semantic visual-control layer. fileciteturn804file0L162-L205

## Three resident GGUF slots
The application exposes exactly three independent resident slots:
- CHAT + CODE — chat, JARVIS and coding
- IMAGE — image pipeline
- VIDEO — video pipeline

The slot contract does not claim that every GGUF can execute every modality. Model compatibility must be verified before loading.

## Hugging Face Model Vault
The existing backend exposes authenticated Hugging Face search and controlled file downloads. Hugging Face documents programmatic model downloads through its Hub tooling. citeturn0search5turn0search9

Required controls:
- authenticated download
- repository and filename validation
- download-size ceiling
- temporary file followed by atomic rename
- license/repository metadata before installation

## Character continuity
Use:
master reference → reference sheet → locked identity → wardrobe state → scene references → generated shots → QA.

Picsart documents fixed character references feeding multiple shots while camera, pose, set and pacing vary. citeturn0search0turn0search1

Google Flow likewise supports reusable visual ingredients across clips and extension of supported generations. citeturn0search3turn0search4turn0search6

## Long-form video
A long film is a timeline of independent generated shots, not one 124-minute generation. The app therefore needs project runtime, scenes, shots, references, continuity state, render order, concatenation/export and per-shot regeneration.

Picsart documents this node-based production pattern and independent shot regeneration. citeturn0search1

## Social publishing
One Publish surface should provide asset selection, caption, destination selection, provider authorization outside agent context, publish receipt and retry/revoke state. The Android client can use the system share sheet for installed apps; direct API publishing requires provider OAuth/API integration.

## Authority and audit
Autonomy settings are external to prompts and cannot be expanded by instructions embedded in an image, video, web page or model output. Consequential operations require action receipts. Credentials remain outside agent context.

The WDA source also requires an identity firewall and subject-count invariant. fileciteturn804file0L213-L231
