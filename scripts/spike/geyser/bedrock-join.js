// T-M9-01 spike client, not production code. A minimal offline Bedrock join with PrismarineJS bedrock-protocol (Node).
// offline: true makes the library sign its own chain; no Xbox/Microsoft call is made (prismarine-auth is not used) and
// no account, token or secret exists. The RakNet backend is raknet-native (its prebuilt .node ships inside the npm tarball;
// the install script, which only runs a build check, is skipped: npm install --ignore-scripts). The pure JS backend
// (PS_RAKNET=jsp-raknet) sends RakNet protocol 10 and Geyser answers "incompatible protocol" (it speaks 11): spike finding.
// Usage: node bedrock-join.js <host> <port> <name> <timeout s> [hook shell command]
// Every packet name received is printed as "PKT <ms> <name>", the evidence for "packets up to spawn". The hook runs at
// spawn (the agent players read of the spike script), then the client closes.
'use strict'
const bedrock = require('bedrock-protocol')
const { spawnSync } = require('child_process')

const [host, portArg, name, timeoutArg, hook] = process.argv.slice(2)
const t0 = Date.now()
let stage = 'start'
const log = (s) => console.log(`[${Date.now() - t0} ms] ${s}`)
let finished = false
const finish = (result, code) => {
  if (finished) return
  finished = true
  log(`RESULT ${result}`)
  try { client.close() } catch (_) { /* already closed */ }
  process.exit(code)
}

const client = bedrock.createClient({
  host,
  port: Number(portArg),
  username: name,
  offline: true,
  version: process.env.PS_BEDROCK_VERSION || '1.26.60',
  raknetBackend: process.env.PS_RAKNET || 'raknet-native',
  skipPing: true,
  connectTimeout: Number(timeoutArg) * 1000
})
log(`client version ${client.options.version} protocol ${client.options.protocolVersion ?? 'n/a'}`)
stage = 'raknet-connect'

client.on('connect', () => { stage = 'raknet-connected'; log('connect') })
client.on('loggingIn', () => { stage = 'login'; log('loggingIn') })
client.on('session', () => { stage = 'session'; log('session') })
client.on('join', () => { stage = 'join'; log('join (login done)') })
client.on('packet', (p) => {
  const n = (p && p.data && p.data.name) || (p && p.name) || '?'
  log(`PKT ${n}`)
  if (n === 'start_game') stage = 'start-game'
  if (n === 'resource_packs_info') stage = 'resource-packs'
})
client.on('error', (e) => finish(`ERROR stage=${stage} ${e && e.message}`, 4))
client.on('disconnect', (p) => finish(`DISCONNECT stage=${stage} reason=${JSON.stringify(p && (p.message || p.reason))}`, 3))
client.on('close', () => finish(`CLOSED stage=${stage}`, 3))
client.on('spawn', () => {
  stage = 'spawn'
  log('spawn')
  let out = 'SPAWNED'
  if (hook) {
    const r = spawnSync(process.env.PS_BASH || 'bash', ['-c', hook], { encoding: 'utf8' })
    log(`HOOK rc=${r.status} ${(r.stdout || '').trim().replace(/\n/g, ' | ')}`)
    out = r.status === 0 ? 'SPAWNED hook-ok' : `SPAWNED hook-failed rc=${r.status}`
  }
  finish(out, out.startsWith('SPAWNED hook-failed') ? 5 : 0)
})
// The await bound (the stage is named in the result). Not a sleep before an action.
setTimeout(() => finish(`TIMEOUT stage=${stage}`, 3), Number(timeoutArg) * 1000)
