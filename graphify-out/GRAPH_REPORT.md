# Graph Report - MedusaAntiXray  (2026-06-26)

## Corpus Check
- 20 files · ~17,608 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 306 nodes · 687 edges · 16 communities (11 shown, 5 thin omitted)
- Extraction: 74% EXTRACTED · 26% INFERRED · 0% AMBIGUOUS · INFERRED: 176 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `37ca5d20`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- [[_COMMUNITY_Core Detection Logic|Core Detection Logic]]
- [[_COMMUNITY_Configuration Management|Configuration Management]]
- [[_COMMUNITY_Session Management|Session Management]]
- [[_COMMUNITY_Alert Dispatch System|Alert Dispatch System]]
- [[_COMMUNITY_Plugin Lifecycle|Plugin Lifecycle]]
- [[_COMMUNITY_Plugin Configuration|Plugin Configuration]]
- [[_COMMUNITY_Command Handler|Command Handler]]
- [[_COMMUNITY_Graphify Agent Rules|Graphify Agent Rules]]
- [[_COMMUNITY_OpenCode Configuration|OpenCode Configuration]]
- [[_COMMUNITY_Plugin Dependencies|Plugin Dependencies]]
- [[_COMMUNITY_MineEvent Model|MineEvent Model]]
- [[_COMMUNITY_Community 12|Community 12]]
- [[_COMMUNITY_Community 13|Community 13]]
- [[_COMMUNITY_Community 14|Community 14]]

## God Nodes (most connected - your core abstractions)
1. `ConfigManager` - 62 edges
2. `String` - 32 edges
3. `PlayerDataTest` - 21 edges
4. `Test` - 18 edges
5. `DatabaseManager` - 16 edges
6. `PlayerData` - 15 edges
7. `of()` - 13 edges
8. `List` - 12 edges
9. `MedusaAntiXray — Long-Term Upgrade Roadmap` - 12 edges
10. `BlockBreakListener` - 11 edges

## Surprising Connections (you probably didn't know these)
- `graphify` --conceptually_related_to--> `Graphify Skill`  [INFERRED]
  .agents/rules/graphify.md → .agents/workflows/graphify.md
- `Medusa Anti-Xray Configuration` --conceptually_related_to--> `Medusa Anti-Xray Plugin Descriptor`  [INFERRED]
  src/main/resources/config.yml → src/main/resources/plugin.yml
- `Medusa Anti-Xray` --conceptually_related_to--> `MedusaAntiXray Main Class`  [INFERRED]
  src/main/resources/config.yml → src/main/resources/plugin.yml
- `Alert System` --conceptually_related_to--> `medusa.staff Permission`  [INFERRED]
  src/main/resources/config.yml → src/main/resources/plugin.yml
- `MedusaAntiXray` --inherits--> `JavaPlugin`  [EXTRACTED]
  src/main/java/me/perseusj/medusaantixray/MedusaAntiXray.java → src/main/java/me/perseusj/medusaantixray/listeners/BlockBreakListener.java

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Detection Pipeline** — resources_config_detection_system, resources_config_ratio_algorithm, resources_config_overworld_tracking, resources_config_nether_tracking [INFERRED 0.95]
- **Medusa Anti-Xray Plugin Bundle** — resources_config, resources_plugin, resources_plugin_main_class, resources_config_medusa_anti_xray [INFERRED 0.95]

## Communities (16 total, 5 thin omitted)

### Community 0 - "Core Detection Logic"
Cohesion: 0.14
Nodes (8): of(), PlayerData, PlayerDataTest, MineEvent, List, MineEvent, MineEvent, Test

### Community 1 - "Configuration Management"
Cohesion: 0.09
Nodes (11): DepthRange, Logger, ConfigManager, Map, String, String, List, MedusaAntiXray (+3 more)

### Community 2 - "Session Management"
Cohesion: 0.33
Nodes (6): Listener, SessionListener, PlayerJoinEvent, DataManager, EventHandler, PlayerQuitEvent

### Community 4 - "Plugin Lifecycle"
Cohesion: 0.11
Nodes (10): Consumer, HikariDataSource, DatabaseManager, ConfigManager, List, MedusaAntiXray, MineEvent, String (+2 more)

### Community 5 - "Plugin Configuration"
Cohesion: 0.27
Nodes (13): Medusa Anti-Xray Configuration, Alert System, Detection System, Medusa Anti-Xray, Nether Tracking, Overworld Tracking, Ratio Detection Algorithm, Medusa Anti-Xray Plugin Descriptor (+5 more)

### Community 6 - "Command Handler"
Cohesion: 0.10
Nodes (19): Collection, Command, CommandExecutor, MedusaCommand, CommandSender, DataManager, Runnable, ConfigManager (+11 more)

### Community 7 - "Graphify Agent Rules"
Cohesion: 0.33
Nodes (7): Graph JSON, Graph Report, graphify, Graphify CLI, Graphify Output Directory, Graphify Skill, Workflow: graphify

### Community 11 - "MineEvent Model"
Cohesion: 0.04
Nodes (44): A1 — Fix async thread-safety bug in `isBlockExposed`, A2 — Race-condition hardening in `DataManager` load-vs-addEvent, A3 — Data retention & cleanup policy, A4 — Config validation on load, A5 — Database-failure fallback hardening, B1 — Y-level / depth normalization, B2 — Vein awareness (ore-vein grouping), B3 — Per-ore configurable weights (+36 more)

### Community 12 - "Community 12"
Cohesion: 0.13
Nodes (13): AlertManager, Block, BlockBreakEvent, DatabaseManager, BlockBreakListener, MedusaAntiXray, ConfigManager, DataManager (+5 more)

## Knowledge Gaps
- **50 isolated node(s):** `$schema`, `plugin`, `@opencode-ai/plugin`, `MineEvent`, `ConfigManager` (+45 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **5 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `ConfigManager` connect `Configuration Management` to `Plugin Lifecycle`, `Community 12`, `Command Handler`?**
  _High betweenness centrality (0.104) - this node is a cross-community bridge._
- **Why does `of()` connect `Core Detection Logic` to `Configuration Management`, `Alert Dispatch System`, `Plugin Lifecycle`?**
  _High betweenness centrality (0.050) - this node is a cross-community bridge._
- **Why does `DatabaseManager` connect `Plugin Lifecycle` to `Command Handler`?**
  _High betweenness centrality (0.048) - this node is a cross-community bridge._
- **What connects `$schema`, `plugin`, `@opencode-ai/plugin` to the rest of the system?**
  _50 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Core Detection Logic` be split into smaller, more focused modules?**
  _Cohesion score 0.14487179487179488 - nodes in this community are weakly interconnected._
- **Should `Configuration Management` be split into smaller, more focused modules?**
  _Cohesion score 0.08651911468812877 - nodes in this community are weakly interconnected._
- **Should `Plugin Lifecycle` be split into smaller, more focused modules?**
  _Cohesion score 0.10795454545454546 - nodes in this community are weakly interconnected._