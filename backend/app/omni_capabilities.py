from __future__ import annotations

import hmac
import os
from typing import Literal
from dataclasses import asdict, dataclass

from fastapi import APIRouter, Header, HTTPException

router = APIRouter(prefix="/api/omni", tags=["omni-capabilities"])
API_KEY = os.getenv("API_KEY", "").strip()


@dataclass(frozen=True)
class Capability:
    id: str
    category: Literal["CREATE", "AGENT", "SYSTEM"]
    title: str
    runtime: str
    providers: list[str]
    supports_reference_continuity: bool = False
    supports_external_publish: bool = False


CAPABILITIES = [
    Capability("image", "CREATE", "IMAGE LAB", "LOCAL / GPU", ["Local-Diffusion", "ComfyUI", "Flux", "Qwen"], True),
    Capability("video", "CREATE", "VIDEO LAB", "GPU WORKER", ["ComfyUI-LTX/WAN", "Veo", "Seedance", "Kling"], True),
    Capability("audio", "CREATE", "AUDIO LAB", "LOCAL / GPU", ["Bark", "AudioGen", "ComfyUI-audio"]),
    Capability("voice", "CREATE", "VOICE LAB", "LOCAL / GPU", ["Whisper", "Bark", "Kokoro", "ElevenLabs"]),
    Capability("music", "CREATE", "MUSIC LAB", "GPU WORKER", ["OpenMusic", "MusicGen", "Giant-Music-Transformer"]),
    Capability("avatar", "CREATE", "AVATAR LAB", "GPU WORKER", ["Duix", "HunyuanPortrait"], True),
    Capability("story", "CREATE", "STORY LAB", "AGENT", ["JARVIS", "Cinematic Agents"]),
    Capability("publish", "CREATE", "PUBLISH", "ANDROID SHARE", ["Android Sharesheet", "MediaStore"], supports_external_publish=True),
    Capability("a01", "AGENT", "INTENT DIRECTOR", "AGENT", ["A01"]),
    Capability("a02", "AGENT", "REFERENCE ANALYST", "AGENT", ["A02"], True),
    Capability("a03", "AGENT", "IMAGE PRODUCTION", "AGENT", ["A03"], True),
    Capability("a04", "AGENT", "MODEL PIPELINE", "AGENT", ["A04"]),
    Capability("a05", "AGENT", "VISUAL QA", "AGENT", ["A05"], True),
    Capability("a06", "AGENT", "CINEMATIC AGENT", "AGENT", ["A06"], True),
    Capability("jarvis", "AGENT", "JARVIS", "LOCAL / REMOTE", ["GGUF", "OpenAI", "Gemini"]),
    Capability("wda", "AGENT", "WDA PHOTO", "AGENT", ["WDA Ω∞"], True),
    Capability("cinema", "AGENT", "CINEMA", "MULTI-AGENT", ["A01-A06", "Veo", "ComfyUI"], True),
    Capability("coding", "AGENT", "CODING", "SANDBOX", ["OpenCode", "GGUF"]),
    Capability("research", "AGENT", "RESEARCH", "WEB / LOCAL", ["Deep Research", "Hugging Face", "GitHub"]),
    Capability("tool-bus", "AGENT", "TOOL BUS", "GATEWAY", ["MCP", "Plugins"]),
    Capability("model-vault", "SYSTEM", "MODEL VAULT", "ON-DEVICE / PRIVATE", ["GGUF", "Hugging Face"]),
    Capability("knowledge", "SYSTEM", "KNOWLEDGE", "ON-DEVICE", ["RAG", "Project Graph"]),
    Capability("evaluation", "SYSTEM", "EVALUATION", "LAB", ["Promptfoo", "OmniVideoBench"]),
    Capability("evolution", "SYSTEM", "EVOLUTION LAB", "ISOLATED", ["AgentOpt", "OpenAlpha Evolve"]),
    Capability("safety", "SYSTEM", "SAFETY", "ON-DEVICE", ["NSFW classifier", "Audit"]),
    Capability("communication", "SYSTEM", "COMMUNICATION", "ADAPTERS", ["Video", "Audio", "Screen Share"]),
]


def _auth(authorization: str | None) -> None:
    if not API_KEY:
        raise HTTPException(status_code=503, detail="Backend API_KEY is not configured")
    if authorization is None or not hmac.compare_digest(authorization, f"Bearer {API_KEY}"):
        raise HTTPException(status_code=401, detail="Unauthorized")


@router.get("/capabilities")
async def capabilities(authorization: str | None = Header(default=None)):
    _auth(authorization)
    return {
        "version": "1.0",
        "capabilities": [asdict(item) for item in CAPABILITIES],
        "governance": {
            "authority_boundary": True,
            "spend_rate_limits": True,
            "action_receipts": True,
            "revocation": True,
            "credential_handoff": True,
        },
    }
