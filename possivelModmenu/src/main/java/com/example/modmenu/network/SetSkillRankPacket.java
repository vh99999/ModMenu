package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SetSkillRankPacket {
    private final String skillId;
    private final int rank;

    public SetSkillRankPacket(String skillId, int rank) {
        this.skillId = skillId;
        this.rank = rank;
    }

    public SetSkillRankPacket(FriendlyByteBuf buf) {
        this.skillId = buf.readUtf();
        this.rank = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(skillId);
        buf.writeInt(rank);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                StorePriceManager.SkillData data = StorePriceManager.getSkills(player.getUUID());
                int maxUnlocked = data.unlockedRanks.getOrDefault(skillId, 0);
                if (rank >= 0 && rank <= maxUnlocked) {
                    data.skillRanks.put(skillId, rank);
                    StorePriceManager.markDirty(player.getUUID());
                    StorePriceManager.applyAllAttributes(player);
                    StorePriceManager.sync(player);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
