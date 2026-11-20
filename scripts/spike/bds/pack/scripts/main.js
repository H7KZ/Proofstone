// T-M10-01 spike, not production code. A read-only agent sketch: BDS scripts cannot listen, so the pack polls the harness
// (the "inverted transport") and posts answers and events back. Observe-only: the only server APIs used are
// world.getAllPlayers() (read), Player.name/id (read), world.afterEvents.playerJoin (an after-event, which cannot cancel)
// and system.run (scheduling). No command, no dynamic property, no block, entity or world write. The spike script greps
// this file for every write-capable call (bds-spike.sh, "observe-only check").
import { world, system } from "@minecraft/server";
import { http, HttpRequest, HttpRequestMethod, HttpHeader } from "@minecraft/server-net";

const BASE = "http://harness:8080";
const TOKEN = "@TOKEN@";

function request(method, path, body) {
  const r = new HttpRequest(BASE + path);
  r.method = method;
  r.headers = [new HttpHeader("Authorization", "Bearer " + TOKEN), new HttpHeader("Content-Type", "application/json")];
  r.timeout = 30;
  if (body !== undefined) r.body = JSON.stringify(body);
  return http.request(r);
}

function log(msg) {
  console.warn("[proofstone-spike] " + msg);
}

async function measurePing(n) {
  const samples = [];
  for (let i = 0; i < n; i++) {
    const t0 = Date.now();
    const resp = await request(HttpRequestMethod.Get, "/pack/ping");
    const ms = Date.now() - t0;
    if (resp.status !== 200) throw new Error("ping status " + resp.status + " body " + resp.body);
    samples.push(ms);
  }
  const sorted = [...samples].sort((a, b) => a - b);
  const m = { type: "pingRtt", n, minMs: sorted[0], medianMs: sorted[Math.floor(n / 2)], maxMs: sorted[n - 1], samples };
  log("ping " + JSON.stringify(m));
  await request(HttpRequestMethod.Post, "/pack/metric", m);
}

function answer(req) {
  if (req.op === "players") {
    const players = world.getAllPlayers().map((p) => ({ name: p.name, id: p.id }));
    return { id: req.id, ok: true, data: { players } };
  }
  return { id: req.id, ok: false, error: { code: "UNKNOWN_OP", message: "op " + req.op } };
}

async function pollLoop() {
  for (;;) {
    const resp = await request(HttpRequestMethod.Get, "/pack/poll");
    if (resp.status === 204) continue;
    if (resp.status !== 200) throw new Error("poll status " + resp.status + " body " + resp.body);
    const req = JSON.parse(resp.body);
    const ans = answer(req);
    const r2 = await request(HttpRequestMethod.Post, "/pack/answer", ans);
    if (r2.status !== 200) throw new Error("answer status " + r2.status);
  }
}

world.afterEvents.playerJoin.subscribe((e) => {
  const ev = { type: "playerJoin", playerName: e.playerName, playerId: e.playerId, tick: system.currentTick };
  request(HttpRequestMethod.Post, "/pack/event", ev).then(
    (r) => log("event posted " + r.status),
    (err) => log("event post failed " + err)
  );
});

system.run(() => {
  log("started");
  measurePing(20)
    .then(() => pollLoop())
    .catch((err) => log("FAILED " + err + (err && err.stack ? " " + err.stack : "")));
});
