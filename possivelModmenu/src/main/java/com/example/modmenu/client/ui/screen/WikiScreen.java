package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.WikiData;
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

        for (WikiData.WikiSection section : WikiData.getSections()) {
            addSection(list, section.title, section.content);
        }

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
