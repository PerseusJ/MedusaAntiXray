# MedusaAntiXray — Long-Term Upgrade Roadmap

> Last updated: 2026-06-22
> Current version: 1.0.0-SNAPSHOT
> Target platform: Paper 1.20.1 · Java 17
> After a version is implemented in the project, please mark that version as [COMPLETED] to keep track which next version is to be continue.

---

## Project Overview

MedusaAntiXray is a **behavioral/statistical X-ray detection plugin** for Paper 1.20+. Unlike engine-level
block-obfuscation (Paper's built-in `anti-xray`), Medusa uses a sliding-window algorithm: every block-break
in a configurable world is classified as either a valuable ore or filler block. Ores mined without an
air-exposed face ("hidden" ores) receive higher suspicion weight. The weighted ratio of suspicion-score
to total blocks mined, computed over a rolling time window, triggers staff alerts when it exceeds a
configurable threshold.

### Current Architecture at a Glance

| Layer | Classes | Responsibility |
|---|---|---|
| **Entry point** | `MedusaAntiXray.java` | Plugin lifecycle, registration, autosave scheduler |
| **Listeners** | `BlockBreakListener.java`, `SessionListener.java` | Detection pipeline, player-join/quit data lifecycle |
| **Managers** | `ConfigManager.java`, `DataManager.java`, `DatabaseManager.java`, `AlertManager.java` | Configuration, in-memory cache, HikariCP persistence (SQLite/MySQL), alert dispatch |
| **Data model** | `PlayerData.java`, `MineEvent.java` | Per-player sliding window, event record |
| **Commands** | `MedusaCommand.java` | `/medusa reload` and `/medusa check` |
| **Utilities** | `Utils.java` | Hex + ampersand colorization |

### Current Feature Set (v1.0.0-SNAPSHOT)

- Weighted ore-ratio detection with hidden/exposed ore distinction
- Configurable world lists, tracked ores, and filler blocks for Overworld & Nether
- Sliding time window with configurable duration
- Alert cooldown to prevent chat spam
- SQLite and MySQL persistence via HikariCP connection pooling
- Async database operations on a single-thread executor
- In-memory `ConcurrentHashMap` player-data cache with load/save on join/quit
- `/medusa reload` (reload config) and `/medusa check <player>` (query a player's stats)
- Three permission nodes: `medusa.admin`, `medusa.staff`, `medusa.bypass`
- Hex color (`#RRGGBB`) and ampersand (`&`) color-code support
- JUnit 5 test suite for `PlayerData` consistency

---

## How to Read This Roadmap

Each update is a **single, independently-shippable item** containing:

- **Goal** — what the update delivers in one sentence.
- **Rationale** — why it matters and what problem it solves.
- **Affected files** — which source files, resources, and new files are touched.
- **New config keys** — any additions or changes to `src/main/resources/config.yml`.
- **Acceptance criteria** — verifiable conditions that mean the update is done.

Phases are organized as **semantic-version releases** (v1.1, v1.2, …) reflecting
increasing scope and complexity. Items within a phase may be shipped in any order;
items across phases are ordered by dependency but are generally self-contained.

An item marked with :exclamation: or a "Priority" callout should be shipped first
within its phase — it either fixes a known bug or unblocks later work.

---

## Phase A — v1.1: Stability & Correctness [COMPLETED]

*Bug fixes, thread-safety hardening, and operational reliability improvements.*
*No new features; every item reduces risk for the phases that follow.*

---

### A1 — Fix async thread-safety bug in `isBlockExposed`

**Priority:** :exclamation: Highest — this is an active concurrency defect.

**Goal**
Move the block-exposure check (`isBlockExposed`) to the main server thread so
that `block.getRelative(face).getType()` is never called from an async task.

**Rationale**
In `BlockBreakListener.java:75`, `isBlockExposed(finalBlock)` is invoked
**inside** the async lambda submitted at line 72. `Block.getRelative(…).getType()`
reads world state through Paper's chunk system, which is **not thread-safe**.
Calling it off the main thread can produce stale block data, spuriously null
results, or (rarely) crash the server. The fix separates the exposure check onto
the synchronous path and passes a `boolean exposed` into the async task.

**Affected files**
- `src/main/java/.../listeners/BlockBreakListener.java` — restructure the
  `onBlockBreak` handler so `isBlockExposed` runs synchronously before the async
  lambda; pass `exposed` as a captured `boolean`.
- (Optional) rename `isBlockExposed` → `hasExposedFace` for clarity.

**New config keys**
None.

**Acceptance criteria**
- `isBlockExposed` is never called from a `runTaskAsynchronously` closure.
- Existing tests pass unchanged.
- Manual testing: place a diamond block fully encased in stone, break it while
  watching a profiler — confirmation that the exposure check runs on the server
  thread, and the ratio logic still receives the correct `exposed` flag.
- No warnings in server logs referencing async chunk access.

---

### A2 — Race-condition hardening in `DataManager` load-vs-addEvent

**Goal**
Ensure that `mergeLoadedEvents` cannot interleave with `addEvent` in a way
that silently drops newly mined blocks from the score.

**Rationale**
`DataManager.loadOrCreateAsync` does `cache.putIfAbsent` synchronously, then
submits the DB load asynchronously. Between the `putIfAbsent` and the callback's
`mergeLoadedEvents`, the main thread can process `BlockBreakEvent` → `addEvent`
on the same `PlayerData`. `mergeLoadedEvents` **clears and rebuilds** the deque,
potentially discarding those in-flight events. The window is small but real on
high-TPS servers or during lag spikes.

**Affected files**
- `src/main/java/.../data/PlayerData.java` — add a `private volatile boolean
  merged` flag; `mergeLoadedEvents` sets it; `addEvent` and `purgeExpired`
  check it. Alternatively, replace the clear+rebuild pattern with a merge
  algorithm that inserts loaded events into the existing deque at the front
  without tearing down live events.
- `src/main/java/.../managers/DataManager.java` — ensure `loadOrCreateAsync`
  guards against duplicate loads for the same UUID.
- `src/test/java/.../data/PlayerDataTest.java` — add a concurrency test that
  interleaves `addEvent` and `mergeLoadedEvents` from two threads.

**New config keys**
None.

**Acceptance criteria**
- New test `mergeLoadedEventsDoesNotDropNewEvents` passes consistently.
- With an artificial 2-second delay injected before `mergeLoadedEvents`,
  breaking blocks during that delay does not change the final score.
- No `ConcurrentModificationException` in `mergeLoadedEvents` or `addEvent`.

---

### A3 — Data retention & cleanup policy

**Goal**
Add a scheduled database-cleanup task that prunes `medusa_events` rows older
than a configurable maximum, preventing unbounded table growth.

**Rationale**
The `medusa_events` table is a write-only accumulation. `save` purges
in-memory events older than `window-minutes`, but expired rows are never
deleted from the database — they are only overwritten by a delete-all +
re-insert cycle on the next save. Over months, this table grows proportionally
to the total number of unique players and eventually degrades query performance
and disk usage.

**Affected files**
- `src/main/java/.../managers/DatabaseManager.java` — add a `purgeExpired(long
  retentionCutoffMs)` method (single `DELETE` statement) and wire it into the
  autosave scheduler or a separate async periodic task.
- `src/main/resources/config.yml` — new retention section.
- `src/main/java/.../managers/ConfigManager.java` — getter for retention config.
- `src/main/java/.../MedusaAntiXray.java` — register the cleanup task.

**New config keys**
```yaml
database:
  retention:
    # Delete events older than this many days (0 = never purge)
    days: 30
```

**Acceptance criteria**
- After the plugin has been running with `retention.days: 30` for more than the
  configured window, rows with `timestamp < (now - 30 days)` are absent from
  `medusa_events`.
- Purge runs asynchronously and does not block the save executor.
- Setting `days: 0` disables the cleanup entirely.
- Row count does not grow indefinitely on a long-running test instance.

---

### A4 — Config validation on load

**Goal**
Validate all config values at startup with explicit warnings for invalid,
out-of-range, or unknown keys so operators can fix misconfigurations immediately.

**Rationale**
Currently, `ConfigManager` reads values with fallback defaults and silently
accepts garbage. A typo in a config path (e.g., `alert-threshhold` instead of
`alert-threshold`) goes unnoticed and the default 0.08 is used. This causes
confusion and support burden.

**Affected files**
- `src/main/java/.../managers/ConfigManager.java` — add a `validate()` method
  called after `reload()` that checks:
  - `alert-threshold` is in [0.0, 1.0]
  - `min-sample-size` >= 1
  - `window-minutes` >= 1
  - `cooldown-seconds` >= 0
  - `hidden-ore-weight` and `exposed-ore-weight` >= 0
  - `database.type` is `sqlite` or `mysql`
  - If MySQL, required fields are non-blank
  - `tracked-ores` and `filler-blocks` contain valid `Material` names
  - Warn about any top-level keys in `config.yml` that are not recognized
  - `overworld.names` and `nether.names` are non-empty when their section is
    enabled
- `src/main/java/.../MedusaAntiXray.java` — call `validate()` after
  `ConfigManager` construction; log warnings via `getLogger().warning()`.

**New config keys**
None.

**Acceptance criteria**
- Setting `alert-threshold: 5` logs a warning on startup.
- Setting `database.type: postgres` logs a warning (unsupported).
- A typo like `min-sample-size: -1` logs a severity warning.
- Valid configs produce no warnings.
- Invalid configs do not prevent plugin enable (warn but continue).

---

### A5 — Database-failure fallback hardening

**Goal**
When database initialization or connections fail, run in memory-only mode with
periodic reconnection attempts and clearer logging, rather than silently
discarding data.

**Rationale**
Currently, if `DatabaseManager.init()` fails (e.g., MySQL unreachable, disk
full for SQLite), `available` is set to `false` and all saves are silently
dropped. The plugin continues running but no data is persisted across restarts.
There is no attempt to reconnect and no indication to the operator that data
is being lost after the initial startup message.

**Affected files**
- `src/main/java/.../managers/DatabaseManager.java` — add a `retryConnect()`
  method scheduled periodically when `available == false`. Add a
  `lastFailureLogged` throttle to avoid log spam. Add a counter for
  dropped-saves-since-last-success.
- `src/main/java/.../MedusaAntiXray.java` — schedule reconnection attempts.
- (Optional) expose `getDroppedSaveCount()` for status reporting.

**New config keys**
```yaml
database:
  # Seconds between reconnection attempts when DB is unavailable (0 = never retry)
  retry-interval-seconds: 120
```

**Acceptance criteria**
- Start plugin with MySQL host `invalid-host`. Plugin enables in memory-only
  mode, logs "Database unavailable — running in memory-only mode. Will retry in
  120s."
- Every 120s, a reconnection attempt is logged at INFO level. If it succeeds,
  "Database reconnected — resuming persistence" is logged.
- If the database never becomes available, save-attempts are counted and the
  count is exposed via a debug command or log line at shutdown.
- No log spam: only one warning per reconnection-cycle failure.

---

## Phase B — v1.2: Detection Accuracy [COMPLETED]

*Improvements to the core statistical model — better signal, less noise.*

---

### B1 — Y-level / depth normalization

**Goal**
Weight ore finds according to the Y-level at which they occurred, using a per-ore
expected distribution table so that ores found at naturally optimal layers are
less suspicious than ores found at improbable depths.

**Rationale**
A player mining diamond at Y=-58 (optimal for 1.20) is expected; a player
consistently beelining to diamond at Y=12 and ignoring everything else is far
more suspicious. The current flat model treats all ores equally regardless of Y.

**Affected files**
- `src/main/java/.../data/MineEvent.java` — add `int y` field (record with
  default `0` for backward compatibility).
- `src/main/java/.../listeners/BlockBreakListener.java` — capture `block.getY()`
  and pass it into `MineEvent`.
- `src/main/java/.../managers/ConfigManager.java` — load per-ore Y distribution
  from config.
- `src/main/resources/config.yml` — new `depth-profiles` section mapping
  ore-name → list of {y-min, y-max, weight-multiplier} ranges.
- `src/main/java/.../data/PlayerData.java` — optionally, `calculateScore` can
  multiply by depth factor; or do it in `BlockBreakListener` at event-creation
  time.

**New config keys**
```yaml
detection:
  depth-normalization:
    enabled: false
    default-multiplier: 1.0
    profiles:
      DIAMOND_ORE:
        - y-min: -64  # Below deepslate
          y-max: -54  # Optimal diamond layer
          multiplier: 0.5  # Low suspicion at optimal Y
        - y-min: -53
          y-max: 320
          multiplier: 2.0  # High suspicion above deepslate
      DEEPSLATE_DIAMOND_ORE:
        - y-min: -64
          y-max: -54
          multiplier: 0.5
        - y-min: -53
          y-max: 320
          multiplier: 2.0
      ANCIENT_DEBRIS:
        - y-min: 8
          y-max: 22
          multiplier: 0.5
        - y-min: 23
          y-max: 119
          multiplier: 2.5
```

**Acceptance criteria**
- Mining diamonds at Y=-58 with depth-normalization enabled produces a lower
  score per diamond than mining them at Y=50.
- Profiles default to empty; when no profile matches an ore, `default-multiplier`
  is used, preserving existing behavior.
- Test: 100 diamond ores at Y=-58 → ratio stays below 0.08. 100 diamond ores
  at Y=50 → ratio triggers alert.
- Config validation warns about ore names in profiles that don't appear in any
  `tracked-ores` list.

---

### B2 — Vein awareness (ore-vein grouping)

**Goal**
Treat a contiguous vein of the same ore type mined in rapid succession as a
single detection event, avoiding inflation of the suspicion score from naturally
large veins.

**Rationale**
Diamond can generate in veins of up to 8 in 1.20, and ancient debris in clusters
of 1–3. When a player mines all blocks in a vein the current code counts each
ore block separately, producing a misleadingly high `calculateScore`. An honest
branch miner who hits a big vein gets the same ratio spike as an xrayer.

**Affected files**
- `src/main/java/.../listeners/BlockBreakListener.java` — add a
  `Map<UUID, VeinContext>` tracking the last ore type + position + timestamp.
  If the next ore is within `vein-distance` blocks and `vein-timeout` ticks of
  the previous, treat it as part of the same vein and don't add a new event.
- `src/main/java/.../data/PlayerData.java` — `addEvent` gains an
  `int veinSize` parameter. Score logic divides ore weight by `veinSize`
  (or uses a configurable decay function).
- `src/main/resources/config.yml` — new `vein` section.

**New config keys**
```yaml
detection:
  vein-grouping:
    enabled: true
    # Max Chebyshev distance between ore blocks to consider same vein
    max-distance: 3
    # Max ticks between adjacent vein-breaks to group them
    timeout-ticks: 100
    # How much weight to assign: "divide" = weight / veinSize, "first-only" = ignore extras
    mode: "divide"
```

**Acceptance criteria**
- Mine a 4-ore diamond vein: 4 block-break events fire but only 1 `MineEvent`
  with `veinSize=4` (or equivalently, 4 events with reduced weight) is added.
- `PlayerData.calculateScore()` for 10 isolated diamonds vs 10 diamonds in a
  single vein produces drastically different scores when vein-grouping is
  enabled.
- Disabling vein-grouping via config restores previous behavior.
- No false-grouping of ores separated by more than `max-distance` blocks.

---

### B3 — Per-ore configurable weights

**Goal**
Allow each tracked ore to have its own `hidden-ore-weight` and
`exposed-ore-weight` overrides, replacing the current global flat weights.

**Rationale**
Currently all overworld ores share the same `hidden-ore-weight: 1.0` and
`exposed-ore-weight: 0.25`. But diamond is far more valuable than iron, and
emerald is biome-limited. Differentiating them produces more accurate scoring.
An xrayer going straight for diamonds should be flagged faster than someone
accumulating iron.

**Affected files**
- `src/main/java/.../managers/ConfigManager.java` — replace
  `getOverworldOres()` with `getOverworldOreWeights()` returning a
  `Map<String, OreWeight>` (or change `getOverworldOres` to return a different
  structure and add a lookup method). Same for Nether.
- `src/main/resources/config.yml` — change `tracked-ores` from a simple
  string list to a map with optional weight overrides.
- `src/main/java/.../listeners/BlockBreakListener.java` — use
  `config.getOreWeight(worldType, materialName)` instead of the global
  `getHiddenOreWeight()` / `getExposedOreWeight()`.
- `src/test/java/.../` — update or add tests reflecting per-ore weights.

**New config keys**
```yaml
worlds:
  overworld:
    tracked-ores:
      DIAMOND_ORE:
        hidden-weight: 1.5
        exposed-weight: 0.4
      DEEPSLATE_DIAMOND_ORE:
        hidden-weight: 1.5
        exposed-weight: 0.4
      GOLD_ORE:
        hidden-weight: 0.6
        exposed-weight: 0.15
      EMERALD_ORE:
        hidden-weight: 1.2
        exposed-weight: 0.3
      # Ores without overrides use the global defaults
    # Global defaults (used when an ore has no per-ore override)
    default-hidden-ore-weight: 1.0
    default-exposed-ore-weight: 0.25
```

**Acceptance criteria**
- Breaking a hidden diamond ore contributes 1.5 to the score; hidden gold
  contributes 0.6, with the new config above.
- Old string-list format still parses correctly and falls back to
  `default-hidden-ore-weight` / `default-exposed-ore-weight`.
- Config validation warns if an ore name in `tracked-ores` is not a valid
  `Material`.
- Ratio calculation uses the correct per-ore weight throughout.

---

### B4 — Tool & enchantment awareness

**Goal**
Factor in the player's held tool and its enchantments when computing ore
weight — silk-touch users get lower suspicion because the glow effect naturally
exposes ores, and fortune users' higher yield is expected.

**Rationale**
A player with Silk Touch sees ore blocks through stone (making them effectively
more exposed); a player with Fortune III gets more drops from each ore, which
is expected behavior. An xrayer is more likely to use a plain Efficiency pick
and not bother with Fortune (since drop yield doesn't matter when you have
unlimited ores). These signals can be integrated into the scoring model.

**Affected files**
- `src/main/java/.../listeners/BlockBreakListener.java` — capture
  `player.getInventory().getItemInMainHand()` and extract `Enchantment` levels.
- `src/main/java/.../data/MineEvent.java` — add fields for `hasSilkTouch:boolean`,
  `fortuneLevel:int`, `efficiencyLevel:int`, `toolType:String`.
- `src/main/java/.../managers/ConfigManager.java` — load tool/enchantment
  weight modifiers.
- `src/main/resources/config.yml` — new `tool-modifiers` section.

**New config keys**
```yaml
detection:
  tool-modifiers:
    enabled: true
    silk-touch-multiplier: 0.5       # Halves suspicion (miner is "seeing" ores)
    fortune-multiplier-per-level: 0.8  # Per fortune level multiplier (Fortune III = 0.8^3 = 0.51)
    no-enchantments-multiplier: 1.2    # No enchantments = slightly more suspicious
```

**Acceptance criteria**
- Hidden diamond ore mined with Silk Touch pick: score = weight × 0.5.
- Hidden diamond ore mined with Fortune III pick: score = weight × 0.51.
- Hidden diamond ore mined with unenchanted diamond pick: score = weight × 1.2.
- `enabled: false` restores flat scoring ignoring tools.
- Non-pickaxe tools (or bare hands — shouldn't happen with `SURVIVAL` check)
  pass `toolType: "OTHER"` and use `no-enchantments-multiplier`.

---

### B5 — Light-level & multi-face exposure refinement

**Goal**
Replace the binary `isBlockExposed` check with a graduated exposure score that
counts how many faces of the ore are adjacent to air, and whether those faces
are naturally dark (cave) or sky-lit (surface).

**Rationale**
An ore with one air-exposed face in a dark cave at Y=-50 is plausibly exposed
by natural cave generation. An ore with five exposed faces at Y=60 is almost
certainly exposed by the player. An ore with a sky-lit face is surface-level
and trivially findable. Differentiating these produces better signal.

**Affected files**
- `src/main/java/.../listeners/BlockBreakListener.java` — replace
  `isBlockExposed(Block)` with `getExposureScore(Block)` that returns a
  `double` in `[0.0, 1.0]` based on exposed-face count and sky-light level.
- `src/main/java/.../config.yml` — new `exposure-scoring` section.
- `src/main/java/.../managers/ConfigManager.java` — load exposure thresholds.

**New config keys**
```yaml
detection:
  exposure-scoring:
    enabled: true
    # Score formula: base_hidden_weight + (exposed_faces / 6) * (base_exposed_weight - base_hidden_weight)
    # capped and clamped. Then apply sky-light penalty.
    # If any face has sky light > 0, multiply score by sky-light-penalty.
    sky-light-penalty: 0.5
    # Treat blocks as hidden (score = 1.0) if total adjacent air-faces <= this
    hidden-threshold-faces: 1
```

**Acceptance criteria**
- An ore with 0 air-exposed faces → weight = `hidden-ore-weight` (maximum).
- An ore with 1 air-exposed face & no sky light → weight close to
  `exposed-ore-weight` (low).
- An ore with 5 air-exposed faces & sky light > 0 → weight = `exposed-ore-weight
  × sky-light-penalty` (very low).
- `enabled: false` falls back to the binary is-exposed check.

---

### B6 — End world support

**Goal**
Add The End as a third detectable dimension with its own tracked blocks and
filler blocks, mirroring the existing Overworld & Nether sections.

**Rationale**
The current `WorldType` enum only has `OVERWORLD`, `NETHER`, `IGNORED`. Players
on End-reset or End-farming servers can xray for end cities, elytra chests,
shulker boxes, etc. Adding End support is low-effort and high-value for these
servers.

**Affected files**
- `src/main/java/.../listeners/BlockBreakListener.java` — add `WorldType.END`
  to the enum; add `getEndOres()` and `getEndFillers()` branches in
  `onBlockBreak`; add `classifyWorld` logic for End names.
- `src/main/java/.../managers/ConfigManager.java` — add End getters.
- `src/main/resources/config.yml` — new `worlds.end` section.
- `src/main/resources/plugin.yml` — none (permissions unchanged).

**New config keys**
```yaml
worlds:
  end:
    enabled: false
    names:
      - "world_the_end"
    tracked-ores:
      - OBSIDIAN
      - END_STONE_BRICKS
      - PURPUR_BLOCK
      - CHEST          # end-city loot chests (if xrayer mines them)
    filler-blocks:
      - END_STONE
```

**Acceptance criteria**
- Breaking tracked end blocks in listed End worlds adds events to player data.
- Breaking filler blocks counts toward total.
- Breaking untracked blocks in End worlds is ignored.
- Setting `end.enabled: false` skips End classification entirely.
- Breaking `END_STONE` in a listed End world is a filler; breaking an untracked
  block there is ignored.

---

## Phase C — v1.3: False-Positive Reduction

*Techniques to lower the false-positive rate without weakening true-positive
detection.*

---

### C1 — Teleport-cooldown grace period

**Goal**
Temporarily exempt a player from detection for a configurable number of seconds
after any teleport event, preventing false flags when staff `/tp` to a player
to observe them or when a player uses `/home` or `/warp`.

**Rationale**
Staff teleporting to a suspected xrayer and watching them mine shouldn't inflate
the suspicion score — the staff member is present and can verify. Similarly, a
player teleporting to their base should not have their immediate mining
activities scored, as they're likely interacting with known blocks.

**Affected files**
- `src/main/java/.../listeners/SessionListener.java` — register
  `PlayerTeleportEvent` handler that sets a teleport timestamp on the
  `PlayerData`.
- `src/main/java/.../data/PlayerData.java` — add `long lastTeleportTimestamp`
  and `boolean isInTeleportCooldown(long now, long cooldownMs)`.
- `src/main/java/.../listeners/BlockBreakListener.java` — skip scoring if
  player is in teleport cooldown.
- `src/main/java/.../managers/ConfigManager.java` — getter for cooldown value.
- `src/main/resources/config.yml` — new config key.

**New config keys**
```yaml
false-positive-guards:
  teleport-cooldown-seconds: 10  # 0 = disabled
```

**Acceptance criteria**
- After `/tp`, `/home`, `/warp`, or `/spawn`, block-breaks within the
  cooldown window are not scored.
- After the cooldown expires, scoring resumes normally.
- Setting `teleport-cooldown-seconds: 0` disables the feature.
- Teleport cooldown is per-player; teleporting one player does not affect others.

---

### C2 — Mining-style classification (branch vs strip vs cave)

**Goal**
Infer the player's mining style from the spatial pattern of their block-breaks
and adjust the detection sensitivity accordingly — cave miners naturally have
higher legitimate ore-to-filler ratios.

**Rationale**
A player exploring the new 1.20 caves will naturally encounter and mine many
ores because cave walls expose ore faces. Their ore-to-filler ratio will be
legitimately much higher than a branch miner. Treating both with the same
threshold leads to false positives on cave explorers.

**Affected files**
- `src/main/java/.../listeners/BlockBreakListener.java` — extract into a
  `MiningStyleClassifier` helper (or keep inline). Track:
  - Percentage of mined blocks that are ores (vs filler).
  - Average Y-level variance (branch miners stay at one Y; cave explorers
    traverse widely).
  - Distance from world spawn or from last-natural-cave air block.
  Classify as `CAVE`, `BRANCH`, `STRIP`, `UNKNOWN`.
- `src/main/java/.../data/PlayerData.java` — store `MiningStyle style` and
  expose `getStyle()`.
- `src/main/resources/config.yml` — style-specific threshold multipliers.

**New config keys**
```yaml
detection:
  style-multipliers:
    enabled: true
    cave: 0.6       # Cave miners get threshold relaxed to 0.08 / 0.6 ≈ 0.133
    branch: 1.0     # Default
    strip: 1.1      # Strip miners are slightly more suspicious
    unknown: 1.0
```

**Acceptance criteria**
- After ~100 blocks in a cave (high ore ratio, high Y variance, many exposed
  faces), player is classified as `CAVE` and sensitivity is reduced.
- Branch miner (low Y variance, consistent pattern) is classified as `BRANCH`.
- Style classification updates at configurable intervals.
- Setting `style-multipliers.enabled: false` avoids classification overhead.

---

### C3 — Learning/baseline calibration mode

**Goal**
Add an optional "learning mode" that samples the server's average ore-to-filler
ratio over a configurable period and logs it, so operators can set the
`alert-threshold` based on empirical data rather than guesswork.

**Rationale**
Every server has a different player base, different world generation tweaks, and
different mining habits. A threshold of 0.08 may be perfect for one server and
produce 50 false positives per hour on another. A calibration mode provides a
data-driven baseline.

**Affected files**
- `src/main/java/.../managers/ConfigManager.java` — loading/learning config.
- `src/main/java/.../listeners/BlockBreakListener.java` — when in learning mode,
  accumulate global server-wide stats in addition to per-player stats.
- `src/main/java/.../MedusaAntiXray.java` — `onEnable` log a message if learning
  mode is active.
- Optionally, a new `CalibrationManager.java` — tracks global aggregates.

**New config keys**
```yaml
detection:
  learning-mode:
    enabled: false
    # How long to collect data before logging the recommended threshold
    duration-minutes: 1440  # 24 hours
    # Where to log results
    log-output: true
    # Also store results in the database
    persist: true
    # Percentile to recommend (e.g. 99 means recommend threshold at the 99th percentile)
    recommend-percentile: 99
```

**Acceptance criteria**
- With learning mode enabled, no alerts are dispatched (alerts suppressed).
- After `duration-minutes`, a log line prints: `[Medusa] Calibration complete:
  mean ratio = 0.012, 99th percentile = 0.035. Recommended alert-threshold: 0.04.`
- If `persist: true`, the calibration result is saved in a `medusa_calibration`
  table for future reference.
- Disabling learning mode resumes normal alerting with the (possibly updated)
  threshold.

---

### C4 — Trust tiers & player whitelist

**Goal**
Allow operators to assign per-player trust multipliers that reduce (or
increase) detection sensitivity, and to maintain a whitelist of completely
exempt players beyond the binary `medusa.bypass` permission.

**Rationale**
Trusted veterans and staff who occasionally build with ores may legitimately
have odd mining patterns. A bypass (full exemption) is too coarse. A trust
multiplier of e.g. 0.5 means "this player needs twice as much evidence to
trigger an alert."

**Affected files**
- `src/main/java/.../managers/ConfigManager.java` — load trust-tiers config.
- `src/main/java/.../data/PlayerData.java` — add `double trustMultiplier`
  field; `calculateScore()` multiplies by it.
- `src/main/java/.../listeners/SessionListener.java` — apply trust multiplier
  on join based on player's UUID matching a whitelist or their permission group.
- `src/main/java/.../commands/MedusaCommand.java` — add subcommand to set/view
  trust for a player.
- `src/main/resources/config.yml` — new `trust` section.

**New config keys**
```yaml
trust:
  # Flat multipliers applied at session load
  # Permissions with multipliers (checked in order; first match wins)
  perm-multipliers:
    medusa.trust.high: 0.25
    medusa.trust.medium: 0.5
    medusa.trust.low: 0.75
  # Explicit UUID whitelist with custom multipliers
  players:
    "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx": 0.0   # fully exempt
    "yyyyyyyy-yyyy-yyyy-yyyy-yyyyyyyyyyyy": 0.5
  # Default multiplier for players not matching any rule
  default: 1.0
```

**Acceptance criteria**
- Player with `medusa.trust.high` permission has effective score multiplied
  by 0.25.
- UUID whitelist overrides permission-based multipliers.
- `/medusa trust set <player> 0.5` updates the config (or separate trust file)
  and reloads the player's data.
- `medusa.bypass` still skips scoring entirely; trust tiers only apply to
  scored players.

---

### C5 — Vein-first-discovery time metric

**Goal**
Track the time-gap between consecutive ore strikes and factor rapid sequential
ore hits more heavily than ores found minutes apart, since xrayers find ores in
tight clusters.

**Rationale**
Honest miners wandering caves may find an ore every few minutes, whereas xrayers
beeline from vein to vein, mining 4-5 ores per minute. A "first-discovery" gap
analysis amplifies the score when ore-strikes are densely packed in time.

**Affected files**
- `src/main/java/.../data/PlayerData.java` — track `long lastOreTimestamp`;
  `addEvent` computes `gap = now - lastOreTimestamp` and applies a gap
  multiplier (short gap = higher weight).
- `src/main/java/.../managers/ConfigManager.java` — load gap-weight config.

**New config keys**
```yaml
detection:
  mine-gap-multiplier:
    enabled: true
    # Gap <= this many millis gets max multiplier
    min-gap-ms: 3000      # 3 seconds
    # Gap >= this gets min multiplier (1.0)
    max-gap-ms: 120000    # 2 minutes
    # Multiplier applied for min-gap (fast sequential finds)
    max-multiplier: 2.0
```

**Acceptance criteria**
- Mining two diamond ores within 2 seconds: second one gets weight × 2.0.
- Mining two diamond ores 5 minutes apart: second one gets weight × 1.0.
- Linear interpolation between `min-gap-ms` and `max-gap-ms`.
- `enabled: false` skips gap calculation.

---

## Phase D — v1.4: Alerting & Notifications

*Expanding how, where, and with what detail staff are notified of detections.*

---

### D1 — Alert history persistence

**Goal**
Persist every dispatched alert as a row in a new `medusa_alerts` database table
for auditing, trend analysis, and cross-session history.

**Rationale**
Currently, alerts are ephemeral — shown in chat and gone forever. Staff who
aren't online miss them. Having a persistent alert log enables later review,
trend analysis (is this player's ratio climbing?), and support for webhook
digests or `/medusa history`.

**Affected files**
- `src/main/java/.../managers/DatabaseManager.java` — add `CREATE TABLE
  medusa_alerts` with columns: `id INTEGER PRIMARY KEY AUTOINCREMENT`,
  `uuid VARCHAR(36) NOT NULL`, `player_name VARCHAR(32)`, `timestamp BIGINT`,
  `ratio DOUBLE`, `score DOUBLE`, `total_blocks INT`, `world VARCHAR(64)`.
  Add `insertAlert` method. Add `queryAlerts` method for history lookups.
- `src/main/java/.../managers/AlertManager.java` — after dispatching to online
  staff, asynchronously insert the alert into the DB.
- `src/main/resources/config.yml` — optional toggle.

**New config keys**
```yaml
alerts:
  persist-history: true
  # Days to keep alert history (0 = keep all)
  history-retention-days: 90
```

**Acceptance criteria**
- An alert is dispatched and a row appears in `medusa_alerts` with correct
  ratio, score, total_blocks, and player info.
- `/medusa history <player>` (implemented in phase E) can query the table.
- Retention policy deletes rows older than `history-retention-days`.
- `persist-history: false` disables persistence without error.

---

### D2 — Escalation tiers (warning → alert → critical)

**Goal**
Introduce a three-tier escalation model so that borderline detections issue a
low-priority "warning," confirmed detections issue an "alert," and extreme
ratios issue a "critical" notification with distinct formatting, permission
requirements, and action-hooks.

**Rationale**
A single `alert-threshold: 0.08` is binary. In practice, a ratio of 0.081 and
0.50 should be treated very differently. Escalation tiers let staff prioritize
responses and optionally trigger automated actions at critical levels.

**Affected files**
- `src/main/java/.../managers/ConfigManager.java` — load tier thresholds and
  messages.
- `src/main/java/.../listeners/BlockBreakListener.java` — determine tier from
  ratio (`ratio >= critical-threshold` → CRITICAL, etc.) and pass into alert.
- `src/main/java/.../managers/AlertManager.java` — `dispatch` gains a
  `Tier tier` parameter; each tier has its own message template, color,
  and permission check.
- `src/main/resources/config.yml` — new tier definitions.

**New config keys**
```yaml
alerts:
  tiers:
    warning:
      threshold: 0.06       # Ratio >= 0.06
      permission: "medusa.staff"  # Same as base
      message: "{prefix} &e⚠ {player} &7shows unusual mining — Ratio: &e{ratio}% &7({score} pts / {total} blocks)"
      sound: BLOCK_NOTE_BLOCK_BELL
      volume: 0.5
      pitch: 1.0
    alert:
      threshold: 0.08
      permission: "medusa.staff"
      message: "{prefix} &c⚠ {player} &7may be X-raying! &cRatio: &f{ratio}% &7({score} pts / {total} blocks)"
      sound: BLOCK_NOTE_BLOCK_PLING
      volume: 1.0
      pitch: 1.0
    critical:
      threshold: 0.25
      permission: "medusa.staff"
      message: "{prefix} &4&l⚠ CRITICAL: {player} &7has extreme ratio! &4Ratio: &f{ratio}% &7({score} pts / {total} blocks)"
      sound: ENTITY_WITHER_SPAWN
      volume: 1.0
      pitch: 1.0
```

**Acceptance criteria**
- Ratio 0.07 → "warning" dispatched to staff.
- Ratio 0.10 → "alert" dispatched.
- Ratio 0.30 → "critical" dispatched.
- Each tier uses its own message template and optional sound.
- Setting `warning.threshold` >= `alert.threshold` logs a config validation
  warning.

---

### D3 — Clickable chat components (teleport-to-player)

**Goal**
Replace plain-text alert messages with Adventure `Component` objects that
include clickable `[TP]` and hoverable `[Check]` actions, letting staff
instantly teleport to or inspect a flagged player.

**Rationale**
When an alert fires, the staff member currently needs to manually type
`/tp <playername>`. A clickable component lets them teleport with one click.
This dramatically reduces response time and friction.

**Affected files**
- `src/main/java/.../managers/AlertManager.java` — construct Paper Adventure
  `TextComponent` objects using `Component.text()` and `.clickEvent()`.
- `src/main/java/.../utils/Utils.java` — add a helper that converts a
  template string with placeholders to a `Component`.
- `src/main/java/.../managers/ConfigManager.java` — message templates may
  need a separate "component" format or the existing templates can be compiled
  to components.
- `src/main/resources/config.yml` — optionally a new `click-commands` section.

**New config keys**
```yaml
alerts:
  clickable-components:
    enabled: true
    tp-command: "/tp {player}"
    tp-hover-text: "&7Click to teleport to &f{player}"
    check-hover-text: "&7Click for detailed stats"
```

**Acceptance criteria**
- The alert message includes a clickable `[TP]` that, when clicked, runs
  `/tp <playername>`.
- Hovering over the player name shows ratio/score/total info.
- `enabled: false` sends plain text messages (backward compatible).
- Click events only function for senders with appropriate permissions
  (Paper's click-event safety handles this; ensure the command requires
  `medusa.staff` or `essentials.tp`).

---

### D4 — Webhook notifications (Discord & Slack)

**Goal**
Send detection alerts to a Discord webhook or Slack incoming webhook so that
staff who are not in-game still receive notifications.

**Rationale**
Many servers have Discord-based staff teams. An alert delivered to a
`#staff-alerts` channel ensures detection is seen even if no one is online.

**Affected files**
- `src/main/java/.../managers/AlertManager.java` — after dispatching in-game,
  asynchronously POST a JSON payload to configured webhook URLs.
- (Optional) `src/main/java/.../notifications/WebhookManager.java` — new class
  handling HTTP POST with `java.net.http.HttpClient` (Java 11+) or OkHttp.
- `src/main/resources/config.yml` — new `webhooks` section.
- `pom.xml` — if using OkHttp, add dependency (or use built-in HttpClient).

**New config keys**
```yaml
alerts:
  webhooks:
    enabled: false
    tiers: ["alert", "critical"]  # Only these tiers fire webhooks
    urls:
      discord: "https://discord.com/api/webhooks/..."
      slack: "https://hooks.slack.com/services/..."
    template:
      discord: >
        {
          "embeds": [{
            "title": "Medusa X-Ray Alert",
            "description": "Player **{player}** triggered a {tier} alert.",
            "color": 16711680,
            "fields": [
              {"name": "Ratio", "value": "{ratio}%", "inline": true},
              {"name": "Score", "value": "{score}", "inline": true},
              {"name": "Total Blocks", "value": "{total}", "inline": true}
            ],
            "timestamp": "{timestamp}"
          }]
        }
```

**Acceptance criteria**
- When an alert fires and webhooks are enabled for that tier, an HTTP POST is
  sent to the Discord URL with the configured embed.
- Webhook fails gracefully: a single failure logs a warning; repeated failures
  are throttled to one warning per minute.
- `enabled: false` makes no HTTP calls.
- Slack webhook format is supported (different JSON schema).

---

### D5 — Sounds, titles & boss-bar alert modes

**Goal**
In addition to chat messages, play a sound effect, display a title/subtitle,
and/or render a boss bar to staff when an alert fires — all independently
configurable.

**Rationale**
Chat messages are easily missed during busy gameplay. Auditory and visual cues
(titles, boss bars) provide higher-urgency notification channels.

**Affected files**
- `src/main/java/.../managers/AlertManager.java` — add `playSound(Player)`,
  `showTitle(Player)`, and `showBossBar` logic per alert tier.
- `src/main/java/.../managers/ConfigManager.java` — load alert-mode config.
- `src/main/resources/config.yml` — new `alert-modes` section.

**New config keys**
```yaml
alerts:
  alert-modes:
    chat: true           # Send chat message
    sound: true          # Play sound
    title: false         # Show title/subtitle
    title-fade-in-ticks: 10
    title-stay-ticks: 70
    title-fade-out-ticks: 20
    boss-bar: false      # Show boss bar
    boss-bar-color: RED  # PINK, BLUE, RED, GREEN, YELLOW, PURPLE, WHITE
    boss-bar-seconds: 5
    # Per-tier overrides (optional; inherited if not set)
```

**Acceptance criteria**
- `sound: true` + alert: staff hear `BLOCK_NOTE_BLOCK_PLING`.
- `title: true` + alert: staff see subtitle with player name and ratio.
- `boss-bar: true` + alert: staff see a red boss bar for 5 seconds.
- Each mode's config key independently controls whether it fires.

---

### D6 — Periodic digest reports

**Goal**
Generate and broadcast a summary of the top-N most suspicious players at a
configurable interval (e.g., every 15 minutes), giving staff a dashboard-like
overview of server-wide detection status.

**Rationale**
Individual alerts provide point-in-time information. A periodic digest gives
staff context — "is this one player spiking, or are 10 players all above
threshold right now?"

**Affected files**
- `src/main/java/.../managers/AlertManager.java` — add `dispatchDigest()` that
  sorts all cached `PlayerData` by ratio and sends a formatted message to staff.
- `src/main/java/.../MedusaAntiXray.java` — schedule the digest task.
- `src/main/java/.../managers/ConfigManager.java` — load digest settings.
- `src/main/resources/config.yml` — new `digest` section.

**New config keys**
```yaml
alerts:
  digest:
    enabled: false
    interval-minutes: 15
    top-n: 5
    min-ratio: 0.04   # Only include players above this ratio
    message: "{prefix} &7Top {n} suspects:\n{entries}"
    entry-format: "  &c{player} &7— Ratio: &c{ratio}% &7({score} pts / {total} blocks)"
```

**Acceptance criteria**
- Every 15 minutes, staff see a message listing the top 5 players by ratio,
  with scores and totals.
- Players below `min-ratio` are excluded.
- Interval can be changed via reload without restarting the scheduler.
- `enabled: false` suppresses digest.

---

## Phase E — v1.5: Commands & UX

*Expanding the command interface and adding quality-of-life tooling for staff.*

---

### E1 — New `/medusa` subcommands

**Goal**
Add the following subcommands to the `/medusa` command:

| Subcommand | Description |
|---|---|
| `/medusa top [page]` | List top-N players by ratio |
| `/medusa history <player> [page]` | Show alert history for a player |
| `/medusa reset <player>` | Clear a player's detection data |
| `/medusa stats` | Show global detection statistics |
| `/medusa watch <player>` | Toggle live verbose output for a player |
| `/medusa list [page]` | List all currently-flagged players |
| `/medusa help` | Show help page |

**Rationale**
The current command set (`reload`, `check`) is minimal. Staff need tools to
investigate trends, review individual histories, clear false positives, and
monitor players. Each subcommand is independently useful and incrementally
developed.

**Affected files**
- `src/main/java/.../commands/MedusaCommand.java` — expand `onCommand` and
  `onTabComplete` to handle the new subcommands. Consider refactoring into
  a `CommandRouter` pattern with per-subcommand handler classes to keep the
  file manageable.
- `src/main/java/.../managers/DataManager.java` — expose methods needed by
  the commands (getTopN, reset).
- `src/main/java/.../managers/DatabaseManager.java` — query methods for
  alert history and stats.
- `src/main/resources/config.yml` — message templates for each new subcommand.
- `src/main/resources/plugin.yml` — update `usage` line.

**New config keys**
```yaml
messages:
  top-header: "&7=== Top Suspects (Page {page}/{totalPages}) ==="
  top-entry: "&c#{rank} {player} &7| Ratio: &c{ratio}% &7| Score: &f{score} &7| Blocks: &f{total}"
  top-no-results: "&7No flagged players."
  history-header: "&7Alert history for &f{player} &7(page {page}/{totalPages}):"
  history-entry: "&7{timestamp} &c{tier} &7Ratio: &c{ratio}% &7Score: &f{score}"
  history-empty: "&7No alert history for &f{player}."
  reset-success: "&aDetection data for &f{player} &ahas been reset."
  reset-confirm: "&eUse &c/medusa reset {player} confirm &eto proceed."
  stats-message: "&7Tracked: &f{tracked} &7| Flagged: &c{flagged} &7| Alerts today: &c{alertsToday}"
  watch-enabled: "&aNow watching &f{player}&a. Live updates enabled."
  watch-disabled: "&7Stopped watching &f{player}&7."
  help-header: "&7=== Medusa Anti-Xray Help ==="
```

**Acceptance criteria**
- `/medusa top` displays the top suspects sorted by descending ratio.
- `/medusa top 2` shows page 2.
- `/medusa history Notch` shows alert history from `medusa_alerts`.
- `/medusa reset Notch` requires a confirmation step (`/medusa reset Notch confirm`).
- `/medusa stats` shows total tracked players, currently flagged count, and
  alerts in the last 24h.
- `/medusa watch Notch` toggles; when enabled, every new event for that player
  is printed to the watcher's chat.
- `/medusa list` shows all cached players currently above the alert threshold.
- Tab completion works for player names on all subcommands.
- `/medusa help` shows a color-coded list of all subcommands and their usage.

---

### E2 — Pagination for list & history output

**Goal**
Implement a shared pagination utility so that any command producing multi-entry
output (`top`, `history`, `list`) can be paginated consistently, with page-size
configurable in config.yml.

**Rationale**
Without pagination, a server with 50 flagged players would spam 50 lines into
chat. Pagination with a page-size of 10 shows 10 entries and prompts for the
next page.

**Affected files**
- `src/main/java/.../utils/PaginationHelper.java` — new utility class with
  `List<T> getPage(List<T>, int page, int pageSize)` and footer message
  generation.
- `src/main/java/.../commands/MedusaCommand.java` — refactor list/top/history
  to use the pagination helper.
- `src/main/resources/config.yml` — page-size config.
- `src/main/java/.../managers/ConfigManager.java` — getter for page size.

**New config keys**
```yaml
commands:
  page-size: 10
```

**Acceptance criteria**
- `top 3` shows entries 21-30 when page-size is 10.
- Footer reads "Page 3/5. Use /medusa top 4 for next page."
- Entering a page number beyond the last page shows "No results for page X."
- Page 0 or negative numbers show page 1.

---

### E3 — Optional inventory-based GUI dashboard

**Goal**
Provide an optional GUI (inventory menu) accessible via `/medusa gui` that lists
flagged players with skull icons, hover-info, and click actions (teleport,
history, reset).

**Rationale**
A GUI is more intuitive than text commands for some staff and supports richer
UX (color-coded skulls, lore text, click actions). It's optional — the CLI
subcommands remain the primary interface.

**Affected files**
- `src/main/java/.../ui/MedusaGui.java` — new class implementing
  `InventoryHolder`, constructs a paged inventory with player skulls, lore
  lines showing ratio/score/total, and click handlers.
- `src/main/java/.../ui/GuiListener.java` — new listener class for
  `InventoryClickEvent` to handle GUI interactions.
- `src/main/java/.../commands/MedusaCommand.java` — add `gui` subcommand.
- `src/main/java/.../MedusaAntiXray.java` — register `GuiListener`.
- `src/main/resources/config.yml` — gui settings.

**New config keys**
```yaml
gui:
  enabled: true
  title: "Medusa — Suspects"
  page-size: 45   # 5 rows of 9
  skull-lore:
    - "&7Ratio: &c{ratio}%"
    - "&7Score: &f{score}"
    - "&7Blocks: &f{total}"
    - ""
    - "&eLeft-click &7→ Teleport"
    - "&eRight-click &7→ History"
    - "&eShift-click &7→ Reset"
```

**Acceptance criteria**
- `/medusa gui` opens a chest inventory titled "Medusa — Suspects."
- Each slot shows a player skull with lore displaying ratio, score, and total
  blocks.
- Left-click teleports; right-click shows history in chat; shift-click resets
  with confirmation.
- Next/Previous page arrows (if applicable) are shown in the bottom row.
- Closing the GUI does not leak items.
- `gui.enabled: false` makes `/medusa gui` return "GUI is disabled."

---

## Phase F — v2.0: Integrations, API & Platform

*Broadening the plugin's ecosystem and preparing for the future.*

---

### F1 — PlaceholderAPI integration

**Goal**
Register PlaceholderAPI placeholders so that other plugins (scoreboards, holograms,
tab lists, chat formatters) can display Medusa detection data.

**Rationale**
PlaceholderAPI is the de-facto standard for cross-plugin data in the Paper
ecosystem. Exposing Medusa stats as placeholders enables integrations like
scoreboard-based live suspect lists, hologram displays, or chat tags.

**Affected files**
- `pom.xml` — add PlaceholderAPI as a `compile` dependency (or `provided` with
  soft-depend).
- `src/main/java/.../integrations/MedusaPlaceholderExpansion.java` — new class
  extending `PlaceholderExpansion`, implementing `onPlaceholderRequest`.
- `src/main/java/.../MedusaAntiXray.java` — if PlaceholderAPI is present,
  register the expansion.
- `src/main/resources/plugin.yml` — add `softdepend: [PlaceholderAPI]`.

**New config keys**
None (placeholders are static identifiers).

**Placeholder list**
| Placeholder | Returns |
|---|---|
| `%medusa_score%` | Current suspicion score for the viewing player |
| `%medusa_ratio%` | Current ratio as percentage (e.g. "5.2") |
| `%medusa_total%` | Total blocks mined in window |
| `%medusa_flagged%` | "true" or "false" — is player above threshold |
| `%medusa_top_1_name%` | Name of #1 suspect |
| `%medusa_top_1_ratio%` | Ratio of #1 suspect |
| `%medusa_flags_today%` | Total alerts dispatched today |

**Acceptance criteria**
- When PlaceholderAPI is installed, `/papi parse me %medusa_ratio%` returns
  the player's current ratio.
- When PlaceholderAPI is absent, the plugin enables without errors and
  placeholders are not registered.
- Placeholders update in real-time (not cached for long periods).

---

### F2 — WorldGuard region awareness

**Goal**
Detect when a block-break occurs inside a WorldGuard region and apply a
configurable multiplier or skip detection entirely for that region.

**Rationale**
X-ray detection in staff-protected regions, spawn areas, or creative-mode
regions is noise. Mining worlds, resource worlds, or PvP zones may want different
detection sensitivity. WorldGuard is near-ubiquitous on Paper servers.

**Affected files**
- `pom.xml` — add WorldGuard as a `provided` dependency.
- `src/main/java/.../integrations/WorldGuardHook.java` — new class querying
  the WorldGuard region API for a `Block` location.
- `src/main/java/.../listeners/BlockBreakListener.java` — check region before
  computing exposure score; apply region multiplier or skip.
- `src/main/java/.../managers/ConfigManager.java` — load region rules.
- `src/main/resources/config.yml` — new `region-rules` section.
- `src/main/resources/plugin.yml` — `softdepend: [WorldGuard, PlaceholderAPI]`.

**New config keys**
```yaml
integrations:
  worldguard:
    enabled: true
    default-behavior: NORMAL   # NORMAL, SKIP, multiplier-NAME
    regions:
      spawn:
        id: "spawn"
        behavior: SKIP
      resource_world:
        id: "resource"
        behavior: MULTIPLY
        multiplier: 0.5
      pvp_zone:
        id: "pvp"
        behavior: NORMAL
```

**Acceptance criteria**
- Breaking a diamond in `spawn` region: no event recorded.
- Breaking a diamond in `resource_world` region: score × 0.5.
- Breaking a diamond in an unlisted region: `default-behavior` applies.
- WorldGuard not installed: no error, plugin functions normally.
- Config validation warns about region IDs without matching WorldGuard regions.

---

### F3 — Cross-server messaging (Bungee/Velocity)

**Goal**
Sync player detection data across a network of Paper servers via plugin messaging
channels so that a player's suspicion follows them across servers and data is
aggregated network-wide.

**Rationale**
On multi-server networks (BungeeCord / Velocity), players can hop servers to
evade detection. Cross-server synchronization ensures their full mining history
follows them and alerts can be network-wide.

**Affected files**
- `src/main/java/.../messaging/PluginMessageHandler.java` — new class
  implementing `PluginMessageListener`; defines a channel protocol
  (`medusa:data`, `medusa:alert`, `medusa:reset`).
- `src/main/java/.../MedusaAntiXray.java` — register outgoing/incoming channels.
- `src/main/java/.../managers/DataManager.java` — send data on player quit;
  accept data on player join via incoming messages.
- `src/main/java/.../managers/AlertManager.java` — forward alerts over the
  channel for network-wide broadcast.
- `src/main/resources/config.yml` — new `messaging` section.

**New config keys**
```yaml
messaging:
  enabled: false
  # Which data to sync
  sync-events: true       # Sync mine events on server switch
  sync-alerts: true       # Broadcast alerts network-wide
  # Server identifier (used to tag event origin)
  server-id: "survival1"
```

**Acceptance criteria**
- Player leaves `survival1`, their MineEvent list is serialized and sent.
- Player joins `survival2` within the data window, events are merged into
  the new server's cache.
- An alert on `survival1` is broadcast via plugin message and staff on
  `survival2` see it (if `sync-alerts: true`).
- `enabled: false` or single-server setups: no plugin channel is registered.

---

### F4 — Public API

**Goal**
Expose a stable public API so that third-party plugins can query player data,
register custom event handlers, and extend or react to Medusa detections.

**Rationale**
Network administrators build custom tooling (admin panels, punishment automation,
web dashboards). A clean API enables composition without reflection or forking.

**Affected files**
- `src/main/java/.../api/MedusaAPI.java` — new interface class with static
  accessor for the plugin instance, exposing:
  - `PlayerData getPlayerData(UUID)`
  - `double getRatio(UUID)`
  - `boolean isFlagged(UUID)`
  - `void resetPlayer(UUID)`
  - Registration for `AlertListener`
- `src/main/java/.../api/AlertEvent.java` — new custom Bukkit `Event` fired
  when an alert is dispatched (cancelable).
- `src/main/java/.../api/PlayerFlaggedEvent.java` — fired when a player first
  crosses the alert threshold.
- `src/main/java/.../listeners/BlockBreakListener.java` — fire API events
  at the appropriate points.
- `src/main/java/.../MedusaAntiXray.java` — set the API instance.

**New config keys**
None.

**Acceptance criteria**
- External plugin can do: `MedusaAPI.getInstance().getRatio(playerUUID)`.
- `AlertEvent` is fired before staff notification; canceling it suppresses the
  in-game alert but still logs to DB.
- `PlayerFlaggedEvent` fires only on first threshold crossing (not on
  subsequent detections within the same window).
- API methods are documented with Javadoc.
- API classes are in a separate `api` package to signal public contract.

---

### F5 — Folia support, Paper 1.21+/Java 21 migration & Brigadier commands

**Goal**
Make the plugin compatible with Folia (region-threaded scheduler), upgrade the
target Paper version to 1.21+ and Java to 21, and migrate the command system
from the legacy Bukkit `CommandExecutor` API to Paper's Brigadier-based command
API.

**Rationale**
Folia adoption is growing and its region-based threading model requires careful
scheduler usage (`getRegionScheduler()` vs `getScheduler()`). Paper 1.21 is the
current standard, and Java 21 is the new LTS. Brigadier commands provide better
UX (error messages, argument suggestions, rich completions).

**Affected files**
- **Folia:**
  - `src/main/java/.../listeners/BlockBreakListener.java` — use
    `plugin.getServer().getRegionScheduler().runDelayed(plugin, location, ...)`
    when Folia is detected, instead of `runTask`.
  - `src/main/java/.../MedusaAntiXray.java` — use `getGlobalRegionScheduler()`
    for the autosave timer; use `getRegionScheduler()` for per-player tasks.
  - `src/main/java/.../managers/DatabaseManager.java` — executor thread is
    already async (not touching world state) — no changes needed.
- **Paper 1.21+/Java 21:**
  - `pom.xml` — bump `paper-api` to `1.21.4-R0.1-SNAPSHOT`; bump
    `maven.compiler.release` to `21`.
  - `src/main/resources/plugin.yml` — bump `api-version` to `1.21`.
  - Review for deprecated API usage across the codebase.
- **Brigadier commands:**
  - `src/main/java/.../commands/MedusaCommand.java` — rewrite using Paper's
    `BrigadierCommand` or the built-in `CommandAPI` (Paper's `Commands` API
    with `LiteralArgumentBuilder`). Register via `getServer().getCommandMap()`
    or Paper's `LifecycleEvent`.
  - `src/main/resources/plugin.yml` — remove legacy `commands:` block if
    registering via Brigadier.
- `README.md` — document Java 21 requirement.

**New config keys**
None (compatibility is transparent to config).

**Acceptance criteria**
- Plugin enables without errors on Paper 1.21.4.
- Plugin enables without errors on Paper 1.21.4 **with Folia** — no
  "this scheduler is not compatible with Folia" errors.
- All commands work identically with Brigadier registration and auto-complete
  player names for the `check` and new subcommands.
- Existing tests pass on Java 21.
- `plugin.yml` and `pom.xml` no longer reference 1.20 or Java 17.

---

## Inter-Item Dependencies

While each item is independently shippable, some natural ordering exists:

```
A1 (thread-safety) ─────────────────────────────────────────────► all other phases
    (must ship first — touching the same code paths)

A2 (race hardening) ──► B2 (vein), B3 (per-ore weights) — touch PlayerData.addEvent

A5 (DB reconnect) ────► D1 (alert history), E1 (history cmd) — need reliable DB

B1 (Y-level) ─────────► B5 (exposure scoring) — share depth context

B3 (per-ore weights) ─► B1 (Y-level profiles), C4 (trust tiers) — config restructuring

C4 (trust tiers) ─────► F2 (WG regions) — both modify per-player multipliers

D1 (alert history) ───► E1 (history command), D6 (digest), F4 (API) — query alert log

F1-F4 (integrations) ─► independent of each other; depend only on A-phase stability

F5 (Folia/Paper21) ───► touches every file; best done as the final v2.0 item
```

Items not listed above have no hard ordering constraints and can ship in any order
within their phase.

---

## Open Questions

1. **Should we add automated punishment hooks?** e.g., `/ban`, `/kick`, `/warn`
   triggers at the critical tier. This is a design question — some operators
   want fully automated enforcement; others want staff to always review. This
   could be a v2.1 item.

2. **Should we add an optional block-obfuscation engine?** Paper's built-in
   `anti-xray` uses engine-level obfuscation. Medusa could optionally integrate
   with it (e.g., when a player's ratio crosses a threshold, enable Paper's
   obfuscation for that chunk). The performance impact and implementation
   complexity would be significant.

3. **Should events be compressed in the database?** Currently every MineEvent
   is a row. For servers with thousands of players, this could produce millions
   of rows. Aggregation (e.g., store counts per ore per 5-minute bucket) would
   reduce storage dramatically at the cost of some granularity. A v2.x config
   option: `storage-mode: raw | aggregated`.

4. **Multi-language / i18n support?** The plugin currently only supports the
   messages in `config.yml`. Proper internationalization with `.properties`
   resource bundles could broaden the install base. This is a cross-cutting
   concern affecting all messages in phases C, D, and E. Could be a v2.1 item.

5. **Performance benchmarks?** Before and after each major phase, profiling
   against a simulated 200-player server with a high block-break rate would
   validate that detection overhead stays within acceptable bounds (<1% TPS
   impact).

---

## Version History

| Version | Date | Description |
|---|---|---|
| 1.0.0-SNAPSHOT | — | Initial release |
| (draft) | 2026-06-22 | Roadmap published |