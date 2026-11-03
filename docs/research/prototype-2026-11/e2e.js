// Throwaway prototype: real Paper server + Disenchantment jar + mineflayer bot + RCON assertions.
// Usage: node e2e.js [host] [mcVersion]
const mineflayer = require('mineflayer')
const { Rcon } = require('rcon-client')

const HOST = process.argv[2] || '127.0.0.1'
const VERSION = process.argv[3] || '1.21.11'
const NAME = 'E2EBot'
const sleep = (ms) => new Promise(r => setTimeout(r, ms))

// prismarine-item's .enchants ignores the 1.20.5+ stored_enchantments component, read components directly
let registry
function enchOf(item) {
  if (!item) return {}
  const c = item.componentMap?.get('stored_enchantments') || item.componentMap?.get('enchantments')
  const list = c ? c.data?.enchantments ?? c.data : item.enchants
  return Object.fromEntries((list || []).map(e => [e.name ?? registry?.enchantments[e.id]?.name ?? e.id, e.lvl ?? e.level]))
}

function check(cond, msg) {
  console.log(`${cond ? 'PASS' : 'FAIL'}  ${msg}`)
  if (!cond) process.exitCode = 1
}

async function waitFor(pred, timeoutMs, label) {
  const end = Date.now() + timeoutMs
  while (Date.now() < end) {
    const v = await pred()
    if (v) return v
    await sleep(100)
  }
  throw new Error(`timeout: ${label}`)
}

;(async () => {
  const t0 = Date.now()
  const rcon = await Rcon.connect({ host: HOST, port: 25575, password: 'e2e' })
  const cmd = async (c) => { const r = await rcon.send(c); console.log(`  rcon> ${c}\n        ${r.replace(/\n/g, ' ')}`); return r }

  // 1. Round trip: bot joins, runs a command, receives the result as a chat/system message
  const bot = mineflayer.createBot({ host: HOST, port: 25565, username: NAME, version: VERSION, auth: 'offline' })
  await new Promise((res, rej) => { bot.once('spawn', res); bot.once('error', rej); bot.once('kicked', rej) })
  registry = bot.registry
  console.log(`joined in ${Date.now() - t0} ms`)

  await cmd(`op ${NAME}`)
  const reply = new Promise(res => bot.on('messagestr', (m) => { if (/Disenchantment/i.test(m)) res(m) }))
  bot.chat('/version Disenchantment')
  const got = await Promise.race([reply, sleep(5000).then(() => null)])
  check(!!got, `command round trip: "${got}"`)

  // 2. Arrange (server side, via RCON)
  await cmd(`deop ${NAME}`)
  await cmd(`gamemode survival ${NAME}`)
  await cmd(`clear ${NAME}`)
  await cmd(`xp set ${NAME} 0 points`)
  await cmd(`xp set ${NAME} 30 levels`)
  // item syntax churn: 1.20.5+ data components vs. legacy NBT
  const components = !/^1\.(1[0-9]|20\.[0-4])(\.|$)/.test(VERSION)
  await cmd(components
    ? `give ${NAME} diamond_sword[enchantments={sharpness:5,unbreaking:3}]`
    : `give ${NAME} diamond_sword{Enchantments:[{id:"minecraft:sharpness",lvl:5s},{id:"minecraft:unbreaking",lvl:3s}]}`)
  await cmd(`give ${NAME} book`)
  await cmd(`execute at ${NAME} run setblock ~2 ~ ~ anvil`)
  await waitFor(() => bot.inventory.items().length >= 2 && bot.experience.level === 30, 5000, 'items + xp synced')

  // 3. Act (client side, as a real player)
  const anvilBlock = await waitFor(() => bot.findBlock({ matching: b => b.name === 'anvil', maxDistance: 6 }), 5000, 'anvil visible')
  await bot.lookAt(anvilBlock.position.offset(0.5, 0.5, 0.5), true)
  const win = await bot.openBlock(anvilBlock)
  console.log(`opened window type=${win.type} slots=${win.slots.length}`)
  const sword = win.items().find(i => i.name === 'diamond_sword')
  check(enchOf(sword).sharpness === 5, `client: sword starts with ${JSON.stringify(enchOf(sword))}`)
  const book = win.items().find(i => i.name === 'book')
  await bot.clickWindow(sword.slot, 0, 0); await bot.clickWindow(0, 0, 0)
  await bot.clickWindow(book.slot, 0, 0); await bot.clickWindow(1, 0, 0)
  const preview = await waitFor(() => win.slots[2], 5000, 'result slot populated by plugin')
  console.log(`result preview: ${preview.name} ${JSON.stringify(enchOf(preview))}`)
  check(preview.name === 'enchanted_book', 'preview is enchanted_book')

  await bot.clickWindow(2, 0, 0) // normal left click on result -> plugin puts book on cursor
  await sleep(300)
  const empty = win.firstEmptyInventorySlot()
  await bot.clickWindow(empty, 0, 0) // drop cursor into inventory
  await sleep(300)
  bot.closeWindow(win)
  // never assert on a fixed sleep: poll until the client inventory reflects the server state
  await waitFor(() => { const n = bot.inventory.items().map(i => i.name); return n.includes('enchanted_book') && n.includes('diamond_sword') }, 5000, 'inventory synced after close')
    .catch(e => console.log(`  warn: ${e.message}; items=${JSON.stringify(bot.inventory.items().map(i => i.name))}`))

  // 4. Assert: client view
  const items = bot.inventory.items()
  const outBook = items.find(i => i.name === 'enchanted_book')
  const outSword = items.find(i => i.name === 'diamond_sword')
  const ench = enchOf(outBook)
  check(ench.sharpness === 5 && ench.unbreaking === 3, `client: book enchants ${JSON.stringify(ench)}`)
  check(outSword && Object.keys(enchOf(outSword)).length === 0, `client: sword stripped ${JSON.stringify(enchOf(outSword))}`)
  check(bot.experience.level < 30, `client: xp level 30 -> ${bot.experience.level}`)

  // 5. Assert: server truth (RCON)
  // RCON truncates long SNBT ("..."), so query the narrow path; path differs per item format
  const inv = await cmd(components
    ? `data get entity ${NAME} Inventory[{id:"minecraft:enchanted_book"}].components."minecraft:stored_enchantments"`
    : `data get entity ${NAME} Inventory[{id:"minecraft:enchanted_book"}].tag.StoredEnchantments`)
  check(/sharpness"?(", lvl)?: 5/.test(inv), 'server: inventory has book with sharpness 5')
  const lvl = await cmd(`xp query ${NAME} levels`)
  const serverLevel = Number((lvl.match(/has (\d+) experience levels/) || [])[1])
  check(serverLevel < 30, `server: xp dropped (${lvl.trim() || 'empty RCON reply'})`)

  console.log(`total ${Date.now() - t0} ms`)
  bot.quit(); await rcon.end()
})().catch(e => { console.error('ERROR', e); process.exit(2) })
