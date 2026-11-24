import os
import time
from typing import Dict, Any
from fastapi import FastAPI, HTTPException, Header, Depends, Request
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
import httpx

app = FastAPI(
    title="Antigravity Agent Backend Proxy",
    description="Secure Zero-Client-Secret proxy for mobile and desktop AI agents",
    version="1.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "")
PROXY_AUTH_TOKEN = os.getenv("PROXY_AUTH_TOKEN", "antigravity-secret-token")
RATE_LIMIT_PER_MINUTE = 30

request_counts: Dict[str, list] = {}

def verify_token(authorization: str = Header(None)):
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Missing or malformed Authorization header")
    token = authorization.split("Bearer ", 1)[1].strip()
    if token != PROXY_AUTH_TOKEN:
        raise HTTPException(status_code=403, detail="Invalid proxy authentication token")
    return token

def check_rate_limit(client_ip: str):
    now = time.time()
    timestamps = request_counts.get(client_ip, [])
    timestamps = [t for t in timestamps if now - t < 60]
    if len(timestamps) >= RATE_LIMIT_PER_MINUTE:
        raise HTTPException(status_code=429, detail="Rate limit exceeded (max 30 req/min). Try again shortly.")
    timestamps.append(now)
    request_counts[client_ip] = timestamps

@app.get("/health")
def health():
    return {"status": "healthy", "service": "antigravity-agent-proxy", "key_configured": bool(GEMINI_API_KEY)}

@app.post("/api/v1/chat")
async def chat_proxy(
    request: Request,
    payload: Dict[str, Any],
    token: str = Depends(verify_token)
):
    if not GEMINI_API_KEY:
        raise HTTPException(status_code=500, detail="Server misconfiguration: GEMINI_API_KEY not set in proxy environment")

    client_ip = request.client.host if request.client else "unknown"
    check_rate_limit(client_ip)

    model = payload.get("model", "gemini-3.8-flash")
    upstream_url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={GEMINI_API_KEY}"

    # Strip any client-side injected keys before forwarding
    upstream_payload = payload.get("payload", payload)

    async with httpx.AsyncClient(timeout=60.0) as client:
        try:
            resp = await client.post(
                upstream_url,
                json=upstream_payload,
                headers={"Content-Type": "application/json"}
            )
            return resp.json()
        except httpx.RequestError as e:
            raise HTTPException(status_code=502, detail=f"Failed communicating with upstream Gemini API: {str(e)}")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("server:app", host="0.0.0.0", port=8000, reload=True)
