package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class TextInputScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private final String title;
    private final String initialValue;
    private final Consumer<String> callback;
    private EditBox input;

    public TextInputScreen(Screen parent, String title, String initialValue, Consumer<String> callback) {
        super(Component.literal(title));
        this.parent = parent;
        this.title = title;
        this.initialValue = initialValue;
        this.callback = callback;
    }

    @Override
    protected void init() {
        super.init();
        input = new EditBox(font, this.width / 2 - 150, this.height / 2 - 10, 300, 20, Component.literal(title));
        input.setMaxLength(256);
        input.setValue(initialValue);
        this.addWidget(input);
        this.setFocused(input);
    }

    @Override
    protected void setupLayout() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        this.layoutRoot.addElement(new ResponsiveButton(cx - 105, cy + 30, 100, 20, Component.literal("Confirm"), btn -> {
            callback.accept(input.getValue());
            this.minecraft.setScreen(parent);
        }));

        this.layoutRoot.addElement(new ResponsiveButton(cx + 5, cy + 30, 100, 20, Component.literal("Cancel"), btn -> {
            this.minecraft.setScreen(parent);
        }));
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, title, this.width / 2, this.height / 2 - 30, 0xFFFFFFFF);
        input.render(g, mx, my, pt);

        // Preview for Join Message
        if (title.contains("Join Message")) {
            String preview = input.getValue().replace("&", "§");
            g.drawCenteredString(font, "Preview:", this.width / 2, this.height / 2 + 60, 0xFFAAAAAA);
            g.drawCenteredString(font, preview, this.width / 2, this.height / 2 + 75, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter
            callback.accept(input.getValue());
            this.minecraft.setScreen(parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
