package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.base.ScrollableUIContainer;
import com.example.modmenu.client.ui.base.UIElement;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.client.ui.layout.VerticalLayoutContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

import java.util.ArrayList;
import java.util.List;

public class WikiScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private ScrollableUIContainer content;

    public WikiScreen(Screen parent) {
        super(Component.literal("Mod Wiki & Informations"));
        this.parent = parent;
    }

    @Override
    protected void setupLayout() {
        this.layoutRoot.addElement(new ResponsiveButton(10, 10, 50, 20, Component.literal("Back"), btn -> {
            this.minecraft.setScreen(parent);
        }));

        content = new ScrollableUIContainer(50, 40, this.width - 100, this.height - 50);
        this.layoutRoot.addElement(content);

        refreshWiki();
    }

    private void refreshWiki() {
        if (content == null) return;
        content.clearChildren();

        VerticalLayoutContainer list = new VerticalLayoutContainer(0, 0, content.getWidth() - 10, 0, 10);

        addSection(list, "General Overview", "The System Mod is a simulated godhood experience. Through economic domination and system-level overrides, players can transcend standard gameplay limits. Access the main menu via the configured keybind (default: '9'). All interfaces are dynamic and respond in real-time to system changes. The mod uses a custom BigDecimal-based engine to support numbers into the octillions and beyond, ensuring server stability even at extreme progression scales.");

        addSection(list, "Economic Singularity & Scaling",
            "§eCurrency Singularity§r: Your bank balance acts as a direct multiplier for all earnings. Up to $100 Trillion, this scaling is linear. Beyond that, the system applies a square-root damping factor to ensure mathematical stability while still allowing for infinite growth into the decillions.\n\n" +
            "§eInfinite Credit§r: Unlocked via the Wealth branch. At Rank III (Absolute Debt), the system allows you to purchase anything regardless of your balance, effectively granting administrative purchasing authority.\n\n" +
            "§eInterest & Savings§r: Passively grow your wealth every 30 seconds. With the Wealth Overflow keystone, all caps are removed and the base multiplier for all income is doubled.");

        addSection(list, "SP Exchange (Meta-Trading)", 
            "§eAccess Control§r: This interface is §lONLY§r accessible once the §6Meta-Trading§r skill is unlocked in the Wealth branch. Access it via the §6Exchange UI§r button on the skill itself.\n\n" +
            "§eCurrency to SP§r: Sacrifice §e$1 Billion§r to gain §d1 SP§r. \n\n" +
            "§eSP to Currency§r: Convert §d1 SP§r into §e$100 Million§r. \n\n" +
            "§7Note: Meta-Trading allows bypassing mob farming for SP if you have a strong economic engine.");

        addSection(list, "Virtual Harvesting (Virtualization)", 
            "A high-performance, lag-free alternative to physical mob grinders. Simulations occur in the background and scale exponentially with your system power.\n\n" +
            "§lStep 1: Enabling Capture Mode§r\n" +
            "Open the §dAbilities§r menu, go to the §dDefense§r section, and toggle §bCapture Mode: ON§r.\n\n" +
            "§lStep 2: Capturing an Entity or Structure§r\n" +
            "- §eMobs§r: Find a living entity and §bShift-Right Click§r it. Generic captures are cheap, Exact captures preserve NBT.\n" +
            "- §eStructures§r: Find a chest in a generated structure (Bastion, Ancient City, etc.) and §bShift-Right Click§r it. Requires §6Virtual Excavation§r skill and §64 Loot Data Fragments§r in your inventory.\n\n" +
            "§lStep 3: Management & Expansion§r\n" +
            "Access the §dVirtual Containment§r menu to purchase and configure chambers.\n" +
            "1. §bCompute Allocation§r: Use sliders to tune §dSimulation Speed§r and §dMulti-Threading§r. High speeds consume significantly more Money/SP Drain per second.\n" +
            "2. §bVirtual Bartering§r: If a Piglin is captured, enable Bartering Mode and provide Gold Ingots in the chamber's Input Buffer to simulate trades.\n" +
            "3. §bIntelligent Condensation§r: Automatically craft items in the buffer (e.g., Ingots -> Blocks). §eSafe Mode§r only allows reversible crafts.\n" +
            "4. §bYield Targets§r: Middle-click an item in the loot grid to set a production cap. The chamber will stop generating that item once reached.\n\n" +
            "§lAdvanced Filtering§r\n" +
            "Upgrade to §dAdvanced Filtering§r to create precise per-chamber rules:\n" +
            "- §bKEEP§r: Store in buffer.\n" +
            "- §bVOID§r: Delete instantly.\n" +
            "- §bLIQUIDATE§r: Sell instantly for cash.\n" +
            "Rules can match by §eItem ID§r, §eForge Tags§r, or §eNBT Signature§r (exact data matching).");

        addSection(list, "Item Protection & Locking",
            "Protect your valuable gear using the System's stability protocols.\n\n" +
            "§lHow to use:§r\n" +
            "Hover over an item in any inventory and use §bShift + Right Click§r to cycle through protection modes:\n\n" +
            "1. §cLocked§r (Red Indicator): Prevents the item from being §lsold§r in the Store or §ldropped§r into the world.\n" +
            "2. §bFrozen§r (Blue Indicator): Prevents the item from being §lsold§r, §ldropped§r, or §lmoved§r within your inventory. Perfect for securing your hotbar during combat.\n" +
            "3. §aDisabled§r: Removes all protections.\n\n" +
            "§7Note: Protected items are completely ignored by the 'Sell All' functionality.");

        addSection(list, "Diagnostics & Analysis",
            "The System provides several analytical tools to monitor your progress and efficiency.\n\n" +
            "§eSystem Diagnostics§r: Accessible via the System menu. Displays:\n" +
            "- §bEconomic Analysis§r: Current income multipliers and interest forecasts.\n" +
            "- §bCombat Metrics§r: Total souls reaped, permanent attribute gains, and damage reflection stats.\n" +
            "- §bVirtualization Performance§r: Throughput measured in Kills/sec and Passive SP Flow.\n\n" +
            "§eCombat Analytics§r: Unlocked in the Combat branch. While looking at a mob, it displays its HP, SP Value, and Loot Table ID on your HUD.");

        addSection(list, "Abilities: Mining & Interaction", 
            "§eMining Subroutines§r: \n" +
            "- §bStandard§r: Mines the block you look at and others in range (Configurable).\n" +
            "- §bFocused§r: Mines a linear shaft in your direction of view.\n" +
            "- §bArea§r: Mines a massive cube (up to 65x65x65 with skills).\n" +
            "§7Activation: Use §bShift-Right Click§r with an empty hand. \n\n" +
            "§eAuto-Seller§r: Liquidates items into cash the moment they enter your inventory. Use §dWhitelist§r mode to only sell specific junk, or §dBlacklist§r to protect your gear. Efficiency is affected by §6Market Manipulation§r.\n\n" +
            "§eMagnets§r: Pulls Items and XP. Speed determines how many entities are processed per tick. Range determines the scan radius.\n\n" +
            "§eGrow Crops§r: Passively applies bone-meal effect to all crops in range. Cost per operation applies.");

        addSection(list, "Abilities: Defense & Movement", 
            "§eDamage Cancel§r: Spends money to negate 100% of incoming damage. Cost: $5,000 base + 100x Health per hit. Rank-based discounts apply via §cEfficiency Core§r.\n\n" +
            "§eNo Aggro§r: Broadcasts a 'System-Friend' signal. Most mobs will ignore you entirely. Maintenance fee applies per second.\n\n" +
            "§eFlight§r: Creative-style flight. Costs $500 per second. Efficiency Core can make this free.\n\n" +
            "§eStep Assist§r: Automatically walk up blocks. Height is configurable.\n\n" +
            "§eSure Kill§r: Instantly kills any non-boss mob you look at for a fee. Bosses take massive percentage-based damage instead.");

        addSection(list, "Specialized Systems",
            "§eEnchantment Engine§r: A specialized interface for applying any enchantment at any level (bypassing vanilla limits) using Currency. Accessible via the 'Enchant' button in the Main Menu.\n\n" +
            "§eAtmospheric Control§r: Unlocked in the Utility branch. Allows real-time manipulation of gravity, world time, weather, and server tick speed via a specialized dashboard.\n\n" +
            "§eMolecular Synthesis§r: Unlocked in the Utility branch. Allows direct purchasing of any item from the global registry, including those from other mods, using your bank balance via the Synthesis UI.");

        addSection(list, "Detailed Skill Tree: Wealth Branch", 
            "§6Market Manipulation§r: +50% sell price per rank. Rank X (Absolute Monopoly) prevents price decay from market flooding.\n" +
            "§6Compound Interest§r: Passive bank growth (1% base + 1% per rank). Wealth Overflow removes all caps and doubles income.\n" +
            "§6Exponential Bounty§r: Mob rewards scale at (MaxHP^2.5). Also grants +2.0 Attack Damage per rank.\n" +
            "§6The Midas Touch§r: Blocks worth 1,000x at Rank V. Global 10x income multiplier with keystone.\n" +
            "§6Investment Portfolio§r: Earn 1 SP per $100B/min. Rank V Institutional Authority adds 1 SP per $10B/min.\n" +
            "§6Capitalist Aura§r: 10% Max HP damage/2s to nearby mobs; converted to cash.\n" +
            "§6Infinite Credit§r: Allows negative balance. Absolute Debt ($100B limit at Rank II, infinite at Rank III) removes all limits.\n" +
            "§6Currency Singularity§r: Total balance acts as a direct multiplier for all income. Soft-capped at $100T with square-root scaling beyond.\n" +
            "§6Meta-Trading§r: Sacrifice $1B for 1 SP, or convert 1 SP into $100M.\n" +
            "§6Contractual Immortality§r: Respawn on death for a fee. Cost reduces significantly with rank.\n" +
            "§6Economic Ego§r: Multiplies Reach (+5.0/zero), Speed (+0.05/zero), and Damage (+2.0/zero) by the number of 'Zero' digits in your balance.\n" +
            "§6Lobbyist Protocol§r: Reduces SP costs by 5% per rank.\n" +
            "§6Golden Handshake§r: Revival triggers an explosion dealing 1% of your balance as magic damage.\n" +
            "§6Asset Seizure§r: Shift-Right Click to liquidate entity gear value into cash.\n" +
            "§6Inflation Control§r: -2% Store buy price per rank.\n" +
            "§6Keystone: Reality Liquidation§r: Passively gain 25% value of all environment in 128-block radius every second.\n" +
            "§6Keystone: Universal Shareholder§r: $1,000 every 10s per nearby living entity.\n" +
            "§6Keystone: Absolute Monopoly§r: All Store items cost $0 and grant 1% cashback reward.");

        addSection(list, "Detailed Skill Tree: Combat Branch", 
            "§cEfficiency Core§r: -20% maintenance drain per rank. Rank V makes maintenance FREE if balance > $500M.\n" +
            "§cDefensive Feedback§r: Reflects up to 20,000% damage taken. Rank X (Retribution) deletes the attacker.\n" +
            "§cLethal Optimization§r: Sure Kill cost reduction. Rank V makes it FREE and doubles SP gain.\n" +
            "§cSystem Lockdown§r: Freezes all mobs below HP threshold (up to 10^12 HP) in 64-block radius.\n" +
            "§cNeural Link§r: Wealth-based bonuses. Each $1B adds Reach (+rank) and Speed (+0.1*rank).\n" +
            "§cAuthority Overload§r: Bonus damage equal to 0.001% of bank balance per rank.\n" +
            "§cCausality Reversal§r: Converts 20% damage per rank into healing. Rank V grants damage immunity.\n" +
            "§cEntropy Field§r: Reduces Movement, Attack Speed, and Damage of nearby mobs by 90%.\n" +
            "§cSoul Reap§r: Permanent attribute gain (+0.033 HP, +0.016 Damage) per kill per rank.\n" +
            "§cBlood Data§r: 10% chance for mobs to drop shards providing massive SP bonuses.\n" +
            "§cSure Kill Protocol§r: Sure Kill grants up to 500% SP and costs $0 at Rank III.\n" +
            "§cChronos Breach§r: Damage repeats every 5s indefinitely on target.\n" +
            "§cProbability Collapse§r: Rank X ensures Critical hits and Maximum Loot drops.\n" +
            "§cLoot Recalibration§r: Spend SP to reroll mob drops in a custom GUI. Rank V makes first 5 rerolls FREE.\n" +
            "§cSingularity Strike§r: Melee hits create black holes that pull in enemies within 15 blocks.\n" +
            "§cTarget Lockdown§r: Tethers mobs preventing teleportation or escape.\n" +
            "§cExecutioner's Tax§r: Every 10th Sure Kill grants a Free Purchase token.\n" +
            "§cOverclocked Reflexes§r: 10% chance per rank to parry projectiles with 5x reflected damage.\n" +
            "§cStatus Override§r: Converts negative status effects (Slowness, Weakness) into positive counterparts (Speed, Strength).\n" +
            "§cKeystone: System Sovereignty§r: Invincibility, 10x Flight, and Infinite Reach. Maintenance costs apply.\n" +
            "§cKeystone: Omnipotence Paradox§r: Melee hits deal 100% of target Max HP as damage.\n" +
            "§cKeystone: Legionary Protocol§r: Summons 3 System Shadows that mimic your held weapon.");

        addSection(list, "Detailed Skill Tree: Utility Branch", 
            "§bQuantum Vacuum§r: Upgrades Item Magnet with global range and instant processing.\n" +
            "§bMolecular Replication§r: Upgrades drop multiplier from mined blocks (up to 10,000x at Rank IX). Rank X ensures full stacks.\n" +
            "§bBatch Processing§r: Industrial Auto-Seller logic. Items are converted to cash instantly upon block break.\n" +
            "§bStructural Refactoring§r: Expands Area Mining to a massive 65x65x65 area.\n" +
            "§bSpatial Folding§r: Remote menu access (default: 'P') and infinite inventory capacity.\n" +
            "§bBiometric Optimization§r: Permanent Night Vision, Haste V, and Creative Flight.\n" +
            "§bMatter Transmutation§r: Transmute any block (including Bedrock) into another using SP. Cost: 100/rank SP.\n" +
            "§bTotal Chunk Liquidation§r: Delete and sell 5x5 chunk areas for 100% value profit.\n" +
            "§bWormhole Protocol§r: $0 cost teleportation to any dimension or coordinates.\n" +
            "§bAtmospheric Control§r: Rewrite world constants (Time, Weather, Gravity, Tick Speed) via Control UI.\n" +
            "§bSystem Overclock§r: Act at up to 32x normal speed (Attack Speed multiplier).\n" +
            "§bMolecular Synthesis§r: Registry-level crafting interface to synthesize any item using Currency.\n" +
            "§bVector Manipulation§r: Freeze non-player entities in your vicinity.\n" +
            "§bTectonic Shift§r: Macro-scale world editing (Push/Pull earth) with empty-hand clicks.\n" +
            "§bQuantum Storage§r: Shift-Click to sync any container for remote cloud access.\n" +
            "§bMatter Synthesis (Passive)§r: Select an item in UI to passively generate stacks every 60 seconds.\n" +
            "§bDimensional Anchor§r: Immunity to forced teleportation and provides 10 Static Rift anchors.\n" +
            "§bNBT Refactor§r: Modify item NBT (Names, Attributes, Colors) via System UI.\n" +
            "§bTime Dilation Aura§r: 16-block field where everything moves at 0.1x speed.\n" +
            "§bKeystone: Architect's Will§r: Shift-Right Click to instantly delete and sell any block at range.\n" +
            "§bKeystone: World Root Access§r: Root shell access to modify biomes and world rules.\n" +
            "§bKeystone: Eternal Simulation§r: Save and Rollback 128x128 chunk regions using SP.");

        addSection(list, "Detailed Skill Tree: Virtualization Branch", 
            "§dMulti-Threaded Simulation§r: Processes massive mob batches. Up to 1,000 kills per cycle at Rank 20.\n" +
            "§dClock Speed Injection§r: Reduces simulation interval down to 1 tick (20 cycles/sec) at Rank 20.\n" +
            "§dIsolated Sandbox§r: Prevents Satiety decay from virtualization kills, ensuring 100% payout efficiency.\n" +
            "§dLoot Table Injection§r: Guarantees Rare/Epic loot triggers significantly more often via Luck (+5.0/rank).\n" +
            "§dVirtual Excavation§r: Simulate structure loot (Bastion, Ancient City). Requires sampling a chest with fragments.\n" +
            "§dRecursive Looting§r: Simulates virtual Looting enchantment on chamber kills (up to Looting XX).\n" +
            "§dVirtual Bartering§r: Simulates Piglin trading if Gold Ingots are present in the chamber's input buffer.\n" +
            "§dMarket-Linkage Protocol§r: Generated items are instantly converted into cash at market rates.\n" +
            "§dAdvanced Filtering§r: Enables precise per-chamber rules (Keep, Void, Liquidate) with NBT matching.\n" +
            "§dNeural Condensation§r: Automatically transmutes virtual XP into SP. Rate: 1 SP per 10,000/rank XP.\n" +
            "§dIntelligent Condensation§r: Internal auto-crafting for chamber buffers. Safe Mode preserves material utility.\n" +
            "§dKeystone: Persistent Simulation§r: Processes all simulation time elapsed while the player was offline upon login.");

        addSection(list, "Executive Protocols (SEP)", 
            "Root-level administrative overrides. Purchased in the §dSystem -> Executive Protocols§r menu.\n\n" +
            "§ePersonal Nexus§r: Calibrate your respawn point to current location.\n" +
            "§eSector Zero§r: Realign world spawn to your current location.\n" +
            "§eDimensional Anchor§r: Immunity to forced teleportation.\n" +
            "§eInventory Preservation§r: Permanent keepInventory for you ($25,000 SP).\n" +
            "§eNeural XP Backup§r: Permanent keepExperience for you ($25,000 SP).\n" +
            "§eAnti-Griefing Aura§r: Prevents environmental damage nearby.\n" +
            "§eGlobal Registry Purge§r: Reset all Mob Satiety instantly ($100,000 SP).\n" +
            "§eChronos Lock§r: Freeze world time and weather permanently ($2,500 SP).\n" +
            "§eTectonic Stabilization§r: Full immunity to fall damage.\n" +
            "§eSpecies Blacklist§r: Look at a mob to discard its entire species from existence near you.\n" +
            "§eSubstrate Injection§r: Replace blocks by clicking while holding target material in off-hand.\n" +
            "§eLoot Table Overclock§r: Guarantees max drops for the next 100 kills.\n" +
            "§eRegistry Editor§r: Overwrite an entity's type using a Spawn Egg in off-hand.\n" +
            "§eCode Optimization§r: Passive 15% reduction to all SP costs.\n" +
            "§eGod Strength§r: 10x Multiplier to ALL base damage ($1,000,000 SP).");

        addSection(list, "Store & Economy", 
            "§eDynamic Economy§r: The system tracks every item sold globally. Flooding the market with a single resource (e.g. 10,000 Cobblestone) will exponentially decrease its sell price using the formula P = P0 * 0.95 ^ (volume/1000). \n" +
            "§7Example: Selling items in diverse batches ensures you always get the best rates.\n\n" +
            "§eTrue Cost Algorithm§r: Prices for crafted items are calculated recursively by breaking them down into their most basic raw materials, plus a processing fee. This ensures that modded items with complex recipes are valued fairly.\n\n" +
            "§eHigh-Capacity Buffers§r: The System uses specialized serialization to bypass vanilla Minecraft's 255-item count limit. Virtual buffers and packets can handle stacks of up to 2.1 Billion items without truncation or data loss.\n\n" +
            "§eData Isolation§r: All skill, currency, and virtualization data is strictly isolated per world or server. Logging out of a session automatically purges the client-side cache to ensure integrity.");

        addSection(list, "Administrative Commands", 
            "§e/setcurrentcurrency <player> <amount>§r: Set a player's bank balance.\n" +
            "§e/seteditor <player>§r: Grant editor status to a player, allowing them to modify store prices.\n" +
            "§e/setspmultiplier <multiplier>§r: Scales the amount of SP earned from all sources.\n" +
            "§e/savelayouts§r: Triggers a manual save of the store's UI layout configuration.");

        content.addElement(list);
        content.setContentHeight(list.getHeight() + 20);
    }

    private void addSection(VerticalLayoutContainer list, String title, String text) {
        list.addElement(new WikiSectionElement(0, 0, list.getWidth(), title, text));
    }

    private static class WikiSectionElement extends UIElement {
        private final List<Component> lines = new ArrayList<>();

        public WikiSectionElement(int x, int y, int width, String title, String text) {
            super(x, y, width, 0);
            lines.add(Component.literal("§6§l" + title));
            for (FormattedText s : Minecraft.getInstance().font.getSplitter().splitLines(FormattedText.of(text), width - 10, net.minecraft.network.chat.Style.EMPTY)) {
                lines.add(Component.literal(s.getString()));
            }
            this.height = lines.size() * 10 + 5;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            int ty = getY();
            for (Component line : lines) {
                g.drawString(Minecraft.getInstance().font, line, getX() + 5, ty, 0xFFFFFFFF);
                ty += 10;
            }
        }
    }
}
