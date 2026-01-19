package com.example.modmenu.network;

import com.example.modmenu.store.StorePriceManager;
import com.example.modmenu.store.SkillManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class AtmosphericControlPacket {
    private final int action; // 0: Sun, 1: Rain, 2: Thunder, 3: Day, 4: Night, 5: FireSpread, 6: MobSpawn, 7: FastTick, 8: NormalTick

    public AtmosphericControlPacket(int action) {
        this.action = action;
    }

    public AtmosphericControlPacket(FriendlyByteBuf buf) {
        this.action = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(action);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                StorePriceManager.SkillData data = StorePriceManager.getSkills(player.getUUID());
                if (SkillManager.getActiveRank(data, "UTILITY_ATMOSPHERIC_CONTROL") > 0) {
                    switch (action) {
                        case 0 -> player.serverLevel().setWeatherParameters(6000, 0, false, false);
                        case 1 -> player.serverLevel().setWeatherParameters(0, 6000, true, false);
                        case 2 -> player.serverLevel().setWeatherParameters(0, 6000, true, true);
                        case 3 -> player.serverLevel().setDayTime(1000);
                        case 4 -> player.serverLevel().setDayTime(13000);
                        case 5 -> {
                            boolean current = player.serverLevel().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOFIRETICK);
                            player.serverLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOFIRETICK).set(!current, player.serverLevel().getServer());
                        }
                        case 6 -> {
                            boolean current = player.serverLevel().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING);
                            player.serverLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(!current, player.serverLevel().getServer());
                        }
                        case 7 -> player.serverLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_RANDOMTICKING).set(300, player.serverLevel().getServer());
                        case 8 -> player.serverLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_RANDOMTICKING).set(3, player.serverLevel().getServer());
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
