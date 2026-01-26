package com.example.modmenu.client;

import java.util.ArrayList;
import java.util.List;

public class WikiData {
    public static class WikiSection {
        public final String title;
        public final String content;

        public WikiSection(String title, String content) {
            this.title = title;
            this.content = content;
        }
    }

    public static List<WikiSection> getSections() {
        List<WikiSection> sections = new ArrayList<>();

        // 1. Global Mod Architecture
        sections.add(new WikiSection("1.0 Global Mod Architecture: Core Engine",
            "Definition: The System Mod is a high-level reality-warping simulation engine that overlays standard Minecraft mechanics with a BigDecimal-based economic and skill progression framework.\n\n" +
            "Design Intent: To provide an end-game experience where the player transitions from a participant in the world to its administrator, managing resources at an infinite scale.\n\n" +
            "Problem Solved: Standard Minecraft cannot handle currency or item counts beyond 2.1 billion (Integer.MAX_VALUE) and lacks a centralized, persistent progression system that scales exponentially without causing server lag.\n\n" +
            "Data Read: Player inventories, world block states, entity data, server tick rates, and Forge capability data (Energy, Fluids, Items).\n\n" +
            "Data Mutates: Player bank balance (BigDecimal), Skill Tree state, World constants (Time/Weather), and virtualized entity storage.\n\n" +
            "Internal Behavior: The mod operates on a dual-thread model. Core economic logic (Interest, Passive SP) and Logistics run on the Server Thread, while the UI and client-side predictions run on the Client Thread. Data is synchronized via custom packet protocols (SyncMoneyPacket, SyncSkillsPacket, etc.).\n\n" +
            "Player View: A unified menu (default '9') providing access to all features and a real-time bank account.\n\n" +
            "Engine View: A series of event listeners (LivingDeathEvent, BlockEvent.BreakEvent, etc.) that intercept vanilla actions to calculate rewards and costs based on the player's unlocked skills.\n\n" +
            "Failure States: Network desync between client and server (resolved by automatic periodic syncing); Data corruption if the server crashes during a save cycle (mitigated by atomic save operations).\n\n" +
            "Edge Cases: Balancing values at the octillion scale; handling entity data for modded mobs with complex NBT.\n\n" +
            "Misuse: Attempting to trigger recursive logistics loops (prevented by a 20-depth execution limit).\n\n" +
            "Performance Impact: Minimal. Most calculations use high-performance BigDecimal operations and cached data to avoid redundant world lookups.\n\n" +
            "Multiplayer Impact: Every player has a unique, private system profile. Economy and Skills are player-specific.\n\n" +
            "Verification: Check the 'Diagnostics' screen for real-time performance metrics and system health.\n\n" +
            "Breaking: Can be broken by external mods that force-clear capabilities or bypass Forge event buses."));

        // 2. Skill Tree & Progression
        sections.add(new WikiSection("2.0 Skill Tree: Master Architecture",
            "What it is: A non-linear progression map consisting of 4 major branches (Wealth, Combat, Utility, Virtualization) with interconnected dependencies.\n" +
            "Why it exists: To provide a structured yet flexible path to total system dominance.\n" +
            "Problem Solved: Provides a meaningful use for extreme wealth and combat success.\n" +
            "Data Read: Player's total SP, current skill ranks, and prerequisite unlock states.\n" +
            "Data Mutates: Total SP, Skill Rank, and specific system flags.\n" +
            "Internal Behavior: Purchases are validated on the server for SP and prerequisites before incrementing rank and syncing to the client.\n" +
            "Player View: An interactive tree where nodes are clicked to upgrade. Color-coded status indicators.\n" +
            "Engine View: A Map<String, Integer> mapping Skill IDs to ranks, checked by every system interceptor.\n" +
            "Failure States: Purchase rejection due to insufficient SP or missing prerequisites.\n" +
            "Edge Cases: Respec rules—skills are permanent to maintain system stability.\n" +
            "Misuse: Attempting to bypass prerequisites via modified packets (prevented by server-side validation).\n" +
            "Performance Impact: Negligible lookup overhead.\n" +
            "Multiplayer Impact: Skills are entirely private to each player.\n" +
            "Interaction: Fuels all other mod systems by unlocking or enhancing their core logic.\n" +
            "Verification: Check the 'Skill Tree' or 'Diagnostics' screens.\n" +
            "How to break: Resetting world data without resetting player data (causes desync)."));

        sections.add(new WikiSection("2.1 Wealth Branch: Skill Specification",
            "WEALTH_MARKET_MANIPULATION:\n" +
            "- Effect: +50% sell price per rank. Rank X: No bulk-price reduction.\n" +
            "- Timing: Sell event. Formula: Price * (1 + 0.5 * Rank).\n" +
            "- Failure: $0 items. Abuse: Infinite loop with buy/sell (prevented by spread).\n\n" +
            "WEALTH_EXPONENTIAL_BOUNTY:\n" +
            "- Effect: MaxHP^exponent rewards. Scaling: 1.0 -> 2.5 exponent.\n" +
            "- Timing: Kill event. Interaction: Multiplied by Satiety.\n\n" +
            "WEALTH_BASIC_SAVINGS & INTEREST:\n" +
            "- Effect: 1% interest + 1% per RateRank every 30s.\n" +
            "- Formula: Balance * Rate. Cap: $100M + $10B * CapRank.\n" +
            "- Keystone: Removes cap, doubles all money income.\n\n" +
            "WEALTH_PORTFOLIO:\n" +
            "- Effect: 1 SP per $100B per minute. YieldRank: +1 SP per $10B.\n" +
            "- Timing: Every 60s. Abuse: High balance = millions of SP/hour."));

        sections.add(new WikiSection("2.2 Combat Branch: Skill Specification",
            "COMBAT_EFFICIENCY_CORE: Reduces ability drain. Rank V: $0 if balance > $500M.\n\n" +
            "COMBAT_DEFENSIVE_FEEDBACK: 200%-20,000% damage reflection. Rank 10: Deletes attackers.\n\n" +
            "COMBAT_CAUSALITY_REVERSAL: 20%/rank damage to healing. Rank 5: Absolute Invincibility.\n\n" +
            "COMBAT_SOUL_REAP: +0.033 HP / +0.016 DMG per rank per kill. Stack behavior: Infinite additive.\n\n" +
            "COMBAT_BLOOD_DATA: 10% chance for extra SP on kill. Bonus: spGain * bloodData * 0.5.\n\n" +
            "COMBAT_PROBABILITY_COLLAPSE: Forces Crits and Max Loot. Timing: Pre-damage hook."));

        sections.add(new WikiSection("2.3 Utility Branch: Skill Specification",
            "UTILITY_QUANTUM_VACUUM: Global item magnet. Timing: Every tick scan.\n\n" +
            "UTILITY_MOLECULAR_REPLICATION: Multiplies block drops (up to 10,000x or 64x Full Stack).\n\n" +
            "UTILITY_STRUCTURAL_REFACTORING: Area Mining size 65x65x65. Formula: Base + 16 * damped(Rank).\n\n" +
            "UTILITY_ATMOSPHERIC_CONTROL: Command-level access to World Rules (Time/Weather/Tick).\n\n" +
            "UTILITY_MATTER_TRANSMUTATION: SP-based block conversion. Cost: 100 / Rank SP.\n\n" +
            "UTILITY_KEYSTONE_ARCHITECT: Delete-on-click for any block within 256m. Costs $0."));

        sections.add(new WikiSection("2.4 Virtualization Branch: Detailed Reference",
            "Focuses on simulation speed, thread allocation, and automated loot processing.\n\n" +
            "VIRT_MULTI_THREAD:\n" +
            "- Effect: Increases kills per simulation interval.\n" +
            "- Formula: Kills = 2 ^ (damped_exponent(Rank)).\n\n" +
            "VIRT_CLOCK_SPEED:\n" +
            "- Effect: Reduces interval between simulation ticks. Rank 20: 1 tick (20 ops/sec).\n\n" +
            "VIRT_ISOLATED_SANDBOX:\n" +
            "- Effect: Prevents Satiety decay from virtualization kills. Ensures 100% yield efficiency forever.\n\n" +
            "VIRT_NEURAL_CONDENSATION:\n" +
            "- Effect: Converts virtual XP into Skill Points (SP).\n" +
            "- Scaling: Cost = 10,000 / Rank XP per 1 SP.\n\n" +
            "VIRT_LOOT_CONDENSATION:\n" +
            "- Effect: Enables internal auto-crafting for chamber buffers (e.g., Iron Ingots to Iron Blocks).\n" +
            "- Modes: Reversible-only (saves space but preserves utility) vs. All (maximizes compression)."));

        // 3. Virtualization System (FULL PIPELINE)
        sections.add(new WikiSection("3.0 Virtualization: Process & Pipeline",
            "What it is: A process-oriented system for capturing, simulating, and harvesting entities and loot tables within isolated memory buffers.\n" +
            "Why it exists: To eliminate world-space mob grinders and entity-based server lag.\n" +
            "Problem Solved: TPS lag from thousands of entities; automation of complex modded loot (Apotheosis).\n" +
            "Data Read: Entity NBT, Loot Tables, and Simulated Tool enchants.\n" +
            "Data Mutates: Virtual Chamber buffers (storedLoot) and player balance.\n" +
            "Internal Behavior: Executes 'virtual kills' on dummy entity instances, triggering Forge drop hooks and applying filters/condensation.\n" +
            "Player View: Active chamber lists with progress bars and detailed loot dashboards.\n" +
            "Engine View: A background ticker (SkillManager) processing kills in batches based on Clock Speed and Threads.\n" +
            "Failure States: Input buffer exhaustion; linked container overflow.\n" +
            "Edge Cases: Simulating unique bosses; handling entities without standard loot tables.\n" +
            "Misuse: Storing millions of high-NBT items causing memory pressure (Solution: Void/Liquidate).\n" +
            "Performance Impact: High efficiency; simulates 10k+ kills/tick with negligible overhead.\n" +
            "Multiplayer Impact: Chambers are private; linked containers are shared blocks.\n" +
            "Interaction: Integrates with Logistics (Source/Dest) and Store (Market-Link).\n" +
            "Verification: Observe 'Update Version' and item counts in Chamber UI.\n" +
            "How to break: Deleting mod while data fragments are in chambers (data persists in NBT but is dead)."));

        sections.add(new WikiSection("3.1 Virtualization: Loot & Engine Logic",
            "Entity Acquisition: Shift-Right Click any living entity to capture its essence. Generic captures EntityType; Exact captures all NBT, including equipment, health, and custom names.\n\n" +
            "Simulation Logic: Every interval, the engine rolls the loot table associated with the entity. This is a pure-data operation; no entity is spawned. It supports 'Looting' and 'Fortune' via the 'Killer Weapon' slot.\n\n" +
            "Apotheosis Integration: The engine manually fires 'onLivingDeath' and 'onLivingDrops' Forge hooks on a dummy entity instance. This ensures that Apotheosis affix gear, gems, and rarity rolls are applied exactly as if the mob were killed in the world.\n\n" +
            "Loot Resolution: Drops are first filtered (Keep/Void/Liquidate), then potentially condensed (Auto-crafted), then pushed to linked containers or stored in the chamber's high-capacity buffer (up to 2,000 unique item stacks).\n\n" +
            "Virtual Bartering: If 'Bartering Mode' is active and the entity is a Piglin, the chamber will consume Gold Ingots from the input buffer to roll the Piglin Bartering loot table."));

        // 4. SP COST SYSTEM
        sections.add(new WikiSection("4.0 Skill Point (SP) Cost System",
            "What it is: A non-linear scaling algorithm determining the SP investment required for system upgrades.\n" +
            "Why it exists: To ensure progression remains challenging as the player's capacity for SP generation grows.\n" +
            "Problem Solved: Prevents instant completion of the skill tree through early-game wealth exploitation.\n" +
            "Data Read: Target Skill ID, Rank, Branch multipliers, and cost-reduction skills.\n" +
            "Data Mutates: Player's 'spentSP' and 'totalSP' balances.\n" +
            "Internal Behavior: Formula: Cost = Base * (Rank^3). Base values: Tier 1=50, Tier 2=250, Tier 3=1000, Keystone=10000.\n" +
            "Player View: Costs displayed on skill nodes; red text if unaffordable.\n" +
            "Engine View: A calculation performed in SkillManager.getSkillCost upon purchase request.\n" +
            "Failure States: SP balance falling below zero prevents the transaction.\n" +
            "Edge Cases: Costs exceeding 1.7e308 handled by BigDecimal math.\n" +
            "Misuse: Attempting to 'refund' skills by modifying local data (prevented by server-side source of truth).\n" +
            "Performance Impact: Zero (simple arithmetic).\n" +
            "Multiplayer Impact: None; costs are individual.\n" +
            "Interaction: Reductions applied by 'Lobbyist' (-5%/rank) and 'Recursive Growth' (-1%/rank).\n" +
            "Verification: Compare node cost with formula: Base * Rank^3 * (1 - reductions).\n" +
            "How to break: Setting base values to 0 in config (allows free upgrades)."));

        // 5. USER INTERFACE – EVERY BUTTON
        sections.add(new WikiSection("5.0 User Interface: Reference Manual",
            "What it is: A comprehensive guide to every interactive element within the System Mod's GUI suite.\n" +
            "Why it exists: To ensure players can navigate the mod's complexity without external aid.\n" +
            "Problem Solved: UI ambiguity and accidental activation of high-cost features.\n" +
            "Data Read: Player's current SP, Balance, active toggles, and network states.\n" +
            "Data Mutate: Active skill states, logistics rules, and virtualization parameters.\n" +
            "Internal Behavior: Buttons trigger packets (e.g., SkillUpgradePacket) which are processed on the server to validate and apply changes.\n" +
            "Player View: Clickable buttons, sliders, and color-coded status indicators.\n" +
            "Engine View: Event-driven GUI listeners sending NBT-wrapped data to the server.\n" +
            "Failure States: Button unresponsive due to server lag or insufficient permissions.\n" +
            "Edge Cases: Opening multiple screens simultaneously (prevented by Minecraft's single-screen limit).\n" +
            "Misuse: Spamming 'Collect All' to cause packet overflow (protected by cooldowns).\n" +
            "Performance Impact: Minimal client-side overhead for rendering.\n" +
            "Multiplayer Impact: UI is entirely client-side; only packets affect the shared server state.\n" +
            "Interaction: Integrates with all other systems (Store, Logistics, etc.).\n" +
            "Verification: Observe UI updates (e.g., Rank number incrementing) after clicking.\n" +
            "How to break: Using macros to click at sub-tick speeds (prevented by server-side rate limiting).\n\n" +
            "Interactive Element Guide:\n" +
            "- 'Add Rule' (Logistics): Creates a new Logic Task. Mutates NetworkData.\n" +
            "- 'Manage' (Containment): Opens the Loot Dashboard for a specific Chamber.\n" +
            "- 'Link/Transfer' (Containment): Sets PULL/PUSH targets in the world.\n" +
            "- 'Void-All' (Containment): Purges the storedLoot buffer.\n" +
            "- 'Set Killer Weapon': Records hand item NBT for simulation.\n" +
            "- 'Reroll Loot': Discards current drops for a new roll via SP."));

        // 6. LINKING, TRANSFER & NETWORKS
        sections.add(new WikiSection("6.0 Linking, Transfer & Networks",
            "What it is: A lag-free logistics framework for moving Items, Energy, and Fluids between physical and virtual nodes.\n" +
            "Why it exists: To provide a unified automation system that scales to thousands of instructions without impacting TPS.\n" +
            "Problem Solved: The performance bottleneck of pipes and conduits in large-scale factories.\n" +
            "Data Read: Resource levels in nodes, Logic Conditions, and Side/Slot configurations.\n" +
            "Data Mutates: BlockEntity inventories/buffers and virtual storage.\n" +
            "Internal Behavior: The NetworkTickHandler processes a global budget of 10,000 rules per tick. Rules are executed in priority order. Successful transfers can trigger 'Chaining' (immediate execution of downstream rules).\n" +
            "Player View: A network map with nodes and rules; real-time status reports (e.g., '[ACTIVE] Moved 64x Iron Ingot').\n" +
            "Engine View: A list of LogisticsRule objects evaluated against the capability cache to avoid redundant BE lookups.\n" +
            "Failure States: Node missing (block broken); Dimension not loaded; Conditions not met.\n" +
            "Edge Cases: Transferring across dimensions—requires the node to be chunk-loaded.\n" +
            "Misuse: Attempting to use a single network for 1M+ rules, which exceeds the tick budget.\n" +
            "Performance Impact: Extremely low due to capability caching and budget-limited processing.\n" +
            "Multiplayer Impact: Networks are private to the player, but can interact with shared world blocks.\n" +
            "Interaction: Fully compatible with Mekanism, AE2, and any mod using standard Forge Capabilities.\n" +
            "Verification: Check the 'Last Report' in the Rule Config screen for real-time status.\n" +
            "How to break: Placing nodes in rapidly loading/unloading chunks may cause temporary transfer interruptions.\n\n" +
            "Transfer Logic: Mode ROUND_ROBIN rotates targets; Mode BALANCED targets the emptiest node. Mode PRIORITY fills targets in list order."));

        sections.add(new WikiSection("6.1 Logistics: Advanced Semantics",
            "Memory Semantics: The system uses a 'Capability Cache' that persists for one server tick. This ensures that even if 100 rules access the same chest, the engine only probes the chest's memory once, drastically reducing overhead.\n\n" +
            "Transfer Tick Logic: Rules are processed sequentially within the 10,000-rule budget. If a rule succeeds, it can trigger a 'Chaining' event where any downstream rule (source = previous destination) is executed immediately, regardless of its position in the list.\n\n" +
            "Side Rules: 'AUTO' mode uses the mod's internal heuristic to find the first valid side for the requested capability (Items, Energy, or Fluids). Manual overrides (North, South, etc.) strictly follow the vanilla Direction protocols.\n\n" +
            "Storage Mod Compatibility: Using standard Forge IItemHandler, IEnergyStorage, and IFluidHandler capabilities ensures 100% compatibility with Mekanism, Applied Energistics 2, Refined Storage, and Sophisticated Storage.\n\n" +
            "Loop Prevention: The engine maintains a 'Chain Depth' counter. If a transfer chain exceeds 20 consecutive rules in a single tick, the chain is forcefully terminated to prevent stack overflow and server crashes."));

        // 7. VOIDING / DESTRUCTION
        sections.add(new WikiSection("7.0 Voiding and Deletion Semantics",
            "What it is: The absolute and irreversible removal of data or items from the system.\n" +
            "Why it exists: To provide high-performance trash-can logic and emergency purges for massive automation overflows.\n" +
            "Problem Solved: Buffer clogging and inventory overflow in high-throughput factories.\n" +
            "Data Read: Items matching a specific filter (ID, Tag, or NBT).\n" +
            "Data Mutates: Absolute deletion of the object from memory.\n" +
            "Internal Behavior: When a 'Void' rule or button is triggered, the engine calls .shrink(count) or .clear() on the target buffer. No item entities are spawned in the world.\n" +
            "Player View: Items simply vanish from the buffer or container.\n" +
            "Engine View: Reference removal from the relevant List or IItemHandler.\n" +
            "Failure States: None—voiding is a guaranteed operation.\n" +
            "Edge Cases: Voiding items with complex NBT—this safely deletes the entire data structure.\n" +
            "Misuse: Accidental deletion of valuable gear (mitigated by 'Locked' item protection).\n" +
            "Performance Impact: Positive. Reducing item counts in buffers reduces memory pressure.\n" +
            "Multiplayer Impact: Only the owner of the network/chamber can trigger a voiding action.\n" +
            "Interaction: Can be gated by Logistics Conditions (e.g., 'Only void if chest is 90% full').\n" +
            "Verification: Observe the item count in the chamber or container dropping to zero.\n" +
            "How to break: Irreversible by design; once voided, the data is gone forever."));

        // 8. FAILURE MODES & ABUSE
        sections.add(new WikiSection("8.0 Failure Modes, Abuse & Risk",
            "What it is: A technical reference for scenarios where the system may fail or be exploited.\n" +
            "Why it exists: To provide full transparency on the mod's technical limits and risks.\n" +
            "Problem Solved: Unexpected data loss or performance degradation.\n" +
            "Data Read: Diagnostic metrics, save states, and packet logs.\n" +
            "Data Mutates: Error logs and Data Corruption flags.\n" +
            "Internal Behavior: Monitor system health and flag anomalies (e.g., failed save cycles).\n" +
            "Player View: 'Data Corruption' warning screens and '[ERROR]' reports in Logistics.\n" +
            "Engine View: Log entries and internal state validation checks.\n" +
            "Failure States: Save Corruption; SP Overflow; Packet Overflow.\n" +
            "Edge Cases: Reaching SP values above 10^308 (Double.MAX_VALUE) causes display 'Infinity'.\n" +
            "Misuse: Using 'Sure Kill' on invulnerable entities resulting in cost without reward.\n" +
            "Performance Impact: High precision diagnostics can slightly increase client CPU usage.\n" +
            "Multiplayer Impact: Large networks can increase the size of the 'SyncNetworksPacket'.\n" +
            "Interaction: Corrupted data may affect all other systems; automatic backups are recommended.\n" +
            "Verification: Check the 'Diagnostics' screen for system stability flags.\n" +
            "How to break: Force-killing the server during 'onLevelSave' (mitigated by atomic writes)."));

        return sections;
    }
}
