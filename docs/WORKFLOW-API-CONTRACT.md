# Server-owned workflow API contract

Status: design contract; not yet implemented by the running API.

## Objective

The Android client must select an allowlisted workflow by stable ID and submit user parameters. It must not upload an arbitrary ComfyUI graph in the production contract.

## Proposed request

`POST /api/v2/jobs`

```json
{
  "workflowId": "image.txt2img.basic",
  "parameters": {
    "prompt": "A fictional adult character in a rainy city",
    "negativePrompt": "low quality",
    "width": 1024,
    "height": 1024,
    "steps": 28,
    "cfg": 6.5,
    "seed": null
  }
}
```

The server resolves `workflowId` against a fixed registry, validates parameters against that workflow's schema, clones its server-owned ComfyUI API graph, and applies only explicit node/input mappings. Unknown workflow IDs and unknown parameter keys are rejected. Clients cannot supply node IDs, class types, file paths, model paths, output paths, or arbitrary graph JSON.

## Discovery

`GET /api/v2/workflows` returns only enabled workflows and their public parameter schemas. It must not disclose server filesystem paths, private model directories, credentials, or internal graph details.

Example response:

```json
{
  "workflows": [
    {
      "id": "image.txt2img.basic",
      "type": "IMAGE",
      "title": "Text to image",
      "parameters": {
        "prompt": {"type": "string", "required": true, "maxLength": 4000},
        "negativePrompt": {"type": "string", "required": false, "maxLength": 4000},
        "width": {"type": "integer", "minimum": 256, "maximum": 2048, "multipleOf": 64},
        "height": {"type": "integer", "minimum": 256, "maximum": 2048, "multipleOf": 64},
        "steps": {"type": "integer", "minimum": 1, "maximum": 100},
        "cfg": {"type": "number", "minimum": 1, "maximum": 20},
        "seed": {"type": ["integer", "null"]}
      }
    }
  ]
}
```

## Registry requirements

Each registry entry must include:

- stable workflow ID and IMAGE/VIDEO type;
- enabled flag and display metadata;
- server-owned API-format graph;
- explicit mapping from accepted parameter names to known node IDs and input names;
- strict parameter schema, defaults, and bounds;
- model/checkpoint identifiers selected from server-side allowlists;
- a version so queued jobs remain interpretable after workflow updates.

Registry loading must fail closed for malformed entries. IDs must be validated as identifiers, never interpreted as filesystem paths. Graph files must be loaded only from a configured, trusted directory; reject path traversal and symlinks escaping that directory. Do not allow user-supplied paths or URLs.

## Job lifecycle

The API should return a stable job ID and an explicit state. Suggested states: `QUEUED`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED`. Store the workflow ID/version and a sanitized error code with each job. Do not expose raw ComfyUI exceptions, internal URLs, filesystem paths, or credentials.

The current in-memory job map is suitable only for a single-process demo. Production requires durable storage, bounded queueing, per-user ownership, retention/cleanup, and reconciliation after restart.

## Compatibility and migration

1. Add `/api/v2/workflows` and `/api/v2/jobs` without silently changing the meaning of `/api/jobs`.
2. Add at least one real image workflow exported in ComfyUI API format and verify its node/input mappings against the selected installed model.
3. Add an independently validated video workflow only after its model/runtime requirements are known.
4. Update Android to discover workflow schemas, render supported parameters, submit v2 requests, poll job state, and show typed errors.
5. After the Android client has migrated, disable arbitrary client-provided graphs on the legacy endpoint and document the removal date.

## Acceptance criteria

- Unknown workflow IDs and extra parameter keys are rejected.
- No request can inject arbitrary ComfyUI nodes or filesystem paths.
- Parameter boundaries and defaults are covered by automated tests.
- A real image workflow completes end-to-end against ComfyUI and returns a retrievable media reference.
- A failed or unreachable ComfyUI request produces a sanitized, actionable API error.
- API keys are not embedded in the APK; production traffic is protected by TLS or a private VPN and authenticated per-user access where multiple users exist.

## Safety and licensing

The application should support lawful generation using models whose licenses permit the intended use. Keep safeguards against sexual content involving minors, non-consensual intimate imagery, and non-consensual sexualized depictions of real people. Model availability does not itself establish permission to use a model or a person's likeness.