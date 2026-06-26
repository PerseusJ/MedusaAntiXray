# Graph Report - .  (2026-06-20)

## Corpus Check
- Corpus is ~1,984 words - fits in a single context window. You may not need a graph.

## Summary
- 137 nodes · 247 edges · 12 communities (10 shown, 2 thin omitted)
- Extraction: 79% EXTRACTED · 21% INFERRED · 0% AMBIGUOUS · INFERRED: 53 edges (avg confidence: 0.81)
- Token cost: 0 input · 0 output

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

## God Nodes (most connected - your core abstractions)
1. `ConfigManager` - 27 edges
2. `String` - 14 edges
3. `PlayerData` - 11 edges
4. `DataManager` - 9 edges
5. `List` - 7 edges
6. `Medusa Anti-Xray Configuration` - 7 edges
7. `Graphify` - 6 edges
8. `Detection System` - 6 edges
9. `Medusa Anti-Xray Plugin Descriptor` - 6 edges
10. `MedusaCommand` - 5 edges

## Surprising Connections (you probably didn't know these)
- `Graphify` --conceptually_related_to--> `Graphify Skill`  [INFERRED]
  .agents/rules/graphify.md → .agents/workflows/graphify.md
- `Medusa Anti-Xray Configuration` --conceptually_related_to--> `Medusa Anti-Xray Plugin Descriptor`  [INFERRED]
  src/main/resources/config.yml → src/main/resources/plugin.yml
- `Medusa Anti-Xray` --conceptually_related_to--> `MedusaAntiXray Main Class`  [INFERRED]
  src/main/resources/config.yml → src/main/resources/plugin.yml
- `Alert System` --conceptually_related_to--> `medusa.staff Permission`  [INFERRED]
  src/main/resources/config.yml → src/main/resources/plugin.yml

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Detection Pipeline** — resources_config_detection_system, resources_config_ratio_algorithm, resources_config_overworld_tracking, resources_config_nether_tracking [INFERRED 0.95]
- **Medusa Anti-Xray Plugin Bundle** — resources_config, resources_plugin, resources_plugin_main_class, resources_config_medusa_anti_xray [INFERRED 0.95]

## Communities (12 total, 2 thin omitted)

### Community 0 - "Core Detection Logic"
Cohesion: 0.12
Nodes (8): Block, BlockBreakEvent, PlayerData, BlockBreakListener, MineEvent, String, UUID, EventHandler

### Community 1 - "Configuration Management"
Cohesion: 0.19
Nodes (4): ConfigManager, MedusaAntiXray, List, String

### Community 2 - "Session Management"
Cohesion: 0.15
Nodes (10): Collection, Listener, SessionListener, DataManager, PlayerData, PlayerJoinEvent, PlayerQuitEvent, EventHandler (+2 more)

### Community 3 - "Alert Dispatch System"
Cohesion: 0.16
Nodes (4): AlertManager, Player, String, Utils

### Community 4 - "Plugin Lifecycle"
Cohesion: 0.18
Nodes (6): ConfigManager, JavaPlugin, MedusaAntiXray, String, Override, WorldType

### Community 5 - "Plugin Configuration"
Cohesion: 0.27
Nodes (13): Medusa Anti-Xray Configuration, Alert System, Detection System, Medusa Anti-Xray, Nether Tracking, Overworld Tracking, Ratio Detection Algorithm, Medusa Anti-Xray Plugin Descriptor (+5 more)

### Community 6 - "Command Handler"
Cohesion: 0.31
Nodes (8): Command, CommandExecutor, MedusaCommand, CommandSender, List, Override, String, TabCompleter

### Community 7 - "Graphify Agent Rules"
Cohesion: 0.39
Nodes (8): Graphify Rules, Graph JSON, Graph Report, Graphify, Graphify CLI, Graphify Output Directory, Graphify Workflow, Graphify Skill

## Knowledge Gaps
- **9 isolated node(s):** `$schema`, `plugin`, `@opencode-ai/plugin`, `String`, `WorldType` (+4 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **2 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `ConfigManager` connect `Configuration Management` to `Core Detection Logic`, `Alert Dispatch System`, `Plugin Lifecycle`?**
  _High betweenness centrality (0.102) - this node is a cross-community bridge._
- **Why does `DataManager` connect `Session Management` to `Plugin Lifecycle`?**
  _High betweenness centrality (0.083) - this node is a cross-community bridge._
- **Why does `ConfigManager` connect `Plugin Lifecycle` to `Core Detection Logic`, `Command Handler`?**
  _High betweenness centrality (0.056) - this node is a cross-community bridge._
- **What connects `$schema`, `plugin`, `@opencode-ai/plugin` to the rest of the system?**
  _9 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Core Detection Logic` be split into smaller, more focused modules?**
  _Cohesion score 0.11965811965811966 - nodes in this community are weakly interconnected._