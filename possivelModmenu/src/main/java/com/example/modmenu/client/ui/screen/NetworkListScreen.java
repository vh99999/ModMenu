package com.example.modmenu.client.ui.screen;
import com.example.modmenu.client.ui.base.BaseResponsiveLodestoneScreen;
import com.example.modmenu.client.ui.base.ScrollableUIContainer;
import com.example.modmenu.client.ui.base.UIElement;
import com.example.modmenu.client.ui.component.ResponsiveButton;
import com.example.modmenu.network.*;
import com.example.modmenu.store.logistics.LogisticsCapability;
import com.example.modmenu.store.logistics.NetworkData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
public class NetworkListScreen extends BaseResponsiveLodestoneScreen {
private final Screen parent;
private ScrollableUIContainer list;
private List<NetworkData> cachedNetworks = new ArrayList<>();
private int lastVersion = -1;
public NetworkListScreen(Screen parent) {
super(Component.literal("Logistics Networks"));
this.parent = parent;
} @Override protected void setupLayout() {
this.layoutRoot.addElement(new ResponsiveButton(10, 10, 50, 20, Component.literal("Back"), btn -> {
this.minecraft.setScreen(parent);
}));
this.layoutRoot.addElement(new ResponsiveButton(70, 10, 120, 20, Component.literal("Create New Network"), btn -> {
PacketHandler.sendToServer(NetworkManagementPacket.create("Network #" + (cachedNetworks.size() + 1)));
}));
list = new ScrollableUIContainer(50, 40, this.width - 100, this.height - 50);
this.layoutRoot.addElement(list);
refreshList();
} private void refreshList() {
if (list == null) return;
list.clearChildren();
LogisticsCapability.getNetworks(Minecraft.getInstance().player).ifPresent(data -> {
this.cachedNetworks = data.getNetworks();
int currentY = 0;
int rowHeight = 50;
for (NetworkData network : cachedNetworks) {
list.addElement(new NetworkRowComponent(0, currentY, list.getWidth() - 10, rowHeight - 5, network));
currentY += rowHeight;
} list.setContentHeight(currentY);
});
} @Override public void render(GuiGraphics g, int mx, int my, float pt) {
LogisticsCapability.getNetworks(Minecraft.getInstance().player).ifPresent(data -> {
if (data.updateVersion != lastVersion) {
refreshList();
lastVersion = data.updateVersion;
}
});
super.render(g, mx, my, pt);
} private class NetworkRowComponent extends UIElement {
private final NetworkData data;
public NetworkRowComponent(int x, int y, int width, int height, NetworkData data) {
super(x, y, width, height);
this.data = data;
} @Override public void render(GuiGraphics g, int mx, int my, float pt) {
boolean hovered = mx >= getX() && my >= getY() && mx < getX() + getWidth() && my < getY() + getHeight();
g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), hovered ? 0x66FFFFFF : 0x33FFFFFF);
g.drawString(font, "Name: \u00A76" + data.networkName, getX() + 10, getY() + 10, 0xFFFFFFFF);
g.drawString(font, "Nodes: \u00A7e" + data.nodes.size() + " \u00A77| Rules: \u00A7b" + data.rules.size(), getX() + 10, getY() + 25, 0xFFAAAAAA);
int bx = getX() + getWidth() - 255;
String activeTxt = data.active ? "\u00A7aActive" : "\u00A7cPaused";
renderBtn(g, bx, getY() + 15, 80, 18, activeTxt, mx, my);
bx += 85;
renderBtn(g, bx, getY() + 5, 80, 18, "Rename", mx, my);
renderBtn(g, bx, getY() + 25, 80, 18, "Manage", mx, my);
bx += 85;
renderBtn(g, bx, getY() + 15, 80, 18, "\u00A7cDelete", mx, my);
} private void renderBtn(GuiGraphics g, int bx, int by, int bw, int bh, String txt, int mx, int my) {
boolean hov = mx >= bx && my >= by && mx < bx + bw && my < by + bh;
g.fill(bx, by, bx + bw, by + bh, hov ? 0xFF666666 : 0xFF444444);
g.drawCenteredString(font, txt, bx + bw / 2, by + bh / 2 - 4, 0xFFFFFFFF);
} @Override public boolean mouseClicked(double mx, double my, int button) {
if (!isMouseOver(mx, my)) return false;
int bx = getX() + getWidth() - 255;
if (mx >= bx && mx < bx + 80 && my >= getY() + 15 && my < getY() + 33) {
PacketHandler.sendToServer(NetworkManagementPacket.toggleActive(data.networkId));
return true;
} bx += 85;
if (mx >= bx && mx < bx + 80) {
if (my >= getY() + 5 && my < getY() + 23) {
Minecraft.getInstance().setScreen(new RenameNetworkScreen(NetworkListScreen.this, data.networkId, data.networkName));
return true;
} if (my >= getY() + 25 && my < getY() + 43) {
Minecraft.getInstance().setScreen(new NetworkManagerScreen(NetworkListScreen.this, data.networkId));
return true;
}
} bx += 85;
if (mx >= bx && mx < bx + 80 && my >= getY() + 15 && my < getY() + 33) {
PacketHandler.sendToServer(NetworkManagementPacket.delete(data.networkId));
return true;
} return false;
}
}
}