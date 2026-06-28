# Graph Report - .  (2026-06-27)

## Corpus Check
- Corpus is ~17,635 words - fits in a single context window. You may not need a graph.

## Summary
- 272 nodes · 672 edges · 21 communities (12 shown, 9 thin omitted)
- Extraction: 75% EXTRACTED · 25% INFERRED · 0% AMBIGUOUS · INFERRED: 168 edges (avg confidence: 0.81)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Player Data & Scoring|Player Data & Scoring]]
- [[_COMMUNITY_Database & Config Persistence|Database & Config Persistence]]
- [[_COMMUNITY_Commands, Alerts & Messages|Commands, Alerts & Messages]]
- [[_COMMUNITY_Session & Data Lifecycle|Session & Data Lifecycle]]
- [[_COMMUNITY_Detection Configuration|Detection Configuration]]
- [[_COMMUNITY_Block Break Detection|Block Break Detection]]
- [[_COMMUNITY_Ore Lists & Alert Dispatch|Ore Lists & Alert Dispatch]]
- [[_COMMUNITY_Config Loading & Validation|Config Loading & Validation]]
- [[_COMMUNITY_Vein Context & Grouping|Vein Context & Grouping]]
- [[_COMMUNITY_Ore Weight by World Type|Ore Weight by World Type]]
- [[_COMMUNITY_Graphify Agent Rules|Graphify Agent Rules]]
- [[_COMMUNITY_OpenCode Plugin Config|OpenCode Plugin Config]]
- [[_COMMUNITY_OpenCode Schema|OpenCode Schema]]
- [[_COMMUNITY_OpenCode Package Deps|OpenCode Package Deps]]
- [[_COMMUNITY_OpenCode Package Single|OpenCode Package Single]]
- [[_COMMUNITY_Roadmap Phase C|Roadmap Phase C]]
- [[_COMMUNITY_Roadmap Phase D|Roadmap Phase D]]
- [[_COMMUNITY_Roadmap Phase E|Roadmap Phase E]]
- [[_COMMUNITY_Roadmap Phase F|Roadmap Phase F]]

## God Nodes (most connected - your core abstractions)
1. `ConfigManager` - 71 edges
2. `String` - 32 edges
3. `PlayerDataTest` - 25 edges
4. `PlayerData` - 23 edges
5. `DatabaseManager` - 23 edges
6. `BlockBreakListener` - 22 edges
7. `Test` - 18 edges
8. `DataManager` - 15 edges
9. `MedusaAntiXray` - 13 edges
10. `of()` - 13 edges

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

## Communities (21 total, 9 thin omitted)

### Community 0 - "Player Data & Scoring"
Cohesion: 0.13
Nodes (9): of(), PlayerData, PlayerDataTest, List, MineEvent, String, UUID, MineEvent (+1 more)

### Community 1 - "Database & Config Persistence"
Cohesion: 0.09
Nodes (15): Consumer, A1: Fix async thread-safety bug in isBlockExposed, A2: Race-condition hardening in DataManager load-vs-addEvent, A3: Data retention & cleanup policy, A4: Config validation on load, A5: Database-failure fallback hardening, Phase A: Stability & Correctness, HikariDataSource (+7 more)

### Community 2 - "Commands, Alerts & Messages"
Cohesion: 0.08
Nodes (20): Command, CommandExecutor, MedusaCommand, CommandSender, Behavioral/Statistical X-ray Detection, Sliding Window Detection Algorithm, Weighted Ore Ratio Scoring, AlertManager (+12 more)

### Community 3 - "Session & Data Lifecycle"
Cohesion: 0.12
Nodes (13): Collection, Listener, SessionListener, DataManager, PlayerJoinEvent, Runnable, DataManager, EventHandler (+5 more)

### Community 5 - "Block Break Detection"
Cohesion: 0.11
Nodes (20): AlertManager, Block, BlockBreakEvent, OreWeight, DatabaseManager, B1: Y-level / depth normalization, B3: Per-ore configurable weights, B4: Tool & enchantment awareness (+12 more)

### Community 6 - "Ore Lists & Alert Dispatch"
Cohesion: 0.24
Nodes (3): String, List, String

### Community 7 - "Config Loading & Validation"
Cohesion: 0.21
Nodes (4): DepthRange, Logger, Map, MedusaAntiXray

### Community 8 - "Vein Context & Grouping"
Cohesion: 0.25
Nodes (3): VeinContext, B2: Vein awareness (ore-vein grouping), String

### Community 9 - "Ore Weight by World Type"
Cohesion: 0.44
Nodes (3): String, OreWeight, WorldType

### Community 10 - "Graphify Agent Rules"
Cohesion: 0.39
Nodes (8): Graphify Rules, Graph JSON, Graph Report, Graphify, Graphify CLI, Graphify Output Directory, Graphify Workflow, Graphify Skill

## Knowledge Gaps
- **19 isolated node(s):** `$schema`, `plugin`, `@opencode-ai/plugin`, `ConfigManager`, `String` (+14 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **9 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `ConfigManager` connect `Detection Configuration` to `Database & Config Persistence`, `Commands, Alerts & Messages`, `Block Break Detection`, `Ore Lists & Alert Dispatch`, `Config Loading & Validation`, `Ore Weight by World Type`, `World Classification`?**
  _High betweenness centrality (0.284) - this node is a cross-community bridge._
- **Why does `DatabaseManager` connect `Database & Config Persistence` to `Player Data & Scoring`, `Commands, Alerts & Messages`, `Session & Data Lifecycle`, `Detection Configuration`, `Block Break Detection`?**
  _High betweenness centrality (0.150) - this node is a cross-community bridge._
- **Why does `BlockBreakListener` connect `Block Break Detection` to `Player Data & Scoring`, `Database & Config Persistence`, `Commands, Alerts & Messages`, `Session & Data Lifecycle`, `Detection Configuration`, `Ore Lists & Alert Dispatch`, `Vein Context & Grouping`, `Ore Weight by World Type`, `World Classification`?**
  _High betweenness centrality (0.142) - this node is a cross-community bridge._
- **Are the 3 inferred relationships involving `ConfigManager` (e.g. with `OreWeight` and `B3: Per-ore configurable weights`) actually correct?**
  _`ConfigManager` has 3 INFERRED edges - model-reasoned connections that need verification._
- **Are the 3 inferred relationships involving `PlayerData` (e.g. with `MineEvent` and `Sliding Window Detection Algorithm`) actually correct?**
  _`PlayerData` has 3 INFERRED edges - model-reasoned connections that need verification._
- **What connects `$schema`, `plugin`, `@opencode-ai/plugin` to the rest of the system?**
  _20 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Player Data & Scoring` be split into smaller, more focused modules?**
  _Cohesion score 0.12956810631229235 - nodes in this community are weakly interconnected._