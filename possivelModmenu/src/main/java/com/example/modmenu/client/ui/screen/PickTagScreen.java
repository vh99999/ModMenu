package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.base.ScrollableUIContainer;
import com.example.modmenu.client.ui.base.UIElement;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class PickTagScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private final ItemStack stack;
    private final Consumer<String> onPick;

    public PickTagScreen(Screen parent, ItemStack stack, Consumer<String> onPick) {
        super(Component.literal("Pick Tag"));
        this.parent = parent;
        this.stack = stack;
        this.onPick = onPick;
    }

    @Override
    protected void setupLayout() {
        this.layoutRoot.addElement(new ResponsiveButton(10, 10, 50, 20, Component.literal("Cancel"), btn -> {
            this.minecraft.setScreen(parent);
        }));

        List<String> tags = stack.getTags().map(t -> t.location().toString()).collect(Collectors.toList());

        if (tags.isEmpty()) {
            this.layoutRoot.addElement(new UIElement(this.width / 2 - 100, this.height / 2, 200, 20) {
                @Override
                public void render(GuiGraphics g, int mx, int my, float pt) {
                    g.drawCenteredString(font, "\u00A7cNo tags found on this item!", 0, 0, 0xFFFFFFFF);
                }
            });
        } else {
            ScrollableUIContainer tagList = new ScrollableUIContainer(50, 40, this.width - 100, this.height - 80);
            this.layoutRoot.addElement(tagList);

            int currentY = 0;
            for (String tag : tags) {
                final String currentTag = tag;
                tagList.addElement(new ResponsiveButton(0, currentY, tagList.getWidth() - 20, 20, Component.literal(currentTag), btn -> {
                    onPick.accept(currentTag);
                    this.minecraft.setScreen(parent);
                }));
                currentY += 22;
            }
            tagList.setContentHeight(currentY);
        }
    }
}
