# Consent UX Contract

This is the user-facing contract for consent surfaces in Android-AI-Studio. It is implementation guidance, not evidence that the UI already exists.

## 1. Action Preview

Before any consequential dispatch, show a stable preview containing:

- **Action:** the concrete verb, e.g. “Generate workflow in ComfyUI”.
- **Workflow:** exact workflow identifier and human-readable name, if available.
- **Parameters:** every consequential parameter verbatim; do not collapse values behind “and more”.
- **Destination:** the external service receiving the request.
- **Authority requested:** explicit `write` label and exact resource scope.
- **Duration:** once, session, or task; “always” is never preselected.
- **Provenance:** identify any material instruction originating from untrusted content.

The preview is a contract. Approval applies only to the displayed digest-bound payload. Any parameter, destination, or workflow change invalidates the approval and requires a new preview.

## 2. Decision controls

- Primary safe action is **Reject** or **Not now**; it must not destroy user data or block unrelated work.
- Approval control names the act (“Allow this generation”), not a generic “OK”.
- Default duration is **Just this once**.
- Long-lived authority is a separate deliberate choice with its consequence adjacent to the option.
- For irreversible actions, enumerate the concrete consequence and require typed confirmation. Do not use hold-to-confirm as the only route.
- Keyboard Escape and dialog dismissal map to rejection/cancellation, never implicit approval.

## 3. Connection / standing-grant card

Every persistent grant must have a visible management card with:

- principal and connected service;
- resource scope and explicit read/write/delete labels;
- duration and expiry;
- status as text (Active, Paused, Revoked, Expired, Needs re-authentication);
- last-used timestamp;
- Pause and Revoke actions, with confirmation only where necessary.

Revocation must take effect on the next authorization check. A UI-only hidden or paused state is not revocation.

## 4. Action receipt

After each consequential attempt, show a receipt containing:

- action and destination;
- decision and outcome (denied, approved, dispatched, failed, or unknown);
- timestamp;
- principal and grant reference;
- digest of the approved payload;
- retry/undo affordance only when the backend can actually honor it.

Do not label a request “completed” merely because it was queued. Distinguish accepted, completed, failed, and indeterminate states. Never include API keys, passwords, or raw credentials in receipts.

## 5. Batch approval

Routine items may be selected together only when each preview is individually inspectable. High-impact, irreversible, flagged, or untrusted-provenance actions are structurally excluded from select-all and require individual review. Display a live count of selected actions and the exact batch effect.

## 6. Accessibility

- Dialogs have an accessible title, trap focus, and initially focus the least-destructive choice.
- Inline consent prompts use a labeled group and do not steal focus.
- Use native checkbox/radio controls with programmatically associated descriptions.
- Announce selection counts and status changes through a polite live region.
- Never communicate access level or status by color alone.
- Ensure scrollable preview content is keyboard reachable.

## 7. Acceptance criteria

- [ ] Preview digest equals the payload dispatched by the backend.
- [ ] Any mutation after preview invalidates the approval.
- [ ] Decline/cancel never triggers the protected action.
- [ ] Standing grants are discoverable, pausable, revocable, and show last use.
- [ ] Receipts distinguish queued/accepted/completed/failed/unknown.
- [ ] Sensitive credentials never enter model context, preview, or receipt.
- [ ] Keyboard-only and screen-reader flows can inspect and decline/approve without ambiguity.
