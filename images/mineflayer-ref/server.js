'use strict';

// Reference client driver sidecar (ADR-0004, client-driver spec section 4). One Mineflayer bot per WebSocket
// connection, exposed as JSON-RPC 2.0. It is only the reference for differential runs: it never answers from a
// prediction, and every value it returns is what the server sent.

const mineflayer = require('mineflayer');
const mcData = require('minecraft-data');
const { Vec3 } = require('vec3');
const { WebSocketServer } = require('ws');

// Mineflayer 4.39.0 speaks up to 26.1 (minecraft-protocol 1.68.0). This is the protocol the Java proxy reports
// (versions.yml, drivers.mineflayer-ref.max-native). Forcing it stops Mineflayer from taking the version from the
// server's ping, which would make the bot speak the server's own protocol and skip ViaProxy.
const MC_VERSION = '26.1';
const PORT = 8765;
const PLAYER_CHAT = /^<[^>]+> /;
const DATA = mcData(MC_VERSION);
// The enchantment registry ids in data components are network ids; minecraft-data numbers the same registry.
const ENCHANTMENT_NAME = new Map(Object.values(DATA.enchantments).map((e) => [e.id, e.name]));

const wss = new WebSocketServer({ port: PORT, host: '0.0.0.0' });

// Component data arrives as NBT ({type, value}) for the custom name and lore. Unwrap it to plain JSON first.
function unwrapNbt(node) {
  if (node === null || typeof node !== 'object' || !('type' in node) || !('value' in node)) {
    return node;
  }
  if (node.type === 'compound') {
    const out = {};
    for (const key of Object.keys(node.value)) {
      out[key] = unwrapNbt(node.value[key]);
    }
    return out;
  }
  if (node.type === 'list') {
    return node.value.value.map(unwrapNbt);
  }
  return node.value;
}

// Text of a chat component: literal text, translation keys, then the extra children. No formatting.
function textOf(raw) {
  const component = unwrapNbt(raw);
  if (component === null || component === undefined) {
    return '';
  }
  if (typeof component === 'string') {
    return component;
  }
  if (Array.isArray(component)) {
    return component.map(textOf).join('');
  }
  let text = typeof component.text === 'string' ? component.text : '';
  if (typeof component.translate === 'string') {
    text += component.translate;
  }
  if (Array.isArray(component.extra)) {
    text += component.extra.map(textOf).join('');
  }
  return text;
}

// One stack as the server sent it: the item type by name, the count, and the components the Java side reads.
// Mineflayer's own enchants getter ignores stored_enchantments, so both enchantment components are read here.
function enchantments(component) {
  const out = {};
  const entries = component && component.data && component.data.enchantments;
  for (const entry of entries || []) {
    const name = ENCHANTMENT_NAME.get(entry.id);
    if (name === undefined) {
      throw new Error('unknown enchantment registry id ' + entry.id);
    }
    out[name] = entry.level;
  }
  return out;
}

function stack(item) {
  if (!item) {
    return { itemId: 0, type: 'air', count: 0, enchants: {}, storedEnchants: {}, name: null, lore: [], damage: 0,
      repairCost: 0 };
  }
  const components = item.componentMap || new Map();
  return {
    itemId: item.type,
    type: item.name,
    count: item.count,
    enchants: enchantments(components.get('enchantments')),
    storedEnchants: enchantments(components.get('stored_enchantments')),
    name: components.has('custom_name') ? textOf(components.get('custom_name').data) : null,
    lore: components.has('lore') ? unwrapNbt(components.get('lore').data).map(textOf) : [],
    damage: components.has('damage') ? components.get('damage').data : 0,
    repairCost: components.has('repair_cost') ? components.get('repair_cost').data : 0,
  };
}

wss.on('connection', (ws) => {
  let bot = null;
  let disconnected = false;
  // The server's latest state id, from the packets that carry one (mineflayer keeps its own copy, private to it).
  let stateId = -1;

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
    bot._client.on('window_items', (packet) => { stateId = packet.stateId; });
    bot._client.on('set_slot', (packet) => { stateId = packet.stateId; });
    bot.on('message', (json) => {
      const text = json.toString();
      notify('chat', { text, system: !PLAYER_CHAT.test(text) });
    });
    bot.on('windowOpen', (window) => notify('windowOpen', { id: window.id, type: window.type }));
    // Mineflayer emits windowClose with null when the window it closes was never tracked (inventory.js).
    bot.on('windowClose', (window) => notify('windowClose', { id: window ? window.id : null }));
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

  // The window the client has open, or the player inventory (window 0) when none is open.
  const snapshot = () => {
    const window = bot.currentWindow;
    if (!window) {
      return inventorySnapshot();
    }
    return { open: true, id: window.id, type: window.type, title: textOf(window.title), stateId,
      slots: window.slots.map(stack), cursor: stack(window.selectedItem) };
  };

  // The player inventory (window 0), even while a container is open.
  const inventorySnapshot = () => ({ open: bot.currentWindow !== null, id: 0, type: 'minecraft:inventory',
    title: 'Inventory', stateId, slots: bot.inventory.slots.map(stack), cursor: stack(bot.inventory.selectedItem) });

  // A click sends the packet without a predicted change and does not touch local state: the server answers with
  // a sync (client-driver spec section 2). Mineflayer's own clickWindow would predict the result locally.
  const click = (params) => {
    const window = bot.currentWindow || bot.inventory;
    bot._client.write('window_click', {
      windowId: window.id,
      stateId,
      slot: params.slot,
      mouseButton: params.button,
      mode: params.mode,
      changedSlots: [],
      cursorItem: null,
    });
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
    try {
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
        case 'window':
          reply(id, snapshot());
          break;
        case 'inventory':
          reply(id, inventorySnapshot());
          break;
        case 'click':
          click(params);
          reply(id, null);
          break;
        case 'closeWindow':
          if (bot.currentWindow) {
            // Mineflayer copies the container's player slots back on close, which is what the server does.
            bot.closeWindow(bot.currentWindow);
          }
          reply(id, null);
          break;
        case 'rename':
          bot._client.write('name_item', { name: String(params.name) });
          reply(id, null);
          break;
        case 'useBlock': {
          const block = bot.blockAt(new Vec3(params.x, params.y, params.z));
          if (!block) {
            fail(id, -32003, 'no block loaded at ' + [params.x, params.y, params.z]);
            break;
          }
          bot.activateBlock(block).then(() => reply(id, null), (error) => fail(id, -32000, String(error.message)));
          break;
        }
        case 'close':
          bot.quit('proofstone closing');
          reply(id, null);
          break;
        default:
          fail(id, -32601, 'method not found: ' + method);
      }
    } catch (error) {
      fail(id, -32000, String(error.message));
    }
  });

  ws.on('close', () => {
    if (bot !== null) {
      bot.quit('client disconnected');
      bot = null;
    }
  });
});
