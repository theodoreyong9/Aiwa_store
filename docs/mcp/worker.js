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
        await push(env, "in", { who: from, text, ts: Math.floor(Date.now() / 1000) });
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

// Storage: ONE key per direction (« in »: messages for the person, « out »: the person's replies), each a JSON list kept 24 h. Only reads and writes: no `list` operation
// (the free plan allows 1 000 of them a day and 100 000 reads); a poll that finds nothing writes nothing.
const KEEP = 24 * 3600, MAX = 50;
async function load(env, key) {
  let items = [];
  try { items = (await env.INBOX.get(key, "json")) || []; } catch { items = []; }
  const now = Math.floor(Date.now() / 1000);
  return Array.isArray(items) ? items.filter((m) => m && now - (m.ts || 0) < KEEP) : [];
}
async function push(env, key, item) {
  const items = await load(env, key);
  items.push(item);
  await env.INBOX.put(key, JSON.stringify(items.slice(-MAX)), { expirationTtl: KEEP });
}
async function take(env, key) {
  const items = await load(env, key);
  if (items.length) await env.INBOX.put(key, "[]", { expirationTtl: KEEP });       // what is taken is removed: a message is told once
  return items;
}

// The phone fetches what is waiting.
async function inbox(request, env) {
  if (request.method !== "GET") return new Response("method not allowed", { status: 405 });
  if (!env.INBOX) return json({ messages: [] });
  return json({ messages: await take(env, "in") });
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
  await push(env, "out", { to, text, ts: Math.floor(Date.now() / 1000) });
  return json({ ok: true });
}

async function takeReplies(env) {
  if (!env.INBOX) return [];
  return take(env, "out");
}
