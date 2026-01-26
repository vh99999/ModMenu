package com.example.modmenu.client.ui.screen;

import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.base.UIElement;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class ColorPickerScreen extends BaseResponsiveLodestoneScreen {
    private final Screen parent;
    private final Consumer<Integer> onConfirm;
    private int r, g, b;
    private boolean isDefault = false;

    private EditBox rField, gField, bField, hexField;
    private RGBSlider rSlider, gSlider, bSlider;

    public ColorPickerScreen(Screen parent, int initialColor, Consumer<Integer> onConfirm) {
        super(Component.literal("Color Picker"));
        this.parent = parent;
        this.onConfirm = onConfirm;
        if (initialColor == -1) {
            this.isDefault = true;
            this.r = 255;
            this.g = 255;
            this.b = 255;
        } else {
            this.isDefault = false;
            this.r = (initialColor >> 16) & 0xFF;
            this.g = (initialColor >> 8) & 0xFF;
            this.b = initialColor & 0xFF;
        }
    }

    @Override
    protected void init() {
        super.init();
        setupFields();
    }

    private void setupFields() {
        int centerX = this.width / 2;
        int fieldY = 60;
        int fieldX = centerX + 60;

        rField = createNumericField(fieldX, fieldY, r);
        gField = createNumericField(fieldX, fieldY + 40, g);
        bField = createNumericField(fieldX, fieldY + 80, b);

        hexField = new EditBox(font, centerX - 50, fieldY + 190, 100, 20, Component.literal("Hex"));
        hexField.setMaxLength(7);
        hexField.setValue(String.format("#%02X%02X%02X", r, g, b));
        hexField.setResponder(val -> {
            if (val.startsWith("#") && val.length() == 7) {
                try {
                    int color = Integer.parseInt(val.substring(1), 16);
                    updateFromHex(color);
                } catch (NumberFormatException ignored) {}
            }
        });
        this.addWidget(hexField);

        rField.setResponder(val -> updateFromField(val, 0));
        gField.setResponder(val -> updateFromField(val, 1));
        bField.setResponder(val -> updateFromField(val, 2));

        this.addWidget(rField);
        this.addWidget(gField);
        this.addWidget(bField);
        this.setFocused(hexField);
    }

    private EditBox createNumericField(int x, int y, int initial) {
        EditBox field = new EditBox(font, x, y, 40, 20, Component.literal(""));
        field.setMaxLength(3);
        field.setValue(String.valueOf(initial));
        return field;
    }

    private void updateFromField(String val, int channel) {
        try {
            int v = Integer.parseInt(val);
            v = Math.max(0, Math.min(255, v));
            if (channel == 0) { r = v; if (rSlider != null) rSlider.value = r / 255.0f; }
            if (channel == 1) { g = v; if (gSlider != null) gSlider.value = g / 255.0f; }
            if (channel == 2) { b = v; if (bSlider != null) bSlider.value = b / 255.0f; }
            isDefault = false;
            updateHexField();
        } catch (NumberFormatException ignored) {}
    }

    private void updateFromHex(int color) {
        r = (color >> 16) & 0xFF;
        g = (color >> 8) & 0xFF;
        b = color & 0xFF;
        isDefault = false;
        rField.setValue(String.valueOf(r));
        gField.setValue(String.valueOf(g));
        bField.setValue(String.valueOf(b));
        if (rSlider != null) rSlider.value = r / 255.0f;
        if (gSlider != null) gSlider.value = g / 255.0f;
        if (bSlider != null) bSlider.value = b / 255.0f;
    }

    private void updateHexField() {
        if (hexField != null && !hexField.isFocused()) {
            hexField.setValue(String.format("#%02X%02X%02X", r, g, b));
        }
    }

    @Override
    protected void setupLayout() {
        this.layoutRoot.addElement(new ResponsiveButton(10, 10, 50, 20, Component.literal("Cancel"), btn -> {
            this.minecraft.setScreen(parent);
        }));

        this.layoutRoot.addElement(new ResponsiveButton(this.width - 60, 10, 50, 20, Component.literal("\u00A7aApply"), btn -> {
            onConfirm.accept(isDefault ? -1 : (r << 16) | (g << 8) | b);
            this.minecraft.setScreen(parent);
        }));

        int centerX = this.width / 2;
        int startY = 60;
        int sliderWidth = 150;

        // Sliders
        rSlider = new RGBSlider(centerX - 100, startY, sliderWidth, 20, "Red", r, val -> {
            this.r = val;
            rField.setValue(String.valueOf(r));
            isDefault = false;
            updateHexField();
        });
        gSlider = new RGBSlider(centerX - 100, startY + 40, sliderWidth, 20, "Green", g, val -> {
            this.g = val;
            gField.setValue(String.valueOf(g));
            isDefault = false;
            updateHexField();
        });
        bSlider = new RGBSlider(centerX - 100, startY + 80, sliderWidth, 20, "Blue", b, val -> {
            this.b = val;
            bField.setValue(String.valueOf(b));
            isDefault = false;
            updateHexField();
        });

        this.layoutRoot.addElement(rSlider);
        this.layoutRoot.addElement(gSlider);
        this.layoutRoot.addElement(bSlider);

        // Default Button
        this.layoutRoot.addElement(new ResponsiveButton(centerX - 50, startY + 120, 100, 20, Component.literal("Set Default"), btn -> {
            isDefault = true;
            // Optionally reset to a specific "default" look in preview, e.g. white or gray
            updateFromHex(0xFFFFFF); // Visual reset
            isDefault = true; // Set again because updateFromHex clears it
        }));

        // Preview
        this.layoutRoot.addElement(new UIElement(centerX - 50, startY + 150, 100, 30) {
            @Override
            public void render(GuiGraphics g, int mx, int my, float pt) {
                if (isDefault) {
                    g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x88444444);
                    g.drawCenteredString(font, "DEFAULT", getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, 0xFFFFFFFF);
                } else {
                    int color = 0xFF000000 | (r << 16) | (ColorPickerScreen.this.g << 8) | b;
                    g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), color);
                }
                g.renderOutline(getX() - 1, getY() - 1, getWidth() + 2, getHeight() + 2, 0xFFFFFFFF);
            }
        });
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        rField.render(g, mx, my, pt);
        gField.render(g, mx, my, pt);
        bField.render(g, mx, my, pt);
        hexField.render(g, mx, my, pt);
    }

    private class RGBSlider extends UIElement {
        private final String label;
        public float value; 
        private final Consumer<Integer> setter;
        private boolean dragging = false;

        public RGBSlider(int x, int y, int width, int height, String label, int initial, Consumer<Integer> setter) {
            super(x, y, width, height);
            this.label = label;
            this.value = initial / 255.0f;
            this.setter = setter;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            // Background
            g.fill(x, y + 8, x + width, y + 12, 0xFF444444);
            // Knob
            int knobX = x + (int)(value * (width - 8));
            g.fill(knobX, y, knobX + 8, y + height, 0xFFFFFFFF);
            
            g.drawString(font, label, x, y - 10, 0xFFFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (isMouseOver(mx, my)) {
                dragging = true;
                updateValue(mx);
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            dragging = false;
            return false;
        }

        @Override
        public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            if (dragging) {
                updateValue(mx);
                return true;
            }
            return false;
        }

        private void updateValue(double mx) {
            this.value = (float) Math.max(0, Math.min(1, (mx - x) / (float)width));
            setter.accept((int)(value * 255));
        }
    }
}
