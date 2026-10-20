// Aiwa MCP door: a Cloudflare Worker that other agents (ChatGPT, Claude, …) call as a remote MCP server.
// It does ONE thing: it keeps the message in a small Cloudflare KV store (binding INBOX); the phone comes to fetch it (every 30 s or so) and announces it.
// Nothing is executed and nothing is sent anywhere else: the person decides what to do with the message on the phone.
//
// Set up in the Cloudflare dashboard (never in this file, never in a repository):
//   a KV namespace bound to this Worker under the variable name INBOX (Settings → Bindings)
//   a secret MCP_KEY: a long random key of your choice (Settings → Variables and Secrets)
// The address to give the other agent is:  https://<your-worker>.workers.dev/<MCP_KEY>/mcp
// The same address (without /mcp) and the key go in the Aiwa app (Settings → Agents externes), which fetches the messages from  /<MCP_KEY>/inbox
const TOOL = {
  name: "envoyer_au_super_agent",
  description: "Send a short message to the person's Aiwa super agent. The person hears it on their phone and decides what to do; nothing is done automatically. Use it to tell them something or to ask them a question.",
  inputSchema: {
    type: "object",
    properties: {
      message: { type: "string", description: "The message, 600 characters at most." },
      from: { type: "string", description: "Who is writing (your name, e.g. ChatGPT)." },
    },
    required: ["message"],
  },
};

const READ_TOOL = {
  name: "lire_les_reponses",
  description: "Read the replies the person gave to your messages (they wait here until you read them). Call it after sending a message, or later, to see whether the person answered.",
  inputSchema: { type: "object", properties: {} },
};

const json = (body, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
const rpc = (id, result) => json({ jsonrpc: "2.0", id, result });
const fail = (id, code, message) => json({ jsonrpc: "2.0", id, error: { code, message } });

function sameKey(a, b) {
  if (typeof a !== "string" || typeof b !== "string" || a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

export default {
  async fetch(request, env) {
    const parts = new URL(request.url).pathname.split("/").filter(Boolean);
    if (parts.length !== 2 || !sameKey(parts[0], env.MCP_KEY || "") || !["mcp", "inbox", "reply"].includes(parts[1])) {
      return new Response("not found", { status: 404 });
    }
    if (parts[1] === "inbox") return inbox(request, env);
    if (parts[1] === "reply") return reply(request, env);
    if (request.method !== "POST") return new Response("method not allowed", { status: 405 });
    let msg;
    try { msg = await request.json(); } catch { return fail(null, -32700, "parse error"); }
    if (Array.isArray(msg)) return fail(null, -32600, "batches are not supported");
    const { id, method, params } = msg || {};
    if (id === undefined) return new Response(null, { status: 202 });      // a notification (notifications/initialized…)
    if (method === "initialize") {
      return rpc(id, {
        protocolVersion: (params && params.protocolVersion) || "2025-03-26",
        capabilities: { tools: {} },
        serverInfo: { name: "aiwa", version: "2" },
      });
    }
    if (method === "ping") return rpc(id, {});
    if (method === "tools/list") return rpc(id, { tools: [TOOL, READ_TOOL] });
    if (method === "tools/call") {
      const args = (params && params.arguments) || {};
      if (params && params.name === READ_TOOL.name) {
        const got = await takeReplies(env);
        return rpc(id, { content: [{ type: "text", text: got.length ? got.map((r) => "Reply from the person" + (r.to ? " (to " + r.to + ")" : "") + ": " + r.text).join("\n") : "No reply yet." }] });
      }
      if (!params || params.name !== TOOL.name) return fail(id, -32602, "unknown tool");
      const text = String(args.message || "").replace(/[\u0000-\u001f]+/g, " ").trim().slice(0, 600);
      const from = String(args.from || "un agent externe").replace(/[\u0000-\u001f]+/g, " ").trim().slice(0, 40);
      if (!text) return rpc(id, { isError: true, content: [{ type: "text", text: "Empty message." }] });
      if (!env.INBOX) return rpc(id, { isError: true, content: [{ type: "text", text: "The door is not set up: no KV binding named INBOX." }] });
      try {
        const now = Date.now();
        await env.INBOX.put("m:" + String(now).padStart(15, "0") + ":" + crypto.randomUUID().slice(0, 8), JSON.stringify({ who: from, text, ts: Math.floor(now / 1000) }), { expirationTtl: 86400 });
      } catch (err) {
        return rpc(id, { isError: true, content: [{ type: "text", text: "The message could not be stored: " + String(err).slice(0, 120) }] });
      }
      const waiting = await takeReplies(env);
      const tail = waiting.length ? "\n" + waiting.map((r) => "Reply from the person" + (r.to ? " (to " + r.to + ")" : "") + ": " + r.text).join("\n") : "";
      return rpc(id, { content: [{ type: "text", text: "Message left for the person's phone. It does not mean it was read: they decide what to do with it. Call lire_les_reponses later to see their answer." + tail }] });
    }
    return fail(id, -32601, "method not found");
  },
};

// The phone fetches what is waiting; what it takes is removed, so that a message is told once.
async function inbox(request, env) {
  if (request.method !== "GET") return new Response("method not allowed", { status: 405 });
  if (!env.INBOX) return json({ messages: [] });
  const listed = await env.INBOX.list({ prefix: "m:", limit: 20 });
  const messages = [];
  for (const key of listed.keys) {
    const raw = await env.INBOX.get(key.name);
    await env.INBOX.delete(key.name);
    try { messages.push(JSON.parse(raw)); } catch { /* a damaged entry is dropped */ }
  }
  return json({ messages });
}

// The person's reply, left by the phone: it waits for the agent to read it (an agent cannot be called, it has to come and look).
async function reply(request, env) {
  if (request.method !== "POST") return new Response("method not allowed", { status: 405 });
  if (!env.INBOX) return json({ ok: false });
  let body;
  try { body = await request.json(); } catch { return json({ ok: false }); }
  const text = String((body && body.text) || "").replace(/[\u0000-\u001f]+/g, " ").trim().slice(0, 600);
  const to = String((body && body.to) || "").replace(/[\u0000-\u001f]+/g, " ").trim().slice(0, 40);
  if (!text) return json({ ok: false });
  const now = Date.now();
  await env.INBOX.put("r:" + String(now).padStart(15, "0") + ":" + crypto.randomUUID().slice(0, 8), JSON.stringify({ to, text, ts: Math.floor(now / 1000) }), { expirationTtl: 86400 });
  return json({ ok: true });
}

async function takeReplies(env) {
  if (!env.INBOX) return [];
  const listed = await env.INBOX.list({ prefix: "r:", limit: 20 });
  const out = [];
  for (const key of listed.keys) {
    const raw = await env.INBOX.get(key.name);
    await env.INBOX.delete(key.name);
    try { out.push(JSON.parse(raw)); } catch { /* dropped */ }
  }
  return out;
}
