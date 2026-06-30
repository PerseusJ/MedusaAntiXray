# Graph Report - MedusaAntiXray  (2026-06-30)

## Corpus Check
- 23 files · ~24,946 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 426 nodes · 1070 edges · 18 communities (13 shown, 5 thin omitted)
- Extraction: 72% EXTRACTED · 28% INFERRED · 0% AMBIGUOUS · INFERRED: 295 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `65fad4cd`
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
- [[_COMMUNITY_Community 11|Community 11]]
- [[_COMMUNITY_OpenCode Plugin Config|OpenCode Plugin Config]]
- [[_COMMUNITY_OpenCode Schema|OpenCode Schema]]
- [[_COMMUNITY_OpenCode Package Deps|OpenCode Package Deps]]
- [[_COMMUNITY_OpenCode Package Single|OpenCode Package Single]]
- [[_COMMUNITY_Roadmap Phase C|Roadmap Phase C]]

## God Nodes (most connected - your core abstractions)
1. `ConfigManager` - 118 edges
2. `String` - 51 edges
3. `PlayerDataTest` - 36 edges
4. `PlayerData` - 31 edges
5. `Test` - 28 edges
6. `DatabaseManager` - 25 edges
7. `BlockBreakListener` - 19 edges
8. `of()` - 18 edges
9. `MedusaCommand` - 15 edges
10. `DataManager` - 15 edges

## Surprising Connections (you probably didn't know these)
- `ConfigManager` --shares_data_with--> `OreWeight`  [INFERRED]
  src/main/java/me/perseusj/medusaantixray/managers/ConfigManager.java → src/main/java/me/perseusj/medusaantixray/data/OreWeight.java
- `DatabaseManager` --shares_data_with--> `MineEvent`  [INFERRED]
  src/main/java/me/perseusj/medusaantixray/managers/DatabaseManager.java → src/main/java/me/perseusj/medusaantixray/data/MineEvent.java
- `DataManager` --shares_data_with--> `PlayerData`  [INFERRED]
  src/main/java/me/perseusj/medusaantixray/managers/DataManager.java → src/main/java/me/perseusj/medusaantixray/data/PlayerData.java
- `graphify` --conceptually_related_to--> `Graphify Skill`  [INFERRED]
  .agents/rules/graphify.md → .agents/workflows/graphify.md
- `MedusaAntiXray` --calls--> `MedusaCommand`  [EXTRACTED]
  src/main/java/me/perseusj/medusaantixray/MedusaAntiXray.java → src/main/java/me/perseusj/medusaantixray/commands/MedusaCommand.java

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Detection Pipeline** — listeners_blockbreaklistener_blockbreaklistener, data_playerdata_playerdata, managers_configmanager_configmanager, managers_datamanager_datamanager, managers_alertmanager_alertmanager, data_mineevent_mineevent [EXTRACTED 1.00]
- **Data Persistence Layer** — managers_datamanager_datamanager, managers_databasemanager_databasemanager, data_playerdata_playerdata, data_mineevent_mineevent [EXTRACTED 1.00]
- **Data Model** — data_playerdata_playerdata, data_mineevent_mineevent, data_oreweight_oreweight, data_veincontext_veincontext [EXTRACTED 1.00]

## Communities (18 total, 5 thin omitted)

### Community 0 - "Player Data & Scoring"
Cohesion: 0.08
Nodes (11): of(), PlayerData, PlayerDataTest, MiningStyle, MineEvent, List, MineEvent, String (+3 more)

### Community 1 - "Database & Config Persistence"
Cohesion: 0.06
Nodes (17): AlertRecord, Consumer, HikariDataSource, CalibrationManager, PlayerStats, DatabaseManager, MineEvent, ConfigManager (+9 more)

### Community 2 - "Commands, Alerts & Messages"
Cohesion: 0.15
Nodes (10): Collection, Component, Player, Sound, ConfigManager, DatabaseManager, JavaPlugin, List (+2 more)

### Community 3 - "Session & Data Lifecycle"
Cohesion: 0.11
Nodes (16): Listener, SessionListener, DataManager, PlayerJoinEvent, PlayerTeleportEvent, ConfigManager, DataManager, EventHandler (+8 more)

### Community 4 - "Detection Configuration"
Cohesion: 0.05
Nodes (11): DepthRange, AlertManager, ConfigManager, String, AlertTier, String, AlertTier, List (+3 more)

### Community 5 - "Block Break Detection"
Cohesion: 0.15
Nodes (14): AlertManager, Block, BlockBreakEvent, CalibrationManager, OreWeight, EventHandler, JavaPlugin, BlockBreakListener (+6 more)

### Community 6 - "Ore Lists & Alert Dispatch"
Cohesion: 0.04
Nodes (44): A1 — Fix async thread-safety bug in `isBlockExposed`, A2 — Race-condition hardening in `DataManager` load-vs-addEvent, A3 — Data retention & cleanup policy, A4 — Config validation on load, A5 — Database-failure fallback hardening, B1 — Y-level / depth normalization, B2 — Vein awareness (ore-vein grouping), B3 — Per-ore configurable weights (+36 more)

### Community 8 - "Community 8"
Cohesion: 0.38
Nodes (3): WebhookManager, Logger, String

### Community 10 - "Graphify Agent Rules"
Cohesion: 0.33
Nodes (7): Graph JSON, Graph Report, graphify, Graphify CLI, Graphify Output Directory, Graphify Skill, Workflow: graphify

### Community 11 - "Community 11"
Cohesion: 0.14
Nodes (15): Command, CommandExecutor, MedusaCommand, CommandSender, Runnable, ConfigManager, DataManager, List (+7 more)

### Community 17 - "Roadmap Phase C"
Cohesion: 0.19
Nodes (5): Double, Map, Logger, MedusaAntiXray, OreWeight

## Knowledge Gaps
- **55 isolated node(s):** `Current Architecture at a Glance`, `Current Feature Set (v1.0.0-SNAPSHOT)`, `How to Read This Roadmap`, `A1 — Fix async thread-safety bug in `isBlockExposed``, `A2 — Race-condition hardening in `DataManager` load-vs-addEvent` (+50 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **5 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `ConfigManager` connect `Detection Configuration` to `Database & Config Persistence`, `Commands, Alerts & Messages`, `Session & Data Lifecycle`, `Block Break Detection`, `Community 11`, `Roadmap Phase C`?**
  _High betweenness centrality (0.256) - this node is a cross-community bridge._
- **Why does `DatabaseManager` connect `Database & Config Persistence` to `Player Data & Scoring`, `Session & Data Lifecycle`, `Detection Configuration`, `Block Break Detection`, `Community 11`?**
  _High betweenness centrality (0.088) - this node is a cross-community bridge._
- **Why does `PlayerData` connect `Player Data & Scoring` to `Session & Data Lifecycle`, `Database & Config Persistence`, `Community 11`, `Block Break Detection`?**
  _High betweenness centrality (0.082) - this node is a cross-community bridge._
- **Are the 2 inferred relationships involving `PlayerData` (e.g. with `MineEvent` and `DataManager`) actually correct?**
  _`PlayerData` has 2 INFERRED edges - model-reasoned connections that need verification._
- **What connects `Current Architecture at a Glance`, `Current Feature Set (v1.0.0-SNAPSHOT)`, `How to Read This Roadmap` to the rest of the system?**
  _55 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Player Data & Scoring` be split into smaller, more focused modules?**
  _Cohesion score 0.08251748251748252 - nodes in this community are weakly interconnected._
- **Should `Database & Config Persistence` be split into smaller, more focused modules?**
  _Cohesion score 0.06462585034013606 - nodes in this community are weakly interconnected._