package com.example.modmenu.client.ui.screen;
import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.component.BlockSideSelector;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.store.logistics.NetworkNode;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;
public class PickSideScreen extends BaseResponsiveLodestoneScreen {
private final Screen parent;
private final NetworkNode node;
private final Consumer<String> onPick;
public PickSideScreen(Screen parent, NetworkNode node, Consumer<String> onPick) {
super(Component.literal("Pick Side"));
this.parent = parent;
this.node = node;
this.onPick = onPick;
} @Override protected void setupLayout() {
this.layoutRoot.addElement(new ResponsiveButton(10, 10, 50, 20, Component.literal("Back"), btn -> {
this.minecraft.setScreen(parent);
}));
this.layoutRoot.addElement(new ResponsiveButton(70, 10, 100, 20, Component.literal("Select AUTO"), btn -> {
onPick.accept("AUTO");
this.minecraft.setScreen(parent);
}));
int midX = this.width / 2;
int midY = this.height / 2;
this.layoutRoot.addElement(new BlockSideSelector(midX - 100, midY - 100, 200, 200, node, (dir, mode) -> {
onPick.accept(dir.name().toUpperCase());
this.minecraft.setScreen(parent);
}));
}
}