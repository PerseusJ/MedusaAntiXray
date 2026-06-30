# Graph Report - MedusaAntiXray  (2026-06-30)

## Corpus Check
- 23 files · ~24,946 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 446 nodes · 1093 edges · 21 communities (13 shown, 8 thin omitted)
- Extraction: 73% EXTRACTED · 27% INFERRED · 0% AMBIGUOUS · INFERRED: 296 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `91df3041`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- [[_COMMUNITY_Player Data & Scoring|Player Data & Scoring]]
- [[_COMMUNITY_Database & Config Persistence|Database & Config Persistence]]
- [[_COMMUNITY_Commands, Alerts & Messages|Commands, Alerts & Messages]]
- [[_COMMUNITY_Session & Data Lifecycle|Session & Data Lifecycle]]
- [[_COMMUNITY_Detection Configuration|Detection Configuration]]
- [[_COMMUNITY_Block Break Detection|Block Break Detection]]
- [[_COMMUNITY_Ore Lists & Alert Dispatch|Ore Lists & Alert Dispatch]]
- [[_COMMUNITY_Config Loading & Validation|Config Loading & Validation]]
- [[_COMMUNITY_Community 8|Community 8]]
- [[_COMMUNITY_Graphify Agent Rules|Graphify Agent Rules]]
- [[_COMMUNITY_OpenCode Plugin Config|OpenCode Plugin Config]]
- [[_COMMUNITY_OpenCode Schema|OpenCode Schema]]
- [[_COMMUNITY_OpenCode Package Deps|OpenCode Package Deps]]
- [[_COMMUNITY_OpenCode Package Single|OpenCode Package Single]]
- [[_COMMUNITY_Roadmap Phase C|Roadmap Phase C]]
- [[_COMMUNITY_Roadmap Phase D|Roadmap Phase D]]
- [[_COMMUNITY_Roadmap Phase E|Roadmap Phase E]]
- [[_COMMUNITY_Roadmap Phase F|Roadmap Phase F]]
- [[_COMMUNITY_Community 22|Community 22]]

## God Nodes (most connected - your core abstractions)
1. `ConfigManager` - 120 edges
2. `String` - 51 edges
3. `PlayerDataTest` - 36 edges
4. `PlayerData` - 33 edges
5. `Test` - 28 edges
6. `DatabaseManager` - 27 edges
7. `BlockBreakListener` - 22 edges
8. `of()` - 18 edges
9. `MedusaCommand` - 15 edges
10. `DataManager` - 15 edges

## Surprising Connections (you probably didn't know these)
- `PlayerData` --conceptually_related_to--> `A2: Race-condition hardening in DataManager load-vs-addEvent`  [EXTRACTED]
  src/main/java/me/perseusj/medusaantixray/data/PlayerData.java → docs/ROADMAP.md
- `PlayerData` --conceptually_related_to--> `Sliding Window Detection Algorithm`  [INFERRED]
  src/main/java/me/perseusj/medusaantixray/data/PlayerData.java → docs/ROADMAP.md
- `BlockBreakListener` --conceptually_related_to--> `A1: Fix async thread-safety bug in isBlockExposed`  [EXTRACTED]
  src/main/java/me/perseusj/medusaantixray/listeners/BlockBreakListener.java → docs/ROADMAP.md
- `ConfigManager` --conceptually_related_to--> `A4: Config validation on load`  [EXTRACTED]
  src/main/java/me/perseusj/medusaantixray/managers/ConfigManager.java → docs/ROADMAP.md
- `ConfigManager` --conceptually_related_to--> `B3: Per-ore configurable weights`  [INFERRED]
  src/main/java/me/perseusj/medusaantixray/managers/ConfigManager.java → docs/ROADMAP.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Detection Pipeline** — listeners_blockbreaklistener_blockbreaklistener, data_playerdata_playerdata, managers_configmanager_configmanager, managers_datamanager_datamanager, managers_alertmanager_alertmanager, data_mineevent_mineevent [EXTRACTED 1.00]
- **Data Persistence Layer** — managers_datamanager_datamanager, managers_databasemanager_databasemanager, data_playerdata_playerdata, data_mineevent_mineevent [EXTRACTED 1.00]
- **Data Model** — data_playerdata_playerdata, data_mineevent_mineevent, data_oreweight_oreweight, data_veincontext_veincontext [EXTRACTED 1.00]

## Communities (21 total, 8 thin omitted)

### Community 0 - "Player Data & Scoring"
Cohesion: 0.06
Nodes (23): Command, CommandExecutor, MedusaCommand, CommandSender, of(), PlayerData, PlayerDataTest, MiningStyle (+15 more)

### Community 1 - "Database & Config Persistence"
Cohesion: 0.08
Nodes (16): AlertRecord, Consumer, A1: Fix async thread-safety bug in isBlockExposed, A2: Race-condition hardening in DataManager load-vs-addEvent, A3: Data retention & cleanup policy, A4: Config validation on load, A5: Database-failure fallback hardening, Phase A: Stability & Correctness (+8 more)

### Community 2 - "Commands, Alerts & Messages"
Cohesion: 0.11
Nodes (14): Component, AlertManager, Sound, AlertTier, Collection, ConfigManager, DatabaseManager, JavaPlugin (+6 more)

### Community 3 - "Session & Data Lifecycle"
Cohesion: 0.10
Nodes (18): Listener, SessionListener, DataManager, PlayerJoinEvent, PlayerTeleportEvent, Runnable, ConfigManager, DataManager (+10 more)

### Community 4 - "Detection Configuration"
Cohesion: 0.05
Nodes (12): DepthRange, Double, ConfigManager, Map, String, AlertTier, List, Logger (+4 more)

### Community 5 - "Block Break Detection"
Cohesion: 0.09
Nodes (25): AlertManager, Block, BlockBreakEvent, CalibrationManager, OreWeight, B1: Y-level / depth normalization, B3: Per-ore configurable weights, B4: Tool & enchantment awareness (+17 more)

### Community 6 - "Ore Lists & Alert Dispatch"
Cohesion: 0.04
Nodes (44): A1 — Fix async thread-safety bug in `isBlockExposed`, A2 — Race-condition hardening in `DataManager` load-vs-addEvent, A3 — Data retention & cleanup policy, A4 — Config validation on load, A5 — Database-failure fallback hardening, B1 — Y-level / depth normalization, B2 — Vein awareness (ore-vein grouping), B3 — Per-ore configurable weights (+36 more)

### Community 7 - "Config Loading & Validation"
Cohesion: 0.25
Nodes (3): VeinContext, B2: Vein awareness (ore-vein grouping), String

### Community 8 - "Community 8"
Cohesion: 0.38
Nodes (3): WebhookManager, Logger, String

### Community 10 - "Graphify Agent Rules"
Cohesion: 0.33
Nodes (7): Graph JSON, Graph Report, graphify, Graphify CLI, Graphify Output Directory, Graphify Skill, Workflow: graphify

### Community 22 - "Community 22"
Cohesion: 0.14
Nodes (6): CalibrationManager, PlayerStats, ConfigManager, DatabaseManager, Logger, UUID

## Knowledge Gaps
- **59 isolated node(s):** `$schema`, `plugin`, `@opencode-ai/plugin`, `ConfigManager`, `DatabaseManager` (+54 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **8 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `ConfigManager` connect `Detection Configuration` to `Player Data & Scoring`, `Database & Config Persistence`, `Commands, Alerts & Messages`, `Session & Data Lifecycle`, `Block Break Detection`, `Community 22`?**
  _High betweenness centrality (0.260) - this node is a cross-community bridge._
- **Why does `DatabaseManager` connect `Database & Config Persistence` to `Player Data & Scoring`, `Session & Data Lifecycle`, `Detection Configuration`, `Block Break Detection`, `Community 22`?**
  _High betweenness centrality (0.096) - this node is a cross-community bridge._
- **Why does `PlayerData` connect `Player Data & Scoring` to `Database & Config Persistence`, `Session & Data Lifecycle`, `Block Break Detection`?**
  _High betweenness centrality (0.092) - this node is a cross-community bridge._
- **Are the 2 inferred relationships involving `ConfigManager` (e.g. with `OreWeight` and `B3: Per-ore configurable weights`) actually correct?**
  _`ConfigManager` has 2 INFERRED edges - model-reasoned connections that need verification._
- **Are the 3 inferred relationships involving `PlayerData` (e.g. with `Sliding Window Detection Algorithm` and `MineEvent`) actually correct?**
  _`PlayerData` has 3 INFERRED edges - model-reasoned connections that need verification._
- **What connects `$schema`, `plugin`, `@opencode-ai/plugin` to the rest of the system?**
  _60 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Player Data & Scoring` be split into smaller, more focused modules?**
  _Cohesion score 0.05925417524593914 - nodes in this community are weakly interconnected._