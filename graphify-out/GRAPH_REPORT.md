# Graph Report - MedusaAntiXray  (2026-06-26)

## Corpus Check
- 20 files · ~17,635 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 299 nodes · 673 edges · 19 communities (14 shown, 5 thin omitted)
- Extraction: 75% EXTRACTED · 25% INFERRED · 0% AMBIGUOUS · INFERRED: 171 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `4eade67a`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- [[_COMMUNITY_Core Detection Logic|Core Detection Logic]]
- [[_COMMUNITY_Configuration Management|Configuration Management]]
- [[_COMMUNITY_Session Management|Session Management]]
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
- [[_COMMUNITY_Community 16|Community 16]]
- [[_COMMUNITY_Community 17|Community 17]]
- [[_COMMUNITY_Community 18|Community 18]]

## God Nodes (most connected - your core abstractions)
1. `ConfigManager` - 62 edges
2. `String` - 32 edges
3. `PlayerDataTest` - 21 edges
4. `Test` - 18 edges
5. `DatabaseManager` - 16 edges
6. `PlayerData` - 15 edges
7. `of()` - 13 edges
8. `MedusaAntiXray — Long-Term Upgrade Roadmap` - 12 edges
9. `List` - 12 edges
10. `BlockBreakListener` - 11 edges

## Surprising Connections (you probably didn't know these)
- `graphify` --conceptually_related_to--> `Graphify Skill`  [INFERRED]
  .agents/rules/graphify.md → .agents/workflows/graphify.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Detection Pipeline** — resources_config_detection_system, resources_config_ratio_algorithm, resources_config_overworld_tracking, resources_config_nether_tracking [INFERRED 0.95]
- **Medusa Anti-Xray Plugin Bundle** — resources_config, resources_plugin, resources_plugin_main_class, resources_config_medusa_anti_xray [INFERRED 0.95]

## Communities (19 total, 5 thin omitted)

### Community 0 - "Core Detection Logic"
Cohesion: 0.14
Nodes (8): of(), PlayerData, PlayerDataTest, MineEvent, List, MineEvent, MineEvent, Test

### Community 1 - "Configuration Management"
Cohesion: 0.20
Nodes (3): ConfigManager, String, String

### Community 2 - "Session Management"
Cohesion: 0.13
Nodes (16): AlertManager, Block, BlockBreakEvent, DataManager, EventHandler, JavaPlugin, Listener, BlockBreakListener (+8 more)

### Community 4 - "Plugin Lifecycle"
Cohesion: 0.13
Nodes (9): Consumer, HikariDataSource, DatabaseManager, ConfigManager, List, MedusaAntiXray, MineEvent, String (+1 more)

### Community 5 - "Plugin Configuration"
Cohesion: 0.40
Nodes (6): Medusa Anti-Xray Plugin Descriptor, MedusaAntiXray Main Class, medusa.admin Permission, medusa.bypass Permission, medusa Command, medusa.staff Permission

### Community 6 - "Command Handler"
Cohesion: 0.11
Nodes (16): Command, CommandExecutor, MedusaCommand, CommandSender, PlayerData, Runnable, ConfigManager, DataManager (+8 more)

### Community 7 - "Graphify Agent Rules"
Cohesion: 0.33
Nodes (7): Graph JSON, Graph Report, graphify, Graphify CLI, Graphify Output Directory, Graphify Skill, Workflow: graphify

### Community 11 - "MineEvent Model"
Cohesion: 0.04
Nodes (44): A1 — Fix async thread-safety bug in `isBlockExposed`, A2 — Race-condition hardening in `DataManager` load-vs-addEvent, A3 — Data retention & cleanup policy, A4 — Config validation on load, A5 — Database-failure fallback hardening, B1 — Y-level / depth normalization, B2 — Vein awareness (ore-vein grouping), B3 — Per-ore configurable weights (+36 more)

### Community 12 - "Community 12"
Cohesion: 0.15
Nodes (9): Collection, DatabaseManager, DataManager, MedusaAntiXray, ConfigManager, PlayerData, String, UUID (+1 more)

### Community 16 - "Community 16"
Cohesion: 0.18
Nodes (3): String, List, WorldType

### Community 17 - "Community 17"
Cohesion: 0.29
Nodes (4): Logger, Map, MedusaAntiXray, OreWeight

## Knowledge Gaps
- **52 isolated node(s):** `Current Architecture at a Glance`, `Current Feature Set (v1.0.0-SNAPSHOT)`, `How to Read This Roadmap`, `A1 — Fix async thread-safety bug in `isBlockExposed``, `A2 — Race-condition hardening in `DataManager` load-vs-addEvent` (+47 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **5 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `ConfigManager` connect `Configuration Management` to `Alert Dispatch System`, `Plugin Lifecycle`, `Community 16`, `Community 17`, `Community 18`?**
  _High betweenness centrality (0.107) - this node is a cross-community bridge._
- **Why does `DatabaseManager` connect `Plugin Lifecycle` to `Community 12`, `Command Handler`?**
  _High betweenness centrality (0.050) - this node is a cross-community bridge._
- **Why does `of()` connect `Core Detection Logic` to `Community 16`, `Community 18`, `Plugin Lifecycle`, `Command Handler`?**
  _High betweenness centrality (0.046) - this node is a cross-community bridge._
- **What connects `Current Architecture at a Glance`, `Current Feature Set (v1.0.0-SNAPSHOT)`, `How to Read This Roadmap` to the rest of the system?**
  _52 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Core Detection Logic` be split into smaller, more focused modules?**
  _Cohesion score 0.1402439024390244 - nodes in this community are weakly interconnected._
- **Should `Session Management` be split into smaller, more focused modules?**
  _Cohesion score 0.13405797101449277 - nodes in this community are weakly interconnected._
- **Should `Plugin Lifecycle` be split into smaller, more focused modules?**
  _Cohesion score 0.12962962962962962 - nodes in this community are weakly interconnected._