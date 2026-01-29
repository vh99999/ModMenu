package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.base.ScrollableUIContainer;
import com.example.modmenu.client.ui.base.UIContainer;
import com.example.modmenu.client.ui.base.UIElement;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.network.PacketHandler;
import com.example.modmenu.network.UpdateGenesisConfigPacket;
import com.example.modmenu.network.RegenerateDimensionPacket;
import com.example.modmenu.network.EnterDimensionPacket;
import com.example.modmenu.store.StorePriceManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class GenesisHubScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private int activeTab = 0; // 0: Generation, 1: Atmosphere, 2: Physics, 3: Laws
    private ScrollableUIContainer contentArea;
    private StorePriceManager.GenesisConfig localConfig;

    public GenesisHubScreen(Screen parent) {
        super(Component.literal("Genesis Hub"));
        this.parent = parent;
        this.localConfig = new StorePriceManager.GenesisConfig();
        this.localConfig.copyFrom(StorePriceManager.clientGenesisConfig);
        if ("Floating Islands".equals(this.localConfig.genType)) {
            this.localConfig.genType = "Floating islands original";
        }
    }

    @Override
    protected void setupLayout() {
        this.layoutRoot.addElement(new ResponsiveButton(10, 10, 50, 20, Component.literal("Back"), btn -> {
            this.minecraft.setScreen(parent);
        }));

        // Tabs
        int tabCount = StorePriceManager.isEditor ? 5 : 4;
        int tabWidth = tabCount == 5 ? 70 : 80;
        int tabX = (this.width - (tabWidth * tabCount + 5 * (tabCount - 1))) / 2;
        addTabButton(tabX, 40, tabWidth, "Generation", 0);
        addTabButton(tabX + tabWidth + 5, 40, tabWidth, "Atmosphere", 1);
        addTabButton(tabX + (tabWidth + 5) * 2, 40, tabWidth, "Physics", 2);
        addTabButton(tabX + (tabWidth + 5) * 3, 40, tabWidth, "Laws", 3);
        if (StorePriceManager.isEditor) {
            addTabButton(tabX + (tabWidth + 5) * 4, 40, tabWidth, "Formulas", 4);
        }

        contentArea = new ScrollableUIContainer(50, 65, this.width - 100, this.height - 110);
        this.layoutRoot.addElement(contentArea);

        refreshTab();

        // Action Bar
        int actionY = this.height - 35;
        int actionWidth = 100;
        int lockWidth = 20;
        int spacing = 8;
        // Total width of all 3 buttons + lock + their spacings
        int totalWidth = (actionWidth * 3) + (spacing * 2) + 5 + lockWidth;
        int actionX = (this.width - totalWidth) / 2;

        // Save Changes
        ResponsiveButton saveBtn = new ResponsiveButton(actionX, actionY, actionWidth, 20, Component.literal("\u00A7eSave Changes"), btn -> {
            boolean hard = hasHardChanges();
            if (hard && localConfig.locked) {
                Minecraft.getInstance().player.displayClientMessage(Component.literal("\u00A7c[Genesis Hub] Hard changes detected! Architectural changes are blocked while the Safety Lock is active."), false);
                return;
            }

            StorePriceManager.clientGenesisConfig.copyFrom(localConfig);
            PacketHandler.sendToServer(new UpdateGenesisConfigPacket(localConfig));
            Minecraft.getInstance().player.displayClientMessage(Component.literal("\u00A7e[Genesis Hub] Configuration Saved!"), true);

            if (hard) {
                Minecraft.getInstance().player.displayClientMessage(Component.literal("\u00A76[Genesis Hub] \u00A7eNotice: Architectural changes require Regeneration to take full effect."), false);
            }
        });
        addTooltip(saveBtn, "\u00A77Apply soft changes (Time, Weather).", "\u00A77Hard changes require Regenerate.");
        this.layoutRoot.addElement(saveBtn);

        // Enter Dimension
        boolean inGenesis = com.example.modmenu.client.ClientHelper.isGenesisLevel();
        Component enterLabel = Component.literal(inGenesis ? "\u00A76Leave Dimension" : "\u00A7bEnter Dimension");
        this.layoutRoot.addElement(new ResponsiveButton(actionX + actionWidth + spacing, actionY, actionWidth, 20, enterLabel, btn -> {
            PacketHandler.sendToServer(new EnterDimensionPacket());
            String msg = inGenesis ? "\u00A76[Genesis Hub] Returning home..." : "\u00A7b[Genesis Hub] Entering your custom realm...";
            Minecraft.getInstance().player.displayClientMessage(Component.literal(msg), true);
        }));

        // Regenerate (Linked to Lock State)
        String regenLabel = localConfig.fullResetRequested ? "\u00A76Wipe Pending" : "\u00A7cRegenerate";
        ResponsiveButton regenBtn = new ResponsiveButton(actionX + (actionWidth + spacing) * 2, actionY, actionWidth, 20, Component.literal(regenLabel), btn -> {
            if (!localConfig.locked) {
                if (localConfig.fullResetRequested) {
                    localConfig.fullResetRequested = false;
                    StorePriceManager.clientGenesisConfig.fullResetRequested = false;
                    PacketHandler.sendToServer(new UpdateGenesisConfigPacket(localConfig));
                    Minecraft.getInstance().player.displayClientMessage(Component.literal("\u00A7a[Genesis Hub] Full reset cancelled."), true);
                } else {
                    PacketHandler.sendToServer(new RegenerateDimensionPacket());
                    localConfig.fullResetRequested = true;
                    StorePriceManager.clientGenesisConfig.fullResetRequested = true;
                }
                this.init();
            }
        });
        if (localConfig.fullResetRequested) {
            addTooltip(regenBtn, "\u00A7eA full world wipe is scheduled.", "\u00A77It will happen on next server restart.", "\u00A7bClick to cancel wipe request.");
        } else {
            addTooltip(regenBtn, "\u00A77Reset the terrain around spawn.", "\u00A77New chunks will use new settings.", "\u00A7cClick to schedule full world wipe.");
        }
        regenBtn.setActive(!localConfig.locked); // Greys out if locked
        this.layoutRoot.addElement(regenBtn);

        // Lock Button
        String lockIcon = localConfig.locked ? "\u00A7c\u00A7l L" : "\u00A7a\u00A7l U";
        this.layoutRoot.addElement(new ResponsiveButton(actionX + (actionWidth + spacing) * 2 + actionWidth + 5, actionY, lockWidth, 20, Component.literal(lockIcon), btn -> {
            localConfig.locked = !localConfig.locked;
            this.init(); // Rebuild to update all button states and colors
        }));
    }

    private void addTabButton(int x, int y, int w, String label, int index) {
        // Dynamic text: Bold Aqua for active, plain for inactive
        Component btnText = Component.literal(activeTab == index ? "\u00A7b\u00A7l" + label : label);
        ResponsiveButton btn = new ResponsiveButton(x, y, w, 20, btnText, b -> {
            activeTab = index;
            this.init(); // Rebuild UI to update highlights and content
        });
        btn.setAlpha(activeTab == index ? 1.0f : 0.6f);
        this.layoutRoot.addElement(btn);
    }

    private void refreshTab() {
        if (contentArea == null) return;
        contentArea.clearChildren();
        
        switch (activeTab) {
            case 0 -> setupGenerationTab();
            case 1 -> setupAtmosphereTab();
            case 2 -> setupPhysicsTab();
            case 3 -> setupLawsTab();
            case 4 -> {
                if (StorePriceManager.isEditor) setupFormulasTab();
                else setupGenerationTab();
            }
        }
    }

    private void setupGenerationTab() {
        int y = 0;
        int spacing = 25;
        int labelX = 10;
        int btnX = 150;
        int btnW = 150;

        addLabel(labelX, y + 5, "\u00A7c* \u00A7fWorld Type:");
        ResponsiveButton typeBtn = addCycleButton(btnX, y, btnW, 20, localConfig.genType, new String[]{"Normal", "Superflat", "Large Biomes", "Amplified", "Floating islands original", "Void", "Caves", "End"}, val -> {
            localConfig.genType = val;
        });
        addTooltip(typeBtn, "\u00A77The base terrain shape of your world.", "\u00A7cRequires Regeneration!");
        y += spacing;

        // Biome Selection
        ResponsiveButton surfBtn = new ResponsiveButton(labelX, y, 130, 20, Component.literal("\u00A7c* \u00A7fSurface Biomes (" + localConfig.biomes.size() + ")"), btn -> {
            this.minecraft.setScreen(new PickBiomeScreen(this, localConfig.biomes, list -> localConfig.biomes = list));
        });
        addTooltip(surfBtn, "\u00A77Select which biomes appear on the surface.", "\u00A7cRequires Regeneration!");
        contentArea.addElement(surfBtn);

        ResponsiveButton caveBtn = new ResponsiveButton(btnX, y, btnW, 20, Component.literal("\u00A7c* \u00A7fCave Biomes (" + localConfig.caveBiomes.size() + ")"), btn -> {
            this.minecraft.setScreen(new PickBiomeScreen(this, localConfig.caveBiomes, list -> localConfig.caveBiomes = list));
        });
        addTooltip(caveBtn, "\u00A77Select which biomes appear in cave layers.", "\u00A7cRequires Regeneration!");
        contentArea.addElement(caveBtn);
        y += spacing;

        addLabel(labelX, y + 5, "\u00A7c* \u00A7fResource Density:");
        UIContainer resSlider = addValueSlider(btnX, y, btnW, 20, String.format("%.1fx", localConfig.resourceDensity), () -> {
            localConfig.resourceDensity = Math.max(0.1, localConfig.resourceDensity - 0.1);
        }, () -> {
            localConfig.resourceDensity = Math.min(10.0, localConfig.resourceDensity + 0.1);
        });
        addTooltip(resSlider, "\u00A77Multiplier for ore and feature frequency.", "\u00A7cRequires Regeneration!");
        y += spacing;

        addLabel(labelX, y + 5, "\u00A7c* \u00A7fStructure Density:");
        UIContainer structSlider = addValueSlider(btnX, y, btnW, 20, String.format("%.1fx", localConfig.structureDensity), () -> {
            localConfig.structureDensity = Math.max(0.0, localConfig.structureDensity - 0.1);
        }, () -> {
            localConfig.structureDensity = Math.min(5.0, localConfig.structureDensity + 0.1);
        });
        addTooltip(structSlider, "\u00A77Control the frequency of villages, etc.", "\u00A77Values > 1.0 may not increase density significantly.", "\u00A7cRequires Regeneration!");
        y += spacing;

        addLabel(labelX, y + 5, "\u00A7c* \u00A7fSea Level Fluid:");
        addCycleButton(btnX, y, btnW, 20, localConfig.seaLevelFluid, new String[]{"minecraft:water", "minecraft:lava", "minecraft:air"}, val -> {
            localConfig.seaLevelFluid = val;
        });
        y += spacing;

        addLabel(labelX, y + 5, "\u00A7c* \u00A7fNatural Lava Pools:");
        addToggleButton(btnX, y, btnW, 20, localConfig.spawnLavaLakes, val -> localConfig.spawnLavaLakes = val);
        y += spacing;

        addLabel(labelX, y + 5, "\u00A7c* \u00A7fDimension Scale:");
        addValueSlider(btnX, y, btnW, 20, String.format("%.1f:1", localConfig.dimensionScale), () -> {
            localConfig.dimensionScale = Math.max(0.1, localConfig.dimensionScale - 0.1);
        }, () -> {
            localConfig.dimensionScale = Math.min(64.0, localConfig.dimensionScale + 0.1);
        });
        y += spacing;

        addLabel(labelX, y + 5, "Spawn Hostile:");
        addToggleButton(btnX, y, btnW, 20, localConfig.spawnHostile, val -> localConfig.spawnHostile = val);
        y += spacing;

        addLabel(labelX, y + 5, "Spawn Passive:");
        addToggleButton(btnX, y, btnW, 20, localConfig.spawnPassive, val -> localConfig.spawnPassive = val);
        y += spacing;

        addLabel(labelX, y + 5, "Spawn Neutral:");
        addToggleButton(btnX, y, btnW, 20, localConfig.spawnNeutral, val -> localConfig.spawnNeutral = val);
        y += spacing;

        contentArea.setContentHeight(y + 20);
    }

    private void addTooltip(UIElement element, String... lines) {
        java.util.List<Component> tooltip = new java.util.ArrayList<>();
        for (String line : lines) {
            tooltip.add(Component.literal(line));
        }
        element.setTooltip(tooltip);
    }

    private UIElement addLabel(int x, int y, String text) {
        UIElement el = new UIElement(x, y, 130, 10) {
            @Override
            public void render(GuiGraphics g, int mx, int my, float pt) {
                g.drawString(font, text, getX(), getY(), 0xFFFFFFFF);
            }
        };
        contentArea.addElement(el);
        if (text.startsWith("\u00A7c*")) {
            addTooltip(el, "\u00A7c* \u00A77This setting is an Architectural Change.", "\u00A77Requires \u00A7cRegenerate \u00A77to take full effect.");
        }
        return el;
    }

    private ResponsiveButton addCycleButton(int x, int y, int w, int h, String current, String[] options, java.util.function.Consumer<String> setter) {
        ResponsiveButton btn = new ResponsiveButton(x, y, w, h, Component.literal(current), b -> {
            int idx = -1;
            for (int i = 0; i < options.length; i++) {
                if (options[i].equals(current)) {
                    idx = i;
                    break;
                }
            }
            int nextIdx;
            if (b.lastClickedButton == 1) { // Right click
                nextIdx = (idx - 1 + options.length) % options.length;
            } else {
                nextIdx = (idx + 1) % options.length;
            }
            setter.accept(options[nextIdx]);
            refreshTab();
        });
        contentArea.addElement(btn);
        return btn;
    }

    private ResponsiveButton addToggleButton(int x, int y, int w, int h, boolean current, java.util.function.Consumer<Boolean> setter) {
        ResponsiveButton btn = new ResponsiveButton(x, y, w, h, Component.literal(current ? "ON" : "OFF"), b -> {
            setter.accept(!current);
            refreshTab();
        });
        if (current) btn.setText(Component.literal("\u00A7aON"));
        else btn.setText(Component.literal("\u00A7cOFF"));
        contentArea.addElement(btn);
        return btn;
    }

    private UIContainer addValueSlider(int x, int y, int w, int h, String value, Runnable dec, Runnable inc) {
        UIContainer row = new UIContainer(x, y, w, h);
        row.addElement(new ResponsiveButton(0, 0, 20, h, Component.literal("-"), b -> {
            dec.run();
            refreshTab();
        }));
        row.addElement(new UIElement(25, 0, w - 50, h) {
            @Override
            public void render(GuiGraphics g, int mx, int my, float pt) {
                g.drawCenteredString(font, value, getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, 0xFFFFFFFF);
            }
        });
        row.addElement(new ResponsiveButton(w - 20, 0, 20, h, Component.literal("+"), b -> {
            inc.run();
            refreshTab();
        }));
        contentArea.addElement(row);
        return row;
    }

    private void setupAtmosphereTab() {
        int y = 0;
        int spacing = 25;
        int labelX = 10;
        int btnX = 150;
        int btnW = 150;

        addLabel(labelX, y + 5, "Day/Night Ratio:");
        addValueSlider(btnX, y, btnW, 20, String.format("%.0f/%.0f", localConfig.dayNightRatio * 100, (1.0 - localConfig.dayNightRatio) * 100), () -> {
            localConfig.dayNightRatio = Math.max(0.0, localConfig.dayNightRatio - 0.05);
        }, () -> {
            localConfig.dayNightRatio = Math.min(1.0, localConfig.dayNightRatio + 0.05);
        });
        y += spacing;

        addLabel(labelX, y + 5, "Time Speed:");
        addValueSlider(btnX, y, btnW, 20, String.format("%.1fx", localConfig.temporalVelocity), () -> {
            localConfig.temporalVelocity = Math.max(0.1, localConfig.temporalVelocity - 0.1);
        }, () -> {
            localConfig.temporalVelocity = Math.min(10.0, localConfig.temporalVelocity + 0.1);
        });
        y += spacing;

        addLabel(labelX, y + 5, "Tick Freeze:");
        addTooltip(addToggleButton(btnX, y, btnW, 20, localConfig.tickFreeze, val -> localConfig.tickFreeze = val), "\u00A77Freeze the passage of time.");
        y += spacing;

        addLabel(labelX, y + 5, "Persistent Weather:");
        addTooltip(addCycleButton(btnX, y, btnW, 20, localConfig.persistentWeather, new String[]{"Dynamic", "Clear", "Rain", "Thunder"}, val -> {
            localConfig.persistentWeather = val;
        }), "\u00A77Force a specific weather state indefinitely.");
        y += spacing;

        addLabel(labelX, y + 5, "Sky Color:");
        addColorPicker(btnX, y, btnW, 20, localConfig.skyColor, col -> localConfig.skyColor = col);
        y += spacing;

        addLabel(labelX, y + 5, "Fog Color:");
        addColorPicker(btnX, y, btnW, 20, localConfig.fogColor, col -> localConfig.fogColor = col);
        y += spacing;

        addLabel(labelX, y + 5, "Water Color:");
        addColorPicker(btnX, y, btnW, 20, localConfig.waterColor, col -> localConfig.waterColor = col);
        y += spacing;

        addLabel(labelX, y + 5, "Grass Color:");
        addColorPicker(btnX, y, btnW, 20, localConfig.grassColor, col -> localConfig.grassColor = col);
        y += spacing;

        addLabel(labelX, y + 5, "Foliage Color:");
        addColorPicker(btnX, y, btnW, 20, localConfig.foliageColor, col -> localConfig.foliageColor = col);
        y += spacing;

        addLabel(labelX, y + 5, "Ambient Light:");
        addValueSlider(btnX, y, btnW, 20, String.format("%.0f%%", localConfig.ambientLight * 100), () -> {
            localConfig.ambientLight = Math.max(0.0, localConfig.ambientLight - 0.05);
        }, () -> {
            localConfig.ambientLight = Math.min(1.0, localConfig.ambientLight + 0.05);
        });
        y += spacing;

        addLabel(labelX, y + 5, "Fog Density:");
        addValueSlider(btnX, y, btnW, 20, String.format("%.1fx", localConfig.fogDensity), () -> {
            localConfig.fogDensity = Math.max(0.0, localConfig.fogDensity - 0.1);
        }, () -> {
            localConfig.fogDensity = Math.min(5.0, localConfig.fogDensity + 0.1);
        });
        y += spacing;

        addLabel(labelX, y + 5, "Celestial Sync:");
        addTooltip(addToggleButton(btnX, y, btnW, 20, localConfig.celestialSync, val -> localConfig.celestialSync = val), "\u00A77Synchronize time with the Overworld.");
        y += spacing;

        contentArea.setContentHeight(y + 20);
    }

    private void addColorPicker(int x, int y, int w, int h, int current, java.util.function.Consumer<Integer> setter) {
        String colorStr;
        if (current == -1) {
            // Try to find the actual color of the first biome
            int vanillaColor = 0xFFFFFF;
            if (!localConfig.biomes.isEmpty()) {
                try {
                    String biomeId = localConfig.biomes.get(0);
                    // This is client-side, so we can use Minecraft's registries
                    net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> holder = 
                        Minecraft.getInstance().level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME)
                        .getHolder(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BIOME, net.minecraft.resources.ResourceLocation.tryParse(biomeId)))
                        .orElse(null);
                    if (holder != null) {
                        // Unfortunately getting the color depends on which field we are picking
                        // and Biome class methods are often protected or obscured.
                        // But we can show a special label.
                    }
                } catch (Exception e) {}
            }
            colorStr = "VANILLA";
        } else {
            colorStr = String.format("#%06X", current & 0xFFFFFF);
        }

        ResponsiveButton btn = new ResponsiveButton(x, y, w, h, Component.literal(colorStr), b -> {
            this.minecraft.setScreen(new ColorPickerScreen(this, current, val -> {
                setter.accept(val);
                this.refreshTab();
            }));
        });
        
        if (current != -1) {
            // Show a small color preview on the button if possible, or just color the text
            btn.setText(Component.literal("\u00A7l" + colorStr));
            // We can't easily change text color of ResponsiveButton yet without modifying it, 
            // but we can use the alpha/active states.
        }
        
        contentArea.addElement(btn);
    }

    private void setupPhysicsTab() {
        int y = 0;
        int spacing = 25;
        int labelX = 10;
        int btnX = 150;
        int btnW = 150;

        addLabel(labelX, y + 5, "Gravity:");
        addValueSlider(btnX, y, btnW, 20, String.format("%.1fg", localConfig.gravity), () -> {
            localConfig.gravity = Math.max(-0.5, localConfig.gravity - 0.1);
        }, () -> {
            localConfig.gravity = Math.min(5.0, localConfig.gravity + 0.1);
        });
        y += spacing;

        addLabel(labelX, y + 5, "Thermal:");
        addCycleButton(btnX, y, btnW, 20, localConfig.thermalRegulation, new String[]{"Normal", "Sub-Zero", "Super-Heated"}, val -> {
            localConfig.thermalRegulation = val;
        });
        y += spacing;

        addLabel(labelX, y + 5, "Fluid Viscosity:");
        addTooltip(addToggleButton(btnX, y, btnW, 20, localConfig.fluidViscosityHigh, val -> localConfig.fluidViscosityHigh = val), "\u00A77Make liquids flow much slower.");
        y += spacing;

        addLabel(labelX, y + 5, "Explosion Yield:");
        addValueSlider(btnX, y, btnW, 20, String.format("%.0f%%", localConfig.explosionYield * 100), () -> {
            localConfig.explosionYield = Math.max(0.0, localConfig.explosionYield - 0.1);
        }, () -> {
            localConfig.explosionYield = Math.min(5.0, localConfig.explosionYield + 0.1);
        });
        y += spacing;

        addLabel(labelX, y + 5, "Fall Damage Mult:");
        addValueSlider(btnX, y, btnW, 20, String.format("%.1fx", localConfig.fallDamageMultiplier), () -> {
            localConfig.fallDamageMultiplier = Math.max(0.0, localConfig.fallDamageMultiplier - 0.1);
        }, () -> {
            localConfig.fallDamageMultiplier = Math.min(10.0, localConfig.fallDamageMultiplier + 0.1);
        });
        y += spacing;

        contentArea.setContentHeight(y + 20);
    }

    private void setupLawsTab() {
        int y = 0;
        int spacing = 25;
        int labelX = 10;
        int btnX = 150;
        int btnW = 150;

        addLabel(labelX, y + 5, "Difficulty:");
        addCycleButton(btnX, y, btnW, 20, localConfig.difficulty, new String[]{"Peaceful", "Easy", "Normal", "Hard"}, val -> {
            localConfig.difficulty = val;
        });
        y += spacing;

        addLabel(labelX, y + 5, "Respawn Logic:");
        addTooltip(addToggleButton(btnX, y, btnW, 20, localConfig.respawnLogicEnabled, val -> localConfig.respawnLogicEnabled = val), "\u00A77Enable Beds and Respawn Anchors in this dimension.");
        y += spacing;

        addLabel(labelX, y + 5, "Loot/XP Multiplier:");
        addValueSlider(btnX, y, btnW, 20, String.format("%.1fx", localConfig.lootXpMultiplier), () -> {
            localConfig.lootXpMultiplier = Math.max(1.0, localConfig.lootXpMultiplier - 1.0);
        }, () -> {
            localConfig.lootXpMultiplier = Math.min(50.0, localConfig.lootXpMultiplier + 1.0);
        });
        y += spacing;

        addLabel(labelX, y + 5, "Reality Persistence:");
        addTooltip(addToggleButton(btnX, y, btnW, 20, localConfig.realityPersistence, val -> localConfig.realityPersistence = val), "\u00A77Keep the core chunks loaded even when no players are present.");
        y += spacing;

        addLabel(labelX, y + 5, "\u00A7c* \u00A7fBedrock Control:");
        addCycleButton(btnX, y, btnW, 20, localConfig.bedrockControl, new String[]{"Normal", "Floor", "Ceiling", "Both", "None"}, val -> {
            localConfig.bedrockControl = val;
        });
        y += spacing;

        addLabel(labelX, y + 5, "Void Mirror (Safety):");
        addTooltip(addToggleButton(btnX, y, btnW, 20, localConfig.voidMirror, val -> localConfig.voidMirror = val), "\u00A77Teleport back to the top if you fall into the void.");
        y += spacing;

        addLabel(labelX, y + 5, "Mob Spawn Rate:");
        addValueSlider(btnX, y, btnW, 20, String.format("%.1fx", localConfig.mobSpawnRate), () -> {
            localConfig.mobSpawnRate = Math.max(0.0, localConfig.mobSpawnRate - 0.1);
        }, () -> {
            localConfig.mobSpawnRate = Math.min(10.0, localConfig.mobSpawnRate + 0.1);
        });
        y += spacing;

        addLabel(labelX, y + 5, "Mob Mutation Rate:");
        addValueSlider(btnX, y, btnW, 20, String.format("%.0f%%", localConfig.mobMutationRate * 100), () -> {
            localConfig.mobMutationRate = Math.max(0.0, localConfig.mobMutationRate - 0.05);
        }, () -> {
            localConfig.mobMutationRate = Math.min(1.0, localConfig.mobMutationRate + 0.05);
        });
        y += spacing;

        addLabel(labelX, y + 5, "Hazard: Radiation:");
        addTooltip(addToggleButton(btnX, y, btnW, 20, localConfig.hazardRadiation, val -> localConfig.hazardRadiation = val), "\u00A77Causes periodic poison damage to all biological entities.");
        y += spacing;

        addLabel(labelX, y + 5, "Hazard: Oxygen:");
        addTooltip(addToggleButton(btnX, y, btnW, 20, localConfig.hazardOxygen, val -> localConfig.hazardOxygen = val), "\u00A77Requires a helmet to breathe. Causes drowning damage otherwise.");
        y += spacing;

        addLabel(labelX, y + 5, "Join Message:");
        ResponsiveButton msgBtn = new ResponsiveButton(btnX, y, btnW, 20, Component.literal("Edit Message"), btn -> {
            this.minecraft.setScreen(new TextInputScreen(this, "Edit Join Message", localConfig.joinMessage, msg -> localConfig.joinMessage = msg));
        });
        addTooltip(msgBtn, "\u00A77The message shown to anyone entering your realm.");
        contentArea.addElement(msgBtn);
        y += spacing;

        contentArea.setContentHeight(y + 20);
    }

    private void setupFormulasTab() {
        int y = 0;
        int spacing = 25;
        int labelX = 10;
        int btnX = 150;
        int btnW = 150;

        StorePriceManager.FormulaConfig f = StorePriceManager.formulas;

        addLabel(labelX, y + 5, "Step Assist Cost:");
        addValueSlider(btnX, y, btnW, 20, f.stepAssistCostPerAssist.toString(), () -> f.stepAssistCostPerAssist = f.stepAssistCostPerAssist.subtract(java.math.BigDecimal.valueOf(5)).max(java.math.BigDecimal.ZERO), () -> f.stepAssistCostPerAssist = f.stepAssistCostPerAssist.add(java.math.BigDecimal.valueOf(5)));
        y += spacing;

        addLabel(labelX, y + 5, "Area Mining Base:");
        addValueSlider(btnX, y, btnW, 20, f.areaMiningCostBase.toString(), () -> f.areaMiningCostBase = f.areaMiningCostBase.subtract(java.math.BigDecimal.valueOf(10)).max(java.math.BigDecimal.ZERO), () -> f.areaMiningCostBase = f.areaMiningCostBase.add(java.math.BigDecimal.valueOf(10)));
        y += spacing;

        addLabel(labelX, y + 5, "Flight Cost/sec:");
        addValueSlider(btnX, y, btnW, 20, f.flightCostPerSecond.toString(), () -> f.flightCostPerSecond = f.flightCostPerSecond.subtract(java.math.BigDecimal.valueOf(50)).max(java.math.BigDecimal.ZERO), () -> f.flightCostPerSecond = f.flightCostPerSecond.add(java.math.BigDecimal.valueOf(50)));
        y += spacing;

        addLabel(labelX, y + 5, "Sure Kill Base:");
        addValueSlider(btnX, y, btnW, 20, f.sureKillBaseCost.toString(), () -> f.sureKillBaseCost = f.sureKillBaseCost.subtract(java.math.BigDecimal.valueOf(500)).max(java.math.BigDecimal.ZERO), () -> f.sureKillBaseCost = f.sureKillBaseCost.add(java.math.BigDecimal.valueOf(500)));
        y += spacing;

        addLabel(labelX, y + 5, "No Aggro Cancel:");
        addValueSlider(btnX, y, btnW, 20, f.noAggroCostPerCancel.toString(), () -> f.noAggroCostPerCancel = f.noAggroCostPerCancel.subtract(java.math.BigDecimal.valueOf(20)).max(java.math.BigDecimal.ZERO), () -> f.noAggroCostPerCancel = f.noAggroCostPerCancel.add(java.math.BigDecimal.valueOf(20)));
        y += spacing;

        addLabel(labelX, y + 5, "Repair Cost/pt:");
        addValueSlider(btnX, y, btnW, 20, String.valueOf(f.repairCostPerPoint), () -> f.repairCostPerPoint = Math.max(0, f.repairCostPerPoint - 1), () -> f.repairCostPerPoint++);
        y += spacing;

        addLabel(labelX, y + 5, "SP Multiplier:");
        addValueSlider(btnX, y, btnW, 20, String.format("%.1fx", f.spMultiplier), () -> f.spMultiplier = Math.max(0.1, f.spMultiplier - 0.1), () -> f.spMultiplier = Math.min(10.0, f.spMultiplier + 0.1));
        y += spacing;

        addLabel(labelX, y + 5, "Link Magnet Base:");
        addValueSlider(btnX, y, btnW, 20, f.linkMagnetMaintenance.toString(), () -> f.linkMagnetMaintenance = f.linkMagnetMaintenance.subtract(java.math.BigDecimal.valueOf(10)).max(java.math.BigDecimal.ZERO), () -> f.linkMagnetMaintenance = f.linkMagnetMaintenance.add(java.math.BigDecimal.valueOf(10)));
        y += spacing;

        addLabel(labelX, y + 5, "Link Mag Dist Mult:");
        addValueSlider(btnX, y, btnW, 20, f.linkMagnetDistanceMultiplier.toString(), () -> f.linkMagnetDistanceMultiplier = f.linkMagnetDistanceMultiplier.subtract(java.math.BigDecimal.valueOf(0.1)).max(java.math.BigDecimal.ZERO), () -> f.linkMagnetDistanceMultiplier = f.linkMagnetDistanceMultiplier.add(java.math.BigDecimal.valueOf(0.1)));
        y += spacing;

        addLabel(labelX, y + 5, "Link Mag Dim Tax:");
        addValueSlider(btnX, y, btnW, 20, f.linkMagnetDimensionTax.toString(), () -> f.linkMagnetDimensionTax = f.linkMagnetDimensionTax.subtract(java.math.BigDecimal.valueOf(100)).max(java.math.BigDecimal.ZERO), () -> f.linkMagnetDimensionTax = f.linkMagnetDimensionTax.add(java.math.BigDecimal.valueOf(100)));
        y += spacing;

        ResponsiveButton saveFormulasBtn = new ResponsiveButton(btnX, y, btnW, 20, Component.literal("\u00A76Update Formulas"), btn -> {
            PacketHandler.sendToServer(new com.example.modmenu.network.UpdateFormulasPacket(f));
            Minecraft.getInstance().player.displayClientMessage(Component.literal("\u00A76[Genesis Hub] Formulas updated on server."), true);
        });
        contentArea.addElement(saveFormulasBtn);
        y += spacing;

        contentArea.setContentHeight(y + 20);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, "GENESIS HUB", this.width / 2, 15, 0xFFFFAA00);
    }

    private boolean hasHardChanges() {
        StorePriceManager.GenesisConfig old = StorePriceManager.clientGenesisConfig;
        for (java.lang.reflect.Field field : StorePriceManager.GenesisConfig.class.getFields()) {
            if (field.isAnnotationPresent(com.example.modmenu.store.HardChange.class)) {
                try {
                    Object val1 = field.get(localConfig);
                    Object val2 = field.get(old);
                    if (val1 == null && val2 == null) continue;
                    if (val1 == null || !val1.equals(val2)) {
                        return true;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }
}
