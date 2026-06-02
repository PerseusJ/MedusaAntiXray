# Medusa-Anti-Xray — Surgical Architectural Blueprint

## Design Decisions Locked In

| Dimension | Decision |
|---|---|
| **Tracked Ores** | Diamond, Gold, Emerald, Ancient Debris only |
| **Algorithm** | Ratio-based: `(valuable_ore_blocks / total_blocks_mined)` |
| **False-Positive Guard** | Exposure-aware: hidden ores score 1.0, air-exposed ores score 0.25 |
| **Fortune Handling** | Count block-break events (not drop quantities); Fortune is naturally irrelevant |
| **Time Window** | Sliding window (configurable, default 30 min); old entries auto-expire |
| **Worlds** | Overworld only for Diamond/Gold/Emerald; Nether only for Ancient Debris |
| **Alert System** | Permission-gated staff chat only; 60s per-player alert cooldown |
| **Commands** | `/medusa reload` + `/medusa check <player>` |
| **Persistence** | Pure in-memory `ConcurrentHashMap`; zero external dependencies |
| **Build Tool** | Maven (existing `pom.xml`), no migration |

---

## Phase 1 — Configuration & Storage Blueprint

### `config.yml` Layout

```yaml
# =============================================
#   Medusa-Anti-Xray Configuration
#   Author: PerseusJ | API: 1.20+
# =============================================

detection:
  # Minimum blocks mined before ratio is evaluated (prevents early false flags)
  min-sample-size: 64
  
  # Ratio of (weighted suspicious ore score / total blocks) to trigger alert
  # 0.08 = ~8% of mined blocks are high-value, well above natural ~1-2%
  alert-threshold: 0.08
  
  # Sliding window duration in minutes
  window-minutes: 30
  
  # Weight applied to ores that had NO exposed face (fully hidden = suspicious)
  hidden-ore-weight: 1.0
  
  # Weight applied to ores that had at least one face exposed to air (cave mining)
  exposed-ore-weight: 0.25

worlds:
  overworld:
    enabled: true
    # The exact world name(s) treated as Overworld. Supports multiple entries.
    names:
      - "world"
    tracked-ores:
      - DIAMOND_ORE
      - DEEPSLATE_DIAMOND_ORE
      - GOLD_ORE
      - DEEPSLATE_GOLD_ORE
      - EMERALD_ORE
      - DEEPSLATE_EMERALD_ORE
    # All stone-family blocks counted as "filler" for ratio denominator
    filler-blocks:
      - STONE
      - DEEPSLATE
      - TUFF
      - COBBLESTONE
      - COBBLED_DEEPSLATE
      - GRAVEL
      - DIRT
      - ANDESITE
      - DIORITE
      - GRANITE

  nether:
    enabled: true
    names:
      - "world_nether"
    tracked-ores:
      - ANCIENT_DEBRIS
    filler-blocks:
      - NETHERRACK
      - BASALT
      - BLACKSTONE
      - SOUL_SAND
      - SOUL_SOIL

alerts:
  # Seconds between repeated alerts for the same player (prevents chat spam)
  cooldown-seconds: 60
  
  # Permission required to receive alerts
  staff-permission: "medusa.staff"
  
  # Message prefix (supports & color codes and hex #RRGGBB)
  prefix: "&8[&4Medusa&8]&r"
  
  # Alert message placeholders: {player}, {ratio}, {score}, {total}
  alert-message: "{prefix} &c⚠ {player} &7may be X-raying! &cRatio: &f{ratio}% &7({score} pts / {total} blocks)"
  
  # Message sent to /medusa check executor
  check-message: "&7Player &f{player} &7| Score: &c{score} &7| Total: &f{total} &7| Ratio: &c{ratio}% &7| Window: &f{window}m"

messages:
  no-permission: "&cYou don't have permission to use this command."
  reload-success: "&aConfiguration reloaded successfully."
  player-not-found: "&cPlayer &f{player} &cnot found or has no data."
  usage-check: "&cUsage: /medusa check <player>"
```

### Memory Cache Architecture

#### Data Model: `PlayerData`
```
PlayerData {
  UUID playerUuid
  String playerName (cached for offline check display)
  Deque<MineEvent> eventWindow        // sliding window of recent events
  long lastAlertTimestamp             // for 60s cooldown enforcement
}

MineEvent {
  long timestamp          // System.currentTimeMillis()
  boolean isValuable      // true = ore in tracked list
  double weight           // 1.0 (hidden) or 0.25 (exposed)
}
```

> **Why `Deque<MineEvent>` instead of counters?**
> A simple counter cannot support a sliding window — you can't "un-count" events older than 30 minutes without storing them. A `Deque` lets us efficiently poll from the front, removing expired entries in O(1) per purge cycle. The tradeoff is slightly more memory (~40 bytes per event object) but with a 30-minute window and normal mining speeds (~200 blocks/hr), a player generates at most ~100 event objects, which is negligible.

#### Cache Lifecycle

| Event | Action |
|---|---|
| `PlayerJoinEvent` | Create fresh `PlayerData` entry in `ConcurrentHashMap<UUID, PlayerData>` |
| `BlockBreakEvent` | Append `MineEvent` to player's deque; purge expired entries from front |
| `PlayerQuitEvent` | Remove UUID key from map — **critical for leak prevention** |
| `/medusa reload` | Call `ConfigManager.reload()` only; do **not** wipe the live player cache |
| Server shutdown (`onDisable`) | Clear the map and null the reference |

#### Leak Prevention Checklist
- ✅ Always remove on `PlayerQuitEvent` (not `PlayerKickEvent` — Spigot fires Quit after Kick anyway)  
- ✅ Use `UUID` as key, never `Player` object (Player references can become stale)
- ✅ Never store `ItemStack` or `World` references inside `PlayerData`
- ✅ The sliding window purge runs inline on every `BlockBreakEvent`, keeping memory bounded without a separate scheduler

---

## Phase 2 — Event Listener & Mathematical Logic

### `BlockBreakListener` Step-by-Step Algorithm

```
ON BlockBreakEvent(event):

  1. GUARD CLAUSES
     └─ if event.isCancelled() → return
     └─ if !(event.getPlayer() instanceof Player) → return
     └─ if player.getGameMode() != SURVIVAL → return (ignore Creative/Spectator)

  2. WORLD SCOPE CHECK
     └─ worldType = classify(player.getWorld().getName())
        → OVERWORLD | NETHER | IGNORED
     └─ if worldType == IGNORED → return

  3. BLOCK CLASSIFICATION
     └─ brokenMaterial = event.getBlock().getType()
     └─ isValuable = trackedOres[worldType].contains(brokenMaterial)
     └─ isFiller = fillerBlocks[worldType].contains(brokenMaterial)
     └─ if !isValuable && !isFiller → return (ignore air, water, wood, etc.)

  4. EXPOSURE CHECK (only if isValuable)
     └─ exposed = false
     └─ for each of 6 BlockFace directions (UP, DOWN, NORTH, SOUTH, EAST, WEST):
          adjacentBlock = event.getBlock().getRelative(face)
          if adjacentBlock.getType() == Material.AIR
          OR adjacentBlock.getType() == Material.CAVE_AIR
          OR adjacentBlock.getType() == Material.VOID_AIR:
               exposed = true; break
     └─ weight = exposed ? config.exposedOreWeight : config.hiddenOreWeight

  5. SLIDING WINDOW PURGE
     └─ cutoff = System.currentTimeMillis() - (windowMinutes * 60_000L)
     └─ while playerData.eventWindow.peekFirst().timestamp < cutoff:
          playerData.eventWindow.pollFirst()

  6. RECORD EVENT
     └─ playerData.eventWindow.addLast(new MineEvent(now, isValuable, weight))

  7. THRESHOLD EVALUATION
     └─ if eventWindow.size() < config.minSampleSize → return (not enough data yet)
     └─ totalBlocks = eventWindow.size()
     └─ suspiciousScore = sum of event.weight for all events where event.isValuable
     └─ ratio = suspiciousScore / totalBlocks
     └─ if ratio < config.alertThreshold → return

  8. ALERT DISPATCH (with cooldown)
     └─ now = System.currentTimeMillis()
     └─ if (now - playerData.lastAlertTimestamp) < (cooldownSeconds * 1000L) → return
     └─ playerData.lastAlertTimestamp = now
     └─ AlertManager.dispatch(player, ratio, suspiciousScore, totalBlocks)
```

### Why This Beats Simple Counters

| Scenario | Counter Approach Result | Ratio + Exposure Result |
|---|---|---|
| Cave miner finds 8 exposed diamonds in 200 blocks | **False Positive** (8 > threshold) | ✅ Safe — score = 8 × 0.25 = 2.0 / 200 = 1% |
| X-rayer finds 8 hidden diamonds in 100 blocks | ✅ Flagged | ✅ Flagged — score = 8 × 1.0 = 8.0 / 100 = 8% |
| New player breaks 10 blocks, finds 1 ore | **False Positive** | ✅ Safe — below min-sample-size |
| Grinder mines 1000 blocks, finds 5 ores | False negative | ✅ Safe — 0.5% ratio, well below 8% |

### Configurable Defaults — Rationale

- **`min-sample-size: 64`** — A player needs at least ~2 minutes of mining before any flag fires. Prevents instant false positives on login.
- **`alert-threshold: 0.08`** — Natural diamond frequency in vanilla is ~0.1% of blocks below Y=16. Even a lucky cave runner rarely exceeds 3-4%. 8% is a 20-40× elevation, indicating deliberate navigation.
- **`hidden-ore-weight: 1.0` / `exposed-ore-weight: 0.25`** — A 4:1 penalty ratio. A cave miner who finds 4 exposed ores contributes the same score as an X-rayer who finds 1 hidden ore. This ratio is configurable.

---

## Phase 3 — Alert & Command Dispatch

### Alert System: `AlertManager`

```
AlertManager {
  - plugin: MedusaAntiXray
  - config: ConfigManager

  +dispatch(Player suspect, double ratio, double score, int totalBlocks):
    message = buildAlertMessage(suspect, ratio, score, totalBlocks)
    for each Player online:
      if player.hasPermission(config.staffPermission):
        player.sendMessage(message)

  -buildAlertMessage(suspect, ratio, score, total) → String:
    template = config.alertMessage
    return template
      .replace("{prefix}", config.prefix)
      .replace("{player}", suspect.getName())
      .replace("{ratio}", String.format("%.1f", ratio * 100))
      .replace("{score}", String.format("%.2f", score))
      .replace("{total}", String.valueOf(total))
}
```

> **Cross-Platform Safety Note:** We use `player.sendMessage(String)` only — never `player.sendMessage(Component)` or any Adventure API method. The `String`-based `sendMessage` is available on all Bukkit/Spigot versions from 1.8 onward, making this safe on 1.20.1 through 1.21.x.

### Color Formatting: Safe Cross-Version Pattern

The existing `Utils.colorize()` uses `net.md_5.bungee.api.ChatColor` which is safe on all Spigot 1.20+ versions. We keep it as-is and pipe all messages through it.

### Command System: `MedusaCommand`

```
/medusa
  ├─ Registered via plugin.yml as the root command executor
  ├─ Permission: "medusa.admin" for all sub-commands
  │
  ├─ reload
  │    └─ Calls ConfigManager.getInstance().reload()
  │    └─ Sends config.messages.reloadSuccess to sender
  │
  └─ check <player>
       └─ Look up PlayerData by name in the live cache
       └─ If not found → send config.messages.playerNotFound
       └─ Calculate current ratio from live sliding window
       └─ Send formatted check-message to command sender
```

**plugin.yml command registration:**
```yaml
commands:
  medusa:
    description: Medusa Anti-Xray management command
    usage: /medusa <reload|check>
    permission: medusa.admin

permissions:
  medusa.admin:
    description: Access to /medusa management commands
    default: op
  medusa.staff:
    description: Receive Medusa X-ray detection alerts
    default: op
  medusa.bypass:
    description: Exempt from Medusa detection (for trusted staff)
    default: false
```

> **`medusa.bypass`** — Added as a safety valve. Any player with this permission is skipped entirely in Step 1 of the BlockBreakEvent algorithm. Essential for staff members who spectate or test using Survival mode.

---

## Phase 4 — Modular Implementation Order

This is a strict dependency-first build order. Each milestone compiles and runs cleanly before the next begins.

### Milestone 1 — Project Skeleton ✅ (Already exists)
- [x] `pom.xml` — Maven, Java 17, Spigot 1.20.1 API (provided scope)
- [x] `plugin.yml` — name, main, version, api-version
- [x] `MedusaAntiXray.java` (main class stub)
- [x] `Utils.java` (colorize method)

### Milestone 2 — Configuration Layer
**Files to create/rewrite in order:**

1. **`src/main/resources/config.yml`** — Full config as designed above
2. **`managers/ConfigManager.java`** — Singleton that wraps `plugin.getConfig()`, exposes typed getters, implements `reload()` which calls `plugin.reloadConfig()`
3. **Update `MedusaAntiXray.java`** — Call `saveDefaultConfig()` in `onEnable()`, initialize `ConfigManager`

> ✅ Compile checkpoint: Plugin enables, loads config, logs values. No listeners yet.

### Milestone 3 — Data Model
4. **`data/MineEvent.java`** — Immutable record: `timestamp`, `isValuable`, `weight`
5. **`data/PlayerData.java`** — `ArrayDeque<MineEvent>`, `lastAlertTimestamp`, computed methods `calculateScore()`, `calculateRatio()`, `purgeExpired(long cutoff)`

> ✅ Compile checkpoint: Data classes compile cleanly with no dependencies on Bukkit.

### Milestone 4 — Cache Manager
6. **`managers/DataManager.java`** — `ConcurrentHashMap<UUID, PlayerData>`, methods: `createEntry(UUID, String)`, `getEntry(UUID)`, `removeEntry(UUID)`, `getAllEntries()`

> ✅ Compile checkpoint: DataManager standalone.

### Milestone 5 — Listeners
7. **`listeners/SessionListener.java`** — Handles `PlayerJoinEvent` (create entry) and `PlayerQuitEvent` (remove entry). Also checks `medusa.bypass` permission to skip registration in DataManager.
8. **`listeners/BlockBreakListener.java`** — Full algorithm from Phase 2. Depends on: `ConfigManager`, `DataManager`, `AlertManager` (stub first, wire later)

> ✅ Compile checkpoint: Break events fire and record to cache; no alerts yet.

### Milestone 6 — Alert Manager
9. **`managers/AlertManager.java`** — `dispatch()` method using permission-gated `sendMessage(String)`. Called from `BlockBreakListener`.

> ✅ Compile checkpoint: Full detection pipeline fires end-to-end. Test by mining diamonds rapidly.

### Milestone 7 — Commands
10. **`commands/MedusaCommand.java`** — Implements `CommandExecutor` and `TabCompleter`. Routes `reload` and `check` sub-commands.
11. **Update `plugin.yml`** — Add `commands:` and `permissions:` blocks
12. **Update `MedusaAntiXray.java`** — Register command executor and tab completer

> ✅ Compile checkpoint: `/medusa reload` and `/medusa check <player>` work in-game.

### Milestone 8 — Cleanup & Polish
13. **Rewrite `PluginManager.java` → replace with proper orchestration in `MedusaAntiXray.onEnable()`** — The current singleton stub is a smell; wire all managers directly from the main class
14. **Rewrite `PlayerListener.java` → split into `SessionListener.java` + `BlockBreakListener.java`**
15. **`plugin.yml`** — Fix invalid `main:` value (`Medusa-Anti-Xray` is not a valid Java class name — rename main class to `MedusaAntiXray`)
16. **Final `pom.xml` check** — Add `maven-shade-plugin` configured with `minimizeJar=true` to produce a clean fat-jar (no external deps to shade, but needed for reproducible builds)

> ✅ Final compile + `/mvn package` produces a working JAR.

---

## File Tree (Target State)

```
src/main/
├── java/me/perseusj/medusaantixray/
│   ├── MedusaAntiXray.java              ← Main class (renamed from Medusa-Anti-Xray)
│   ├── commands/
│   │   └── MedusaCommand.java           ← CommandExecutor + TabCompleter
│   ├── data/
│   │   ├── MineEvent.java               ← Immutable event record
│   │   └── PlayerData.java              ← Sliding window + computed metrics
│   ├── listeners/
│   │   ├── BlockBreakListener.java      ← Core detection algorithm
│   │   └── SessionListener.java         ← Join/quit cache lifecycle
│   ├── managers/
│   │   ├── AlertManager.java            ← Staff notification dispatch
│   │   ├── ConfigManager.java           ← Typed config access + reload
│   │   └── DataManager.java             ← ConcurrentHashMap cache
│   └── utils/
│       └── Utils.java                   ← colorize() (keep as-is)
└── resources/
    ├── config.yml
    └── plugin.yml
```

---

## Critical Cross-Version Safety Notes

| Risk | Mitigation |
|---|---|
| `player.sendMessage(Component)` — Added in newer Paper/Adventure API, absent in plain Spigot 1.20.1 | ✅ Use `sendMessage(String)` exclusively |
| `Material.DEEPSLATE_DIAMOND_ORE` — Added in 1.17 | ✅ Tracking from 1.20+ only, so this is safe |
| `Block.getRelative(BlockFace)` — Stable across all versions | ✅ Safe to use |
| `ChatColor.of(hexString)` — `net.md_5.bungee.api.ChatColor` requires Spigot 1.16+ | ✅ Safe for 1.20+ target |
| Main class name `Medusa-Anti-Xray` — **Invalid Java identifier** (hyphen not allowed) | ⚠️ Must rename class to `MedusaAntiXray` in Milestone 8 |
| `PluginManager` name collision — `org.bukkit.plugin.PluginManager` is a Bukkit interface | ⚠️ Must rename to `DataManager` or `MedusaPluginManager` to avoid import confusion |
