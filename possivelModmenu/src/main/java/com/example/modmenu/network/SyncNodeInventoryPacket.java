package com.example.modmenu.network;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
public class SyncNodeInventoryPacket {
private final UUID nodeId;
private final List<ItemStack> inventory;
private final List<Integer> slotX;
private final List<Integer> slotY;
private ResourceLocation guiTexture;
public SyncNodeInventoryPacket(UUID nodeId, List<ItemStack> inventory, List<Integer> slotX, List<Integer> slotY) {
this(nodeId, inventory, slotX, slotY, null);
}
public SyncNodeInventoryPacket(UUID nodeId, List<ItemStack> inventory, List<Integer> slotX, List<Integer> slotY, ResourceLocation guiTexture) {
this.nodeId = nodeId;
this.inventory = inventory;
this.slotX = slotX;
this.slotY = slotY;
this.guiTexture = guiTexture;
}
public SyncNodeInventoryPacket(FriendlyByteBuf buf) {
this.nodeId = buf.readUUID();
int size = Math.min(buf.readInt(), 1000);
this.inventory = new ArrayList<>(Math.max(0, size));
this.slotX = new ArrayList<>(Math.max(0, size));
this.slotY = new ArrayList<>(Math.max(0, size));
for (int i = 0; i < size; i++) {
this.inventory.add(buf.readItem());
this.slotX.add(buf.readInt());
this.slotY.add(buf.readInt());
}
if (buf.readBoolean()) this.guiTexture = buf.readResourceLocation();
}
    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(nodeId);
        // Limit number of items to avoid oversized packets (2.0 MB limit)
        int size = Math.min(inventory.size(), 500);
        buf.writeInt(size);
        for (int i = 0; i < size; i++) {
            buf.writeItem(inventory.get(i));
            buf.writeInt(slotX.get(i));
            buf.writeInt(slotY.get(i));
        }
        buf.writeBoolean(guiTexture != null);
        if (guiTexture != null) buf.writeResourceLocation(guiTexture);
    }
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientPacketHandler.handleSyncNodeInventory(nodeId, inventory, slotX, slotY, guiTexture);
        });
        ctx.get().setPacketHandled(true);
    }
}
