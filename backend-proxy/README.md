# Antigravity Agent Secure Backend Proxy

Zero-Client-Secret architecture for your Mobile & Desktop Antigravity AI Agents.

## Why Use a Backend Proxy?
- **Zero Key Exposure**: Your Google Gemini API Key (`AIza...`) stays completely on the server. Even if someone decompiles your Android APK, there is no API key to steal.
- **Spending & Rate Limit Controls**: Enforces IP/Token rate limiting and request size bounds.
- **Short-Lived Token Auth**: Clients connect using a simple pre-shared Bearer token (`PROXY_AUTH_TOKEN`) or short-lived JWT.

---

## Deployment Option 1: Cloudflare Workers (Free & Instant Global Edge)
1. Install Wrangler:
   ```bash
   npm install -g wrangler
   ```
2. Set your secrets:
   ```bash
   wrangler secret put GEMINI_API_KEY
   wrangler secret put PROXY_AUTH_TOKEN
   ```
3. Deploy:
   ```bash
   wrangler deploy cloudflare-worker.js --name antigravity-agent-proxy
   ```
4. Copy your worker URL (e.g. `https://antigravity-agent-proxy.your-account.workers.dev/api/v1/chat`) into the **Antigravity Mobile App Settings > Backend Proxy Server**.

---

## Deployment Option 2: Python FastAPI (Home Server / Tailscale / VPS)
1. Install dependencies:
   ```bash
   pip install fastapi uvicorn httpx pydantic
   ```
2. Run with environment variables:
   ```bash
   export GEMINI_API_KEY="AIzaSy..."
   export PROXY_AUTH_TOKEN="my-super-secret-passphrase"
   python server.py
   ```
3. Connect your phone using your internal Tailscale IP:
   `http://100.x.y.z:8000/api/v1/chat`
