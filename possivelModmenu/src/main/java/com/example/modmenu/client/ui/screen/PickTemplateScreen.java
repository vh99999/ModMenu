package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.base.ScrollableUIContainer;
import com.example.modmenu.client.ui.base.UIElement;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.store.logistics.NetworkData;
import com.example.modmenu.store.logistics.RuleTemplate;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class PickTemplateScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private final NetworkData networkData;
    private final Consumer<RuleTemplate> onPick;
    private ScrollableUIContainer list;

    public PickTemplateScreen(Screen parent, NetworkData networkData, Consumer<RuleTemplate> onPick) {
        super(Component.literal("Pick Rule Blueprint"));
        this.parent = parent;
        this.networkData = networkData;
        this.onPick = onPick;
    }

    @Override
    protected void setupLayout() {
        this.layoutRoot.addElement(new ResponsiveButton(10, 10, 50, 20, Component.literal("Back"), btn -> this.minecraft.setScreen(parent)));

        list = new ScrollableUIContainer(20, 40, this.width - 40, this.height - 60);
        this.layoutRoot.addElement(list);

        int cy = 0;
        for (RuleTemplate template : networkData.ruleTemplates) {
            final RuleTemplate t = template;
            list.addElement(new ResponsiveButton(0, cy, list.getWidth() - 20, 25, Component.literal(t.name + " (" + t.rule.type + ")"), btn -> {
                onPick.accept(t);
                this.minecraft.setScreen(parent);
            }));
            cy += 27;
        }
        list.setContentHeight(cy + 20);
    }
}
