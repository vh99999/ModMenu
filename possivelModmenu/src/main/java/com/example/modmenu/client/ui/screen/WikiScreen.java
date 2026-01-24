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
            "\u00A7eCurrency Singularity\u00A7r: Your bank balance acts as a direct multiplier for all earnings. Up to $100 Trillion, this scaling is linear. Beyond that, the system applies a square-root damping factor to ensure mathematical stability while still allowing for infinite growth into the decillions.\n\n" +
            "\u00A7eInfinite Credit\u00A7r: Unlocked via the Wealth branch. At Rank III (Absolute Debt), the system allows you to purchase anything regardless of your balance, effectively granting administrative purchasing authority.\n\n" +
            "\u00A7eInterest & Savings\u00A7r: Passively grow your wealth every 30 seconds. With the Wealth Overflow keystone, all caps are removed and the base multiplier for all income is doubled.");

        addSection(list, "SP Exchange (Meta-Trading)", 
            "\u00A7eAccess Control\u00A7r: This interface is \u00A7lONLY\u00A7r accessible once the \u00A76Meta-Trading\u00A7r skill is unlocked in the Wealth branch. Access it via the \u00A76Exchange UI\u00A7r button on the skill itself.\n\n" +
            "\u00A7eCurrency to SP\u00A7r: Sacrifice \u00A7e$1 Billion\u00A7r to gain \u00A7d1 SP\u00A7r. \n\n" +
            "\u00A7eSP to Currency\u00A7r: Convert \u00A7d1 SP\u00A7r into \u00A7e$100 Million\u00A7r. \n\n" +
            "\u00A77Note: Meta-Trading allows bypassing mob farming for SP if you have a strong economic engine.");

        addSection(list, "Virtual Harvesting (Virtualization)", 
            "A high-performance, lag-free alternative to physical mob grinders. Simulations occur in the background and scale exponentially with your system power.\n\n" +
            "\u00A7lStep 1: Enabling Capture Mode\u00A7r\n" +
            "Open the \u00A7dAbilities\u00A7r menu, go to the \u00A7dDefense\u00A7r section, and toggle \u00A7bCapture Mode: ON\u00A7r.\n\n" +
            "\u00A7lStep 2: Capturing an Entity or Structure\u00A7r\n" +
            "- \u00A7eMobs\u00A7r: Find a living entity and \u00A7bShift-Right Click\u00A7r it. Generic captures are cheap, Exact captures preserve NBT.\n" +
            "- \u00A7eStructures\u00A7r: Find a chest in a generated structure (Bastion, Ancient City, etc.) and \u00A7bShift-Right Click\u00A7r it. Requires \u00A76Virtual Excavation\u00A7r skill and \u00A764 Loot Data Fragments\u00A7r in your inventory.\n\n" +
            "\u00A7lStep 3: Management & Expansion\u00A7r\n" +
            "Access the \u00A7dVirtual Containment\u00A7r menu to purchase and configure chambers.\n" +
            "1. \u00A7bCompute Allocation\u00A7r: Use sliders to tune \u00A7dSimulation Speed\u00A7r and \u00A7dMulti-Threading\u00A7r. High speeds consume significantly more Money/SP Drain per second.\n" +
            "2. \u00A7bVirtual Bartering\u00A7r: If a Piglin is captured, enable Bartering Mode and provide Gold Ingots in the chamber's Input Buffer to simulate trades.\n" +
            "3. \u00A7bIntelligent Condensation\u00A7r: Automatically craft items in the buffer (e.g., Ingots -> Blocks). \u00A7eSafe Mode\u00A7r only allows reversible crafts.\n" +
            "4. \u00A7bYield Targets\u00A7r: Middle-click an item in the loot grid to set a production cap. The chamber will stop generating that item once reached.\n" +
            "5. \u00A7bLoot Transfer\u00A7r: Use the \u00A7eTransfer\u00A7r button to move items directly to a world container. After clicking, \u00A7bShift-Right Click\u00A7r any chest or barrel to move all stored loot into it.\n\n" +
            "\u00A7lAdvanced Filtering\u00A7r\n" +
            "Upgrade to \u00A7dAdvanced Filtering\u00A7r to create precise per-chamber rules:\n" +
            "- \u00A7bKEEP\u00A7r: Store in buffer.\n" +
            "- \u00A7bVOID\u00A7r: Delete instantly.\n" +
            "- \u00A7bLIQUIDATE\u00A7r: Sell instantly for cash.\n" +
            "Rules can match by \u00A7eItem ID\u00A7r, \u00A7eForge Tags\u00A7r, or \u00A7eNBT Signature\u00A7r (exact data matching).");

        addSection(list, "Item Protection & Locking",
            "Protect your valuable gear using the System's stability protocols.\n\n" +
            "\u00A7lHow to use:\u00A7r\n" +
            "Hover over an item in any inventory and use \u00A7bShift + Right Click\u00A7r to cycle through protection modes:\n\n" +
            "1. \u00A7cLocked\u00A7r (Red Indicator & \uD83D\uDD12): Prevents the item from being \u00A7lsold\u00A7r in the Store or \u00A7ldropped\u00A7r into the world.\n" +
            "2. \u00A7bFrozen\u00A7r (Blue Indicator & \u2744): Prevents the item from being \u00A7lsold\u00A7r, \u00A7ldropped\u00A7r, or \u00A7lmoved\u00A7r within your inventory. Perfect for securing your hotbar during combat.\n" +
            "3. \u00A7aDisabled\u00A7r: Removes all protections.\n\n" +
            "\u00A77Note: Protected items are completely ignored by the 'Sell All' functionality and display prominent visual feedback in all System interfaces.");

        addSection(list, "Diagnostics & Analysis",
            "The System provides several analytical tools to monitor your progress and efficiency.\n\n" +
            "\u00A7eSystem Diagnostics\u00A7r: Accessible via the System menu. Displays:\n" +
            "- \u00A7bEconomic Analysis\u00A7r: Current income multipliers and interest forecasts.\n" +
            "- \u00A7bCombat Metrics\u00A7r: Total souls reaped, permanent attribute gains, and damage reflection stats.\n" +
            "- \u00A7bVirtualization Performance\u00A7r: Throughput measured in Kills/sec and Passive SP Flow.\n\n" +
            "\u00A7eCombat Analytics\u00A7r: Unlocked in the Combat branch. While looking at a mob, it displays its HP, SP Value, and Loot Table ID on your HUD.");

        addSection(list, "Abilities: Mining & Interaction", 
            "\u00A7eMining Subroutines\u00A7r: \n" +
            "- \u00A7bStandard\u00A7r: Mines the block you look at and others in range (Configurable).\n" +
            "- \u00A7bFocused\u00A7r: Mines a linear shaft in your direction of view.\n" +
            "- \u00A7bArea\u00A7r: Mines a massive cube (up to 65x65x65 with skills).\n" +
            "\u00A77Activation: Use \u00A7bShift-Right Click\u00A7r with an empty hand. \n\n" +
            "\u00A7eAuto-Seller\u00A7r: Liquidates items into cash the moment they enter your inventory. Use \u00A7dWhitelist\u00A7r mode to only sell specific junk, or \u00A7dBlacklist\u00A7r to protect your gear. Efficiency is affected by \u00A76Market Manipulation\u00A7r.\n\n" +
            "\u00A7eMagnets\u00A7r: Pulls Items and XP. Speed determines how many entities are processed per tick. Range determines the scan radius.\n\n" +
            "\u00A7eGrow Crops\u00A7r: Passively applies bone-meal effect to all crops in range. Cost per operation applies.");

        addSection(list, "Abilities: Defense & Movement", 
            "\u00A7eDamage Cancel\u00A7r: Spends money to negate 100% of incoming damage. Cost: $5,000 base + 100x Health per hit. Rank-based discounts apply via \u00A7cEfficiency Core\u00A7r.\n\n" +
            "\u00A7eNo Aggro\u00A7r: Broadcasts a 'System-Friend' signal. Most mobs will ignore you entirely. Maintenance fee applies per second.\n\n" +
            "\u00A7eFlight\u00A7r: Creative-style flight. Costs $500 per second. Efficiency Core can make this free.\n\n" +
            "\u00A7eStep Assist\u00A7r: Automatically walk up blocks. Height is configurable.\n\n" +
            "\u00A7eSure Kill\u00A7r: Instantly kills any non-boss mob you look at for a fee. Bosses take massive percentage-based damage instead.");

        addSection(list, "Specialized Systems",
            "\u00A7eEnchantment Engine\u00A7r: A specialized interface for applying any enchantment at any level (bypassing vanilla limits) using Currency. Accessible via the 'Enchant' button in the Main Menu.\n\n" +
            "\u00A7eAtmospheric Control\u00A7r: Unlocked in the Utility branch. Allows real-time manipulation of gravity, world time, weather, and server tick speed via a specialized dashboard.\n\n" +
            "\u00A7eMolecular Synthesis\u00A7r: Unlocked in the Utility branch. Allows direct purchasing of any item from the global registry, including those from other mods, using your bank balance via the Synthesis UI.");

        addSection(list, "Detailed Skill Tree: Wealth Branch", 
            "\u00A76Market Manipulation\u00A7r: +50% sell price per rank. Rank X (Absolute Monopoly) prevents price decay from market flooding.\n" +
            "\u00A76Compound Interest\u00A7r: Passive bank growth (1% base + 1% per rank). Wealth Overflow removes all caps and doubles income.\n" +
            "\u00A76Exponential Bounty\u00A7r: Mob rewards scale at (MaxHP^2.5). Also grants +2.0 Attack Damage per rank.\n" +
            "\u00A76The Midas Touch\u00A7r: Blocks worth 1,000x at Rank V. Global 10x income multiplier with keystone.\n" +
            "\u00A76Investment Portfolio\u00A7r: Earn 1 SP per $100B/min. Rank V Institutional Authority adds 1 SP per $10B/min.\n" +
            "\u00A76Capitalist Aura\u00A7r: 10% Max HP damage/2s to nearby mobs; converted to cash.\n" +
            "\u00A76Infinite Credit\u00A7r: Allows negative balance. Absolute Debt ($100B limit at Rank II, infinite at Rank III) removes all limits.\n" +
            "\u00A76Currency Singularity\u00A7r: Total balance acts as a direct multiplier for all income. Soft-capped at $100T with square-root scaling beyond.\n" +
            "\u00A76Meta-Trading\u00A7r: Sacrifice $1B for 1 SP, or convert 1 SP into $100M.\n" +
            "\u00A76Contractual Immortality\u00A7r: Respawn on death for a fee. Cost reduces significantly with rank.\n" +
            "\u00A76Economic Ego\u00A7r: Multiplies Reach (+5.0/zero), Speed (+0.05/zero), and Damage (+2.0/zero) by the number of 'Zero' digits in your balance.\n" +
            "\u00A76Lobbyist Protocol\u00A7r: Reduces SP costs by 5% per rank.\n" +
            "\u00A76Golden Handshake\u00A7r: Revival triggers an explosion dealing 1% of your balance as magic damage.\n" +
            "\u00A76Asset Seizure\u00A7r: Shift-Right Click to liquidate entity gear value into cash.\n" +
            "\u00A76Inflation Control\u00A7r: -2% Store buy price per rank.\n" +
            "\u00A76Keystone: Reality Liquidation\u00A7r: Passively gain 25% value of all environment in 128-block radius every second.\n" +
            "\u00A76Keystone: Universal Shareholder\u00A7r: $1,000 every 10s per nearby living entity.\n" +
            "\u00A76Keystone: Absolute Monopoly\u00A7r: All Store items cost $0 and grant 1% cashback reward.");

        addSection(list, "Detailed Skill Tree: Combat Branch", 
            "\u00A7cEfficiency Core\u00A7r: -20% maintenance drain per rank. Rank V makes maintenance FREE if balance > $500M.\n" +
            "\u00A7cDefensive Feedback\u00A7r: Reflects up to 20,000% damage taken. Rank X (Retribution) deletes the attacker.\n" +
            "\u00A7cLethal Optimization\u00A7r: Sure Kill cost reduction. Rank V makes it FREE and doubles SP gain.\n" +
            "\u00A7cSystem Lockdown\u00A7r: Freezes all mobs below HP threshold (up to 10^12 HP) in 64-block radius.\n" +
            "\u00A7cNeural Link\u00A7r: Wealth-based bonuses. Each $1B adds Reach (+rank) and Speed (+0.1*rank).\n" +
            "\u00A7cAuthority Overload\u00A7r: Bonus damage equal to 0.001% of bank balance per rank.\n" +
            "\u00A7cCausality Reversal\u00A7r: Converts 20% damage per rank into healing. Rank V grants damage immunity.\n" +
            "\u00A7cEntropy Field\u00A7r: Reduces Movement, Attack Speed, and Damage of nearby mobs by 90%.\n" +
            "\u00A7cSoul Reap\u00A7r: Permanent attribute gain (+0.033 HP, +0.016 Damage) per kill per rank.\n" +
            "\u00A7cBlood Data\u00A7r: 10% chance for mobs to drop shards providing massive SP bonuses.\n" +
            "\u00A7cSure Kill Protocol\u00A7r: Sure Kill grants up to 500% SP and costs $0 at Rank III.\n" +
            "\u00A7cChronos Breach\u00A7r: Damage repeats every 5s indefinitely on target.\n" +
            "\u00A7cProbability Collapse\u00A7r: Rank X ensures Critical hits and Maximum Loot drops.\n" +
            "\u00A7cLoot Recalibration\u00A7r: Spend SP to reroll mob drops in a custom GUI. Rank V makes first 5 rerolls FREE.\n" +
            "\u00A7cSingularity Strike\u00A7r: Melee hits create black holes that pull in enemies within 15 blocks.\n" +
            "\u00A7cTarget Lockdown\u00A7r: Tethers mobs preventing teleportation or escape.\n" +
            "\u00A7cExecutioner's Tax\u00A7r: Every 10th Sure Kill grants a Free Purchase token.\n" +
            "\u00A7cOverclocked Reflexes\u00A7r: 10% chance per rank to parry projectiles with 5x reflected damage.\n" +
            "\u00A7cStatus Override\u00A7r: Converts negative status effects (Slowness, Weakness) into positive counterparts (Speed, Strength).\n" +
            "\u00A7cKeystone: System Sovereignty\u00A7r: Invincibility, 10x Flight, and Infinite Reach. Maintenance costs apply.\n" +
            "\u00A7cKeystone: Omnipotence Paradox\u00A7r: Melee hits deal 100% of target Max HP as damage.\n" +
            "\u00A7cKeystone: Legionary Protocol\u00A7r: Summons 3 System Shadows that mimic your held weapon.");

        addSection(list, "Detailed Skill Tree: Utility Branch", 
            "\u00A7bQuantum Vacuum\u00A7r: Upgrades Item Magnet with global range and instant processing.\n" +
            "\u00A7bMolecular Replication\u00A7r: Upgrades drop multiplier from mined blocks (up to 10,000x at Rank IX). Rank X ensures full stacks.\n" +
            "\u00A7bBatch Processing\u00A7r: Industrial Auto-Seller logic. Items are converted to cash instantly upon block break.\n" +
            "\u00A7bStructural Refactoring\u00A7r: Expands Area Mining to a massive 65x65x65 area.\n" +
            "\u00A7bSpatial Folding\u00A7r: Remote menu access (default: 'P') and infinite inventory capacity.\n" +
            "\u00A7bBiometric Optimization\u00A7r: Permanent Night Vision, Haste V, and Creative Flight.\n" +
            "\u00A7bMatter Transmutation\u00A7r: Transmute any block (including Bedrock) into another using SP. Cost: 100/rank SP.\n" +
            "\u00A7bTotal Chunk Liquidation\u00A7r: Delete and sell 5x5 chunk areas for 100% value profit.\n" +
            "\u00A7bWormhole Protocol\u00A7r: $0 cost teleportation to any dimension or coordinates.\n" +
            "\u00A7bAtmospheric Control\u00A7r: Rewrite world constants (Time, Weather, Gravity, Tick Speed) via Control UI.\n" +
            "\u00A7bSystem Overclock\u00A7r: Act at up to 32x normal speed (Attack Speed multiplier).\n" +
            "\u00A7bMolecular Synthesis\u00A7r: Registry-level crafting interface to synthesize any item using Currency.\n" +
            "\u00A7bVector Manipulation\u00A7r: Freeze non-player entities in your vicinity.\n" +
            "\u00A7bTectonic Shift\u00A7r: Macro-scale world editing (Push/Pull earth) with empty-hand clicks.\n" +
            "\u00A7bQuantum Storage\u00A7r: Shift-Click to sync any container for remote cloud access.\n" +
            "\u00A7bMatter Synthesis (Passive)\u00A7r: Select an item in UI to passively generate stacks every 60 seconds.\n" +
            "\u00A7bDimensional Anchor\u00A7r: Immunity to forced teleportation and provides 10 Static Rift anchors.\n" +
            "\u00A7bNBT Refactor\u00A7r: Modify item NBT (Names, Attributes, Colors) via System UI.\n" +
            "\u00A7bTime Dilation Aura\u00A7r: 16-block field where everything moves at 0.1x speed.\n" +
            "\u00A7bKeystone: Architect's Will\u00A7r: Shift-Right Click to instantly delete and sell any block at range.\n" +
            "\u00A7bKeystone: World Root Access\u00A7r: Root shell access to modify biomes and world rules.\n" +
            "\u00A7bKeystone: Eternal Simulation\u00A7r: Save and Rollback 128x128 chunk regions using SP.");

        addSection(list, "Detailed Skill Tree: Virtualization Branch", 
            "\u00A7dMulti-Threaded Simulation\u00A7r: Processes massive mob batches. Up to 1,000 kills per cycle at Rank 20.\n" +
            "\u00A7dClock Speed Injection\u00A7r: Reduces simulation interval down to 1 tick (20 cycles/sec) at Rank 20.\n" +
            "\u00A7dIsolated Sandbox\u00A7r: Prevents Satiety decay from virtualization kills, ensuring 100% payout efficiency.\n" +
            "\u00A7dLoot Table Injection\u00A7r: Guarantees Rare/Epic loot triggers significantly more often via Luck (+5.0/rank).\n" +
            "\u00A7dVirtual Excavation\u00A7r: Simulate structure loot (Bastion, Ancient City). Requires sampling a chest with fragments.\n" +
            "\u00A7dRecursive Looting\u00A7r: Simulates virtual Looting enchantment on chamber kills (up to Looting XX).\n" +
            "\u00A7dVirtual Bartering\u00A7r: Simulates Piglin trading if Gold Ingots are present in the chamber's input buffer.\n" +
            "\u00A7dMarket-Linkage Protocol\u00A7r: Generated items are instantly converted into cash at market rates.\n" +
            "\u00A7dAdvanced Filtering\u00A7r: Enables precise per-chamber rules (Keep, Void, Liquidate) with NBT matching.\n" +
            "\u00A7dNeural Condensation\u00A7r: Automatically transmutes virtual XP into SP. Rate: 1 SP per 10,000/rank XP.\n" +
            "\u00A7dIntelligent Condensation\u00A7r: Internal auto-crafting for chamber buffers. Safe Mode preserves material utility.\n" +
            "\u00A7dKeystone: Persistent Simulation\u00A7r: Processes all simulation time elapsed while the player was offline upon login.");

        addSection(list, "Executive Protocols (SEP)", 
            "Root-level administrative overrides. Purchased in the \u00A7dSystem -> Executive Protocols\u00A7r menu.\n\n" +
            "\u00A7ePersonal Nexus\u00A7r: Calibrate your respawn point to current location.\n" +
            "\u00A7eSector Zero\u00A7r: Realign world spawn to your current location.\n" +
            "\u00A7eDimensional Anchor\u00A7r: Immunity to forced teleportation.\n" +
            "\u00A7eInventory Preservation\u00A7r: Permanent keepInventory for you ($25,000 SP).\n" +
            "\u00A7eNeural XP Backup\u00A7r: Permanent keepExperience for you ($25,000 SP).\n" +
            "\u00A7eAnti-Griefing Aura\u00A7r: Prevents environmental damage nearby.\n" +
            "\u00A7eGlobal Registry Purge\u00A7r: Reset all Mob Satiety instantly ($100,000 SP).\n" +
            "\u00A7eChronos Lock\u00A7r: Freeze world time and weather permanently ($2,500 SP).\n" +
            "\u00A7eTectonic Stabilization\u00A7r: Full immunity to fall damage.\n" +
            "\u00A7eSpecies Blacklist\u00A7r: Look at a mob to discard its entire species from existence near you.\n" +
            "\u00A7eSubstrate Injection\u00A7r: Replace blocks by clicking while holding target material in off-hand.\n" +
            "\u00A7eLoot Table Overclock\u00A7r: Guarantees max drops for the next 100 kills.\n" +
            "\u00A7eRegistry Editor\u00A7r: Overwrite an entity's type using a Spawn Egg in off-hand.\n" +
            "\u00A7eCode Optimization\u00A7r: Passive 15% reduction to all SP costs.\n" +
            "\u00A7eGod Strength\u00A7r: 10x Multiplier to ALL base damage ($1,000,000 SP).");

        addSection(list, "Store & Economy", 
            "\u00A7eDynamic Economy\u00A7r: The system tracks every item sold globally. Flooding the market with a single resource (e.g. 10,000 Cobblestone) will exponentially decrease its sell price using the formula P = P0 * 0.95 ^ (volume/1000). \n" +
            "\u00A77Example: Selling items in diverse batches ensures you always get the best rates.\n\n" +
            "\u00A7eTrue Cost Algorithm\u00A7r: Prices for crafted items are calculated recursively by breaking them down into their most basic raw materials, plus a processing fee. This ensures that modded items with complex recipes are valued fairly.\n\n" +
            "\u00A7eHigh-Capacity Buffers\u00A7r: The System uses specialized serialization to bypass vanilla Minecraft's 255-item count limit. Virtual buffers and packets can handle stacks of up to 2.1 Billion items without truncation or data loss.\n\n" +
            "\u00A7eData Isolation\u00A7r: All skill, currency, and virtualization data is strictly isolated per world or server. Logging out of a session automatically purges the client-side cache to ensure integrity.");

        addSection(list, "Administrative Commands", 
            "\u00A7e/setcurrentcurrency <player> <amount>\u00A7r: Set a player's bank balance.\n" +
            "\u00A7e/seteditor <player>\u00A7r: Grant editor status to a player, allowing them to modify store prices.\n" +
            "\u00A7e/setspmultiplier <multiplier>\u00A7r: Scales the amount of SP earned from all sources.\n" +
            "\u00A7e/savelayouts\u00A7r: Triggers a manual save of the store's UI layout configuration.");

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
            lines.add(Component.literal("\u00A76\u00A7l" + title));
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
