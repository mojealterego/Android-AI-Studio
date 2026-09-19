# Server-owned ComfyUI workflows

This directory is the deployment boundary for approved ComfyUI API-format workflow templates. The backend does **not** load arbitrary client-provided filesystem paths.

## Required workflow manifest (planned registry contract)

Each workflow is a versioned JSON manifest maintained by the server, containing:

- `id`: stable lowercase identifier (`image-sdxl`, `video-...`)
- `version`: immutable template version
- `type`: `IMAGE` or `VIDEO`
- `title`: user-facing display name
- `graph`: exported ComfyUI API prompt graph
- `bindings`: explicit allowlisted mapping from public parameters to exact node/input pairs
- `limits`: allowed width/height, steps, frame count, and other resource bounds

Example shape (illustrative only; not executable until a real ComfyUI API export is added):

```json
{
  "id": "image-template",
  "version": 1,
  "type": "IMAGE",
  "title": "Image template (unconfigured)",
  "graph": {},
  "bindings": {
    "prompt": {"node": "POSITIVE_PROMPT_NODE_ID", "input": "text"},
    "negativePrompt": {"node": "NEGATIVE_PROMPT_NODE_ID", "input": "text"},
    "seed": {"node": "SAMPLER_NODE_ID", "input": "seed"}
  },
  "limits": {"steps": {"min": 1, "max": 40}}
}
```

Do not deploy the illustrative manifest above: node IDs are placeholders and `graph` is intentionally empty.

## Security requirements

1. Never accept a graph, node IDs, file paths, model paths, or arbitrary ComfyUI inputs from the Android client.
2. Validate workflow ID against a server-side allowlist and clone the selected graph before applying bindings.
3. Bind only explicitly declared fields; reject unknown parameters and out-of-range values.
4. Keep ComfyUI inaccessible from public networks; expose only the authenticated backend over TLS/VPN.
5. Treat model/checkpoint/LoRA names as server-managed configuration, not filesystem paths supplied by clients.
6. Add a real exported API workflow and integration test before enabling a workflow in production.

## Current status

This directory documents the format only. No production-ready workflow is enabled yet. The API still has a legacy `workflow` request field; do not expose the current backend publicly until that compatibility path is removed or strictly gated.