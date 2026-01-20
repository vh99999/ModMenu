package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ToggleSkillPacket {
    private final String skillId;

    public ToggleSkillPacket(String skillId) {
        this.skillId = skillId;
    }

    public ToggleSkillPacket(FriendlyByteBuf buf) {
        this.skillId = buf.readUtf();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.skillId);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                StorePriceManager.SkillData data = StorePriceManager.getSkills(player.getUUID());
                if (data.unlockedRanks.getOrDefault(skillId, 0) > 0) {
                    if (data.activeToggles.contains(skillId)) {
                        data.activeToggles.remove(skillId);
                    } else {
                        data.activeToggles.add(skillId);
                        // If current selected rank is 0, set it to 1
                        if (data.skillRanks.getOrDefault(skillId, 0) == 0) {
                            data.skillRanks.put(skillId, 1);
                        }
                    }
                    StorePriceManager.markDirty(player.getUUID());
                    StorePriceManager.applyAllAttributes(player);
                    StorePriceManager.sync(player);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
