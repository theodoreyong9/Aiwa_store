// Aiwa MCP door: a Cloudflare Worker that other agents (ChatGPT, Claude, …) call as a remote MCP server.
// It does ONE thing: it drops the message on the person's private ntfy channel; the phone, which already listens to that channel, announces it.
// Nothing is executed and nothing is sent anywhere else: the person decides what to do with the message on the phone.
//
// Two secrets, set in the Worker's Settings → Variables and Secrets (never in this file, never in a repository):
//   NTFY_TOPIC : the channel shown in the Aiwa app (Settings → Agents externes)
//   MCP_KEY    : the key shown in the same screen
// The address to give the other agent is:  https://<your-worker>.workers.dev/<MCP_KEY>/mcp
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
    if (parts.length !== 2 || parts[1] !== "mcp" || !sameKey(parts[0], env.MCP_KEY || "")) {
      return new Response("not found", { status: 404 });
    }
    if (request.method === "GET") return new Response("method not allowed", { status: 405 });
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
        serverInfo: { name: "aiwa", version: "1" },
      });
    }
    if (method === "ping") return rpc(id, {});
    if (method === "tools/list") return rpc(id, { tools: [TOOL] });
    if (method === "tools/call") {
      const args = (params && params.arguments) || {};
      if (!params || params.name !== TOOL.name) return fail(id, -32602, "unknown tool");
      const text = String(args.message || "").replace(/[\u0000-\u001f]+/g, " ").trim().slice(0, 600);
      const from = String(args.from || "un agent externe").replace(/[\u0000-\u001f]+/g, " ").trim().slice(0, 40);
      if (!text) return rpc(id, { isError: true, content: [{ type: "text", text: "Empty message." }] });
      if (!env.NTFY_TOPIC) return rpc(id, { isError: true, content: [{ type: "text", text: "The door is not set up." }] });
      let sent;
      try {
        sent = await fetch("https://ntfy.sh/" + encodeURIComponent(String(env.NTFY_TOPIC).trim()), {
          method: "POST",
          headers: { Title: "aiwa-ext", "User-Agent": "aiwa-door" },
          body: JSON.stringify({ from, message: text }),
        });
      } catch (err) {
        return rpc(id, { isError: true, content: [{ type: "text", text: "The relay could not be reached: " + String(err).slice(0, 120) }] });
      }
      if (!sent.ok) {
        const why = (await sent.text().catch(() => "")).slice(0, 160);
        return rpc(id, { isError: true, content: [{ type: "text", text: "The relay answered " + sent.status + ": " + why }] });
      }
      return rpc(id, { content: [{ type: "text", text: "Message left for the person's phone. It does not mean it was read: they decide what to do with it." }] });
    }
    return fail(id, -32601, "method not found");
  },
};
