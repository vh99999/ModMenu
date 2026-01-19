package com.example.modmenu.store;

import java.util.*;

public class SkillDefinitions {
    public enum Branch {
        WEALTH("Wealth", "Economic Singularity"),
        COMBAT("Combat", "Sovereign Authority"),
        UTILITY("Utility", "Reality Refactoring"),
        CONTAINMENT("Virtualization", "Simulation Overlord");

        public final String name;
        public final String subTitle;
        Branch(String name, String subTitle) { this.name = name; this.subTitle = subTitle; }
    }

    public static class SkillPath {
        public final String id;
        public final String name;
        public final String description;
        public final int maxRank;
        public final Branch branch;
        public final String prerequisiteId;
        public final int prerequisiteRank;
        public final int x, y;

        public SkillPath(String id, String name, String description, int maxRank, Branch branch, String prerequisiteId, int prerequisiteRank, int x, int y) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.maxRank = maxRank;
            this.branch = branch;
            this.prerequisiteId = prerequisiteId;
            this.prerequisiteRank = prerequisiteRank;
            this.x = x;
            this.y = y;
        }
    }

    public static final Map<String, SkillPath> ALL_SKILLS = new LinkedHashMap<>();

    static {
        // --- BRANCH A: WEALTH ---
        // (Coordinates: West quadrant)
        add("WEALTH_MARKET_MANIPULATION", "Market Manipulation", "Control item valuations through system authority. Each rank provides a +50% additive bonus to the sell price of every item in the store. At Rank X (Absolute Monopoly), selling items in bulk will no longer reduce their price.", 10, Branch.WEALTH, -100, 0);
        add("WEALTH_EXPONENTIAL_BOUNTY", "Exponential Bounty", "Recalibrates mob kill rewards. Rewards scale exponentially with MaxHP (MaxHP^2.5). Also provides a flat +2.0 Attack Damage bonus per rank.", 5, Branch.WEALTH, "WEALTH_MARKET_MANIPULATION", 1, -200, 0);
        add("WEALTH_MIDAS_TOUCH", "The Midas Touch", "Synchronizes mining subroutines. Multiplies the value of blocks broken. At Rank V, blocks are worth 1,000x their standard price.", 5, Branch.WEALTH, "WEALTH_EXPONENTIAL_BOUNTY", 1, -300, 0);
        add("WEALTH_BASIC_SAVINGS", "Basic Savings", "Enables basic passive growth. Every 30 seconds, gain 1% interest, capped at $100 Million per cycle. Requires active effort before high-yield investment.", 5, Branch.WEALTH, "WEALTH_MIDAS_TOUCH", 1, -400, 0);
        
        // Split: Portfolio Path
        add("WEALTH_PORTFOLIO_BASIC", "Asset Allocation", "Enables passive generation of Skill Points (SP) every minute based on your current bank balance. Base rate: 1 SP per $100 Billion.", 1, Branch.WEALTH, "WEALTH_BASIC_SAVINGS", 1, -500, -50);
        add("WEALTH_PORTFOLIO_YIELD", "Institutional Authority", "Increases the SP yield. Each rank adds 1 SP per $10 Billion to your minute-cycle generation.", 5, Branch.WEALTH, "WEALTH_PORTFOLIO_BASIC", 1, -600, -50);
        add("WEALTH_PORTFOLIO_RECURSIVE", "Recursive Growth", "SP generated from wealth reduces the cost of future Wealth branch skills by 1% per rank.", 5, Branch.WEALTH, "WEALTH_PORTFOLIO_YIELD", 1, -700, -50);
        
        // Split: Interest Path
        add("WEALTH_INTEREST_RATE", "Interest Rate Optimization", "Increases the interest percentage by +1% per rank (stacks with Basic Savings).", 10, Branch.WEALTH, "WEALTH_BASIC_SAVINGS", 1, -500, 50);
        add("WEALTH_INTEREST_CAP", "Liquidity Expansion", "Increases the maximum interest gain cap per 30s cycle by $10 Billion per rank.", 10, Branch.WEALTH, "WEALTH_INTEREST_RATE", 1, -600, 50);
        add("WEALTH_INTEREST_UNLIMIT", "Wealth Overflow", "KEYSTONE: Removes the interest cap entirely and doubles all money received from mobs, sales, and rewards.", 1, Branch.WEALTH, "WEALTH_INTEREST_CAP", 5, -700, 50);

        // Later Wealth skills
        add("WEALTH_CAPITALIST_AURA", "Capitalist Aura", "Radiates an aura of economic pressure. Hostile mobs take up to 10% Max HP as damage every 2s, converted to cash.", 5, Branch.WEALTH, "WEALTH_INTEREST_UNLIMIT", 1, -800, 50);
        add("WEALTH_PHILANTHROPY", "Philanthropy Protocol", "Accelerates the growth and breeding of nearby animals.", 5, Branch.WEALTH, "WEALTH_CAPITALIST_AURA", 1, -900, 50);
        add("WEALTH_INFINITE_CREDIT", "Infinite Credit", "Allows your balance to drop below zero. Rank III (Absolute Debt) allows buying anything regardless of balance.", 3, Branch.WEALTH, "WEALTH_PHILANTHROPY", 1, -1000, 50);
        add("WEALTH_DIVIDEND", "Dividend Protocol", "Generates a massive flat amount of passive income every minute from system background energy.", 5, Branch.WEALTH, "WEALTH_INFINITE_CREDIT", 1, -1100, 50);
        add("WEALTH_SINGULARITY", "Currency Singularity", "Your bank balance acts as a direct multiplier for all earnings. $10 Billion = +5x multiplier at Rank V.", 5, Branch.WEALTH, "WEALTH_DIVIDEND", 1, -1200, 50);
        add("WEALTH_META_TRADING", "Meta-Trading", "Direct transmutation between Currency and SP via specialized UI.", 5, Branch.WEALTH, "WEALTH_SINGULARITY", 1, -1300, 50);
        add("WEALTH_IMMORTALITY", "Contractual Immortality", "The system buys your life back upon fatal damage. Cost scales down with rank.", 5, Branch.WEALTH, "WEALTH_META_TRADING", 1, -1400, 50);
        add("WEALTH_MERCENARY", "Mercenary Protocol", "Respawn killed mobs as loyal scaled allies for a fee.", 5, Branch.WEALTH, "WEALTH_IMMORTALITY", 1, -1500, 50);
        add("WEALTH_ECONOMIC_EGO", "Economic Ego", "Multiplies Speed, Damage, and Reach by the number of 'Zero' digits in your balance.", 10, Branch.WEALTH, "WEALTH_MERCENARY", 1, -1600, 50);
        add("WEALTH_LOBBYIST", "Lobbyist Protocol", "Reduces the SP cost of all Executive Protocols and Feature Unlocks by 5% per rank.", 10, Branch.WEALTH, "WEALTH_ECONOMIC_EGO", 1, -1700, 50);
        add("WEALTH_GOLDEN_HANDSHAKE", "Golden Handshake", "Revival triggers a gold explosion dealing damage equal to 1% of your balance.", 5, Branch.WEALTH, "WEALTH_LOBBYIST", 1, -1800, 50);
        add("WEALTH_MARKET_INSIDER", "Market Insider", "30s warning for upcoming Speculation events.", 5, Branch.WEALTH, "WEALTH_GOLDEN_HANDSHAKE", 1, -1900, 50);
        add("WEALTH_ASSET_SEIZURE", "Asset Seizure", "Liquidate entity gear value into cash without killing it.", 5, Branch.WEALTH, "WEALTH_MARKET_INSIDER", 1, -2000, 50);
        add("WEALTH_INFLATION_CONTROL", "Inflation Control", "Passively reduces all Store buy prices by 2% per rank.", 10, Branch.WEALTH, "WEALTH_ASSET_SEIZURE", 1, -2100, 50);

        // Wealth Keystones
        add("WEALTH_KEYSTONE_LIQUIDATION", "Reality Liquidation", "ULTIMATE KEYSTONE: Passively appraises and gains 25% value of environment in 128-block radius.", 1, Branch.WEALTH, "WEALTH_ECONOMIC_EGO", 1, -1600, -100);
        add("WEALTH_KEYSTONE_SHAREHOLDER", "Universal Shareholder", "Gain $1,000 every 10s for every living entity in your dimension.", 1, Branch.WEALTH, "WEALTH_INFLATION_CONTROL", 1, -2200, 0);
        add("WEALTH_KEYSTONE_MONOPOLY", "Absolute Monopoly", "All Store items cost $0, and grant 1% cashback reward.", 1, Branch.WEALTH, "WEALTH_INFLATION_CONTROL", 1, -2200, 100);

        // --- BRANCH B: COMBAT ---
        // (Coordinates: East quadrant)
        add("COMBAT_EFFICIENCY_CORE", "Efficiency Core", "Reduces maintenance cost (Drain) of active skills. Rank V makes it $0 if balance > $500M.", 5, Branch.COMBAT, 100, 0);
        add("COMBAT_DEFENSIVE_FEEDBACK", "Defensive Feedback", "Reflects up to 20,000% damage taken. Rank X (Retribution) deletes attackers.", 10, Branch.COMBAT, "COMBAT_EFFICIENCY_CORE", 1, 200, 0);
        add("COMBAT_LETHAL_OPTIMIZATION", "Lethal Optimization", "Sure Kill cost reduction. Rank V makes it $0 and doubles SP gain.", 5, Branch.COMBAT, "COMBAT_DEFENSIVE_FEEDBACK", 1, 300, 0);
        add("COMBAT_SOVEREIGN_DOMAIN", "System Lockdown", "Frozen stasis for weak mobs in 64-block radius.", 5, Branch.COMBAT, "COMBAT_LETHAL_OPTIMIZATION", 1, 400, 0);
        add("COMBAT_NEURAL_LINK", "Neural Link", "Massive Speed, Attack Speed, and Reach bonuses based on wealth ($1B = 1x).", 3, Branch.COMBAT, "COMBAT_SOVEREIGN_DOMAIN", 1, 500, 0);
        add("COMBAT_AUTHORITY_OVERLOAD", "Authority Overload", "Bonus damage equal to a percentage of bank balance.", 3, Branch.COMBAT, "COMBAT_NEURAL_LINK", 1, 600, 0);
        add("COMBAT_CAUSALITY_REVERSAL", "Causality Reversal", "Converts damage into healing. Rank V makes you unkillable through normal damage.", 5, Branch.COMBAT, "COMBAT_AUTHORITY_OVERLOAD", 1, 700, 0);
        add("COMBAT_JUDGMENT_AURA", "Entropy Field", "Reduces Movement, Attack Speed, and Damage of nearby hostile entities by 90%.", 5, Branch.COMBAT, "COMBAT_CAUSALITY_REVERSAL", 1, 800, 0);
        add("COMBAT_SOUL_REAP", "Soul Reap", "Permanent base attribute increases (Max HP, Damage) on kill.", 10, Branch.COMBAT, "COMBAT_JUDGMENT_AURA", 1, 900, 0);
        add("COMBAT_BLOOD_DATA", "Blood Data", "Chance for mobs to drop Data Shards providing massive SP bonuses.", 5, Branch.COMBAT, "COMBAT_SOUL_REAP", 1, 1000, 0);
        add("COMBAT_TEMPORAL_REFLEX", "Temporal Reflex", "Slows down nearby enemies and projectiles when HP is low.", 5, Branch.COMBAT, "COMBAT_BLOOD_DATA", 1, 1100, 0);
        add("COMBAT_SURE_KILL_PROTOCOL", "Sure Kill Protocol", "Sure Kill grants up to 500% SP and costs $0.", 3, Branch.COMBAT, "COMBAT_TEMPORAL_REFLEX", 1, 1200, 0);
        add("COMBAT_CHRONOS_BREACH", "Chronos Breach", "Inflicts temporal rift on targets, repeating damage every 5 seconds.", 5, Branch.COMBAT, "COMBAT_SURE_KILL_PROTOCOL", 1, 1300, 0);
        add("COMBAT_PROBABILITY_COLLAPSE", "Probability Collapse", "Ensures Critical hits, maximum loot, and ignores enemy dodge/block.", 10, Branch.COMBAT, "COMBAT_CHRONOS_BREACH", 1, 1400, 0);
        add("COMBAT_LOOT_RECALIBRATION", "Loot Recalibration", "Spend SP to Reroll loot tables on mob kill.", 5, Branch.COMBAT, "COMBAT_PROBABILITY_COLLAPSE", 1, 1500, 0);
        add("COMBAT_SINGULARITY_STRIKE", "Singularity Strike", "Melee hits create black holes pulling mobs in.", 5, Branch.COMBAT, "COMBAT_LOOT_RECALIBRATION", 1, 1600, 0);
        add("COMBAT_TARGET_LOCKDOWN", "Target Lockdown", "Tethers hostile mobs preventing teleportation or escape.", 5, Branch.COMBAT, "COMBAT_SINGULARITY_STRIKE", 1, 1700, 0);
        add("COMBAT_EXECUTIONERS_TAX", "Executioner's Tax", "Every 10th Sure Kill grants a Free Purchase token.", 5, Branch.COMBAT, "COMBAT_TARGET_LOCKDOWN", 1, 1800, 0);
        add("COMBAT_OVERCLOCKED_REFLEXES", "Overclocked Reflexes", "Chance to parry projectiles with 5x damage.", 10, Branch.COMBAT, "COMBAT_EXECUTIONERS_TAX", 1, 1900, 0);
        add("COMBAT_STATUS_OVERRIDE", "Status Override", "Converts negative status effects into positive counterparts.", 5, Branch.COMBAT, "COMBAT_OVERCLOCKED_REFLEXES", 1, 2000, 0);
        add("COMBAT_ANALYTICS", "Combat Analytics", "Displays Mob HP, loot potential, and SP value HUD.", 5, Branch.COMBAT, "COMBAT_STATUS_OVERRIDE", 1, 2100, 0);

        // Combat Keystones
        add("COMBAT_KEYSTONE_SOVEREIGNTY", "System Sovereignty", "ULTIMATE KEYSTONE: Invincibility, 10x Flight Speed, Infinite Reach. Purges status effects.", 1, Branch.COMBAT, "COMBAT_SINGULARITY_STRIKE", 1, 1600, 100);
        add("COMBAT_KEYSTONE_OMNIPOTENCE", "Omnipotence Paradox", "Deal 100% Max HP damage and become immune to all damage.", 1, Branch.COMBAT, "COMBAT_ANALYTICS", 1, 2200, -50);
        add("COMBAT_KEYSTONE_LEGIONARY", "Legionary Protocol", "Summons 3 immortal System Shadows (clones).", 1, Branch.COMBAT, "COMBAT_ANALYTICS", 1, 2200, 50);

        // --- BRANCH C: UTILITY ---
        // (Coordinates: South quadrant)
        add("UTILITY_QUANTUM_VACUUM", "Quantum Vacuum", "Upgrades Item Magnet with global range and infinite processing.", 5, Branch.UTILITY, 0, 100);
        add("UTILITY_MOLECULAR_REPLICATION", "Molecular Replication", "Multiplies drops from blocks (up to 1,000x or full stack).", 10, Branch.UTILITY, "UTILITY_QUANTUM_VACUUM", 1, 0, 200);
        add("UTILITY_BATCH_PROCESSING", "Batch Processing", "Industrial Auto-Seller logic. Items are converted to cash instantly.", 5, Branch.UTILITY, "UTILITY_MOLECULAR_REPLICATION", 1, 0, 300);
        add("UTILITY_STRUCTURAL_REFACTORING", "Structural Refactoring", "Expands Area Mining to massive 65x65x65 area.", 5, Branch.UTILITY, "UTILITY_BATCH_PROCESSING", 1, 0, 400);
        add("UTILITY_SPATIAL_FOLDING", "Spatial Folding", "Infinite inventory capacity and remote Store/System access.", 3, Branch.UTILITY, "UTILITY_STRUCTURAL_REFACTORING", 1, 0, 500);
        add("UTILITY_BIOMETRIC_OPTIMIZATION", "Biometric Optimization", "Permanent Night Vision, Haste, Water Breathing, Flight.", 3, Branch.UTILITY, "UTILITY_SPATIAL_FOLDING", 1, 0, 600);
        add("UTILITY_MATTER_TRANSMUTATION", "Matter Transmutation", "Transmute blocks into anything using SP, including bedrock.", 5, Branch.UTILITY, "UTILITY_BIOMETRIC_OPTIMIZATION", 1, 0, 700);
        add("UTILITY_CHUNK_LIQUIDATION", "Total Chunk Liquidation", "Delete and sell entire 5x5 chunk areas instantly.", 3, Branch.UTILITY, "UTILITY_MATTER_TRANSMUTATION", 1, 0, 800);
        add("UTILITY_WORMHOLE_PROTOCOL", "Wormhole Protocol", "Instant teleportation to any coord/dim and Return to Death anchor.", 3, Branch.UTILITY, "UTILITY_CHUNK_LIQUIDATION", 1, 0, 900);
        add("UTILITY_ATMOSPHERIC_CONTROL", "Atmospheric Control", "Rewrite world constants (Time, Weather, Gravity, Tick Speed).", 3, Branch.UTILITY, "UTILITY_WORMHOLE_PROTOCOL", 1, 0, 1000);
        add("UTILITY_ENTITY_DEFRAGMENTATION", "Data Extraction", "Harvest System Logs from mobs for profit or buffs.", 5, Branch.UTILITY, "UTILITY_ATMOSPHERIC_CONTROL", 1, 0, 1100);
        add("UTILITY_SYSTEM_OVERCLOCK", "System Overclock", "Multiplies speed of all actions (up to 32x speed).", 5, Branch.UTILITY, "UTILITY_ENTITY_DEFRAGMENTATION", 1, 0, 1200);
        add("UTILITY_MOLECULAR_SYNTHESIS", "Molecular Synthesis", "System Crafting interface to synthesize any item using balance.", 10, Branch.UTILITY, "UTILITY_SYSTEM_OVERCLOCK", 1, 0, 1300);
        add("UTILITY_VECTOR_MANIPULATION", "Vector Manipulation", "Control physics engine velocity and Time Stop.", 5, Branch.UTILITY, "UTILITY_MOLECULAR_SYNTHESIS", 1, 0, 1400);
        add("UTILITY_TECTONIC_SHIFT", "Tectonic Shift", "Macro-scale world editing (Push/Pull earth).", 5, Branch.UTILITY, "UTILITY_VECTOR_MANIPULATION", 1, 0, 1500);
        add("UTILITY_QUANTUM_STORAGE", "Quantum Storage", "Remote access to any container via Shift-click sync.", 1, Branch.UTILITY, "UTILITY_TECTONIC_SHIFT", 1, 0, 1600);
        add("UTILITY_MATTER_SYNTHESIS_PASSIVE", "Matter Synthesis (Passive)", "Passively generate stacks of items every 60 seconds.", 5, Branch.UTILITY, "UTILITY_QUANTUM_STORAGE", 1, 0, 1700);
        add("UTILITY_STATIC_RIFTS", "Dimensional Anchor", "Prevents forced teleports and sets 10 Static Rifts.", 5, Branch.UTILITY, "UTILITY_MATTER_SYNTHESIS_PASSIVE", 1, 0, 1800);
        add("UTILITY_NBT_REFACTOR", "NBT Refactor", "Modify item names and base attributes using SP.", 5, Branch.UTILITY, "UTILITY_STATIC_RIFTS", 1, 0, 1900);
        add("UTILITY_TIME_DILATION_AURA", "Time Dilation Aura", "16-block field where everything moves at 0.1x speed.", 5, Branch.UTILITY, "UTILITY_NBT_REFACTOR", 1, 0, 2000);

        // Utility Keystones
        add("UTILITY_KEYSTONE_ARCHITECT", "Architect's Will", "ULTIMATE KEYSTONE: Delete Mode for any block clicked (up to 256m).", 1, Branch.UTILITY, "UTILITY_TECTONIC_SHIFT", 1, 100, 1500);
        add("UTILITY_KEYSTONE_ROOT_ACCESS", "World Root Access", "/god-style shell to change biomes and world rules.", 1, Branch.UTILITY, "UTILITY_TIME_DILATION_AURA", 1, -100, 2100);
        add("UTILITY_KEYSTONE_ETERNAL_SIM", "Eternal Simulation", "Save and rollback chunk regions (128x128).", 1, Branch.UTILITY, "UTILITY_TIME_DILATION_AURA", 1, 100, 2100);

        // --- BRANCH D: VIRTUALIZATION ---
        // (Coordinates: South-East quadrant)
        add("VIRT_MULTI_THREAD", "Multi-Threaded Simulation", "Allocates multiple compute threads to each chamber. Processes massive mob batches.", 20, Branch.CONTAINMENT, 100, 100);
        add("VIRT_CLOCK_SPEED", "Clock Speed Injection", "Reduces simulation interval down to as low as 1 tick (20 kills/s).", 20, Branch.CONTAINMENT, "VIRT_MULTI_THREAD", 1, 200, 200);
        add("VIRT_ISOLATED_SANDBOX", "Isolated Sandbox", "Prevents diminishing returns from virtualization kills.", 1, Branch.CONTAINMENT, "VIRT_CLOCK_SPEED", 1, 300, 300);
        add("VIRT_LOOT_INJECTION", "Loot Table Injection", "Guarantees Rare/Epic loot triggers significantly more often.", 20, Branch.CONTAINMENT, "VIRT_ISOLATED_SANDBOX", 1, 400, 400);
        add("VIRT_RECURSIVE_LOOTING", "Recursive Looting", "Simulates virtual Looting X or higher on weapons.", 20, Branch.CONTAINMENT, "VIRT_LOOT_INJECTION", 1, 500, 500);
        add("VIRT_MARKET_LINK", "Market-Linkage Protocol", "Generated items are instantly converted into cash at market rate.", 1, Branch.CONTAINMENT, "VIRT_RECURSIVE_LOOTING", 1, 600, 600);
        add("VIRT_NEURAL_CONDENSATION", "Neural Condensation", "Automatically transmutes virtual XP into Skill Points (SP).", 20, Branch.CONTAINMENT, "VIRT_MARKET_LINK", 1, 700, 700);

        // Virtualization Keystones
        add("VIRT_KEYSTONE_PERSISTENCE", "Persistent Simulation", "ULTIMATE KEYSTONE: Processes time elapsed while offline upon login.", 1, Branch.CONTAINMENT, "VIRT_NEURAL_CONDENSATION", 1, 800, 800);
    }

    private static void add(String id, String name, String desc, int maxRank, Branch branch, int x, int y) {
        ALL_SKILLS.put(id, new SkillPath(id, name, desc, maxRank, branch, null, 0, x, y));
    }

    private static void add(String id, String name, String desc, int maxRank, Branch branch, String prereqId, int prereqRank, int x, int y) {
        ALL_SKILLS.put(id, new SkillPath(id, name, desc, maxRank, branch, prereqId, prereqRank, x, y));
    }
}
