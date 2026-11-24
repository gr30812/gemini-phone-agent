/**
 * Antigravity Agent Cloudflare Worker Proxy
 * Zero-Client-Secret proxy protecting Gemini API Keys on Cloudflare Global Edge.
 */

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") {
      return new Response(null, {
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "POST, GET, OPTIONS",
          "Access-Control-Allow-Headers": "Authorization, Content-Type",
        },
      });
    }

    const url = new URL(request.url);

    if (url.pathname === "/health") {
      return new Response(JSON.stringify({ status: "healthy", edge: "cloudflare" }), {
        headers: { "Content-Type": "application/json" },
      });
    }

    if (url.pathname === "/api/v1/chat" && request.method === "POST") {
      const authHeader = request.headers.get("Authorization") || "";
      const expectedToken = env.PROXY_AUTH_TOKEN || "antigravity-secret-token";

      if (!authHeader.startsWith("Bearer ") || authHeader.split("Bearer ")[1].trim() !== expectedToken) {
        return new Response(JSON.stringify({ error: "Unauthorized: Invalid or missing proxy token" }), {
          status: 401,
          headers: { "Content-Type": "application/json" },
        });
      }

      if (!env.GEMINI_API_KEY) {
        return new Response(JSON.stringify({ error: "Server configuration error: GEMINI_API_KEY secret missing" }), {
          status: 500,
          headers: { "Content-Type": "application/json" },
        });
      }

      try {
        const body = await request.json();
        const model = body.model || "gemini-3.8-flash";
        const upstreamPayload = body.payload || body;

        const upstreamUrl = `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${env.GEMINI_API_KEY}`;
        const upstreamResp = await fetch(upstreamUrl, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(upstreamPayload),
        });

        const data = await upstreamResp.text();
        return new Response(data, {
          status: upstreamResp.status,
          headers: {
            "Content-Type": "application/json",
            "Access-Control-Allow-Origin": "*",
          },
        });
      } catch (err) {
        return new Response(JSON.stringify({ error: "Internal Gateway Error", details: err.message }), {
          status: 502,
          headers: { "Content-Type": "application/json" },
        });
      }
    }

    return new Response("Not Found", { status: 404 });
  },
};
