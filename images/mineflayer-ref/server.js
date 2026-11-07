'use strict';

// Reference client driver sidecar (ADR-0004, client-driver spec section 4). One Mineflayer bot per WebSocket
// connection, exposed as JSON-RPC 2.0. It is only the reference for differential runs: it never answers from a
// prediction, and every value it returns is what the server sent.

const mineflayer = require('mineflayer');
const { WebSocketServer } = require('ws');

// Mineflayer 4.39.0 speaks up to 26.1 (minecraft-protocol 1.68.0). This is the protocol the Java proxy reports
// (versions.yml, drivers.mineflayer-ref.max-native). Forcing it stops Mineflayer from taking the version from the
// server's ping, which would make the bot speak the server's own protocol and skip ViaProxy.
const MC_VERSION = '26.1';
const PORT = 8765;
const PLAYER_CHAT = /^<[^>]+> /;

const wss = new WebSocketServer({ port: PORT, host: '0.0.0.0' });

wss.on('connection', (ws) => {
  let bot = null;
  let disconnected = false;

  const send = (message) => ws.send(JSON.stringify(message));
  const notify = (method, params) => send({ jsonrpc: '2.0', method, params });
  const reply = (id, result) => send({ jsonrpc: '2.0', id, result: result === undefined ? null : result });
  const fail = (id, code, message) => send({ jsonrpc: '2.0', id, error: { code, message } });

  const endSession = (reason) => {
    if (!disconnected) {
      disconnected = true;
      notify('disconnect', { reason: String(reason) });
    }
  };

  // Replies once: at spawn, or with the first error or disconnect. The Java side bounds the wait with its timeout.
  const connect = (id, params) => {
    let settled = false;
    const settle = (error) => {
      if (settled) {
        return;
      }
      settled = true;
      if (error) {
        fail(id, -32000, String(error.message));
      } else {
        reply(id, null);
      }
    };
    bot = mineflayer.createBot({
      host: params.host,
      port: params.port,
      username: params.username,
      auth: 'offline',
      version: MC_VERSION,
    });
    bot.on('message', (json) => {
      const text = json.toString();
      notify('chat', { text, system: !PLAYER_CHAT.test(text) });
    });
    bot.on('error', (error) => {
      notify('error', { message: String(error && error.message) });
      settle(error);
    });
    bot.on('kicked', (reason) => {
      endSession(reason);
      settle(new Error('kicked: ' + reason));
    });
    bot.on('end', (reason) => {
      endSession(reason);
      settle(new Error('disconnected: ' + reason));
    });
    bot.once('spawn', () => settle(null));
  };

  ws.on('message', (raw) => {
    let request;
    try {
      request = JSON.parse(raw.toString());
    } catch (error) {
      fail(null, -32700, 'parse error: ' + error.message);
      return;
    }
    const { id, method } = request;
    const params = request.params || {};

    if (method === 'connect') {
      if (bot !== null) {
        fail(id, -32001, 'already connected');
        return;
      }
      connect(id, params);
      return;
    }
    if (bot === null) {
      fail(id, -32002, 'not connected');
      return;
    }
    switch (method) {
      case 'chat':
        bot.chat(String(params.message));
        reply(id, null);
        break;
      case 'command':
        bot.chat('/' + String(params.command));
        reply(id, null);
        break;
      case 'state': {
        const position = bot.entity ? bot.entity.position : null;
        const level = bot.experience && bot.experience.level !== undefined ? bot.experience.level : null;
        reply(id, {
          position: position ? [position.x, position.y, position.z] : null,
          experienceLevel: level,
        });
        break;
      }
      case 'close':
        bot.quit('proofstone closing');
        reply(id, null);
        break;
      default:
        fail(id, -32601, 'method not found: ' + method);
    }
  });

  ws.on('close', () => {
    if (bot !== null) {
      bot.quit('client disconnected');
      bot = null;
    }
  });
});
