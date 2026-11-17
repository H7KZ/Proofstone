// T-M9-01 measurement tool, not production code. Preloaded into the Node client (node --require net-log.js) to list every host
// name or address the client process looks up or connects to, so "does the client contact any Microsoft or Xbox host" has a
// client-side answer, not only the server's DNS log. It wraps dns.lookup, net.Socket#connect, tls.connect, dgram#send and
// fetch, prints one "NETLOG <kind> <host>" line per use to stdout and changes nothing else.
'use strict'
const dns = require('dns')
const net = require('net')
const tls = require('tls')
const dgram = require('dgram')
const seen = new Set()
const note = (kind, host) => {
  const key = kind + ' ' + host
  if (!seen.has(key)) { seen.add(key); console.log('NETLOG ' + key) }
}
const lookup = dns.lookup
dns.lookup = function (host, ...rest) { note('dns.lookup', String(host)); return lookup.call(this, host, ...rest) }
const connect = net.Socket.prototype.connect
net.Socket.prototype.connect = function (...args) {
  const o = args[0]
  note('tcp.connect', typeof o === 'object' && o ? `${o.host || o.path || '?'}:${o.port || ''}` : `${args[1] || '?'}:${o}`)
  return connect.apply(this, args)
}
const tlsConnect = tls.connect
tls.connect = function (...args) {
  const o = typeof args[0] === 'object' ? args[0] : (typeof args[1] === 'object' ? args[1] : {})
  note('tls.connect', String(o.servername || o.host || '?'))
  return tlsConnect.apply(this, args)
}
const send = dgram.Socket.prototype.send
dgram.Socket.prototype.send = function (...args) {
  const host = args.find((a) => typeof a === 'string' && /[a-z.:0-9]/i.test(a) && !/^\d+$/.test(a))
  const port = args.find((a, i) => typeof a === 'number' && i > 0 && a > 0 && a < 65536)
  note('udp.send', `${host || '?'}:${port || '?'}`)
  return send.apply(this, args)
}
if (typeof globalThis.fetch === 'function') {
  const f = globalThis.fetch
  globalThis.fetch = function (u, ...rest) { note('fetch', String(u && u.url ? u.url : u)); return f.call(this, u, ...rest) }
}
