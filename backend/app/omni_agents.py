from __future__ import annotations
from dataclasses import dataclass, asdict
import hmac
import os
from fastapi import APIRouter, Header, HTTPException

router = APIRouter(prefix="/api/omni/agents", tags=["omni-agents"])
API_KEY = os.getenv("API_KEY", "").strip()

@dataclass(frozen=True)
class AgentSpec:
    id: str
    title: str
    responsibility: str

AGENTS = (
    AgentSpec("A01", "Intent & Creative Director", "intent, ambiguity and target specification"),
    AgentSpec("A02", "Reference & Identity Analyst", "reference roles, identity separation and conflicts"),
    AgentSpec("A03", "Image Production Agent", "image production, masks, composition and change control"),
    AgentSpec("A04", "Model & Pipeline Engineer", "model capability, limits, formats and cost"),
    AgentSpec("A05", "Visual QA & Continuity", "identity drift, anatomy, continuity and specification QA"),
    AgentSpec("A06", "Video & Cinematic Agent", "storyboard, blocking, timeline, camera and video continuity"),
)

def _auth(authorization: str | None) -> None:
    if not API_KEY:
        raise HTTPException(status_code=503, detail="Backend API_KEY is not configured")
    if authorization is None or not hmac.compare_digest(authorization, f"Bearer {API_KEY}"):
        raise HTTPException(status_code=401, detail="Unauthorized")

@router.get("")
async def list_agents(authorization: str | None = Header(default=None)):
    _auth(authorization)
    return {"agents": [asdict(agent) for agent in AGENTS],
            "authority_boundary": "Autonomy is configured outside prompts and cannot be expanded by untrusted media or web content.",
            "credential_handoff": "Credentials remain outside agent context.",
            "revocation": "Delegation can be revoked independently of project configuration."}
